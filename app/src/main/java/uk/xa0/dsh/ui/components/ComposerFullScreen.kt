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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
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
    onFullscreen: () -> Unit,
) {
    val request = toolbar.menu ?: return
    val colors = DshTheme.colors
    val shape = RoundedCornerShape(DshRadius.card)
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
                SelectionRow(Icons.Rounded.SelectAll, "Select all") {
                    toolbar.dismiss()
                    it()
                }
            }
            request.onCopy?.let {
                SelectionRow(Icons.Rounded.ContentCopy, "Copy") {
                    toolbar.dismiss()
                    it()
                }
            }
            request.onCut?.let {
                SelectionRow(Icons.Rounded.ContentCut, "Cut") {
                    toolbar.dismiss()
                    it()
                }
            }
            request.onPaste?.let {
                SelectionRow(Icons.Rounded.ContentPaste, "Paste") {
                    toolbar.dismiss()
                    it()
                }
            }
            SelectionRow(Icons.Rounded.Fullscreen, "Full screen") {
                toolbar.dismiss()
                onFullscreen()
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
) {
    val colors = DshTheme.colors
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val text = state.text

    // Entering this screen is a request to type in it: focus and the keyboard, once.
    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.bgBase)
            .statusBarsPadding()
            // A full-screen surface still has to keep its bottom row off the system bar,
            // without double-padding when the keyboard already covers that strip.
            .windowInsetsPadding(WindowInsets.navigationBars.exclude(WindowInsets.ime))
            .imePadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = DshSpacing.xl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Close full screen input",
                tint = colors.labelSecondary,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(DshRadius.md))
                    .clickableNoRipple(onClick = onClose)
                    .padding(10.dp),
            )
            Spacer(Modifier.width(DshSpacing.sm))
            Text(
                text = "Message",
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
                    text = "Message DSH…",
                    style = DshType.bodyLarge.copy(fontSize = 16.sp, lineHeight = 26.sp),
                    color = colors.labelCaption,
                    maxLines = 1,
                )
            }
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
                icon = Icons.Rounded.Add,
                contentDescription = "Add files or run commands",
                onClick = onToggleCommands,
                size = 32.dp,
                background = colors.selector,
                tint = colors.labelPrimary,
                iconSize = 17.dp,
            )
        }
    }
}