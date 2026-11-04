package uk.xa0.dsh.diag

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The **native** crash record: the trace a RenderThread SIGSEGV leaves behind, read back
 * through [ApplicationExitInfo] instead of through adb.
 *
 * ## Why [CrashLog] cannot do this
 *
 * [CrashLog] observes an *uncaught Java exception*. A native crash never reaches it: the
 * process dies inside the graphics driver or in libhwui, the JVM is never asked, and the
 * handler is not run. Two crashes on the test handset were exactly that — "Fatal signal 11
 * (SIGSEGV) ... tid 2180 (RenderThread)" with no Java frame anywhere — and the only copy of
 * the backtrace lived in logcat, which this development box cannot read because it has no
 * adb and no cable.
 *
 * The platform keeps those traces itself. [ApplicationExitInfo] is the supported,
 * permission-free way for an app to read *its own* past deaths, and for a native crash it
 * carries the debuggerd tombstone text. So the report comes back through the app, on the
 * next launch, with no adb in the loop — which is the same argument [CrashLog] makes, one
 * layer down.
 *
 * ## What it does not promise
 *
 * The platform may hand back an empty trace: some OEM builds trim exit-info, and the
 * history is bounded (the last few deaths, not all of them). An empty trace is still
 * worth keeping — the reason, the timestamp and the description are facts — so the file is
 * written either way and says "no trace" rather than pretending the crash did not happen.
 *
 * ## Debug only, and why that is the right boundary
 *
 * Installed from [uk.xa0.dsh.DshApplication] under BuildConfig.DEBUG, exactly like
 * [CrashLog]: a shipped build must not spend a launch reading its own death records. The
 * Diagnostics page says so.
 */
object NativeCrashLog {

    const val TAG = "DshNative"

    /** The same directory [CrashLog] writes to, so the Diagnostics page reads one place. */
    private const val DIR = "diag"

    /**
     * Prefix, distinct from [CrashLog]'s "crash-", so one listing can hold both kinds and
     * neither prune can delete the other's files.
     */
    private const val PREFIX = "native-"

    /** Kept small: a tombstone is tens of KiB and this is a diagnostic, not an archive. */
    const val MAX_REPORTS = 6

    /** How far back the platform is asked for deaths. */
    private const val MAX_LOOKBACK = 12

    /** One report's ceiling, so a runaway trace cannot become a file nobody can share. */
    private const val MAX_BYTES = 128 * 1024

    @Volatile
    private var dir: File? = null

    /** Point the reader at base/diag. A no-op without a usable directory, as in [CrashLog]. */
    fun install(base: File) {
        val d = File(base, DIR)
        if (!d.exists() && !d.mkdirs()) return
        dir = d
    }

    /**
     * Read this process's recent deaths and write the ones not already on disk.
     *
     * Safe to call on every launch and cheap when nothing new happened: the file name
     * carries the death's own timestamp and pid, so an already-recorded death is skipped
     * rather than rewritten. Call it off the main thread — [ApplicationExitInfo] is a
     * binder call and reading the trace touches the filesystem.
     */
    fun capture(context: Context) {
        val d = dir ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        val exits = runCatching {
            am.getHistoricalProcessExitReasons(context.packageName, 0, MAX_LOOKBACK)
        }.getOrNull() ?: return
        exits.forEach { info ->
            if (!isInteresting(info.reason)) return@forEach
            runCatching { write(d, info) }
        }
        prune(d)
    }

    /** Every kept native report, newest first. */
    fun reports(): List<CrashReport> {
        val d = dir ?: return emptyList()
        val files = d.listFiles { file -> file.isFile && file.name.startsWith(PREFIX) }
            ?: return emptyList()
        return files.sortedByDescending { it.name }
            .mapNotNull { file ->
                runCatching {
                    CrashReport(file = file, text = file.readText(), bytes = file.length())
                }.getOrNull()
            }
    }

    /** Delete every kept native report. */
    fun clear() {
        val d = dir ?: return
        d.listFiles { file -> file.isFile && file.name.startsWith(PREFIX) }
            ?.forEach { runCatching { it.delete() } }
    }

    /** Delete one report. */
    fun delete(report: CrashReport) {
        runCatching { report.file.delete() }
    }

    /**
     * Which deaths are worth a file.
     *
     * A native crash is the case this exists for. A Java crash is included because the
     * platform's copy is written *before* the process dies and is therefore more reliable
     * than anything the in-process handler can flush. An ANR is included because a frozen
     * frame and a dead frame look the same to a reader holding a phone that stopped
     * responding.
     *
     * Everything else — a user swipe-away, a low-memory kill, a package update — is the
     * system doing its job and is not a bug report.
     */
    private fun isInteresting(reason: Int): Boolean = when (reason) {
        ApplicationExitInfo.REASON_CRASH_NATIVE,
        ApplicationExitInfo.REASON_CRASH,
        ApplicationExitInfo.REASON_ANR,
        -> true

        else -> false
    }

    private fun write(dir: File, info: ApplicationExitInfo) {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
            .format(Date(info.timestamp))
        val name = PREFIX + stamp + "-" + info.pid + ".txt"
        val file = File(dir, name)
        // Already recorded on an earlier launch. Rewriting it would be harmless and would
        // also re-touch every old report's mtime, which is how "newest first" would stop
        // meaning anything.
        if (file.exists()) return

        val trace = runCatching {
            info.traceInputStream?.use { stream -> stream.readBytes() }
        }.getOrNull()

        val body = buildString {
            // The header lines are deliberately the same shape [CrashReport] parses, so the
            // Diagnostics page needs no second reader for the two kinds of report.
            append("time: ").append(ISO.format(Date(info.timestamp))).append('\n')
            append("thread: ").append(threadNameOf(trace)).append('\n')
            append("exception: ").append(exceptionOf(info, trace)).append('\n')
            append("message: ").append(info.description ?: "").append('\n')
            append("version: ").append(uk.xa0.dsh.BuildConfig.VERSION_NAME)
                .append(" (").append(uk.xa0.dsh.BuildConfig.VERSION_CODE).append(")\n")
            append("reason: ").append(reasonName(info.reason))
                .append(" pid: ").append(info.pid).append('\n')
            append("importance: ").append(importanceName(info.importance)).append('\n')
            append('\n')
            if (trace == null || trace.isEmpty()) {
                // Said plainly: a missing trace is the platform's answer, not this app's
                // failure, and a reader who sees the words knows not to go looking.
                append("no trace: the platform returned none for this exit reason.\n")
            } else {
                append(String(trace, Charsets.UTF_8))
            }
        }.let { text ->
            if (text.length > MAX_BYTES) text.take(MAX_BYTES) + "\n… truncated\n" else text
        }

        file.writeText(body)
        Log.e(TAG, "recorded " + name + ": " + exceptionOf(info, trace))
    }

    /**
     * The signal, when the tombstone names one.
     *
     * "signal 11 (SIGSEGV), code 1 (SEGV_MAPERR)" is the line that tells a reader whether
     * this is a null dereference, a use-after-free or a bad pointer, so it is lifted into
     * the headline rather than left to be found in a thousand-line trace.
     */
    private fun exceptionOf(info: ApplicationExitInfo, trace: ByteArray?): String {
        val head = reasonName(info.reason)
        if (trace == null) return head
        val text = String(trace, Charsets.UTF_8)
        val signal = text.lineSequence()
            .firstOrNull { it.startsWith("signal ") }
            ?.trim()
        return if (signal != null) head + ": " + signal else head
    }

    /** The crashing thread, from the tombstone's own "name:" field when it is there. */
    private fun threadNameOf(trace: ByteArray?): String {
        if (trace == null) return "unknown"
        val text = String(trace, Charsets.UTF_8)
        val named = text.lineSequence()
            .firstOrNull { it.contains(">>>") && it.contains("<<<") && it.contains("name:") }
            ?.substringAfterLast("name:")
            ?.substringBefore(">>>")
            ?.trim()
        if (!named.isNullOrEmpty()) return named
        return "unknown"
    }

    private fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash"
        ApplicationExitInfo.REASON_CRASH -> "java crash"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        else -> "reason " + reason
    }

    private fun importanceName(importance: Int): String = when (importance) {
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "foreground"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "visible"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "service"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> "cached"
        else -> "importance " + importance
    }

    /** Keep the newest [MAX_REPORTS]; names sort chronologically by construction. */
    private fun prune(dir: File) {
        val files = dir.listFiles { file -> file.isFile && file.name.startsWith(PREFIX) }
            ?: return
        if (files.size <= MAX_REPORTS) return
        files.sortedBy { it.name }
            .take(files.size - MAX_REPORTS)
            .forEach { runCatching { it.delete() } }
    }

    private val ISO = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
}
