package uk.xa0.dsh.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import uk.xa0.dsh.PendingAttachment
import uk.xa0.dsh.data.BusyEnter
import uk.xa0.dsh.ui.clickableNoRipple
import uk.xa0.dsh.ui.glass.GlassBarBackdrop
import uk.xa0.dsh.ui.glass.glassLift
import uk.xa0.dsh.ui.glass.GlassComponent
import uk.xa0.dsh.ui.glass.liquidPane
import uk.xa0.dsh.ui.nav.BarHeight
import uk.xa0.dsh.ui.nav.BottomBarItem
import uk.xa0.dsh.ui.nav.FloatingBottomBar
import uk.xa0.dsh.ui.nav.barNaturalWidth
import uk.xa0.dsh.ui.theme.DshSpacing
import uk.xa0.dsh.ui.theme.DshTheme

/**
 * The composer as the bottom bar's other pane: a 56dp circular button that opens into a
 * message bar, or a bar that closes back into the button.
 *
 * ## The reference and the split
 *
 * This is the shape BiliPai's liquid-glass dock uses for its search control: at rest the
 * navigation dock and a circle sit side by side; when the circle opens, the dock gives up
 * its tab strip and keeps only a circle of the current destination, and the search grows
 * into the space it freed. The composer is the same gesture here — tap the circle to
 * write, tap the dock's circle to put the bar back — because the phone's bottom edge has
 * one thumb and one place to reach.
 *
 * The invariant is that the row is always the same width: the dock contracts by exactly
 * what the input takes, so opening the composer never makes the two panes jump or overflow.
 * On a 360dp phone that is 264 + 8 + 56 = 328 at rest and 56 + 8 + 264 = 328 open.
 *
 * ## Why the dock keeps a circle rather than a squeezed tab row
 *
 * Four 64dp tabs cannot shrink to 56dp without cutting labels; a dock that narrowed by
 * clipping its own tabs would look broken for the whole animation. Collapsing to the
 * current destination's icon is the same move the reference makes, and it still says
 * where you are. Tapping it shows the tab row again; the single icon is the door to the
 * four, which is what keeps the default state from spending a third of the row on
 * labels nobody asked for.
 *
 * ## The controls are not in here
 *
 * The bar keeps the `+` (which belongs beside the text it writes into) and send/stop.
 * Everything else moved to [ComposerAccessoryRail] above it, one pill each, which is what
 * gives send the whole trailing edge and keeps the bar's content a text field rather than
 * a toolbar.
 */
@Composable
fun ComposerDock(
    state: TextFieldState,
    expanded: Boolean,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    running: Boolean,
    attachments: List<PendingAttachment>,
    onRemoveAttachment: (String) -> Unit,
    items: List<BottomBarItem>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    backdrop: GlassBarBackdrop?,
    modifier: Modifier = Modifier,
    busyEnter: String = BusyEnter.QUEUE,
    onSendMode: (String) -> Unit = {},
    onToggleBusyEnter: () -> Unit = {},
    /** The `+` in the bar's leading edge: the command menu that writes into this field. */
    onToggleCommands: () -> Unit = {},
    showAttach: Boolean = true,
    /** Asked by the selection toolbar's "Full screen" row. */
    onFullscreen: () -> Unit = {},
    /** The selection menu's "Quote text" row; null leaves it out. */
    onQuote: ((String) -> Unit)? = null,
    /** The selection menu's "Translate" row; null leaves it out. */
    onTranslate: ((String) -> Unit)? = null,
    /** False on a wide window, where the rail owns navigation and the bar would duplicate it. */
    showNavigation: Boolean = true,
    /** False while a read-only subagent's frame claims the composer's seat. */
    showInput: Boolean = true,
) {
    val navFull = barNaturalWidth(items.size)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val available = (maxWidth - DshSpacing.xl * 2).coerceAtLeast(0.dp)
        // The dock gives up exactly what the bar takes, so the row never resizes the
        // screen around it.
        val roomBesideDock = (available - CompactNavSize - DockGap).coerceAtLeast(0.dp)
        // Collapsed, the tab bar **fills** the row: the four slots share everything the
        // input gives up, so the dock's outer margins are the same 16dp in both shapes
        // instead of the bar floating 41dp in from the edge. At 360dp the stretched width
        // is exactly `barNaturalWidth`; wider phones spread the four slots.
        val stretchedNavWidth = (available - DockGap - CompactNavSize).coerceAtLeast(navFull)
        val expandedWidth = if (showNavigation) {
            roomBesideDock.coerceAtMost(ExpandedInputMax)
        } else {
            available.coerceAtMost(WideInputMax)
        }

        val navWidth by animateDpAsState(
            targetValue = if (expanded && showNavigation) CompactNavSize else stretchedNavWidth,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 420f),
            label = "dockNavWidth",
        )
        val navHeight by animateDpAsState(
            targetValue = if (expanded && showNavigation) CompactNavSize else BarHeight,
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
            label = "dockNavHeight",
        )
        // The bar is 64dp tall and the circle 56; with bottoms aligned the circle's
        // centre sits 4dp lower than the bar's own inner pill. Raising the circle by the
        // bar's content inset is what puts the two glass panes on one line. The expanded
        // bar has no nudge: its own height sets the row, and the dock circle matches it.
        val inputNudge by animateDpAsState(
            targetValue = if (expanded || !showNavigation) 0.dp else -BarContentInset,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 420f),
            label = "dockInputNudge",
        )
        val inputWidth by animateDpAsState(
            targetValue = when {
                !showInput -> 0.dp
                expanded -> expandedWidth
                else -> DockCircleSize
            },
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
            label = "dockInputWidth",
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = DshSpacing.xl),
            horizontalArrangement = Arrangement.Center,
            // Bottoms, not centres: the bar is 64dp tall and the circle 56dp, and when a
            // long draft grows the bar the dock must stay on the floor of the screen
            // rather than float in the middle of the taller pane.
            verticalAlignment = Alignment.Bottom,
        ) {
            if (showNavigation) {
                NavigationPane(
                    expanded = expanded,
                    width = navWidth,
                    height = navHeight,
                    fullWidth = stretchedNavWidth,
                    items = items,
                    selectedKey = selectedKey,
                    onSelect = onSelect,
                    onCollapse = onCollapse,
                    backdrop = backdrop,
                )
            }
            if (showNavigation && showInput) {
                Spacer(Modifier.width(DockGap))
            }
            if (showInput) {
                InputPane(
                    state = state,
                    expanded = expanded,
                    width = inputWidth,
                    onExpand = onExpand,
                    onSend = onSend,
                    onStop = onStop,
                    running = running,
                    attachments = attachments,
                    onRemoveAttachment = onRemoveAttachment,
                    busyEnter = busyEnter,
                    onSendMode = onSendMode,
                    onToggleBusyEnter = onToggleBusyEnter,
                    onToggleCommands = onToggleCommands,
                    showAttach = showAttach,
                    onFullscreen = onFullscreen,
                    onQuote = onQuote,
                    onTranslate = onTranslate,
                    backdrop = backdrop,
                    verticalNudge = inputNudge,
                )
            }
        }
    }
}

private val DockCircleSize = 56.dp
private val DockGap = 8.dp
private val CompactNavSize = 56.dp

/**
 * The bar's own content inset, mirrored here so the circle's centre lines up with the
 * bar's inner pill rather than with its slightly taller shell.
 *
 * `BarInset` in `:design` is internal; this is the one number the dock needs, and copying
 * it is honest only if it is named and explained — a change to the bar's inset should
 * change this too.
 */
private val BarContentInset = 4.dp

/**
 * How wide the expanded bar may grow beside the dock.
 *
 * On a phone it takes the whole remainder (the default shape is the open bar, so it should
 * read as the row's owner rather than the dock's guest); the cap only stops a very wide
 * phone window from stretching one message field across it. On a tablet the bar is the
 * only pane ([showNavigation] false) and takes its own, wider cap.
 */
private val ExpandedInputMax = 360.dp
private val WideInputMax = 720.dp

/**
 * The navigation side of the dock: the full tab bar at rest, the current destination's
 * icon while the composer is open.
 *
 * Both are kept in the same box and clipped to the animating width, so the bar appears to
 * fold into the circle rather than being swapped for one. The circle's meaning is "show the
 * navigation": the composer is the default pane now, so putting the tab bar back is the move
 * the reader asks for there. The bar's own width is forced
 * with `requiredWidth`: it is still measured at its natural size while the box around it
 * narrows, which is what makes the fold a clip instead of a re-layout of four tabs.
 */
@Composable
private fun NavigationPane(
    expanded: Boolean,
    width: Dp,
    height: Dp,
    fullWidth: Dp,
    items: List<BottomBarItem>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    onCollapse: () -> Unit,
    backdrop: GlassBarBackdrop?,
) {
    val colors = DshTheme.colors
    val barAlpha by animateFloatAsState(
        targetValue = if (expanded) 0f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "dockBarAlpha",
    )
    val compactAlpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(durationMillis = 180, delayMillis = if (expanded) 90 else 0),
        label = "dockCompactAlpha",
    )
    val selectedIcon = remember(items, selectedKey) {
        items.firstOrNull { it.key == selectedKey }?.icon ?: items.firstOrNull()?.icon
    }

    // Clip **only while folding**. At rest the tab bar's pressed tab grows past the panel
    // (78/56 of the pill height, and a little past the shell on both axes) — that bloom is
    // the material's whole press affordance, and clipping the box at rest cut it off. The
    // fold needs the clip because the tab bar is measured at its full width inside a box
    // that is shrinking; the resting shapes need none.
    val folding = width < fullWidth - 0.5.dp
    Box(
        Modifier
            .width(width)
            .height(height)
            .then(if (folding) Modifier.clip(CircleShape).clipToBounds() else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        // Composed only while it has something to paint: once the fold has finished the
        // tab bar costs nothing, and its backdrop pass is not run behind a circle that
        // covers it.
        if (barAlpha > 0.01f) {
            FloatingBottomBar(
                items = items,
                selectedKey = selectedKey,
                onSelect = onSelect,
                backdrop = backdrop,
                modifier = Modifier
                    .requiredWidth(fullWidth)
                    .graphicsLayer { alpha = barAlpha },
            )
        }
        if (compactAlpha > 0.01f && selectedIcon != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = compactAlpha }
                    .glassLift(CircleShape)
                    .liquidPane(backdrop, GlassComponent.COMPOSER, CircleShape)
                    .clickableNoRipple(onClick = onCollapse)
                    .semantics { contentDescription = "Show navigation" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = selectedIcon,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/**
 * The input side: a circle at rest, a message bar when open.
 *
 * The width is animated here rather than by an `AnimatedContent` size transform, because
 * the dock needs the two panes to reach their target widths together — the row's other
 * pane is animating its own width, and only a shared shape of motion keeps the pair from
 * looking like two independent controls.
 */
@Composable
private fun InputPane(
    state: TextFieldState,
    expanded: Boolean,
    width: Dp,
    onExpand: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    running: Boolean,
    attachments: List<PendingAttachment>,
    onRemoveAttachment: (String) -> Unit,
    busyEnter: String,
    onSendMode: (String) -> Unit,
    onToggleBusyEnter: () -> Unit,
    onToggleCommands: () -> Unit,
    showAttach: Boolean,
    onFullscreen: () -> Unit,
    onQuote: ((String) -> Unit)?,
    onTranslate: ((String) -> Unit)?,
    backdrop: GlassBarBackdrop?,
    verticalNudge: Dp,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(DshRadiusForCapsule)

    // ## The dock never raises the keyboard by itself
    //
    // Opening the bar with the circle (or collapsing it again) is a *shape* gesture, not
    // a request to type: it used to request focus and show the IME, so putting the dock
    // back to its resting shape — or asking for the navigation — threw a keyboard over the
    // transcript. The field is still focusable and a tap on it opens the keyboard the
    // ordinary way; nothing else does. Collapsing clears focus so a keyboard raised by a
    // tap does not outlive the bar it belonged to.
    LaunchedEffect(expanded) {
        if (!expanded) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }

    Box(
        Modifier
            .offset(y = verticalNudge)
            .width(width)
            .heightIn(min = DockCircleSize)
            .glassLift(shape)
            .liquidPane(backdrop, GlassComponent.COMPOSER, shape)
            .clip(shape)
            .clipToBounds(),
    ) {
        // The circle is measured at the pane's full height so the pane does not change
        // height on the cross-fade; the bar's content is allowed to grow past it.
        AnimatedVisibility(
            visible = !expanded,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(90)),
        ) {
            CollapsedComposer(
                hasDraft = state.text.isNotEmpty(),
                onExpand = onExpand,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(160, delayMillis = 70)),
            exit = fadeOut(tween(90)),
        ) {
            ComposerContent(
                state = state,
                onSend = onSend,
                onStop = onStop,
                running = running,
                attachments = attachments,
                onRemoveAttachment = onRemoveAttachment,
                busyEnter = busyEnter,
                onSendMode = onSendMode,
                onToggleBusyEnter = onToggleBusyEnter,
                onToggleCommands = onToggleCommands,
                showAttach = showAttach,
                onFullscreen = onFullscreen,
                onQuote = onQuote,
                onTranslate = onTranslate,
            )
        }
    }
}

/** The 56dp radius that makes a 56dp-tall bar a capsule and keeps it one when it grows. */
private val DshRadiusForCapsule = 28.dp

/**
 * The resting composer: a pencil in a glass circle, the whole circle a target.
 *
 * ## Two ways to open it
 *
 * A tap is the obvious one. The swipe is the reference's other half — a horizontal or
 * upward pull on the circle opens the bar — and it is a *drag* detector rather than a
 * scroll: the circle is not in a scrollable, so there is no gesture to compete with, and
 * the threshold (24dp) is what keeps a slightly smudged tap from opening the keyboard.
 *
 * A dot marks a draft that was typed and then collapsed: without it the circle would look
 * empty while the session still held the reader's words.
 */
@Composable
private fun CollapsedComposer(
    hasDraft: Boolean,
    onExpand: () -> Unit,
) {
    val colors = DshTheme.colors
    val density = LocalDensity.current
    val threshold = with(density) { 24.dp.toPx() }
    Box(
        Modifier
            .fillMaxWidth()
            .height(DockCircleSize)
            .clickableNoRipple(onClick = onExpand)
            .then(
                Modifier.pointerInput(Unit) {
                    var dragged = 0f
                    detectDragGestures(
                        onDragStart = { dragged = 0f },
                        onDragEnd = { if (dragged >= threshold) onExpand() },
                        onDrag = { change, amount ->
                            change.consume()
                            // Either axis: towards the middle of the dock or up out of
                            // the way, both read as "open it".
                            dragged += abs(amount.x) + (-amount.y).coerceAtLeast(0f)
                        },
                    )
                }
            )
            .semantics { contentDescription = if (hasDraft) "Write a message, draft kept" else "Write a message" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = DshIconVectors.EditOutline,
            contentDescription = null,
            tint = colors.labelPrimary,
            modifier = Modifier.size(22.dp),
        )
        if (hasDraft) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 12.dp)
                    .size(6.dp)
                    .background(colors.accent, CircleShape),
            )
        }
    }
}