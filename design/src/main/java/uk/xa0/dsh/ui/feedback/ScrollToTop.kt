// Vendored from chenaizhang/KernelSU-Style-UI-Kit (GPL-3.0) — see NOTICE.md.
// Upstream path: app/src/main/java/com/example/kernelsustyleuikit/ui/component/ScrollToTop.kt
// Change: kept the settle-based pin as-is and added the floating jump button the file was missing (upstream's name promised one; the file only held the effect), drawn from DshTheme colours.

package uk.xa0.dsh.ui.feedback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import uk.xa0.dsh.ui.glass.GlassComponent
import uk.xa0.dsh.ui.glass.LocalGlassBarBackdrop
import uk.xa0.dsh.ui.glass.liquidPane
import uk.xa0.dsh.ui.theme.DshSpacing
import uk.xa0.dsh.ui.theme.DshTheme

/**
 * How long the list has to be quiet before it counts as settled.
 *
 * 250ms is the measurement: a reload that lands in one batch produces a single frame of change and
 * settles a quarter of a second later, while a streaming reload emits continuously and never
 * settles — which is exactly the case the timeout below exists for.
 */
private const val LIST_QUIET_MILLIS = 250L

/**
 * The ceiling on keeping the list pinned to the top.
 *
 * Without it, a reload that never stops emitting would hold the list at the top forever and the
 * reader could not scroll away from it. Three seconds is long enough for every reload this app
 * performs and short enough that a stuck one is a nuisance rather than a lock.
 */
private const val LIST_SETTLE_TIMEOUT_MS = 3000L

private class KeysRef {
    var value: List<Any?>? = null
}

/**
 * Scrolls to the top when [keys] change, without the flicker of doing it naively.
 *
 * The keys are the list's identity — sort, filter, query, refresh token. When they change the
 * content is different, so staying scrolled where you were is meaningless, but scrolling naively
 * fights LazyColumn: it restores a keyed position on the same frame, so the two writes land in one
 * pass and the list visibly jumps up and back.
 *
 * So this does it twice, on purpose:
 *
 *  1. [SideEffect] calls `requestScrollToItem(0)` *before* the frame is drawn, which cancels the
 *     keyed restore rather than racing it.
 *  2. [scrollToTopAfterListSettles] then keeps pinning the top each frame until the content stops
 *     changing, so a reload that arrives in several batches cannot drag the list back down between
 *     them.
 *
 * It never scrolls on first composition or on returning from a detail screen: both of those compose
 * this with no previous keys, and the previous keys being absent is what "this is not a change"
 * means. For an action that does not change the keys — pull-to-refresh, say — pass a token that
 * increments only when you want the scroll.
 *
 * @param isBusy `{ isRefreshing }` for a slow reload, so the pin lasts until it finishes. Read it
 *   through `rememberUpdatedState` at the call site if it comes from a plain data class.
 * @param observedList the state the settle detector watches. It must read a snapshot-trackable
 *   State; bridging a plain value through `rememberUpdatedState` is the usual way. Without one the
 *   detector falls back to the timeout, which is correct but slower.
 */
@Composable
fun ScrollToTopOnChange(
    listState: LazyListState,
    vararg keys: Any?,
    onScrolledToTop: () -> Unit = {},
    isBusy: () -> Boolean = { false },
    observedList: () -> Any?,
) {
    val keysList = keys.asList()
    val sideRef = remember { KeysRef() }
    SideEffect {
        val prev = sideRef.value
        if (prev != null && prev != keysList) {
            listState.requestScrollToItem(0)
        }
        sideRef.value = keysList
    }

    val effectRef = remember { KeysRef() }
    LaunchedEffect(*keys) {
        val prev = effectRef.value
        effectRef.value = keysList
        if (prev != null && prev != keysList) {
            scrollToTopAfterListSettles(listState, isBusy = isBusy, observedList = observedList)
            onScrolledToTop()
        }
    }
}

/**
 * Pins the list to the top each frame until [observedList] has been quiet for [LIST_QUIET_MILLIS]
 * and [isBusy] is false, capped by [LIST_SETTLE_TIMEOUT_MS].
 *
 * A loop rather than a single scroll because LazyColumn restores its position by key on every
 * content change: one scroll at the start would be undone by the first batch that lands after it.
 * Pinning for the whole settle is what overrides that restore.
 *
 * Exposed separately for a caller that has the trigger but not the composition — a snackbar action
 * or a `ViewModel` event — since the loop itself needs no composable.
 */
suspend fun scrollToTopAfterListSettles(
    listState: LazyListState,
    isBusy: () -> Boolean = { false },
    observedList: () -> Any?,
): Unit = coroutineScope {
    var settled = false
    val watcher = launch {
        while (true) {
            val changed = withTimeoutOrNull(LIST_QUIET_MILLIS) {
                snapshotFlow(observedList).drop(1).first()
            }
            // `changed == null` means the timeout expired, i.e. nothing moved for a quarter second.
            // The busy check is second because a reload can be quiet and still not be finished.
            if (changed == null && !isBusy()) break
        }
        settled = true
    }
    withTimeoutOrNull(LIST_SETTLE_TIMEOUT_MS) {
        while (!settled) {
            listState.requestScrollToItem(0)
            withFrameNanos { }
        }
    }
    watcher.cancel()
    // One last one outside the loop: the loop can exit by timeout while the watcher is still
    // waiting, and the reader is owed the scroll either way.
    listState.requestScrollToItem(0)
}

/**
 * The floating "back to top" button.
 *
 * Visible only once the list is a screenful past the top: at two items the button offers to scroll
 * somewhere the reader can see, which is noise. [LazyListState.firstVisibleItemIndex] is the wrong
 * thing to read on its own — a list of tall cards can be at index 2 and still show its top — so the
 * threshold is a count of items scrolled past, and the caller can override it.
 *
 * @param listState the list it belongs to. The button is placed by the caller (usually in a `Box`
 *   over the list); it does not position itself, because the right margin depends on what else
 *   floats on that side.
 * @param scrollOnClick whether to animate the scroll itself. `false` lets a caller drive it, which
 *   is what a list inside a pager wants so the animation matches the page transition.
 * @param contentDescription announced by a screen reader; the button is an icon, so this is the only
 *   thing that makes it reachable.
 */
@Composable
fun ScrollToTopButton(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    visibleAfter: Int = 8,
    scrollOnClick: Boolean = true,
    contentDescription: String = "Scroll to top",
) {
    val colors = DshTheme.colors
    val backdrop = LocalGlassBarBackdrop.current
    val coroutineScope = rememberCoroutineScope()
    // derivedStateOf, not a plain read: firstVisibleItemIndex changes on every scroll frame, and a
    // direct read would recompose this composable — and anything it is next to — for the whole
    // gesture. Derived, it only recomposes when the boolean actually flips.
    val visible by remember(listState, visibleAfter) {
        derivedStateOf { listState.firstVisibleItemIndex > visibleAfter }
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
    ) {
        FloatingActionButton(
            onClick = {
                if (scrollOnClick) {
                    coroutineScope.launch { listState.animateScrollToItem(0) }
                }
            },
            containerColor = colors.bgLayer2,
            // The FAB paints its own opaque fill internally, so this overlay only ever mattered
            // on the liquid tier; panes stay flat now. Color.Transparent makes the flat branch
            // return this unchanged.
            modifier = Modifier
                .liquidPane(backdrop, GlassComponent.SCROLL_TO_TOP, CircleShape, flatFill = Color.Transparent)
                .semantics { this.contentDescription = contentDescription },
        ) {
            Row(
                modifier = Modifier.padding(DshSpacing.lg),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // miuix's own Basic icon set has no ArrowUp (only ArrowRight, ArrowUpDown and the
                // like), and mixing in a Material glyph for one button would give this shell two
                // stroke weights. Rotating the right arrow is the cheap honest fix.
                Icon(
                    imageVector = MiuixIcons.Basic.ArrowRight,
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(-90f),
                    tint = colors.labelPrimary,
                )
            }
        }
    }
}
