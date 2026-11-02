package uk.xa0.dsh.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uk.xa0.dsh.model.SessionStats
import uk.xa0.dsh.scroll.HarnessApplication
import uk.xa0.dsh.ui.components.ComposerAccessoryRail
import uk.xa0.dsh.ui.components.ComposerDock
import uk.xa0.dsh.ui.theme.DshTheme

/**
 * The dock's two shapes, and the one move between them.
 *
 * The composer is a circle beside the tab bar at rest and a bar beside the current
 * destination's circle when open; each pane is the other's switch. These tests pin the
 * *semantics* of that move, not its pixels — the screenshot baselines cover the shape, and
 * a node that cannot be found is the cheapest way to catch a pane that stopped composing
 * when it should have, or kept composing when it should not have.
 *
 * The accessory rail is checked in the same file because it is the other half of the
 * change: the controls that left the bar have to be reachable without opening it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = HarnessApplication::class, qualifiers = "w411dp-h891dp")
class ComposerDockTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `the circle opens the bar and the dock's circle closes it`() {
        rule.setContent {
            DshTheme {
                var expanded by remember { mutableStateOf(true) }
                var imeRequests by remember { mutableStateOf(0) }
                val state = remember { TextFieldState("hello") }
                Box(Modifier.fillMaxWidth()) {
                    ComposerDock(
                        state = state,
                        expanded = expanded,
                        onExpand = {
                            expanded = true
                            imeRequests++
                        },
                        onCollapse = { expanded = false },
                        onSend = {},
                        onStop = {},
                        running = false,
                        attachments = emptyList(),
                        onRemoveAttachment = {},
                        items = DESTINATION_ITEMS,
                        selectedKey = Destination.CHAT.name,
                        onSelect = {},
                        backdrop = null,
                        imeRequest = imeRequests,
                    )
                }
            }
        }
        rule.waitForIdle()

        // Open: the tab bar has folded away and the compact dock's circle is the way back.
        rule.onNodeWithContentDescription("Send and queue").assertIsDisplayed()
        rule.onNodeWithContentDescription("Show navigation").assertIsDisplayed()

        rule.onNodeWithContentDescription("Show navigation").performClick()
        rule.waitForIdle()

        // Resting: a circle that offers to write, and no send control anywhere. The
        // "hello" draft is still held, so the circle says so — that is the state a
        // reader who collapsed mid-message sees.
        rule.onNodeWithContentDescription("Write a message, draft kept").assertIsDisplayed()
        rule.onNodeWithContentDescription("Send and queue").assertDoesNotExist()

        rule.onNodeWithContentDescription("Write a message, draft kept").performClick()
        rule.waitForIdle()

        rule.onNodeWithContentDescription("Send and queue").assertIsDisplayed()
        rule.onNodeWithContentDescription("Write a message").assertDoesNotExist()
    }

    @Test
    fun `a draft kept behind the circle is announced, not lost`() {
        rule.setContent {
            DshTheme {
                val state = remember { TextFieldState("still here") }
                ComposerDock(
                    state = state,
                    expanded = false,
                    onExpand = {},
                    onCollapse = {},
                    onSend = {},
                    onStop = {},
                    running = false,
                    attachments = emptyList(),
                    onRemoveAttachment = {},
                    items = DESTINATION_ITEMS,
                    selectedKey = Destination.CHAT.name,
                    onSelect = {},
                    backdrop = null,
                )
            }
        }
        rule.waitForIdle()

        rule.onNodeWithContentDescription("Write a message, draft kept").assertIsDisplayed()
    }

    @Test
    fun `every control has its own pill and its own action`() {
        var models = 0
        var permissions = 0
        rule.setContent {
            DshTheme {
                ComposerAccessoryRail(
                    backdrop = null,
                    permissionLabel = "Full access",
                    onPermissionClick = { permissions++ },
                    planActive = true,
                    onExitPlan = {},
                    modelTrigger = ModelTriggerState(
                        label = "DeepSeek V4 · High",
                        marker = null,
                        description = "Select model, current DeepSeek V4 · High",
                        alert = false,
                    ),
                    onModelClick = { models++ },
                    contextPercent = 12,
                    contextTokens = 12_000,
                    contextWindow = 100_000,
                    contextBreakdown = null,
                    stats = SessionStats(
                        hasStats = true,
                        turns = 1,
                        steps = 3,
                        llmMs = 1_000,
                        decodeMs = 1_000,
                        decodeTokens = 128,
                        hasUsage = true,
                        uncachedInputTokens = 10,
                        cacheReadTokens = 990,
                        outputTokens = 500,
                    ),
                    rates = null,
                    family = null,
                    onBalance = {},
                )
            }
        }
        rule.waitForIdle()

        // One pill per control, each findable by the sentence a screen reader would use.
        rule.onNodeWithContentDescription("Add files or run commands").assertIsDisplayed()
        rule.onNodeWithContentDescription("Change access mode, current Full access").assertIsDisplayed()
        rule.onNodeWithContentDescription("Leave plan mode").assertIsDisplayed()
        rule.onNodeWithContentDescription("Select model, current DeepSeek V4 · High").assertIsDisplayed()
        rule.onNodeWithContentDescription("Context window, 12 percent used").assertIsDisplayed()
        rule.onNodeWithText("Plan").assertIsDisplayed()
        rule.onNodeWithText("12%").assertIsDisplayed()
        rule.onNodeWithText("128 tok/s").assertIsDisplayed()
        rule.onNodeWithText("Cache 99%").assertIsDisplayed()

        rule.onNodeWithContentDescription("Select model, current DeepSeek V4 · High").performClick()
        rule.onNodeWithContentDescription("Change access mode, current Full access").performClick()
        assertEquals(1, models)
        assertEquals(1, permissions)
    }
}