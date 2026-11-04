package uk.xa0.dsh

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps the connection to DSH alive with no Activity in existence.
 *
 * This is the answer to "does it push even when the app is closed?". Nothing in
 * Android can hold a WebSocket open for an app that the system has reaped, and
 * there is no push channel here — FCM would need a server, which would mean host
 * changes. A foreground service is the one mechanism the platform provides: while
 * it runs, the process is not a background candidate for the reaper, so the event
 * stream stays connected and an agent's approval or question reaches the phone.
 *
 * The ongoing notification is not decoration; it is the price of the mechanism,
 * and it doubles as the honest state of the connection.
 *
 * What this cannot fix: if the process is *killed outright* — which on this
 * phone's OEM (OPPO/ColorOS) the battery manager will do unless the app is
 * exempted — the socket dies with it, the host fails the pending waterfall with
 * `NO_PROVIDER`, and no notification can be raised for something that never
 * arrived. Hence [BatteryExemption], which asks for the one exemption that makes
 * this reliable on ColorOS.
 */
class DshConnectionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var keepAlive: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as DshApplication
        Attention.ensureChannel(this)
        startForeground(ONGOING_ID, ongoingNotification())
        app.attention.start(app.client)
        // The centre's stream loop reconnects on its own; this only makes sure the
        // socket is actually open even if no other component ever asks for it.
        keepAlive = scope.launch {
            while (true) {
                runCatching { app.client.mux().ensureConnected() }
                // The homescreen widget's reading is *renewed* here, not polled. This
                // loop is the one thing that runs whenever the app can see anything at
                // all, so it is what keeps "checked 4 minutes ago" meaning "the app was
                // still listening four minutes ago" rather than "the pending set has not
                // changed since whenever". The publisher collapses the tick into at most
                // one write per quarter hour, so this costs a boolean per fifteen
                // seconds and a file write per fifteen minutes — and nothing at all once
                // no widget is placed.
                runCatching { app.attention.refreshWidget() }
                delay(CONNECT_INTERVAL_MS)
            }
        }
    }

    /**
     * `START_STICKY` because a service the system killed for memory should come
     * back: the whole point is to be reachable when the user is not looking.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A stop request that raced the start: honour it here rather than in the caller,
        // because by the time the caller's stopService arrived this service may not have
        // been created yet — and a service that starts after being asked to stop would sit
        // in the shade with a notification nobody wants.
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    /**
     * Stop being a foreground service **first**, then clean up.
     *
     * ## The crash this prevents
     *
     * Recorded on the device, on the main thread, at a cold start:
     *
     *   android.app.RemoteServiceException$ForegroundServiceDidNotStopInTimeException:
     *     A foreground service of type dataSync did not stop within its timeout:
     *     ComponentInfo{uk.xa0.dsh.debug/uk.xa0.dsh.DshConnectionService}
     *
     * That exception is thrown by `ActivityThread` when a service was stopped while it
     * still held a foreground-service state of type `dataSync` and did not leave it within
     * the platform's deadline. The platform requires the app to say "I am done being
     * foreground" explicitly — `stopForeground` — and it must be said *before* the
     * teardown, because `super.onDestroy()` is what removes the service record and the
     * deadline is measured against that.
     *
     * The previous code never called it at all. It got away with it most of the time
     * because the process was usually killed or the service restarted; a start that stops
     * the service promptly — which is exactly what [uk.xa0.dsh.model.runForceQuit] does —
     * made the deadline miss reachable.
     *
     * `STOP_FOREGROUND_REMOVE` rather than `DETACH`: the ongoing notification is the
     * price of the mechanism and has no meaning once the mechanism is stopping. Leaving a
     * stale "DSH connected" in the shade would be a lie about the connection.
     */
    override fun onDestroy() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }
        keepAlive?.cancel()
        super.onDestroy()
    }

    private fun ongoingNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, Attention.CHANNEL_ID_CONNECTION)
            // LOW: it is a standing fact, not something to be alerted about.
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSmallIcon(R.drawable.ic_stat_dsh)
            .setContentTitle("DSH connected")
            .setContentText("Ready for approvals and questions")
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val ONGOING_ID = 1000
        private const val CONNECT_INTERVAL_MS = 15_000L

        /** Starts (or re-prompts) the standing connection. */
        fun start(context: Context) {
            val intent = Intent(context, DshConnectionService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        /**
         * Stops the standing connection.
         *
         * [stopForeground] is not redundant with the service's own `onDestroy`: the
         * platform's foreground-service deadline starts when the stop is *requested*, and
         * a service that is busy in a coroutine may not reach `onDestroy` in time. Asking
         * the platform to drop the state here — from the caller's thread, immediately —
         * is what makes the deadline comfortably met instead of marginally met.
         *
         * The intent carries `ACTION_STOP` so a service that is somehow still starting
         * can see why it was asked to go, rather than being torn down mid-`onCreate`.
         */
        fun stop(context: Context) {
            runCatching {
                context.stopService(
                    Intent(context, DshConnectionService::class.java)
                        .setAction(ACTION_STOP),
                )
            }
        }

        /** The action [stop] sends; see its note. */
        const val ACTION_STOP = "uk.xa0.dsh.action.STOP_CONNECTION"
    }
}

/**
 * Android's battery optimisation is what actually reaps this app, and on ColorOS
 * it is aggressive enough that a foreground service alone is not enough. Asking
 * for the exemption is the difference between "usually delivers" and "delivers".
 */
object BatteryExemption {

    fun isExempt(context: Context): Boolean {
        val power = context.getSystemService(android.os.PowerManager::class.java) ?: return false
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Opens the system's exemption prompt. This app is sideloaded and personal, so
     * the Play policy that reserves this intent for a narrow set of app types does
     * not apply.
     */
    fun request(context: Context) {
        if (isExempt(context)) return
        runCatching {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(android.net.Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
