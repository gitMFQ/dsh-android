// Vendored from chenaizhang/KernelSU-Style-UI-Kit (GPL-3.0) — see NOTICE.md.
// Upstream path: app/src/main/java/com/example/kernelsustyleuikit/ui/component/bottombar/BottomBar.kt
// Change: package renamed; UiMode branching, LocalUiMode and the Material siblings removed, so
// //       there is one bar and one rail; enableFloatingBottomBar became an explicit argument.

package uk.xa0.dsh.ui.nav

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.utils.springAnimateToPage

/**
 * The selected page, kept in step with a [PagerState] in both directions.
 *
 * The reason this exists at all is that a pager and a tab bar disagree about what "selected"
 * means for about 300ms at a time. The bar fires the moment a finger lifts; the pager is still
 * springing. Without a holder that remembers which one moved last, the two corrections fight
 * and the highlight snaps back and forth.
 *
 * [isNavigating] is that memory: while a programmatic navigation is in flight, [syncPage] leaves
 * the selection alone, so the pager's intermediate pages cannot pull the bar backwards. It is
 * cleared in a `finally`, and only by the job that owns it — a second tap that cancelled the
 * first job must not clear the flag the second one just set.
 */
class MainPagerState(
    val pagerState: PagerState,
    private val coroutineScope: CoroutineScope,
    private val animatePageChanges: Boolean,
) {
    var selectedPage by mutableIntStateOf(pagerState.currentPage)
        private set

    var isNavigating by mutableStateOf(false)
        private set

    private var navJob: Job? = null

    fun animateToPage(targetIndex: Int) {
        // Already there: do nothing at all. Re-running the animation on the current page is
        // what makes a double tap visibly bounce the highlight.
        if (targetIndex == selectedPage) return

        navJob?.cancel()

        selectedPage = targetIndex
        isNavigating = true

        navJob = coroutineScope.launch {
            val myJob = coroutineContext.job
            try {
                if (animatePageChanges) {
                    // miuix's spring rather than the pager's own tween: the tab bar's highlight
                    // and the page underneath are then driven by the same physics, so they
                    // arrive together instead of one beating the other by a frame or two.
                    pagerState.springAnimateToPage(targetIndex)
                } else {
                    pagerState.scrollToPage(targetIndex)
                }
            } finally {
                if (navJob == myJob) {
                    isNavigating = false
                    // The scroll can be intercepted (a drag mid-spring stops it short). When
                    // that happens, say so, so the bar shows where the pager actually landed.
                    if (pagerState.currentPage != targetIndex) {
                        selectedPage = pagerState.currentPage
                    }
                }
            }
        }
    }

    fun syncPage() {
        if (!isNavigating && selectedPage != pagerState.currentPage) {
            selectedPage = pagerState.currentPage
        }
    }
}

@Composable
fun rememberMainPagerState(
    pagerState: PagerState,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    animatePageChanges: Boolean = true,
): MainPagerState {
    return remember(pagerState, coroutineScope, animatePageChanges) {
        MainPagerState(pagerState, coroutineScope, animatePageChanges)
    }
}

/**
 * Whether this window is wide enough to deserve a rail instead of a bar.
 *
 * Two thresholds, because one is not enough:
 *  - 840dp is Material's own breakpoint for "tablet"; past it, a bottom bar wastes a lot of
 *    horizontal space it could be using.
 *  - 600dp wide with an aspect ratio flatter than 1.2 is the landscape-phone case: wide enough
 *    to hold a rail, short enough that a bottom bar would eat the transcript.
 *
 * [enableFloatingBottomBar] is taken as an argument rather than read from a global, and it only
 * ever *prevents* the rail: a user who asked for the floating bar gets it even where a rail
 * would fit, because the floor-level layout is their choice, not the window's.
 */
@Composable
fun useNavigationRail(enableFloatingBottomBar: Boolean): Boolean {
    val windowSize = LocalWindowInfo.current.containerSize
    val deviceDensity = LocalResources.current.displayMetrics.density
    val widthDp = windowSize.width / deviceDensity
    val heightDp = windowSize.height / deviceDensity
    val wideWindow = widthDp >= 840f || (widthDp >= 600f && heightDp / widthDp < 1.2f)
    return wideWindow && !enableFloatingBottomBar
}
