package uk.xa0.dsh.diag

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The boot beacon: how far a launch got, written where a dead process can still be read.
 *
 * ## Why this exists
 *
 * The crash on the test handset is a `SIGSEGV` on the RenderThread. It produces no Java
 * exception, so [CrashLog] cannot see it; it produces no tombstone in dropbox on this
 * ROM, so [NativeCrashLog] comes back empty; and it happens before the UI exists, so
 * nothing in the app can report it. Every diagnostic this project had was a *report of a
 * failure*, and a native fault leaves no report at all.
 *
 * So this records the opposite: the **last thing that succeeded**. Each milestone is one
 * line appended to a file, flushed immediately. A process that dies between two
 * milestones leaves the earlier one on disk, and the pair names the step that killed it
 * without needing a stack trace of any kind.
 *
 * ## Why a file and not logcat
 *
 * Same reason as everything else here: this development box has no adb, and the phone's
 * logcat buffer is flooded by the audio HAL at DEBUG level, so a line written at launch
 * is gone before anyone can read it. A file survives the process and is readable from a
 * file manager.
 *
 * ## Cost
 *
 * One `appendText` per milestone, on the calling thread, with a flush. There are fewer
 * than a dozen per launch, they are a few dozen bytes each, and the file is truncated on
 * every cold start. That is cheaper than the first frame.
 */
object BootBeacon {

    const val TAG = "DshBoot"

    private const val FILE = "boot.log"

    /** How many launches the file keeps. Enough to show a pattern, small enough to send. */
    private const val MAX_LAUNCHES = 8

    @Volatile
    private var target: File? = null

    /**
     * Start a new launch's record. Called first thing in Application.onCreate.
     *
     * ## Why this appends instead of truncating
     *
     * The first version rewrote the file on every launch, so the only launch anyone could
     * read was the newest one. That is the wrong half of the evidence: the question is not
     * "how far did one launch get" but **"does the crash point move between launches"**,
     * and a file that keeps only the latest answer cannot show a moving target. It also
     * made the guard unreadable — three launches of "no change" followed by one "stepped
     * down" is the whole story, and truncation threw it away.
     *
     * So: append, separate each launch with a visible marker, and keep the newest
     * [MAX_LAUNCHES] blocks.
     */
    fun begin(context: Context) {
        runCatching {
            val dir = context.getExternalFilesDir(null)?.let { File(it, "diag") }
                ?: File(context.filesDir, "diag")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, FILE)
            val previous = if (file.exists()) file.readLines() else emptyList()
            val kept = trimToLaunches(previous)
            file.writeText(kept.joinToString("\n") + if (kept.isEmpty()) "" else "\n")
            target = file
        }
        mark("===== launch " + STAMP.format(Date()) + " =====")
        mark("application.onCreate: begin")
    }

    /**
     * Keep only the newest [MAX_LAUNCHES] launch blocks.
     *
     * Split on the separator [begin] writes rather than counting lines: a launch that died
     * immediately has one line and one that reached the transcript has thirty, so a line
     * budget would cut the long ones in half.
     */
    private fun trimToLaunches(lines: List<String>): List<String> {
        val starts = lines.indices.filter { lines[it].startsWith("===== launch ") }
        if (starts.size <= MAX_LAUNCHES) return lines
        return lines.subList(starts[starts.size - MAX_LAUNCHES], lines.size)
    }

    /**
     * Record one milestone.
     *
     * Synchronous on purpose. The whole value of this record is that it is on disk before
     * the next statement runs, and a queued write is a write the crash can outrun.
     */
    fun mark(stage: String) {
        val file = target ?: return
        val line = STAMP.format(Date()) + "  " + stage
        runCatching {
            file.appendText(line + "\n")
            Log.i(TAG, stage)
        }
    }

    /** The record so far, for the Diagnostics page. */
    fun read(): String = runCatching { target?.readText().orEmpty() }.getOrDefault("")

    private val STAMP = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
}