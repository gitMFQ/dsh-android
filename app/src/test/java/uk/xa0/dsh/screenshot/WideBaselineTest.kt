package uk.xa0.dsh.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uk.xa0.dsh.AppPhase
import uk.xa0.dsh.UiState
import uk.xa0.dsh.data.ThemeMode
import uk.xa0.dsh.model.ChatEntry
import uk.xa0.dsh.model.SessionHeader
import uk.xa0.dsh.model.SessionItem
import uk.xa0.dsh.scroll.HarnessApplication
import uk.xa0.dsh.ui.ChatActions
import uk.xa0.dsh.ui.ChatPanel
import uk.xa0.dsh.ui.ChatUiState
import uk.xa0.dsh.ui.glass.LocalGlassEnabled
import uk.xa0.dsh.ui.theme.DshTheme

/**
 * The conversation screen on a **wide** window, where the bar becomes a rail (2.5d).
 *
 * ## Why this is its own class
 *
 * Robolectric's window size is a class-level `@Config` qualifier, not something a test
 * can change inside `setContent`. `ScreenBaselineTest` is pinned to the 411x891 phone
 * surface every other baseline uses, and adding a wide capture to it would mean either
 * changing that surface for all thirteen (invalidating every baseline) or rendering a
 * "wide" fixture inside a phone-shaped window — a picture of a rail that no real device
 * would ever draw, because `BoxWithConstraints` would see 411dp.
 *
 * So the size is the *class*, and the two layouts are two files. That is also the
 * honest shape of the claim: these are two different windows, not two states of one.
 *
 * ## What it pins
 *
 * The rail exists and the bar does not: exactly one of the two is composed, chosen once
 * from `WIDE_LAYOUT`. The three destinations, their order and their selected treatment
 * are the same three the bar draws, because both read `BOTTOM_BAR_ITEMS` and the same
 * derived `view`.
 *
 * ## What it cannot say
 *
 * Robolectric lays text out with stub metrics, so this is not proof that the labels fit
 * a 72dp rail on a real tablet. It can say the same seats land in the same column as
 * last time, and that a change to the breakpoint moved this file rather than quietly
 * changing what every wide window shows.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// 840x891: comfortably past WIDE_LAYOUT (600dp), and the same height as the phone
// baselines so the transcript's vertical metrics stay comparable between the two.
@Config(
    sdk = [34],
    application = HarnessApplication::class,
    qualifiers = "en-rUS-w840dp-h891dp",
)
class WideBaselineTest {

    @get:Rule
    val rule = createComposeRule()

    /**
     * A fixed wall clock, matching `ScreenBaselineTest`'s. A baseline may not depend on
     * when it was recorded.
     */
    private val FIXTURE_CLOCK_MS = 1_793_446_800_000L

    private fun capture(name: String, content: @Composable () -> Unit) {
        rule.setContent {
            DshTheme(themeMode = ThemeMode.LIGHT) {
                // Glass off, same as the phone fixture — and this surface needs the
                // argument spelled out too, because it *does* compose a backdrop: on a
                // wide window the bar is replaced by the rail, but the pager still
                // records into the layer the bar would have sampled. See
                // `ScreenBaselineTest.capture` for why the switch exists at all.
                CompositionLocalProvider(LocalGlassEnabled provides false) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(DshTheme.colors.bgBase),
                ) {
                    content()
                }
                }
            }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    /**
     * The conversation on a wide window: a transcript, the composer, and a rail instead
     * of a bar.
     *
     * The rows are the same plainest-available ones the phone capture uses, so a
     * difference between the two pictures is a difference in *layout*, not in content.
     */
    @Test
    fun chatWide() {
        val state = ChatUiState(
            ui = UiState(
                phase = AppPhase.READY,
                currentSessionId = "s1",
                sessions = listOf(
                    SessionItem(
                        id = "s1",
                        title = "Re-skin the shell",
                        cwd = "/home/user/var/work/dsh-android",
                        updatedAt = 1_000L,
                        running = false,
                        isSubagent = false,
                    ),
                ),
            ),
            entries = listOf(
                ChatEntry.UserMessage(
                    seq = 1,
                    id = "m1",
                    text = "Re-skin the shell, one screen at a time.",
                    time = FIXTURE_CLOCK_MS,
                    fromPlugin = false,
                    summary = null,
                ),
                ChatEntry.AssistantMessage(
                    seq = 2,
                    turn = 1,
                    step = 1,
                    reasoning = "",
                    text = "Starting with the frame: the bar, the header, the composer.",
                    time = FIXTURE_CLOCK_MS + 61_000L,
                ),
            ),
            header = SessionHeader(
                id = "s1",
                cwd = "/home/user/var/work/dsh-android",
                createdAt = 0L,
                agentPreset = null,
                parentSession = null,
            ),
        )
        capture("chat-wide") {
            ChatPanel(
                state = state,
                actions = ChatActions(),
                terminal = { Modifier.fillMaxSize() },
            )
        }
    }
}
