package uk.xa0.dsh.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uk.xa0.dsh.data.ThemeMode
import uk.xa0.dsh.diag.CrashLog
import uk.xa0.dsh.scroll.HarnessApplication
import uk.xa0.dsh.ui.theme.DshTheme

/**
 * The Me page's Crash log page.
 *
 * The behaviour worth pinning is not the visual: it is that a reader who has just hit a
 * crash can **find the report, read it, and get it off the device**. That is the whole
 * reason the page exists — this development box has no `adb`, so a trace that cannot be
 * shared through the app is a trace that never gets read.
 *
 * The page draws from the process-wide [CrashLog], so [crash] installs it against a
 * temporary directory and the fixture is written before the page is composed; the
 * assertions are on what the page renders for a given set of reports.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = HarnessApplication::class, qualifiers = "w411dp-h891dp")
class CrashLogPageTest {

    @get:Rule
    val rule = createComposeRule()

    @get:Rule
    val folder = TemporaryFolder()

    @After
    fun clearReports() = CrashLog.clear()

    private fun page() {
        rule.setContent {
            DshTheme(themeMode = ThemeMode.LIGHT) {
                Box(Modifier.fillMaxSize()) {
                    MeSettingsPage(
                        id = "crashes",
                        state = MeSettingsState(),
                        actions = MeSettingsActions(),
                    )
                }
            }
        }
    }

    /** Write one crash the way the platform would, so the page has a real report to draw. */
    private fun crash(message: String) {
        CrashLog.install(folder.root)
        Thread.getDefaultUncaughtExceptionHandler()
            ?.uncaughtException(Thread.currentThread(), RuntimeException(message))
    }

    /**
     * With nothing recorded the page says so, and says *why* it might be empty.
     *
     * An empty card is indistinguishable from a broken page. The second line is the one
     * that matters: a release build records nothing, and a reader who opened this page
     * expecting last night's crash needs to be told that rather than left guessing.
     */
    @Test
    fun `an empty log explains itself`() {
        page()
        rule.onNodeWithText("No crashes recorded on this device.").assertIsDisplayed()
        rule.onNodeWithText(
            "Reports are written only in debug builds. Reproduce the crash, reopen the " +
                "app, and the report appears here.",
        ).assertIsDisplayed()
    }

    /** A recorded crash is listed by its exception, which is what identifies it. */
    @Test
    fun `a recorded crash is listed`() {
        crash("the to-dos card took the process down")
        page()
        rule.onNodeWithText("java.lang.RuntimeException").assertIsDisplayed()
    }

    /**
     * Tapping a row expands the report, and the trace is on screen.
     *
     * The trace is the deliverable: a row that opened onto nothing would be a list of
     * exception names, which is not enough to fix anything. The footer's actions only
     * exist while expanded, so their presence is the assertion that the row opened.
     */
    @Test
    fun `expanding a crash shows the stack`() {
        crash("boom")
        page()
        rule.onNodeWithText("java.lang.RuntimeException").performClick()
        // `assertExists`, not `assertIsDisplayed`: a real stack trace is tall enough to
        // push the footer below the fold on a phone-sized viewport, and a node that is
        // composed but scrolled off is exactly what the reader scrolls down to reach.
        // What is asserted is that expanding *produced* the actions, not that they fit.
        rule.onNodeWithText("Copy").assertExists()
        rule.onNodeWithText("Share").assertExists()
        rule.onNodeWithText("Delete").assertExists()
    }

    /**
     * Delete-all empties the list, and the page goes back to saying it is empty.
     *
     * This is the action a reader takes once the report has been shared, so it has to
     * work from the page rather than needing a file manager.
     */
    @Test
    fun `delete all empties the page`() {
        crash("one")
        page()
        rule.onNodeWithText("java.lang.RuntimeException").assertIsDisplayed()

        rule.onNodeWithText("Delete all reports").performClick()

        assertTrue("the store must be empty", CrashLog.reports().isEmpty())
        rule.onNodeWithText("No crashes recorded on this device.").assertIsDisplayed()
    }

    /**
     * Deleting one report leaves the other, both in the store and on screen.
     *
     * Two rows carry the same label here, so the click is scoped to the first — the
     * newest, which is the one the page lists first.
     */
    @Test
    fun `deleting one crash keeps the rest`() {
        crash("keep")
        Thread.sleep(5)
        crash("drop")
        assertEquals(2, CrashLog.reports().size)
        page()

        rule.onAllNodesWithText("java.lang.RuntimeException")[0].performClick()
        // Scrolled to first: a real stack trace is tall enough to put the footer below the
        // fold, and a click on an off-screen node lands on nothing.
        rule.onNodeWithText("Delete").performScrollTo().performClick()

        assertEquals(1, CrashLog.reports().size)
        rule.onNodeWithText("java.lang.RuntimeException").assertIsDisplayed()
    }
}
