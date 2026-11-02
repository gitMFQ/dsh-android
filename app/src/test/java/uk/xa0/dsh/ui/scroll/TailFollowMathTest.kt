package uk.xa0.dsh.ui.scroll

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The arithmetic that keeps a streaming answer above the composer rather than behind it.
 *
 * The transcript reserves the dock's height as bottom `contentPadding`. Compose's
 * `viewportEndOffset` is **not** the padded edge — in the item coordinate system it
 * resolves to the layout's own bottom — so a correction written against it lets the
 * newest line grow behind the dock until it crosses the screen edge, and then scrolls a
 * whole chunk: the reported "the output lifts a little every so often". The correction
 * targets `viewportEndOffset - afterContentPadding` instead, and these cases pin the
 * difference at the sizes the conversation actually uses (891dp viewport, 110dp dock).
 */
class TailFollowMathTest {

    private companion object {
        const val VIEWPORT_END = 891
        const val DOCK = 110
    }

    @Test
    fun `a tail that grew into the dock's reserved strip is corrected`() {
        // The line's bottom is 860 — well inside the screen, but 79px below the edge
        // the reader can see above the dock. The old arithmetic answered zero here.
        assertEquals(
            79f,
            tailOverflow(
                lastBottom = 860,
                viewportEndOffset = VIEWPORT_END,
                afterContentPadding = DOCK,
            ),
            0.001f,
        )
    }

    @Test
    fun `a tail sitting above the dock needs no correction`() {
        assertEquals(
            0f,
            tailOverflow(
                lastBottom = VIEWPORT_END - DOCK,
                viewportEndOffset = VIEWPORT_END,
                afterContentPadding = DOCK,
            ),
            0.001f,
        )
        assertEquals(
            0f,
            tailOverflow(
                lastBottom = 700,
                viewportEndOffset = VIEWPORT_END,
                afterContentPadding = DOCK,
            ),
            0.001f,
        )
    }

    @Test
    fun `a tail extending past the screen is corrected by its full overflow`() {
        assertEquals(
            169f,
            tailOverflow(
                lastBottom = 950,
                viewportEndOffset = VIEWPORT_END,
                afterContentPadding = DOCK,
            ),
            0.001f,
        )
    }

    @Test
    fun `no padding makes the padded edge the layout edge`() {
        assertEquals(
            891,
            contentBottom(viewportEndOffset = VIEWPORT_END, afterContentPadding = 0),
        )
        assertEquals(
            59f,
            tailOverflow(
                lastBottom = 950,
                viewportEndOffset = VIEWPORT_END,
                afterContentPadding = 0,
            ),
            0.001f,
        )
    }
}
