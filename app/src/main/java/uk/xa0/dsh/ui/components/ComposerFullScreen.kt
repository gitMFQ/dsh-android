package uk.xa0.dsh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import uk.xa0.dsh.PendingAttachment
import uk.xa0.dsh.R
import uk.xa0.dsh.data.BusyEnter
import uk.xa0.dsh.ui.clickableNoRipple
import uk.xa0.dsh.ui.theme.DshRadius
import uk.xa0.dsh.ui.theme.DshSpacing
import uk.xa0.dsh.ui.theme.DshTheme
import uk.xa0.dsh.ui.theme.DshType

/**
 * The composer's selection toolbar, with one extra door: **full-screen input**.
 *
 * ## Why the text toolbar is replaced rather than extended
 *
 * Compose's text context menu is a platform surface, and the platform's floating
 * toolbar has no public way to append a row to it. But `LocalTextToolbar` is the
 * seam the field asks for its menu — so the app provides its own, with the same
 * copy/cut/paste/select-all callbacks the field hands over plus the row this screen
 * needs. The menu is drawn in the app's own vocabulary instead of the platform's
 * Material chrome, which is why it reads as part of the composer.
 *
 * The new Compose context menu (targetSdk 35+) bypasses `LocalTextToolbar`; see
 * `DshApplication`, which pins the legacy path for the same reason.
 *
 * ## Coordinates
 *
 * `TextToolbar.showMenu` hands a rect in **window** coordinates, and a
 * [PopupPositionProvider] answers in the same space, so the menu is placed directly
 * above the selection without a second transform.
 */
internal class SelectionMenu(
    val rect: Rect,
    val onCopy: (() -> Unit)?,
    val onPaste: (() -> Unit)?,
    val onCut: (() -> Unit)?,
    val onSelectAll: (() -> Unit)?,
)

internal class ComposerTextToolbar : TextToolbar {
    var menu by mutableStateOf<SelectionMenu?>(null)
        private set

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        menu = SelectionMenu(rect, onCopyRequested, onPasteRequested, onCutRequested, onSelectAllRequested)
    }

    override fun hide() {
        menu = null
    }

    override val status: TextToolbarStatus
        get() = if (menu != null) TextToolbarStatus.Shown else TextToolbarStatus.Hidden

    fun dismiss() {
        menu = null
    }
}

/**
 * The menu [toolbar] is holding, if any: selection actions the field offered, then
 * the full-screen door. A row is drawn only when its callback exists, which is the
 * same rule the platform toolbar uses.
 */
@Composable
internal fun ComposerSelectionMenu(
    toolbar: ComposerTextToolbar,
    /**
     * The "Full screen" door. Null on a surface that already *is* the full screen, and
     * on any surface that has no bigger one to offer.
     */
    onFullscreen: (() -> Unit)? = null,
    /**
     * What the field currently has selected. Read only when the menu opens, so a row
     * always acts on the text the reader sees highlighted rather than on whatever the
     * field held when the popup was first composed.
     */
    selectedText: () -> String = { "" },
    /**
     * "Quote text" (`quote.add` in the web client): hand the selection to the host, which
     * is where a quote becomes a `>` block in the draft. Null leaves the row out, which
     * is what a host with nowhere to put a quote should pass.
     */
    onQuote: ((String) -> Unit)? = null,
    /**
     * "Translate": hand the selection to the host's translation RPC
     * (`cotTranslation/translate`). Null leaves the row out — there is no client-side
     * translator to fall back on.
     */
    onTranslate: ((String) -> Unit)? = null,
) {
    val request = toolbar.menu ?: return
    val colors = DshTheme.colors
    val shape = RoundedCornerShape(DshRadius.card)
    // Captured while the menu is shown: the selection is what the reader highlighted,
    // and it stops being the current selection the moment a row is tapped.
    val selected = remember(request) { selectedText() }
    val hasSelection = selected.isNotBlank()
    Popup(
        onDismissRequest = { toolbar.dismiss() },
        properties = PopupProperties(focusable = false, dismissOnClickOutside = true),
        popupPositionProvider = remember(request.rect) {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize,
                ): IntOffset = IntOffset(
                    x = (request.rect.center.x.toInt() - popupContentSize.width / 2)
                        .coerceIn(8, (windowSize.width - popupContentSize.width - 8).coerceAtLeast(8)),
                    y = (request.rect.top.toInt() - popupContentSize.height - 8).coerceAtLeast(8),
                )
            }
        },
    ) {
        Column(
            Modifier
                .widthIn(min = 168.dp, max = 260.dp)
                .shadow(6.dp, shape)
                .clip(shape)
                .background(colors.menu)
                .border(0.5.dp, colors.borderL1, shape)
                .padding(vertical = DshSpacing.xs),
        ) {
            request.onSelectAll?.let {
                SelectionRow(Icons.Rounded.SelectAll, stringResource(R.string.composer_select_all)) {
                    toolbar.dismiss()
                    it()
                }
            }
            request.onCopy?.let {
                SelectionRow(DshIconVectors.CopyOutline, stringResource(R.string.chat_copy)) {
                    toolbar.dismiss()
                    it()
                }
            }
            request.onCut?.let {
                SelectionRow(Icons.Rounded.ContentCut, stringResource(R.string.composer_cut)) {
                    toolbar.dismiss()
                    it()
                }
            }
            request.onPaste?.let {
                SelectionRow(Icons.Rounded.ContentPaste, stringResource(R.string.composer_paste)) {
                    toolbar.dismiss()
                    it()
                }
            }
            // The two text actions sit above the full-screen door, in the web's order:
            // what to do with the selection, then where to keep writing.
            if (onQuote != null && hasSelection) {
                SelectionRow(
                    Icons.Rounded.FormatQuote,
                    stringResource(R.string.composer_quote_text),
                ) {
                    toolbar.dismiss()
                    onQuote(selected)
                }
            }
            if (onTranslate != null && hasSelection) {
                SelectionRow(Icons.Rounded.Translate, stringResource(R.string.composer_translate)) {
                    toolbar.dismiss()
                    onTranslate(selected)
                }
            }
            if ((onQuote != null || onTranslate != null) && hasSelection) {
                Box(
                    Modifier
                        .padding(horizontal = DshSpacing.md, vertical = DshSpacing.xs)
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(colors.borderL1),
                )
            }
            if (onFullscreen != null) {
                SelectionRow(DshIconVectors.FullscreenOutline, stringResource(R.string.composer_full_screen)) {
                    toolbar.dismiss()
                    onFullscreen()
                }
            }
        }
    }
}

@Composable
private fun SelectionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = DshTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickableNoRipple(onClick = onClick)
            .padding(horizontal = DshSpacing.md, vertical = DshSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.labelSecondary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(DshSpacing.md))
        Text(label, style = DshType.bodyMedium, color = colors.labelPrimary, maxLines = 1)
    }
}

/**
 * Full-screen writing.
 *
 * The bar is deliberately a bar: one line, a thumb's reach, and the transcript behind
 * it. Long drafts are the case it is bad at, so the selection toolbar offers this
 * screen — the field uses the whole window above the keyboard, with the same
 * [TextFieldState] (so nothing is copied or handed over, and closing returns to the
 * draft exactly as it was). Attachments and `+` come with it; send/stop is the same
 * control, in the header where a full-screen composer's primary action belongs.
 *
 * The dialog window is full-size and does not fit system windows (`decorFitsSystemWindows
 * = false` in the caller), so this surface pads the bars itself and the IME shrinks the
 * writing area rather than hiding it.
 */
@Composable
fun FullScreenComposer(
    state: TextFieldState,
    running: Boolean,
    attachments: List<PendingAttachment>,
    onRemoveAttachment: (String) -> Unit,
    onToggleCommands: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    busyEnter: String = BusyEnter.QUEUE,
    onSendMode: (String) -> Unit = {},
    onToggleBusyEnter: () -> Unit = {},
    /**
     * Whether the `+`'s roster is open.
     *
     * ## Why the roster comes in, rather than being toggled outside
     *
     * This surface is a dialog window, and the dock's own roster is drawn in the
     * activity's window — behind it. That was the bug: the `+` here opened a menu the
     * reader could not see, because the full-screen surface was covering it. The roster
     * has to be composed *in this window*, so the caller hands both the state and the
     * content over.
     */
    commandsOpen: Boolean = false,
    /** The roster to draw while [commandsOpen] — [CommandMenu] or any other menu. */
    commandMenu: (@Composable () -> Unit)? = null,
    /** The selection menu's "Quote text" row; null leaves it out. */
    onQuote: ((String) -> Unit)? = null,
    /** The selection menu's "Translate" row; null leaves it out. */
    onTranslate: ((String) -> Unit)? = null,
) {
    val colors = DshTheme.colors
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val text = state.text
    // The same replaced toolbar the bar's field has, so a selection made in full screen
    // offers the same rows — quote and translate included. There is no "Full screen"
    // row here: this surface already is it.
    val textToolbar = remember { ComposerTextToolbar() }

    // Entering this screen is a request to type in it: focus and the keyboard, once.
    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }

    // A Box, not a bare Column, so the command roster can be drawn in *this* window,
    // over the writing surface, instead of in the activity window behind it.
    Box(
        modifier
            .fillMaxSize()
            .background(colors.bgBase)
            .statusBarsPadding()
            // A full-screen surface still has to keep its bottom row off the system bar,
            // without double-padding when the keyboard already covers that strip.
            .windowInsetsPadding(WindowInsets.navigationBars.exclude(WindowInsets.ime))
            .imePadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = DshSpacing.xl),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    DshIconVectors.CloseOutline,
                    contentDescription = stringResource(R.string.composer_close_full_screen),
                    tint = colors.labelSecondary,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(DshRadius.md))
                        .clickableNoRipple(onClick = onClose)
                        .padding(10.dp),
                )
                Spacer(Modifier.width(DshSpacing.sm))
                Text(
                    text = stringResource(R.string.composer_full_screen_title),
                    style = DshType.titleSmall,
                    color = colors.labelSecondary,
                    modifier = Modifier.weight(1f),
                )
                SendButton(
                    canSend = text.isNotBlank() || running || attachments.isNotEmpty(),
                    running = running,
                    draftBlank = text.isBlank(),
                    busyEnter = busyEnter,
                    onSend = onSend,
                    onStop = onStop,
                    onSendMode = onSendMode,
                    onToggleBusyEnter = onToggleBusyEnter,
                )
            }

            if (attachments.isNotEmpty()) {
                AttachmentRail(
                    attachments = attachments,
                    onRemove = onRemoveAttachment,
                    modifier = Modifier.padding(
                        start = DshSpacing.xl,
                        end = DshSpacing.xl,
                        top = DshSpacing.sm,
                    ),
                )
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = DshSpacing.xl, vertical = DshSpacing.md),
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = stringResource(R.string.composer_placeholder),
                        style = DshType.bodyLarge.copy(fontSize = 16.sp, lineHeight = 26.sp),
                        color = colors.labelCaption,
                        maxLines = 1,
                    )
                }
                CompositionLocalProvider(LocalTextToolbar provides textToolbar) {
                    BasicTextField(
                        state = state,
                        textStyle = DshType.bodyLarge.copy(
                            fontSize = 16.sp,
                            lineHeight = 26.sp,
                            color = colors.labelPrimary,
                        ),
                        cursorBrush = SolidColor(colors.accent),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Default,
                        ),
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(focus),
                    )
                }
            }

            ComposerSelectionMenu(
                toolbar = textToolbar,
                selectedText = {
                    val selection = state.selection
                    val full = state.text
                    if (selection.collapsed) {
                        ""
                    } else {
                        runCatching { full.subSequence(selection.min, selection.max).toString() }
                            .getOrDefault("")
                    }
                },
                onQuote = onQuote,
                onTranslate = onTranslate,
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = DshSpacing.xl,
                        end = DshSpacing.xl,
                        top = DshSpacing.xs,
                        bottom = DshSpacing.md,
                    ),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIconButton(
                    icon = DshIconVectors.PlusOutline,
                    contentDescription = stringResource(R.string.composer_add_files_or_commands),
                    onClick = onToggleCommands,
                    size = 32.dp,
                    background = colors.selector,
                    tint = colors.labelPrimary,
                    iconSize = 17.dp,
                )
            }
        }

        // The roster, inside this window. It sits just above the action row rather than
        // at the caret: there is no anchor on this screen, and the `+` is the control
        // that opened it, so the menu belongs over the field it writes into.
        val roster = commandMenu
        if (commandsOpen && roster != null) {
            // No scrim: the dock's roster is an inline panel with no outside-dismiss
            // either, and a full-bleed tap-catcher here would cover the very field this
            // screen exists to write in. The `+` toggles it shut, exactly as it opens it.
            // Anchored to the top of the action row and growing upward, capped so a long
            // roster cannot swallow the field it writes into.
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .padding(
                        start = DshSpacing.xl,
                        end = DshSpacing.xl,
                        // Clear the action row below (32dp button + its padding) so the
                        // menu never covers the `+` that opened it.
                        bottom = 32.dp + DshSpacing.xs + DshSpacing.md,
                        top = DshSpacing.xl,
                    ),
            ) {
                roster()
            }
        }
    }
}