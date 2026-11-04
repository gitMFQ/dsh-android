package uk.xa0.dsh.diag

import android.content.Context
import android.util.Log
import java.io.File

/**
 * The launch guard: how a build that dies before its own settings page can still be
 * brought back to a usable state.
 *
 * ## The situation this exists for
 *
 * "It closes as soon as it opens" is the one crash a *setting* cannot fix, because the
 * setting lives behind the screen that never finishes drawing. The liquid-glass switch was
 * added for exactly this crash and was useless for it: a reader who cannot reach Settings
 * cannot turn anything off.
 *
 * So the decision has to be made by the app, from evidence it already has. The evidence is
 * the platform's own record of how this process ended last time —
 * [android.app.ApplicationExitInfo] — and the rule is deliberately blunt:
 *
 * > **Two consecutive starts that ended in a crash, with no clean run in between, turn the
 * > expensive material off.**
 *
 * Blunt is the point. A guard that tried to be clever about *which* crash would need the
 * stack trace to be attributable to a component, and a RenderThread SIGSEGV's frames are
 * inside libhwui and the driver — there is nothing there to attribute. What is known for
 * certain is that the process died twice in a row before it could finish starting, and
 * that the one material this app paints which is not plain Compose drawing is the glass.
 *
 * ## Why "before it could finish starting"
 *
 * [markStarted] is called from [uk.xa0.dsh.MainActivity] once the first frame is up. An
 * exit recorded before that marker is a start that never got anywhere; an exit after it is
 * an ordinary crash in a working app, and the guard stays out of the way. Without the
 * distinction, an app that crashed once a day while a transcript was being read would
 * silently lose its material for no reason.
 *
 * ## What it does not do
 *
 * It does not clear data, does not restart itself, does not disable the app. It writes one
 * preference — liquidGlass = false — and records the decision so the Diagnostics page can
 * say it happened and offer to undo it. Everything else keeps working; the flat tier is a
 * complete design, not a broken one.
 *
 * Debug and release alike: this one is not a diagnostic, it is a recovery path.
 */
object LaunchGuard {

    const val TAG = "DshLaunchGuard"

    /** How many crash-ends in a row are enough. Two, so a single fluke does not strip the UI. */
    private const val CRASHES_BEFORE_DEGRADING = 2

    /** The preference key, the same string [uk.xa0.dsh.data.ConfigStore] uses. */
    private const val KEY_LIQUID_GLASS = "liquidGlass"

    /** The level key, likewise; see [uk.xa0.dsh.data.ConfigStore]. */
    private const val KEY_LIQUID_GLASS_LEVEL = "liquidGlassLevel"

    /** The store the UI reads — the same file [uk.xa0.dsh.data.ConfigStore] uses. */
    private const val CONFIG_PREFS = "dsh_config"

    /** What the guard last chose, so the Diagnostics page can say which step it took. */
    private const val KEY_DEGRADED_LEVEL = "degradedLevel"

    /** The guard's own bookkeeping, in its own store so the two cannot corrupt each other. */
    private const val PREFS = "dsh_launch_guard"
    /** Non-zero while a launch is in progress; deleted once a first frame exists. */
    private const val KEY_STARTED_AT = "startedAt"
    private const val KEY_DEGRADED_AT = "degradedAt"

    /** How many launches in a row have died without reaching a first frame. */
    private const val KEY_CRASH_STREAK = "crashStreak"

    /**
     * Decide whether this launch needs to degrade, and do it.
     *
     * Called from [uk.xa0.dsh.DshApplication.onCreate] **before** the UI reads its config,
     * so the value it writes is the value the first frame is painted with.
     *
     * @return true when this launch turned the material off, so the caller can say so.
     */
    fun check(context: Context): Boolean {
        // An explicit level from the reader wins over every inference below.
        levelOverride(context)?.let { wanted ->
            if (currentLevel(context) != wanted) {
                degradeTo(context, wanted)
                Log.e(TAG, "glass-level.txt asked for " + wanted)
                return true
            }
            return false
        }
        // The external marker wins over everything else, and is checked next: it is the
        // route that works when neither the preferences nor the UI can be reached.
        if (safeModeRequested(context)) {
            // The marker is a request for the flat tier specifically — a reader who made
            // it wants a *usable* app, not a prettier one that still crashes.
            if (currentLevel(context) != uk.xa0.dsh.data.LiquidGlassLevel.OFF) {
                degradeTo(context, uk.xa0.dsh.data.LiquidGlassLevel.OFF)
                Log.e(TAG, "safe-mode marker found; glass is off")
            }
            return true
        }
        // Count **our own** unfinished starts, not the platform's death records.
        //
        // ## Why ApplicationExitInfo had to go
        //
        // Two versions of this guard used it, and on the test handset it reports nothing:
        // `dumpsys dropbox --print data_app_native_crash` answers "No entries found",
        // every exit reason this app could see came back empty, and the guard therefore
        // never fired once — three separate reports of "it does not degrade by itself",
        // all with the crashes plainly happening.
        //
        // The platform is allowed to be silent; the app is not. So the evidence is now
        // local and unconditional: **every launch writes a start marker and deletes it
        // only after the first frame.** A marker still present at the next launch means the
        // previous launch died — no exit record, no permission, no API level, no OEM
        // behaviour involved.
        val pending = readLong(context, KEY_STARTED_AT)
        val streak = if (pending > 0L) {
            // The previous launch never reached its first frame.
            val next = readInt(context, KEY_CRASH_STREAK) + 1
            writeInt(context, KEY_CRASH_STREAK, next)
            next
        } else {
            // No marker: either a clean previous run, or a launch that died before even
            // writing one (the marker is written at the very top of onCreate). Neither is
            // a streak, so it starts again.
            0
        }
        Log.i(TAG, "unfinished streak=" + streak + " pending=" + pending)
        if (streak < CRASHES_BEFORE_DEGRADING) return false
        // Step **down one level**, not "turn it off once".
        //
        // ## Why the first version could not help
        //
        // It degraded once and then returned early forever (`if (degradedAt > 0) return
        // false`). On the device the app kept crashing at the level it had been dropped
        // to, and the guard had already spent its one shot — so it sat at "blur only"
        // while the app died every launch, which is what "it does not degrade by itself"
        // meant.
        //
        // The material has three levels and a crash is evidence about *one* of them. So
        // each qualifying streak drops exactly one step: LIQUID -> BLUR -> OFF. If the app
        // still dies after OFF, the material is not the cause and the guard stops trying,
        // which is the honest answer rather than an infinite loop.
        val current = currentLevel(context)
        val next = nextLevelDown(current) ?: return false
        degradeTo(context, next)
        return true
    }

    /**
     * Record that this process reached its first frame.
     *
     * Written with commit, not apply: the process is about to start doing work that may
     * kill it, and a marker still queued in a background write is a marker that is not
     * there when the next launch reads it.
     */
    fun markStarted(context: Context) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                // Delete the marker: this launch *did* reach a first frame, so it is not
                // an unfinished start. This is the whole mechanism — see check().
                .remove(KEY_STARTED_AT)
                .putInt(KEY_CRASH_STREAK, 0)
                .commit()
        }
    }

    /**
     * Write the marker that says "a launch is in progress".
     *
     * Called at the very top of [uk.xa0.dsh.DshApplication.onCreate], before anything that
     * can fail. It is deleted by [markStarted] once the first frame is up, so its mere
     * presence at the next launch is the evidence: **this process died before it ever
     * became usable.**
     *
     * `commit`, not `apply`: the process is about to start work that may kill it, and a
     * marker still queued in a background write is a marker that is not there when the
     * next launch looks.
     */
    fun markLaunching(context: Context) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_STARTED_AT, System.currentTimeMillis())
                .commit()
        }
    }

    /** Whether an earlier launch already degraded the material, for the Diagnostics note. */
    fun degradedAt(context: Context): Long = readLong(context, KEY_DEGRADED_AT)

    /** The level the guard last forced, or blank when it has never fired. */
    fun degradedLevel(context: Context): String = runCatching {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DEGRADED_LEVEL, null)
    }.getOrNull().orEmpty()

    /**
     * Undo the guard, and put the material back where it started.
     *
     * Both halves matter. Clearing the flag alone would leave the *level* at whatever the
     * last step-down chose — the reader would press "try the full material again", see the
     * note disappear, and still be looking at the flat tier. The level is the setting; the
     * flag is only the record of who wrote it.
     */
    fun reset(context: Context) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_DEGRADED_AT)
                .remove(KEY_DEGRADED_LEVEL)
                .commit()
            context.getSharedPreferences(CONFIG_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LIQUID_GLASS_LEVEL, uk.xa0.dsh.data.LiquidGlassLevel.LIQUID)
                .commit()
        }
    }

    /**
     * The one write that matters: turn the material off in the store the UI reads.
     *
     * Deliberately *not* through [uk.xa0.dsh.data.ConfigStore]. That store is encrypted,
     * and constructing it is one of the things a crashing start may be dying in — a
     * recovery path that depends on the thing that broke is not a recovery path. The plain
     * preference is a different file the store never touches, and
     * [uk.xa0.dsh.data.ConfigStore.load] prefers it when it is present; see the
     * degradation key there.
     */
    private fun degradeTo(context: Context, level: String) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_DEGRADED_AT, System.currentTimeMillis())
                .putString(KEY_DEGRADED_LEVEL, level)
                .commit()
            Log.e(TAG, "two starts died before their first frame; glass is now " + level)
        }
    }

    /**
     * The level the app is currently on, from the plain preference.
     *
     * Read from the *store the UI reads*, not from a copy the guard keeps: a reader who
     * chose a level by hand should be stepped down from where they actually are.
     */
    private fun currentLevel(context: Context): String {
        val stored = runCatching {
            context.getSharedPreferences(CONFIG_PREFS, Context.MODE_PRIVATE)
                .getString(KEY_LIQUID_GLASS_LEVEL, null)
        }.getOrNull()
        return uk.xa0.dsh.data.LiquidGlassLevel.parse(stored)
    }

    /**
     * One step down, or null at the floor.
     *
     * The order is the material's own order of GPU cost — see LiquidGlassLevel. Null at
     * OFF means "there is nothing left to turn off", and the guard then stays silent: an
     * app that dies on the flat tier has a different problem and pretending otherwise
     * would only hide it.
     */
    private fun nextLevelDown(level: String): String? = when (level) {
        uk.xa0.dsh.data.LiquidGlassLevel.LIQUID -> uk.xa0.dsh.data.LiquidGlassLevel.BLUR
        uk.xa0.dsh.data.LiquidGlassLevel.BLUR -> uk.xa0.dsh.data.LiquidGlassLevel.OFF
        else -> null
    }

    /**
     * A marker file the reader can create **without the app running**.
     *
     * ## Why a file and not a setting
     *
     * Because the app never reaches its settings page. This is the escape hatch for a
     * build that closes on launch: drop a file at
     *
     *     Android/data/uk.xa0.dsh.debug/files/safe-mode
     *
     * — any file manager can do that, and so can a shell — and the next start comes up on
     * the flat tier. It needs no keystore, no preferences and no UI, which is the whole
     * point: it must work when nothing else does.
     *
     * The file's *contents* are ignored and its presence is the whole signal, so an empty
     * file from a file manager is as good as one written by hand. Delete it to leave.
     *
     * External *app-specific* storage, not shared storage: it needs no permission on any
     * API this app supports, and a file manager still sees it under Android/data.
     */
    private const val SAFE_MODE_FILE = "safe-mode"

    /** Whether the external safe-mode marker is present. */
    fun safeModeRequested(context: Context): Boolean = runCatching {
        context.getExternalFilesDir(null)?.let { File(it, SAFE_MODE_FILE).exists() } == true
    }.getOrDefault(false)

    /**
     * A glass level the reader set **from outside the app**, or null.
     *
     * ## Why a file beats every other route here
     *
     * The app closes during rasterisation of the chat screen, so the settings page is not
     * reachable, the guard has to infer what happened, and every inference has been wrong
     * once. A file has no inference in it: the reader states the level, the app obeys.
     *
     * Write one of `liquid`, `blur` or `off` into
     *
     *     Android/data/<pkg>/files/glass-level.txt
     *
     * and the next launch runs at that level. Delete the file to go back to whatever the
     * app has stored. A trailing newline or stray whitespace is tolerated, because a file
     * written by hand or by `echo` will have one.
     *
     * This is read *before* the launch guard, and wins over it: an explicit instruction
     * from the person holding the phone outranks anything the app guessed.
     */
    fun levelOverride(context: Context): String? = runCatching {
        val file = context.getExternalFilesDir(null)?.let { File(it, LEVEL_FILE) } ?: return null
        if (!file.exists()) return null
        val text = file.readText().trim().lowercase()
        if (text in uk.xa0.dsh.data.LiquidGlassLevel.all) text else null
    }.getOrDefault(null)

    /** The file [levelOverride] reads. */
    const val LEVEL_FILE = "glass-level.txt"

    private fun readLong(context: Context, key: String): Long = runCatching {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(key, 0L)
    }.getOrDefault(0L)

    private fun readInt(context: Context, key: String): Int = runCatching {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(key, 0)
    }.getOrDefault(0)

    private fun writeInt(context: Context, key: String, value: Int) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(key, value).commit()
        }
    }
}
