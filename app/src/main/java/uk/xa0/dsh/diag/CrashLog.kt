package uk.xa0.dsh.diag

import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * The crash record: what took the process down, kept where the reader can get it back.
 *
 * ## Why this exists
 *
 * Two crashes on the device had no trace anybody could read. This box has no `adb`, no
 * emulator and no usable `aapt2`, so a stack trace that only exists in logcat is a stack
 * trace nobody on the development side will ever see — and "it closes when the to-dos
 * appear" is not enough to fix a rendering bug with. The report has to survive the crash
 * and come back through the app itself.
 *
 * ## What it records, and what it deliberately does not
 *
 * The **uncaught exception** — its type, message and full stack, plus the thread it died
 * on — is the whole record. That is what a rendering crash needs: the exception, the
 * frames, and which thread.
 *
 * It is not a general log sink. [ScrollDiag] already streams gestures and frames, and
 * copying that machinery here would mean two ring buffers, two files and two rotation
 * policies to keep in step. A crash is a single event; it gets a single file.
 *
 * ## The ordering rule
 *
 * A crash handler runs on a thread that is already dying, and the platform kills the
 * process as soon as `handleUncaughtException` returns. So the file is written
 * **synchronously, here, before anything else runs** — no executor, no buffered writer
 * left open. The one thing that may not be lost is the one thing this exists for.
 *
 * The previous handler is always called afterwards: this class observes a crash, it does
 * not replace the platform's own reporting (logcat, the Play Console, the debugger).
 *
 * ## Debug only
 *
 * [install] is called from [uk.xa0.dsh.DshApplication] only when `BuildConfig.DEBUG` is
 * true. A release build carries the code but never installs it, so a shipped app writes
 * nothing and reads nothing.
 */
object CrashLog {

    const val TAG = "DshCrash"

    /** Directory name under `filesDir`, matching `diag/scroll.jsonl`'s home. */
    private const val DIR = "diag"

    /** How many reports are kept. Enough to reproduce a bug a few times, bounded. */
    const val MAX_REPORTS = 12

    /**
     * A single report is truncated past this. A stack trace is a few KiB; a runaway
     * cause chain or an OOM dump is not, and an unbounded file is a file that cannot be
     * shared through a chat app.
     */
    private const val MAX_BYTES = 64 * 1024

    private val installed = AtomicBoolean(false)

    /** Monotonic, so two reports inside one millisecond cannot collide on a file name. */
    private val sequence = AtomicInteger(0)

    @Volatile
    private var dir: File? = null

    /**
     * Install the handler, and point it at [base].
     *
     * The *handler* is installed once — installing a second one would chain a second
     * observer onto the platform's and write every crash twice. The **directory**,
     * however, is re-read by every call, and that is deliberate: the handler must write
     * wherever [reports] is going to look, or a process that re-attached (a test, a
     * second Application instance) would record crashes somewhere nothing reads.
     *
     * Capturing the directory in the handler's closure is what got this wrong the first
     * time: the first `install` fixed the path forever, so a later `install` moved
     * [reports] but not the writer, and every report vanished. The path is a field, and
     * the handler reads it.
     *
     * A no-op without a usable directory: a process that cannot write a report should not
     * pretend it can.
     */
    fun install(base: File) {
        val d = File(base, DIR)
        if (!d.exists() && !d.mkdirs()) return
        dir = d
        if (!installed.compareAndSet(false, true)) return

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Synchronous and first: see the ordering rule above. The directory is read
            // here rather than captured, so it always matches what [reports] reads.
            dir?.let { target -> runCatching { write(target, thread, error) } }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Every kept report, newest first. Empty when nothing has crashed or nothing is kept. */
    fun reports(): List<CrashReport> {
        val d = dir ?: return emptyList()
        val files = d.listFiles { file -> file.isFile && file.name.startsWith(PREFIX) }
            ?: return emptyList()
        return files.sortedByDescending { it.name }
            .mapNotNull { file ->
                runCatching {
                    CrashReport(
                        file = file,
                        text = file.readText(),
                        bytes = file.length(),
                    )
                }.getOrNull()
            }
    }

    /** Delete every kept report. The page offers this so the list is not permanent litter. */
    fun clear() {
        val d = dir ?: return
        d.listFiles { file -> file.isFile && file.name.startsWith(PREFIX) }
            ?.forEach { runCatching { it.delete() } }
    }

    /** Delete one report, for a reader who has shared it and wants it gone. */
    fun delete(report: CrashReport) {
        runCatching { report.file.delete() }
    }

    private const val PREFIX = "crash-"

    /**
     * Write one report, then prune to [MAX_REPORTS].
     *
     * The file name leads with a sortable timestamp because [reports] sorts by name —
     * a directory listing's own order is not something to rely on, and a report whose
     * position in the list depends on the filesystem is a report that moves while
     * somebody is reading it.
     */
    private fun write(dir: File, thread: Thread, error: Throwable) {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        // Milliseconds after the seconds, then a counter: a crash loop can put two
        // reports inside one millisecond, and those two must not overwrite each other.
        // The counter is what guarantees the file name is unique; the timestamp is what
        // makes the name *sort* chronologically, which is how [reports] orders itself.
        val millis = System.currentTimeMillis() % 1000
        val seq = sequence.incrementAndGet() % 1000
        val name = PREFIX + stamp + "-" + millis.toString().padStart(3, '0') +
            seq.toString().padStart(3, '0') + ".txt"
        val body = buildString {
            append("time: ").append(ISO.format(Date())).append('\n')
            append("thread: ").append(thread.name).append('\n')
            append("exception: ").append(error.javaClass.name).append('\n')
            append("message: ").append(error.message ?: "").append('\n')
            append("version: ").append(uk.xa0.dsh.BuildConfig.VERSION_NAME)
                .append(" (").append(uk.xa0.dsh.BuildConfig.VERSION_CODE).append(")\n")
            append("\n")
            val writer = StringWriter()
            error.printStackTrace(PrintWriter(writer))
            append(writer.toString())
        }.let { text ->
            if (text.length > MAX_BYTES) text.take(MAX_BYTES) + "\n… truncated\n" else text
        }
        File(dir, name).writeText(body)
        Log.e(TAG, "recorded " + name + ": " + error.javaClass.name + ": " + (error.message ?: ""))
        prune(dir)
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

/**
 * One kept crash, as the page reads it.
 *
 * [text] is the file's whole content — the reader may want all of it in the clipboard,
 * and a page that re-formatted a stack trace would be a second, wrong rendering of it.
 */
data class CrashReport(
    val file: File,
    val text: String,
    val bytes: Long,
) {
    /** The first line after the header block: the exception, for a list row. */
    val summary: String
        get() = text.lineSequence()
            .firstOrNull { it.startsWith("exception: ") }
            ?.removePrefix("exception: ")
            ?: file.name

    /** When it happened, as the report recorded it rather than as the file is named. */
    val time: String
        get() = text.lineSequence()
            .firstOrNull { it.startsWith("time: ") }
            ?.removePrefix("time: ")
            .orEmpty()
}
