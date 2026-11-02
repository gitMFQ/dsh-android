package uk.xa0.dsh.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import uk.xa0.dsh.PendingAttachment
import uk.xa0.dsh.data.BusyEnter
import uk.xa0.dsh.ui.clickableNoRipple
import uk.xa0.dsh.ui.theme.DshRadius
import uk.xa0.dsh.ui.theme.DshSpacing
import uk.xa0.dsh.ui.theme.DshTheme
import uk.xa0.dsh.ui.theme.DshType

/**
 * What an expanded composer bar draws: the `+`, the text, its attachments, and send (or
 * stop while a turn runs).
 *
 * ## Why this is only the inside now
 *
 * The bar used to own its whole card: the text row *and* a toolbar of attach, access,
 * plan, model and context controls. That made the card a second screen's worth of chrome
 * on a phone, and every one of those controls had to be squeezed into the same horizontal
 * row as the primary action. They now live in [ComposerAccessoryRail] above the dock, one
 * pill each, and this composable is the bar's *content*: the caller supplies the glass
 * surface, because the same content is drawn in two shapes — a 56dp circle when the
 * composer is collapsed and a wide capsule when it is open (see [ComposerDock]).
 *
 * The `+` came back inside the bar in round 2. It is the exception to "controls left the
 * card" because it is the only one whose *purpose* is to write into this field — the
 * command menu it opens seeds tokens for this text — and with the toolbar gone it costs
 * the text one 28dp button rather than a share of the action row.
 *
 * Keeping the surface out of here is also what lets the wide layout reuse the same row at
 * full width without a second, drifting copy of the text-field wiring.
 *
 * ## The text is the field's own state
 *
 * This takes a [TextFieldState] rather than a copy of the text and an `onValueChange`.
 * Nothing is echoed back: the field writes to the state, the state is observable, and the
 * caller derives everything it needs from it — `state.text` for the content and
 * `state.selection.end` for the caret the `/` and `@` menus follow.
 *
 * That is also where undo and redo come from. `state.undoState` keeps the history of
 * edits the **reader** made, and two routes reach it: the Ctrl chords below, and the
 * software keyboard's own undo/redo, which arrive as `android.R.id.undo`/`redo`
 * context-menu actions and are routed here by [ComposerImeUndo]. What is deliberately
 * *not* undoable is the rest of the draft's life: `state.edit` — which is how a send
 * clears the box, how a failed send is handed its text back, and how a `/` or `@` pick
 * rewrites its token — commits an edit without recording an undo step and clears the
 * history it found.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ComposerContent(
    state: TextFieldState,
    onSend: () -> Unit,
    onStop: () -> Unit,
    running: Boolean,
    attachments: List<PendingAttachment>,
    onRemoveAttachment: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** What the plain submit does while running: `queue` or `steer`. */
    busyEnter: String = BusyEnter.QUEUE,
    /** Send with one explicit delivery mode, bypassing the preference. */
    onSendMode: (String) -> Unit = {},
    onToggleBusyEnter: () -> Unit = {},
    /** The `+` inside the bar: commands, the attachment picker and the model/permission rows. */
    onToggleCommands: () -> Unit = {},
    /** Whether the `+` is offered at all; false for a surface that cannot take files. */
    showAttach: Boolean = true,
    /** Asked by the selection toolbar's "Full screen" row. */
    onFullscreen: () -> Unit = {},
    /**
     * Asked for focus by the caller that opened the bar. Null in a composition that is
     * already expanded and should not steal focus (the wide layout, a baseline).
     */
    focusRequester: FocusRequester? = null,
) {
    val colors = DshTheme.colors
    val focusManager = LocalFocusManager.current
    // The field's selection toolbar, replaced so it can carry the full-screen door.
    val textToolbar = remember { ComposerTextToolbar() }
    // One read of the state's text, shared by the placeholder and the stop/send swap.
    // It is also what subscribes this composable to the field, so each keystroke
    // recomposes the row with the current text.
    val text = state.text
    // An attachment is a message all by itself: a screenshot with no caption is a
    // perfectly ordinary thing to send, and requiring text made the picture unsendable.
    val canSend = text.isNotBlank() || running || attachments.isNotEmpty()
    Column(modifier.fillMaxWidth()) {
        if (attachments.isNotEmpty()) {
            AttachmentRail(
                attachments = attachments,
                onRemove = onRemoveAttachment,
                modifier = Modifier.padding(
                    start = DshSpacing.md,
                    end = DshSpacing.md,
                    top = DshSpacing.sm,
                    bottom = DshSpacing.xs,
                ),
            )
        }

        // The placeholder sits in the box *behind* the field, which draws nothing of its
        // own when there is no text, so this is the same layering a `decorationBox`
        // produces. The padding lives on the row rather than on the field, so the text
        // and the ghost text share one inset and stay on the same first line.
        Row(
            Modifier
                .fillMaxWidth()
                // 56dp is the dock's resting height and every control in the row must fit
                // inside it; a single line of 14/24 text leaves the send circle room to
                // centre, so the minimum is met without a second padding constant.
                .defaultMinSize(minHeight = 56.dp)
                .padding(start = 8.dp, end = 8.dp, top = DshSpacing.xs, bottom = DshSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The `+` lives *inside* the bar again. It is the one control that belongs
            // next to the text — it opens the command menu that writes into this field —
            // and with the toolbar gone it costs the text 34dp rather than the whole
            // row it used to share.
            if (showAttach) {
                CircleIconButton(
                    icon = Icons.Rounded.Add,
                    // The web's `+` is the command menu, not an attachment button:
                    // `input.commands` = "Add files or run commands".
                    contentDescription = "Add files or run commands",
                    onClick = onToggleCommands,
                    size = 28.dp,
                    background = colors.selector,
                    tint = colors.labelPrimary,
                    iconSize = 15.dp,
                )
                Spacer(Modifier.width(DshSpacing.sm))
            }
            Box(
                Modifier
                    .weight(1f)
                    // A long draft grows the bar, but only to five lines: past that the
                    // field scrolls internally rather than turning the dock into a sheet.
                    .heightIn(max = 120.dp),
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = "Message DSH…",
                        style = DshType.messageBody,
                        color = colors.labelCaption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ComposerImeUndo(state) {
                    CompositionLocalProvider(LocalTextToolbar provides textToolbar) {
                    BasicTextField(
                        state = state,
                        textStyle = DshType.messageBody.copy(color = colors.labelPrimary),
                        cursorBrush = SolidColor(colors.accent),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Default,
                        ),
                        // Preview, not the field's own handler: this runs before the
                        // text input does, so the chord is ours and the field never
                        // also applies it. Only the two chords the request named are
                        // taken; everything else falls through to the field.
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (focusRequester != null) {
                                    Modifier.focusRequester(focusRequester)
                                } else {
                                    Modifier
                                }
                            )
                            .onPreviewKeyEvent { event ->
                                val shortcut = composerShortcut(
                                    key = event.key,
                                    ctrl = event.isCtrlPressed,
                                    shift = event.isShiftPressed,
                                    down = event.type == KeyEventType.KeyDown,
                                ) ?: return@onPreviewKeyEvent false
                                when (shortcut) {
                                    ComposerShortcut.UNDO -> state.undoState.undo()
                                    ComposerShortcut.REDO -> state.undoState.redo()
                                }
                                true
                            },
                    )
                    }
                }
            }

            Spacer(Modifier.width(DshSpacing.md))

            SendButton(
                canSend = canSend,
                running = running,
                draftBlank = text.isBlank(),
                busyEnter = busyEnter,
                onSend = {
                    focusManager.clearFocus()
                    onSend()
                },
                onStop = onStop,
                onSendMode = { mode ->
                    focusManager.clearFocus()
                    onSendMode(mode)
                },
                onToggleBusyEnter = onToggleBusyEnter,
            )
        }

        // Window-level, so it escapes the bar's clip and sits above the selection.
        ComposerSelectionMenu(textToolbar, onFullscreen)
    }
}

/**
 * The 34dp circular send button, and the delivery-mode menu behind a long press.
 *
 * While the agent runs, the message has to go somewhere: into the turn (steer, which
 * interrupts it) or into the queue (deliver once it ends). The web puts that choice on
 * `busyEnter` and swaps it with Cmd/Ctrl+Enter; a phone has no chord, so the same two
 * modes live on a long-press menu with the preference toggleable in place. Both are real
 * submissions — the menu replaces the tap rather than deferring a decision, so a steer is
 * never silently queued.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SendButton(
    canSend: Boolean,
    running: Boolean,
    draftBlank: Boolean,
    busyEnter: String,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onSendMode: (String) -> Unit,
    onToggleBusyEnter: () -> Unit,
) {
    val colors = DshTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    val isStop = running && draftBlank
    val sendFill by animateColorAsState(
        targetValue = if (canSend) colors.sendFill else colors.sendFill.copy(alpha = 0.4f),
        label = "sendFill",
    )

    Box {
        val interaction = remember { MutableInteractionSource() }
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(sendFill)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = canSend,
                    // Only a running agent has two plausible destinations.
                    onLongClick = { if (running && !draftBlank) menuOpen = true },
                    onClick = { if (isStop) onStop() else onSend() },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isStop) Icons.Rounded.Stop else Icons.Rounded.Send,
                contentDescription = when {
                    isStop -> "Stop"
                    busyEnter == BusyEnter.STEER -> "Send and steer"
                    else -> "Send and queue"
                },
                tint = colors.sendGlyph,
                modifier = Modifier.size(16.dp),
            )
        }

        if (menuOpen) {
            Popup(
                onDismissRequest = { menuOpen = false },
                popupPositionProvider = remember {
                    object : PopupPositionProvider {
                        override fun calculatePosition(
                            anchorBounds: IntRect,
                            windowSize: IntSize,
                            layoutDirection: LayoutDirection,
                            popupContentSize: IntSize,
                        ): IntOffset = IntOffset(
                            x = (anchorBounds.right - popupContentSize.width)
                                .coerceIn(8, (windowSize.width - popupContentSize.width - 8).coerceAtLeast(8)),
                            y = (anchorBounds.top - popupContentSize.height - 8).coerceAtLeast(8),
                        )
                    }
                },
            ) {
                SendModeMenu(
                    busyEnter = busyEnter,
                    onPick = { mode ->
                        menuOpen = false
                        onSendMode(mode)
                    },
                    // Deliberately stays open: flipping the default and closing in the
                    // same tap gave no sign that anything happened.
                    onToggleDefault = onToggleBusyEnter,
                )
            }
        }
    }
}

@Composable
private fun SendModeMenu(
    busyEnter: String,
    onPick: (String) -> Unit,
    onToggleDefault: () -> Unit,
) {
    val colors = DshTheme.colors
    val shape = RoundedCornerShape(DshRadius.card)
    Column(
        Modifier
            .width(246.dp)
            .shadow(6.dp, shape)
            .clip(shape)
            .background(colors.menu)
            .border(0.5.dp, colors.borderL1, shape)
            .padding(vertical = DshSpacing.xs),
    ) {
        SendModeRow(
            icon = Icons.Rounded.Send,
            title = "Steer now",
            subtitle = "Cut into the current turn",
            selected = busyEnter == BusyEnter.STEER,
            onClick = { onPick(BusyEnter.STEER) },
        )
        SendModeRow(
            icon = DshIconQueue14,
            title = "Queue for later",
            subtitle = "Deliver when this turn ends",
            selected = busyEnter == BusyEnter.QUEUE,
            onClick = { onPick(BusyEnter.QUEUE) },
        )

        Box(
            Modifier
                .padding(horizontal = DshSpacing.md, vertical = DshSpacing.xs)
                .fillMaxWidth()
                .height(0.5.dp)
                .background(colors.borderL1),
        )

        SendModeRow(
            icon = Icons.Rounded.Tune,
            title = "Default while busy",
            subtitle = "What Send does while the agent runs",
            // The current value rides on the row itself, so the toggle's effect is
            // visible while the menu stays open.
            trailing = if (busyEnter == BusyEnter.STEER) "Steer" else "Queue",
            selected = false,
            onClick = onToggleDefault,
        )
    }
}

@Composable
private fun SendModeRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    trailing: String? = null,
) {
    val colors = DshTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickableNoRipple(onClick = onClick)
            .padding(horizontal = DshSpacing.md, vertical = DshSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected || trailing != null) colors.accent else colors.labelTertiary,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(DshSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = DshType.bodyMedium, color = colors.labelPrimary, maxLines = 1)
            Text(subtitle, style = DshType.micro, color = colors.labelTertiary, maxLines = 1)
        }
        if (trailing != null) {
            Spacer(Modifier.width(DshSpacing.sm))
            Text(
                text = trailing,
                style = DshType.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = colors.accent,
                maxLines = 1,
            )
            Spacer(Modifier.width(DshSpacing.xs))
            Icon(
                Icons.Rounded.SwapVert,
                contentDescription = "Switch default",
                tint = colors.accent,
                modifier = Modifier.size(14.dp),
            )
        }
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = "Current default",
                tint = colors.accent,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/** Compact attachment chips; the web UI uses 64dp thumbnails, which is too wide here. */
@Composable
internal fun AttachmentRail(
    attachments: List<PendingAttachment>,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DshTheme.colors
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(DshSpacing.md),
    ) {
        attachments.forEach { attachment ->
            val shape = RoundedCornerShape(DshRadius.md)
            val tint = when {
                attachment.error != null -> colors.error
                attachment.receiptId == null -> colors.labelTertiary
                else -> colors.labelSecondary
            }
            Row(
                Modifier
                    .clip(shape)
                    .background(colors.tip)
                    .border(0.5.dp, colors.borderL2, shape)
                    .padding(start = DshSpacing.md, end = DshSpacing.xs, top = DshSpacing.sm, bottom = DshSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Description,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(DshSpacing.sm))
                Text(
                    text = attachment.error
                        ?: if (attachment.receiptId == null) "${attachment.name} · uploading…" else attachment.name,
                    style = DshType.micro,
                    color = tint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(DshSpacing.sm))
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Remove attachment",
                    tint = colors.labelTertiary,
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .clickableNoRipple { onRemove(attachment.id) }
                        .padding(3.dp),
                )
            }
        }
    }
}

/**
 * A small circular icon button: the composer's `+` (and the shape the old action row
 * used for it). Its tap target is the circle, so `size` is both the draw size and the
 * target.
 */
@Composable
internal fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    size: Dp,
    background: Color,
    tint: Color,
    iconSize: Dp = size * 0.5f,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickableNoRipple(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}
