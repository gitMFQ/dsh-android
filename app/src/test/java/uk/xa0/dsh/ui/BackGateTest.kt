package uk.xa0.dsh.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uk.xa0.dsh.scroll.HarnessApplication
import uk.xa0.dsh.ui.theme.DshTheme

/**
 * What the root does with Back, and what the drawer's last row asks before closing.
 *
 * These are now two different gestures. Back with nothing left to dismiss leaves the
 * task in the background — no dialog, no `finish()`, the connection and the agent
 * carry on behind the home screen. Closing the screen is an explicit act from the
 * drawer, and only that act raises [CLOSE_DSH_TITLE]. The test pins both halves: the
 * press goes to the background callback alone, and the dialog's **Close** is the one
 * path that calls [BackGate]'s `onClose`.
 *
 * It cannot prove the connection survives, because the callbacks here are flags
 * rather than `moveTaskToBack(true)` / `finish()` and the harness has no client or
 * service; that half is a property of `MainActivity` wiring those two calls and of
 * nothing else in the close path touching `DshApplication`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = HarnessApplication::class)
class BackGateTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `back goes to the background and never asks to close`() {
        var backgrounded = 0
        var closed = false
        rule.setContent {
            DshTheme {
                BackGate(
                    confirming = false,
                    onBackground = { backgrounded++ },
                    onDismiss = { error("nothing was asking") },
                    onClose = { closed = true },
                )
            }
        }
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()

        assertEquals(1, backgrounded)
        assertFalse("Back must not finish the Activity", closed)
        rule.onNodeWithText(CLOSE_DSH_TITLE).assertDoesNotExist()
    }

    @Test
    fun `back while the dialog is up keeps the screen open`() {
        var backgrounded = 0
        var dismissed = 0
        var closed = false
        rule.setContent {
            DshTheme {
                BackGate(
                    confirming = true,
                    onBackground = { backgrounded++ },
                    onDismiss = { dismissed++ },
                    onClose = { closed = true },
                )
            }
        }
        rule.onNodeWithText(CLOSE_DSH_TITLE).assertExists()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()

        assertEquals("Back behind a dialog must not leave the app", 0, backgrounded)
        assertFalse("Back behind a dialog must not close it", closed)
        // Either the dialog's own dismiss handler or our guarded BackHandler answers it;
        // both routes mean Stay, and both reach the same callback.
        assertTrue("Back while asking means Stay", dismissed > 0)
    }

    @Test
    fun `the dialog says the connection stays open and Close is the one path out`() {
        var confirming by mutableStateOf(false)
        var closed = false
        rule.setContent {
            DshTheme {
                BackGate(
                    confirming = confirming,
                    onBackground = { error("Back was not pressed") },
                    onDismiss = { confirming = false },
                    onClose = {
                        confirming = false
                        closed = true
                    },
                )
            }
        }

        // Simulates the drawer's last row: the root raises its own dialog.
        rule.runOnUiThread { confirming = true }
        rule.waitForIdle()
        rule.onNodeWithText(CLOSE_DSH_TITLE).assertExists()
        rule.onNodeWithText(CLOSE_DSH_BODY).assertExists()
        rule.onNodeWithText(CLOSE_DSH_CONFIRM).assertExists()
        rule.onNodeWithText(CLOSE_DSH_STAY).assertExists()
        assertFalse("the dialog must not close anything by itself", closed)

        rule.onNodeWithText(CLOSE_DSH_CONFIRM).performClick()
        rule.waitForIdle()
        assertTrue("the confirm button is the one path out", closed)
        rule.onNodeWithText(CLOSE_DSH_TITLE).assertDoesNotExist()
    }

    @Test
    fun `staying leaves the screen open`() {
        var confirming by mutableStateOf(false)
        var closed = false
        rule.setContent {
            DshTheme {
                BackGate(
                    confirming = confirming,
                    onBackground = { error("Back was not pressed") },
                    onDismiss = { confirming = false },
                    onClose = { closed = true },
                )
            }
        }
        rule.runOnUiThread { confirming = true }
        rule.waitForIdle()

        rule.onNodeWithText(CLOSE_DSH_STAY).performClick()
        rule.waitForIdle()
        assertFalse(closed)
        rule.onNodeWithText(CLOSE_DSH_TITLE).assertDoesNotExist()
    }

    @Test
    fun `no user-facing word still says Exit or quit`() {
        // The label is the subject: this control only finishes the Activity, while
        // the process, the event socket and the connection service keep running, so
        // a dialog that offered "Exit" would claim a shutdown it never performed.
        val copy = listOf(CLOSE_DSH_TITLE, CLOSE_DSH_BODY, CLOSE_DSH_CONFIRM, CLOSE_DSH_STAY)
        copy.forEach { line ->
            assertFalse("copy claims an exit: $line", line.contains("Exit"))
            assertFalse("copy claims a quit: $line", line.contains("Quit", ignoreCase = true))
        }
    }
}
