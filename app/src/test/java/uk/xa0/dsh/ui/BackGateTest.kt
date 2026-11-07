package uk.xa0.dsh.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
 * The root's contract with the platform's predictive-back gesture.
 *
 * Since Android 13 the system animates the home screen under the finger only while
 * the app has **no enabled Back callback**. So the gate's whole job is to stay out
 * of the way: with nothing to confirm it must register zero enabled callbacks and
 * let the platform finish the Activity. This is the regression that a
 * `moveTaskToBack(true)` handler caused — it consumed Back, so the animation never
 * appeared and the screen simply jumped to the launcher.
 *
 * Closing the screen is a separate, explicit act from the drawer, and only that act
 * raises [CLOSE_DSH_TITLE]. Those tests pin the dialog's copy and its **Close**
 * button, while the dispatcher assertions pin the animation precondition.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = HarnessApplication::class)
class BackGateTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `with nothing to confirm the app claims no Back callback`() {
        rule.setContent {
            DshTheme {
                BackGate(
                    confirming = false,
                    onDismiss = { error("nothing was asking") },
                    onClose = { error("Back must not close the screen") },
                )
            }
        }
        rule.waitForIdle()

        // Zero enabled callbacks is exactly the state in which the platform owns the
        // gesture and draws the predictive-back animation.
        assertFalse(
            "an enabled Back callback suppresses the platform's predictive-back animation",
            rule.activity.onBackPressedDispatcher.hasEnabledCallbacks(),
        )
        rule.onNodeWithText(CLOSE_DSH_TITLE).assertDoesNotExist()
    }

    @Test
    fun `the confirmation claims Back and dismissing it releases the claim`() {
        var confirming by mutableStateOf(true)
        rule.setContent {
            DshTheme {
                BackGate(
                    confirming = confirming,
                    onDismiss = { confirming = false },
                    onClose = { error("Back must not close the screen") },
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText(CLOSE_DSH_TITLE).assertExists()
        assertTrue(
            "while asking, Back must belong to the app so it can mean Stay",
            rule.activity.onBackPressedDispatcher.hasEnabledCallbacks(),
        )

        rule.onNodeWithText(CLOSE_DSH_STAY).performClick()
        rule.waitForIdle()
        assertFalse(
            "after Stay the platform must own Back again",
            rule.activity.onBackPressedDispatcher.hasEnabledCallbacks(),
        )
    }

    @Test
    fun `back while the dialog is up keeps the screen open`() {
        var confirming by mutableStateOf(true)
        var closed = false
        rule.setContent {
            DshTheme {
                BackGate(
                    confirming = confirming,
                    onDismiss = { confirming = false },
                    onClose = { closed = true },
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText(CLOSE_DSH_TITLE).assertExists()

        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()

        assertFalse("Back behind a dialog must not close the screen", closed)
        rule.onNodeWithText(CLOSE_DSH_TITLE).assertDoesNotExist()
        assertFalse(rule.activity.onBackPressedDispatcher.hasEnabledCallbacks())
    }

    @Test
    fun `the dialog says the connection stays open and Close is the one path out`() {
        var confirming by mutableStateOf(false)
        var closed = false
        rule.setContent {
            DshTheme {
                BackGate(
                    confirming = confirming,
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
