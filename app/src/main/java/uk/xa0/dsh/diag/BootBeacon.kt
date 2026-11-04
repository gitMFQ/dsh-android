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

    @Volatile
    private var target: File? = null

    /** Start a new launch's record. Called first thing in Application.onCreate. */
    fun begin(context: Context) {
        runCatching {
            val dir = context.getExternalFilesDir(null)?.let { File(it, "diag") }
                ?: File(context.filesDir, "diag")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, FILE)
            // Truncated, not appended: the question is always "how far did *this* launch
            // get", and a file that accumulates would need a timestamp parser to answer it.
            file.writeText("")
            target = file
        }
        mark("application.onCreate: begin");
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