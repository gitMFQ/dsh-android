// Vendored from chenaizhang/KernelSU-Style-UI-Kit (GPL-3.0) — see NOTICE.md.
// Upstream path: app/src/main/java/com/example/kernelsustyleuikit/ui/component/SearchStatus.kt
// Change: moved into the list package, dropped the MiuixTheme import (the bar's backing colour is a parameter), added the expanding()/collapsing() targets and documented the transition rules.

package uk.xa0.dsh.ui.list

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import uk.xa0.dsh.ui.glass.LocalGlassBarBackdrop
import uk.xa0.dsh.ui.glass.liquidPane
import uk.xa0.dsh.ui.theme.DshTheme

/**
 * The drawer search bar's state machine: four states, and the two round trips between them.
 *
 * This is a plain immutable value so a caller can hold it in a `ViewModel` and the animation never
 * fights it: the composable never animates *to* a target it decides, it animates *from* the state
 * it is handed and reports back through [onAnimationComplete].
 *
 * `COLLAPSED → EXPANDING → EXPANDED → COLLAPSING → COLLAPSED`
 *
 * The intermediate states are not decoration. Without them the bar would either jump (no
 * intermediate frame to animate) or, worse, the results area would appear at the final size in the
 * same frame the top padding starts moving, so the list would visibly slide under a bar that has
 * not finished arriving.
 */
@Stable
data class SearchStatus(
    /** The query placeholder; also what the collapsed bar reads as. */
    val label: String,
    val searchText: String = "",
    val current: Status = Status.COLLAPSED,
    /** How far the bar has been pushed down, in the collapsed state. */
    val offsetY: Dp = 0.dp,
    val resultStatus: ResultStatus = ResultStatus.DEFAULT,
) {
    fun isExpand(): Boolean = current == Status.EXPANDED

    fun isCollapsed(): Boolean = current == Status.COLLAPSED

    /** True from the first frame of expanding until collapsing finishes — the "bar is up" answer. */
    fun shouldExpand(): Boolean = current == Status.EXPANDED || current == Status.EXPANDING

    /** The inverse of [shouldExpand]; the two are exhaustive together, so one is always true. */
    fun shouldCollapsed(): Boolean = current == Status.COLLAPSED || current == Status.COLLAPSING

    fun isAnimatingExpand(): Boolean = current == Status.EXPANDING

    /**
     * The state to commit once the running animation lands.
     *
     * Called from `onAnimationComplete` on the *animated* value, so it runs exactly once per
     * transition and never on a recomposition that did not move anything.
     *
     * A finishing *collapse* also clears [searchText]. That is the one piece of state the search bar
     * gives up on its own, and it is deliberate: the query is a view of the results below it, and
     * once the results are gone the text describes nothing. It is also what makes reopening the bar
     * show the placeholder rather than the last thing the user typed weeks ago.
     */
    fun onAnimationComplete(): SearchStatus = when (current) {
        Status.EXPANDING -> copy(current = Status.EXPANDED)
        Status.COLLAPSING -> copy(searchText = "", current = Status.COLLAPSED)
        else -> this
    }

    /** A target state for "open the search bar". */
    fun expanding(): SearchStatus = copy(current = Status.EXPANDING)

    /** A target state for "close the search bar"; the query is cleared when the animation lands. */
    fun collapsing(): SearchStatus = copy(current = Status.COLLAPSING)

    /**
     * The bar that shows *behind* the expanded one, faded out while the search is up.
     *
     * Upstream called this `TopAppBarAnim`. It is a backing panel rather than a `graphicsLayer` on
     * the caller's own bar because the bar has to keep its height while hidden: fading the whole
     * thing would collapse the layout and move everything under it, which is the flicker this
     * exists to avoid.
     */
    @Composable
    fun TopAppBarAnim(
        modifier: Modifier = Modifier,
        visible: Boolean = shouldCollapsed(),
        backgroundColor: Color = DshTheme.colors.bgBase,
        content: @Composable () -> Unit,
    ) {
        val backdrop = LocalGlassBarBackdrop.current
        Box(modifier = modifier) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    // RectangleShape: the original panel was an unclipped full-size rect, so
                    // any rounded shape would shave flat-tier corners and break the baseline.
                    .liquidPane(backdrop, RectangleShape, flatFill = backgroundColor),
            )
            Box(modifier = Modifier.graphicsLayer { alpha = if (visible) 1f else 0f }) { content() }
        }
    }

    enum class Status { EXPANDED, EXPANDING, COLLAPSED, COLLAPSING }

    /**
     * What the expanded area shows.
     *
     * [EMPTY] and [LOAD] render nothing at all rather than a spinner or a "nothing found" line: the
     * drawer's own results list already draws both, and two of them stacked is the bug this enum
     * exists to prevent. [SHOW] is the only state that hands the area to the caller's result slot.
     */
    enum class ResultStatus { DEFAULT, EMPTY, LOAD, SHOW }
}
