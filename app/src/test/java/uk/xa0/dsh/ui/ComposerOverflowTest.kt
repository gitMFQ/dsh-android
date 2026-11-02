package uk.xa0.dsh.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uk.xa0.dsh.PendingAttachment
import uk.xa0.dsh.scroll.HarnessApplication
import uk.xa0.dsh.ui.components.ComposerContent
import uk.xa0.dsh.ui.theme.DshSpacing
import uk.xa0.dsh.ui.theme.DshTheme

/**
 * The composer bar cannot hide its own primary action, and the field is what gives way.
 *
 * The bar used to carry five controls beside the text, all unweighted, and the model chip
 * was the only thing that could shrink. Since those controls moved out into
 * `ComposerAccessoryRail`, the bar's content is a weighted text field and one 34dp action
 * — so the invariant is simpler and stronger: the action keeps its full width and the
 * field absorbs every dp the bar loses.
 *
 * **On the text, and why the width pressure is applied by hand.** This harness cannot
 * measure text: stub metrics leave a paragraph unwrapped, so a long message creates no
 * width pressure here. The squeeze test tightens the slot instead, which is exactly what a
 * narrow phone does, and unlike a long string it depends on no metric the shim stubs out.
 *
 * The slot is exercised at 288dp — the composer's room inside a 320dp phone's 16dp inset —
 * because that is the narrowest bar any Android phone hands this component.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = HarnessApplication::class, qualifiers = "w320dp-h640dp")
class ComposerOverflowTest {

    @get:Rule
    val rule = createComposeRule()

    private companion object {
        /** The bar's slot, tagged on its own node so bounds compare against the bar. */
        const val SLOT = "composer-slot"

        const val SEND = "Send and queue"

        /** How far each turn of the squeeze test tightens the bar. */
        val STEP = 6.dp
    }

    private fun composeBar(
        running: Boolean = false,
        attachments: List<PendingAttachment> = emptyList(),
        width: () -> Dp? = { null },
    ) {
        rule.setContent {
            DshTheme {
                Box(Modifier.fillMaxWidth().padding(horizontal = DshSpacing.xl)) {
                    val fixed = width()
                    Box(
                        // Tagged on its own node, with no padding of its own, so the bounds
                        // the assertions compare against are the width the bar is actually
                        // given rather than the screen's.
                        (if (fixed != null) Modifier.width(fixed) else Modifier.fillMaxWidth())
                            .testTag(SLOT),
                    ) {
                        val state = remember { TextFieldState("hello") }
                        ComposerContent(
                            state = state,
                            onSend = {},
                            onStop = {},
                            running = running,
                            attachments = attachments,
                            onRemoveAttachment = {},
                        )
                    }
                }
            }
        }
    }

    /**
     * The send control is present and wholly inside the bar's width.
     *
     * `assertIsDisplayed` alone would not catch this: a button half off the right edge is
     * still "displayed". The bounds comparison is the assertion that matters.
     */
    private fun assertSendIsWhole(state: String, description: String = SEND) {
        val slot = rule.onNodeWithTag(SLOT).fetchSemanticsNode().boundsInRoot
        val send = rule.onNodeWithContentDescription(description)
        send.assertIsDisplayed()
        val bounds = send.fetchSemanticsNode().boundsInRoot
        assertTrue(
            "send is pushed past the bar's right edge with $state: send=$bounds slot=$slot",
            bounds.right <= slot.right + 0.5f,
        )
        assertTrue(
            "send starts left of the bar with $state: send=$bounds slot=$slot",
            bounds.left >= slot.left - 0.5f,
        )
    }

    @Test
    fun `send is whole at the narrowest bar`() {
        composeBar()
        assertSendIsWhole("the resting bar")
    }

    @Test
    fun `a long attachment name does not push send out`() {
        composeBar(
            attachments = listOf(
                PendingAttachment(
                    id = "att-1",
                    name = "a-very-long-screenshot-name-with-a-long-extension.png",
                    bytes = 184_320,
                    mediaType = "image/png",
                ),
            ),
        )
        assertSendIsWhole("a long attachment chip")
    }

    @Test
    fun `a running turn swaps send for stop and keeps it whole`() {
        composeBar(running = true)
        assertSendIsWhole("the running bar", description = "Stop")
    }

    /**
     * The bar can shrink and send keeps every pixel of its width.
     *
     * The loop stops the moment the field gives way, so nothing rests on guessing the
     * bar's natural width. A bar whose send shrank *is* the failure: the action is the
     * screen's primary one, and it is the field — the thing that can scroll — that has to
     * absorb the loss.
     */
    @Test
    fun `the field gives way, not send, when the bar is tighter than its contents`() {
        val width = mutableStateOf<Dp?>(null)
        composeBar(width = { width.value })

        val slot = rule.onNodeWithTag(SLOT).fetchSemanticsNode().boundsInRoot
        val fieldNatural = rule.onNode(hasSetTextAction()).fetchSemanticsNode().size.width
        val sendNatural = rule.onNodeWithContentDescription(SEND).fetchSemanticsNode().size.width

        var turns = 0
        var gaveWay = false
        while (turns < 24) {
            turns += 1
            width.value = with(rule.density) { (slot.width - (STEP * turns).toPx()).toDp() }
            rule.waitForIdle()
            if (rule.onNode(hasSetTextAction()).fetchSemanticsNode().size.width < fieldNatural) {
                gaveWay = true
                break
            }
        }

        assertTrue(
            "the field held its ${fieldNatural}px width through ${turns * STEP.value}dp of tightening: " +
                "nothing in the bar yields, so the width comes out of send",
            gaveWay,
        )
        assertEquals(
            "send must keep its full width while the field gives way",
            sendNatural,
            rule.onNodeWithContentDescription(SEND).fetchSemanticsNode().size.width,
        )
        assertSendIsWhole("a bar tightened until the field had to give way")
    }
}