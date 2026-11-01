// Vendored from chenaizhang/KernelSU-Style-UI-Kit (GPL-3.0) — see NOTICE.md.
// Upstream path: app/src/main/java/com/example/kernelsustyleuikit/ui/component/FloatingBottomBar.kt (the state block inline in the composable)
// Change: extracted verbatim into one shared holder so the liquid and the flat bar cannot drift apart; LocalUiMode/LocalEnableBlur replaced by an explicit tier.

package uk.xa0.dsh.ui.nav

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * Everything the bar animates, in one place, for every glass tier.
 *
 * The bar itself is painted three different ways (see [uk.xa0.dsh.ui.glass.GlassTier]) but it
 * *moves* one way, and that is deliberate: the sliding highlight and the rubber band are the
 * design, and a device that cannot blur should still get the same motion. Keeping the state
 * out of the composable is what makes that structurally true rather than a coincidence of
 * two copies agreeing.
 */
@Stable
internal class FloatingBarState(
    val animationScope: CoroutineScope,
    val tabsCount: Int,
    val isLtr: Boolean,
    private val density: Density,
    initialIndex: Int,
    private val onSelected: (Int) -> Unit,
) {
    /** Width of one tab, measured — not computed from the count. See [onBarMeasured]. */
    var tabWidthPx by mutableFloatStateOf(0f)
        private set

    /** The bar's own width, measured. */
    var totalWidthPx by mutableFloatStateOf(0f)
        private set

    /**
     * How far the whole panel has been dragged past its ends, in pixels, for the rubber band.
     * Distinct from the highlight's position: that one snaps between tabs, this one is the
     * overscroll the finger has actually produced and springs back on release.
     */
    private val offsetAnimation = Animatable(0f)
    private val rubberBandPx = with(density) { 4.dp.toPx() }

    /**
     * The index the bar is showing.
     *
     * A drag writes it on release; a tap writes it through [onCurrentIndexChanged]'s
     * collector. It is the single source of truth for the highlight position, which is why
     * [damped] is driven from here instead of from the touch handler directly.
     */
    var currentIndex by mutableIntStateOf(initialIndex)
        private set

    private class Holder {
        var instance: DampedDragAnimation? = null
    }

    private val holder = Holder()

    /**
     * The highlight's animation, in *index* units rather than pixels.
     *
     * Index units are what make the bar resize-safe: a rotation that changes
     * [tabWidthPx] does not invalidate the animation, because the same 1.5 means "half way
     * between tab 1 and tab 2" whatever a tab is currently wide.
     */
    val damped: DampedDragAnimation = DampedDragAnimation(
        animationScope = animationScope,
        initialValue = initialIndex.toFloat(),
        // Never a zero-width range: the velocity calculation divides by it, and a one-tab bar
        // would otherwise produce NaN on every frame.
        valueRange = 0f..(tabsCount - 1).coerceAtLeast(1).toFloat(),
        visibilityThreshold = 0.001f,
        initialScale = 1f,
        // 78/56 — the height the pressed pill grows to over the height it rests at. Taken
        // from the reference implementation rather than rounded, because the squeeze is what
        // sells the material as elastic.
        pressedScale = 78f / 56f,
        canDrag = { offset ->
            val anim = holder.instance ?: return@DampedDragAnimation true
            if (tabWidthPx == 0f) return@DampedDragAnimation false

            val currentValue = anim.value
            val indicatorX = currentValue * tabWidthPx
            val padding = with(density) { 4.dp.toPx() }
            val globalTouchX = if (isLtr) {
                padding + indicatorX + offset.x
            } else {
                totalWidthPx - padding - tabWidthPx - indicatorX + offset.x
            }
            globalTouchX in 0f..totalWidthPx
        },
        onDragStarted = {},
        onDragStopped = {
            val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
            currentIndex = targetIndex
            animateToValue(targetIndex.toFloat())
            animationScope.launch {
                offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
            }
        },
        onDrag = { _, dragAmount ->
            if (tabWidthPx > 0) {
                updateValue(
                    (targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                        .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                )
                animationScope.launch {
                    offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                }
            }
        }
    ).also { holder.instance = it }

    /** One tab's width in dp, for the pill's width modifier. */
    val tabWidth: Dp
        get() = with(density) { tabWidthPx.toDp() }

    /**
     * The rubber-band translation, derived rather than stored.
     *
     * `EaseOut` rather than linear so the panel decelerates into its limit: a linear mapping
     * makes the drag feel like it hit a wall at a fixed distance, while the eased one feels
     * like the panel is getting heavier the further it goes.
     */
    val panelOffset: Float
        get() {
            if (totalWidthPx == 0f) return 0f
            val fraction = (offsetAnimation.value / totalWidthPx).fastCoerceIn(-1f, 1f)
            return rubberBandPx * fraction.sign * EaseOut.transform(abs(fraction))
        }

    /**
     * Measures the bar.
     *
     * The 8dp is the horizontal padding the Row spends on its own inset (4dp each side), so
     * the tabs share what is left. Measured rather than assumed: the app draws this bar at
     * every screen width, and a computed width would put the highlight half a tab off on the
     * first frame after a rotation.
     */
    fun onBarMeasured(widthPx: Int) {
        totalWidthPx = widthPx.toFloat()
        val contentWidthPx = totalWidthPx - with(density) { 8.dp.toPx() }
        tabWidthPx = (contentWidthPx / tabsCount.coerceAtLeast(1)).coerceAtLeast(0f)
    }

    /** A selection made outside the bar (a rail, a deep link, the back gesture). */
    fun onExternalSelection(index: Int) {
        currentIndex = index
    }

    /**
     * The single place a new index becomes motion and a callback.
     *
     * Called with `drop(1)` semantics from the factory, so the first composition does not
     * report a selection nobody made.
     */
    fun onCurrentIndexChanged(index: Int) {
        damped.animateToValue(index.toFloat())
        onSelected(index)
    }
}

/**
 * Builds the state, wires the two flows, and keeps the two lambdas fresh.
 *
 * The lambdas are read through [rememberUpdatedState] because the state object itself is
 * remembered for the life of the bar: without that, a caller that passed a new `onSelected`
 * (a bound ViewModel method, say) would keep being called through the closure captured when
 * the bar first composed.
 */
@Composable
internal fun rememberFloatingBarState(
    tabsCount: Int,
    selectedIndex: () -> Int,
    onSelected: (Int) -> Unit,
): FloatingBarState {
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()

    val latestSelectedIndex = rememberUpdatedState(selectedIndex)
    val latestOnSelected = rememberUpdatedState(onSelected)

    val state = remember(animationScope, tabsCount, density, isLtr) {
        FloatingBarState(
            animationScope = animationScope,
            tabsCount = tabsCount,
            isLtr = isLtr,
            density = density,
            initialIndex = latestSelectedIndex.value(),
            onSelected = { latestOnSelected.value(it) },
        )
    }

    // External selection in. `snapshotFlow` rather than a plain read: the caller's lambda is
    // arbitrary and may read several states, and this way any of them changing re-evaluates.
    LaunchedEffect(state) {
        snapshotFlow { latestSelectedIndex.value() }
            .collectLatest { state.onExternalSelection(it) }
    }

    // Selection out. `drop(1)` skips the value the bar already had, so composing it does not
    // fire a navigation to the tab that was already showing.
    LaunchedEffect(state) {
        snapshotFlow { state.currentIndex }
            .drop(1)
            .collectLatest { state.onCurrentIndexChanged(it) }
    }

    return state
}
