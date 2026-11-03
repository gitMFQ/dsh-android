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
 * The crash record, and the two properties the page depends on.
 *
 * [CrashLog] is the only path by which a device-side stack trace reaches a development
 * box that has no `adb`, so the two things that matter are that it **writes** and that it
 * **stays bounded**. Both are asserted here rather than on a device because the writing
 * has to happen on a dying thread, which is the one thing a device cannot be asked to
 * reproduce on demand.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = HarnessApplication::class)
class CrashLogTest {

    @get:Rule
    val folder = TemporaryFolder()

    @After
    fun reset() {
        // The object is a process singleton and the tests share it, so the directory has
        // to be handed back: a later test would otherwise read the previous one's reports.
        CrashLog.clear()
    }

    /**
     * A crash is written with the fields the page reads back.
     *
     * The headline is the exception, the timestamp is the one the report recorded, and
     * the stack is present — which together are the whole contract between the writer
     * and [CrashLogSettingCards].
     */
    @Test
    fun `a crash is recorded with its type, time and stack`() {
        CrashLog.install(folder.root)
        val boom = IllegalStateException("No NavigationEventDispatcher was provided")
        // The handler the app installs is the platform's; call it the way the platform
        // does, on this thread, so the write happens synchronously as designed.
        Thread.getDefaultUncaughtExceptionHandler()
            ?.uncaughtException(Thread.currentThread(), boom)

        val reports = CrashLog.reports()
        assertEquals("one crash should produce one report", 1, reports.size)
        val report = reports.single()
        assertEquals("IllegalStateException", report.summary)
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
        CrashLog.install(folder.root)
        val handler = Thread.getDefaultUncaughtExceptionHandler()
        handler?.uncaughtException(Thread.currentThread(), RuntimeException("first"))
        // Names carry milliseconds, so two reports in the same second still sort.
        Thread.sleep(5)
        handler?.uncaughtException(Thread.currentThread(), RuntimeException("second"))

        val reports = CrashLog.reports()
        assertEquals(2, reports.size)
        assertEquals("RuntimeException", reports[0].summary)
        assertTrue(reports[0].text.contains("second"))
        assertTrue(reports[1].text.contains("first"))
    }

    /**
     * The kept set is capped, and the cap drops the *oldest*.
     *
     * A crash loop would otherwise fill the device: the to-dos crash fires on every
     * appearance of the card, which in a loop is once a second. The newest report has to
     * survive, because it is the one the reader is about to share.
     */
    @Test
    fun `the kept set is capped and keeps the newest`() {
        CrashLog.install(folder.root)
        val handler = Thread.getDefaultUncaughtExceptionHandler()
        repeat(CrashLog.MAX_REPORTS + 4) { index ->
            handler?.uncaughtException(Thread.currentThread(), RuntimeException("crash " + index))
            Thread.sleep(2)
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
        CrashLog.install(folder.root)
        Thread.getDefaultUncaughtExceptionHandler()
            ?.uncaughtException(Thread.currentThread(), RuntimeException("gone"))
        assertFalse(CrashLog.reports().isEmpty())

        CrashLog.clear()
        assertTrue(CrashLog.reports().isEmpty())
    }

    /** Deleting one leaves the others, which is what a per-report Delete promises. */
    @Test
    fun `deleting one report leaves the rest`() {
        CrashLog.install(folder.root)
        val handler = Thread.getDefaultUncaughtExceptionHandler()
        handler?.uncaughtException(Thread.currentThread(), RuntimeException("keep me"))
        Thread.sleep(5)
        handler?.uncaughtException(Thread.currentThread(), RuntimeException("delete me"))

        val newest = CrashLog.reports().first()
        assertTrue(newest.text.contains("delete me"))
        CrashLog.delete(newest)

        val left = CrashLog.reports()
        assertEquals(1, left.size)
        assertTrue(left.single().text.contains("keep me"))
    }
}
