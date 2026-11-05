// Vendored from chenaizhang/KernelSU-Style-UI-Kit (GPL-3.0) — see NOTICE.md.
// Upstream path: app/src/main/java/com/example/kernelsustyleuikit/ui/component/miuix/SuperSearchBar.kt
// Change: rebuilt on DshTheme colours with explicit parameters — the SearchStatus extension functions became top-level, the navigation-back handler and stringResource lookups became caller-supplied callbacks, and the deferred-padding helper is unchanged.

package uk.xa0.dsh.ui.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.basic.SearchCleanup
import uk.xa0.dsh.ui.glass.LocalGlassBarBackdrop
import uk.xa0.dsh.ui.glass.liquidPane
import uk.xa0.dsh.ui.theme.DshSpacing
import uk.xa0.dsh.ui.theme.DshTheme
import uk.xa0.dsh.ui.theme.DshType

/**
 * Padding at the top, but read in the layout phase.
 *
 * A frame-rate value — the animated top padding the pager drives — passed through the ordinary
 * `Modifier.padding` would recompose the whole subtree on every frame. Reading it inside
 * `Modifier.layout` instead only relayouts, which is the difference between a smooth expand and a
 * janky one over a drawer with a long results list beneath it.
 */
internal fun Modifier.deferredTopPadding(top: () -> Dp): Modifier = layout { measurable, constraints ->
    val topPx = top().roundToPx().coerceAtLeast(0)
    val placeable = measurable.measure(
        Constraints(
            // Height is the only dimension this trades against: subtracting the padding from the
            // child's max height is what keeps the *total* inside the incoming constraint.
            minWidth = constraints.minWidth,
            maxWidth = constraints.maxWidth,
            minHeight = (constraints.minHeight - topPx).coerceAtLeast(0),
            maxHeight = if (constraints.maxHeight == Constraints.Infinity) {
                Constraints.Infinity
            } else {
                (constraints.maxHeight - topPx).coerceAtLeast(0)
            },
        ),
    )
    val width = constraints.constrainWidth(placeable.width)
    val height = constraints.constrainHeight(placeable.height + topPx)
    layout(width, height) {
        placeable.place(0, topPx)
    }
}

/**
 * The drawer's search field, in its collapsed form: a tappable capsule with a search glyph.
 *
 * [onClick] is what opens the search — normally "put the status into EXPANDING". The capsule itself
 * holds no focus: it is a button, and tapping it swaps in the pager's real field, so the keyboard
 * never appears on a surface that is about to be replaced.
 *
 * [content] replaces the placeholder label, for a drawer that wants to show a hint line of its own.
 */
@Composable
fun SuperSearchBar(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    topPadding: Dp = 12.dp,
    content: (@Composable () -> Unit)? = null,
) {
    val colors = DshTheme.colors
    val backdrop = LocalGlassBarBackdrop.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DshSpacing.lg)
            .padding(top = topPadding, bottom = 6.dp)
            .heightIn(min = 45.dp)
            .liquidPane(backdrop, CircleShape, flatFill = colors.bgLayer1)
            .clickable(interactionSource = null, indication = null, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = MiuixIcons.Basic.Search,
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp)
                    .padding(start = 16.dp, end = 8.dp),
                tint = colors.labelTertiary,
            )
            if (content != null) {
                content()
            } else {
                Text(
                    text = label,
                    style = DshType.bodyMedium,
                    color = colors.labelTertiary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * The expanded search surface: the bar, the cancel action and a results area beneath it.
 *
 * ## Why this is not miuix's SearchBar
 *
 * miuix ships one and it is close, but its expansion happens *inside* a component that owns the
 * field, while this bar has to cross-fade a different surface — the results list — in the same
 * animation, keyed on [SearchStatus]'s four states. Driving both animations from one animated value
 * is the whole point of that state machine, so the bar is drawn here.
 *
 * ## Where "back" went
 *
 * Upstream registered a NavigationBackHandler so a system back press collapsed the search. That API
 * lives in navigationevent-compose, which this module does not depend on, so the behaviour is an
 * ordinary parameter: [onCollapseRequest] is called when the search asks to close, and the caller
 * wires it to whatever back handling its host provides. Nothing about the animation depends on how
 * the request arrives.
 *
 * @param onSearchStatusChange the only way out of this composable: the pager reports a target state
 *   and the caller decides whether to adopt it. A callback rather than internal state is what lets a
 *   drawer keep the query in a ViewModel and survive a rotation mid-search.
 * @param defaultResult what the results area shows before a query has been typed — usually the
 *   drawer's own list, unfiltered.
 * @param result what it shows once the status reaches [SearchStatus.ResultStatus.SHOW].
 * @param expandBar the bar itself, replaceable so a drawer wanting a different field keeps the
 *   pager's animation and results plumbing.
 * @param onCollapseRequest called by the cancel action, for the caller's own back handling.
 */
@Composable
fun SearchPager(
    searchStatus: SearchStatus,
    onSearchStatusChange: (SearchStatus) -> Unit,
    defaultResult: @Composable () -> Unit,
    result: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    searchBarTopPadding: Dp = 12.dp,
    onCollapseRequest: () -> Unit = {},
    expandBar: @Composable (
        SearchStatus,
        (SearchStatus) -> Unit,
        Dp,
    ) -> Unit = { status, onChange, padding ->
        SearchBar(status = status, onStatusChange = onChange, topPadding = padding)
    },
) {
    val colors = DshTheme.colors
    val systemBarsPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()

    // Two animated values, one state. The completion callback hangs off the padding one because it
    // is the slower and longer of the two (300ms against 200ms), so its end is what "the transition
    // is over" actually means; committing from the alpha animation would fire while the bar is
    // still visibly moving.
    val topPadding by animateDpAsState(
        targetValue = if (searchStatus.shouldExpand()) {
            systemBarsPadding + 5.dp
        } else {
            max(searchStatus.offsetY, 0.dp)
        },
        animationSpec = tween(300, easing = LinearOutSlowInEasing),
        label = "SearchPagerTopPadding",
    ) {
        onSearchStatusChange(searchStatus.onAnimationComplete())
    }
    val surfaceAlpha by animateFloatAsState(
        targetValue = if (searchStatus.shouldExpand()) 1f else 0f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "SearchPagerSurfaceAlpha",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .zIndex(5f)
            // A veil drawn behind the content rather than a fade of the whole column: fading the
            // column would fade the results with it, and the results are the point.
            .drawBehind { drawRect(colors.bgBase.copy(alpha = surfaceAlpha)) }
            // A deliberately unhandled click: while expanded this sits over the drawer, and without
            // it a tap in the padding would reach the list underneath and scroll what is being
            // searched.
            .semantics { onClick { false } }
            .then(
                if (!searchStatus.isCollapsed()) Modifier.pointerInput(Unit) {} else Modifier,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Not Modifier.padding(top = ...): this value is animated per frame, and reading it
                // in the layout phase keeps the expand from recomposing the results beneath it.
                .deferredTopPadding { topPadding }
                .then(
                    if (!searchStatus.isCollapsed()) {
                        Modifier.background(colors.bgBase)
                    } else {
                        Modifier
                    },
                ),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!searchStatus.isCollapsed()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(colors.bgBase),
                ) {
                    expandBar(searchStatus, onSearchStatusChange, searchBarTopPadding)
                }
            }
            AnimatedVisibility(
                visible = searchStatus.isExpand() || searchStatus.isAnimatingExpand(),
                enter = expandHorizontally() + slideInHorizontally(initialOffsetX = { it }),
                exit = shrinkHorizontally() + slideOutHorizontally(targetOffsetX = { it }),
            ) {
                Text(
                    text = "Cancel",
                    fontWeight = FontWeight.Bold,
                    color = colors.accent,
                    modifier = Modifier
                        .padding(
                            start = 4.dp,
                            end = 16.dp,
                            top = searchBarTopPadding,
                            bottom = 6.dp,
                        )
                        .clickable(
                            interactionSource = null,
                            enabled = searchStatus.isExpand(),
                            indication = null,
                        ) {
                            onCollapseRequest()
                            onSearchStatusChange(searchStatus.collapsing())
                        },
                )
            }
        }
        AnimatedVisibility(
            visible = searchStatus.isExpand(),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            when (searchStatus.resultStatus) {
                SearchStatus.ResultStatus.DEFAULT -> defaultResult()
                // EMPTY and LOAD deliberately draw nothing here: the caller's result slot already
                // owns the empty and loading presentations, and a second one here would stack two
                // spinners over each other.
                SearchStatus.ResultStatus.EMPTY -> Unit
                SearchStatus.ResultStatus.LOAD -> Unit
                SearchStatus.ResultStatus.SHOW -> result()
            }
        }
    }
}

/**
 * The field itself: one line, a search glyph, and a clear button that appears with the text.
 *
 * Focus is requested when the bar starts expanding, not when it is first composed: asking for it
 * while the bar is still 0dp wide is the one arrangement where an IME drops the request and the
 * user gets an expanded bar with no keyboard.
 */
@Composable
fun SearchBar(
    status: SearchStatus,
    onStatusChange: (SearchStatus) -> Unit,
    modifier: Modifier = Modifier,
    topPadding: Dp = 12.dp,
) {
    val colors = DshTheme.colors
    val backdrop = LocalGlassBarBackdrop.current
    val focusRequester = remember { FocusRequester() }
    // The field keeps its own TextFieldValue so the selection survives a text change; the status is
    // the source of truth, and the effect below reconciles the two when it changes from outside
    // (a cancel, or a restore after a configuration change).
    var textFieldValue by remember { mutableStateOf(TextFieldValue(status.searchText)) }

    LaunchedEffect(status.searchText) {
        if (textFieldValue.text != status.searchText) {
            textFieldValue = TextFieldValue(status.searchText)
        }
    }

    BasicTextField(
        value = textFieldValue,
        onValueChange = {
            textFieldValue = it
            onStatusChange(status.copy(searchText = it.text))
        },
        singleLine = true,
        textStyle = TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = 17.sp,
            color = colors.labelPrimary,
        ),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DshSpacing.lg)
            .padding(top = topPadding, bottom = 6.dp)
            .heightIn(min = 45.dp)
            .liquidPane(backdrop, CircleShape, flatFill = colors.bgLayer1)
            .focusRequester(focusRequester),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = MiuixIcons.Basic.Search,
                    contentDescription = null,
                    modifier = Modifier
                        .size(44.dp)
                        .padding(start = 16.dp, end = 8.dp),
                    tint = colors.labelTertiary,
                )
                Box(modifier = Modifier.weight(1f)) {
                    innerTextField()
                }
                AnimatedVisibility(
                    visible = status.searchText.isNotEmpty(),
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                ) {
                    Icon(
                        imageVector = MiuixIcons.Basic.SearchCleanup,
                        tint = colors.labelSecondary,
                        contentDescription = null,
                        modifier = Modifier
                            .size(44.dp)
                            .padding(start = 8.dp, end = 16.dp)
                            .clickable(interactionSource = null, indication = null) {
                                textFieldValue = TextFieldValue("")
                                onStatusChange(status.copy(searchText = ""))
                            },
                    )
                }
            }
        },
    )

    LaunchedEffect(Unit) {
        if (status.isAnimatingExpand()) {
            focusRequester.requestFocus()
        }
    }
}
