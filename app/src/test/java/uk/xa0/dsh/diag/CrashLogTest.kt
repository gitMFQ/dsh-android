package uk.xa0.dsh.diag

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uk.xa0.dsh.scroll.HarnessApplication

/**
 * The crash record, and the properties the page depends on.
 *
 * [CrashLog] is the only path by which a device-side stack trace reaches a development
 * box that has no `adb`, so what matters is that it **writes**, that it **stays bounded**,
 * and that the writer and the reader agree about where the reports are. All three are
 * asserted here rather than on a device because the writing has to happen on a dying
 * thread, which is the one thing a device cannot be asked to reproduce on demand.
 *
 * The handler is a process singleton, so [install] is called once for the class and each
 * test clears the directory instead. Calling `install` per test is what the first
 * version did, and it was wrong for a reason worth keeping in mind: installing does not
 * re-point an already-installed handler unless the handler reads the directory rather
 * than capturing it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = HarnessApplication::class)
class CrashLogTest {

    @get:Rule
    val folder = TemporaryFolder()

    @After
    fun clearReports() = CrashLog.clear()

    /** Record one crash the way the platform does: through the installed handler. */
    private fun crash(error: Throwable) {
        CrashLog.install(folder.root)
        Thread.getDefaultUncaughtExceptionHandler()
            ?.uncaughtException(Thread.currentThread(), error)
    }

    /**
     * A crash is written with the fields the page reads back.
     *
     * The headline is the exception, the timestamp is the one the report recorded, and
     * the stack is present — which together are the whole contract between the writer
     * and [uk.xa0.dsh.ui.settings.CrashLogSettingCards].
     */
    @Test
    fun `a crash is recorded with its type, time and stack`() {
        crash(IllegalStateException("No NavigationEventDispatcher was provided"))

        val reports = CrashLog.reports()
        assertEquals("one crash should produce one report", 1, reports.size)
        val report = reports.single()
        assertEquals("java.lang.IllegalStateException", report.summary)
        assertTrue(
            "the stack must be in the report, or it cannot be diagnosed",
            report.text.contains("IllegalStateException"),
        )
        assertTrue(
            "the throw site must be in the report",
            report.text.contains("CrashLogTest"),
        )
        assertTrue("the time must be recorded", report.time.isNotEmpty())
        assertTrue("the thread must be recorded", report.text.contains("thread: "))
    }

    /**
     * The list is newest-first, because that is the order the page shows and the only
     * order that puts the crash being reproduced at the top.
     */
    @Test
    fun `reports are newest first`() {
        crash(RuntimeException("first"))
        Thread.sleep(5)
        crash(RuntimeException("second"))

        val reports = CrashLog.reports()
        assertEquals(2, reports.size)
        assertEquals("java.lang.RuntimeException", reports[0].summary)
        assertTrue(reports[0].text.contains("second"))
        assertTrue(reports[1].text.contains("first"))
    }

    /**
     * Two crashes inside one millisecond still get their own file.
     *
     * A crash loop fires far faster than the second-resolution stamp the name starts
     * with, so the name carries a counter as well. Without it the second report silently
     * overwrote the first, which is the one thing this must never do.
     */
    @Test
    fun `two crashes in one millisecond do not collide`() {
        crash(RuntimeException("one"))
        crash(RuntimeException("two"))
        assertEquals("both must survive", 2, CrashLog.reports().size)
    }

    /**
     * The kept set is capped, and the cap drops the *oldest*.
     *
     * A crash loop would otherwise fill the device: the to-dos crash fires on every
     * appearance of the card. The newest report has to survive, because it is the one
     * the reader is about to share.
     */
    @Test
    fun `the kept set is capped and keeps the newest`() {
        repeat(CrashLog.MAX_REPORTS + 4) { index ->
            crash(RuntimeException("crash " + index))
        }

        val reports = CrashLog.reports()
        assertEquals("the cap must hold", CrashLog.MAX_REPORTS, reports.size)
        val newest = "crash " + (CrashLog.MAX_REPORTS + 3)
        assertTrue(
            "the newest crash must survive the prune, got " + reports[0].text.take(200),
            reports[0].text.contains(newest),
        )
    }

    /** Clearing is the page's own destructive action, and it has to actually empty it. */
    @Test
    fun `clear removes every report`() {
        crash(RuntimeException("gone"))
        assertFalse(CrashLog.reports().isEmpty())

        CrashLog.clear()
        assertTrue(CrashLog.reports().isEmpty())
    }

    /** Deleting one leaves the others, which is what a per-report Delete promises. */
    @Test
    fun `deleting one report leaves the rest`() {
        crash(RuntimeException("keep me"))
        Thread.sleep(5)
        crash(RuntimeException("delete me"))

        val newest = CrashLog.reports().first()
        assertTrue(newest.text.contains("delete me"))
        CrashLog.delete(newest)

        val left = CrashLog.reports()
        assertEquals(1, left.size)
        assertTrue(left.single().text.contains("keep me"))
    }
}
