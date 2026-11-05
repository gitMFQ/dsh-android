// Vendored from chenaizhang/KernelSU-Style-UI-Kit (GPL-3.0) — see NOTICE.md.
// Upstream path: app/src/main/java/com/example/kernelsustyleuikit/ui/component/material/SegmentedList.kt
// Change: rebuilt on miuix's BasicComponent inside a shared Card instead of material3's experimental SegmentedListItem; LocalListItemShapes is gone, colours come from DshTheme, and rows take content slots rather than M3 ListItem parameters.

package uk.xa0.dsh.ui.list

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.window.WindowListPopup
import uk.xa0.dsh.ui.glass.LocalGlassBarBackdrop
import uk.xa0.dsh.ui.glass.liquidPane
import uk.xa0.dsh.ui.theme.DshRadius
import uk.xa0.dsh.ui.theme.DshSpacing
import uk.xa0.dsh.ui.theme.DshTheme
import uk.xa0.dsh.ui.theme.DshType

/**
 * The settings-row vocabulary: rows with a leading icon, a title, a summary and a trailing control,
 * grouped into cards.
 *
 * Upstream built this on material3's experimental `ListItem`/`SegmentedListItem`, so its rows
 * carried a second design language — Material's list metrics, its elevation-tinted container and
 * its shape-per-position — inside an otherwise Miuix app. This app has one visual language, so the
 * rows are rebuilt on miuix's [BasicComponent], the same primitive miuix's own preferences use: the
 * same 56dp row, the same start/centre/end measure, the same end-action alignment. The *API* stays
 * close to upstream's, because the app's Me page will be written against it.
 *
 * ## How a group is put together
 *
 * [SegmentedColumn] owns the card, the heading and the dividers; the rows own their content. That
 * split is upstream's and it is the right one: the card has to know how many rows it holds, so it
 * can draw a separator *between* them and no line after the last one, while a row must not.
 *
 * ## Why `LocalListItemShapes` is gone
 *
 * Upstream published a composition local so each row could pick the corner shape for its position in
 * the group — leading shape, middle shape, trailing shape. On a card that clips its children the
 * per-row shape draws nothing visible; the card's radius is the only radius, so the local is not
 * carried over as a constant that would only mislead the next reader.
 */
val LocalSegmentedRowPadding: ProvidableCompositionLocal<PaddingValues> =
    compositionLocalOf { BasicComponentDefaults.InsideMargin }

/**
 * A group of rows in one card.
 *
 * @param title an optional section heading, drawn above the card in the accent colour — the colour
 *   is the whole reason it sits outside the card, since inside it the accent would read as a link.
 *   Blank means "no heading", not "an empty heading".
 * @param modifier applied to the whole group, heading included.
 * @param content the rows, top to bottom. An empty list draws nothing at all: an empty card reads
 *   as a loading state rather than as an empty section.
 */
@Composable
fun SegmentedColumn(
    modifier: Modifier = Modifier,
    title: String = "",
    content: List<@Composable () -> Unit>,
) {
    if (content.isEmpty()) return
    val colors = DshTheme.colors
    val backdrop = LocalGlassBarBackdrop.current
    Column(modifier = modifier) {
        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = DshType.titleSmall,
                color = colors.accent,
                modifier = Modifier.padding(start = DshSpacing.xl, bottom = DshSpacing.md),
            )
        }
        Card(
            // `codeBlock`, not `bgLayer1`: in this app's light palette every layer token is
            // pure white (`bgBase` through `bgLayer3` are all `#FFFFFF`), so a card filled
            // with one of them is invisible against the page — the rows read as a bare list
            // with hairlines, which is what the first recording of the Me pages showed.
            // `codeBlock` is the app's existing "recessed surface" token (`#F9FAFB` light /
            // `#1B1B1C` dark) and is what the Settings sheet's own cards were drawn with
            // before this vocabulary replaced them, so the two surfaces agree.
            //
            // The hairline is the other half: on a white page the fill alone is a 2-value
            // difference, and the border is what makes the card's edge a line rather than a
            // guess. Same 0.5dp/`borderL1` pair the old cards used.
            modifier = Modifier
                .fillMaxWidth()
                // The Card paints its own opaque fill internally, so the glass is a flat-safe
                // best-effort overlay: flatFill = Transparent leaves the flat tier untouched.
                .liquidPane(backdrop, RoundedCornerShape(DshRadius.card), flatFill = Color.Transparent)
                .border(0.5.dp, colors.borderL1, RoundedCornerShape(DshRadius.card)),
            colors = CardDefaults.defaultColors(
                color = colors.codeBlock,
                contentColor = colors.labelPrimary,
            ),
        ) {
            content.forEachIndexed { index, row ->
                if (index > 0) {
                    // Inset from the leading edge so the hairline starts where the text does: a
                    // full-width line would also cut the leading icon column in half.
                    HorizontalDivider(
                        modifier = Modifier.padding(start = DshSpacing.xl),
                        color = colors.borderL3,
                    )
                }
                row()
            }
        }
    }
}

/**
 * One row of a [SegmentedColumn], by content slot.
 *
 * The escape hatch as well as the base: every typed row below funnels through here, so a change to
 * row behaviour — padding, separators, the enabled dim — lands in one place rather than in seven.
 *
 * @param headline the title area.
 * @param supporting the summary line under the headline.
 * @param leading the start slot, typically an icon.
 * @param trailing the end slot, typically a switch or a value. A [RowScope], because a value that
 *   should shrink next to an arrow needs `weight`.
 * @param onClick the row action. `null` makes the row non-interactive: no ripple, no press
 *   feedback, and no accessibility "click" action.
 * @param onLongClick a secondary action, used as the row's click when [onClick] is null — a row that
 *   only answers to a long press still has to be reachable by the accessibility action.
 * @param enabled dims the row and ignores taps. Independent of [onClick]: a row may keep its action
 *   attached while it is disabled, for the moment it becomes enabled.
 */
@Composable
fun SegmentedListItem(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    headline: @Composable () -> Unit,
    supporting: (@Composable () -> Unit)? = null,
) {
    BasicComponent(
        modifier = modifier,
        insideMargin = LocalSegmentedRowPadding.current,
        startAction = leading,
        endActions = trailing,
        onClick = onClick ?: onLongClick,
        enabled = enabled,
        interactionSource = interactionSource,
    ) {
        headline()
        supporting?.invoke()
    }
}

/**
 * A switch row.
 *
 * The whole row is the target and the switch is state only (`onCheckedChange = null`). A 40dp
 * switch inside a 56dp row is a small target, and a settings list is exactly where people tap the
 * label; letting both handle the tap would toggle twice and land on the value they started with.
 */
@Composable
fun SegmentedSwitchItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = DshTheme.colors
    val haptic = LocalHapticFeedback.current
    SegmentedListItem(
        modifier = modifier,
        enabled = enabled,
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onCheckedChange(!checked)
        },
        leading = icon?.let { vector -> { Icon(vector, title, tint = colors.labelPrimary) } },
        headline = { Text(text = title, style = DshType.rowTitle, color = colors.labelPrimary) },
        supporting = summary?.let { line ->
            { Text(text = line, style = DshType.rowSummary, color = colors.labelSecondary) }
        },
        trailing = {
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        },
    )
}

/**
 * A row that opens a list and shows the current choice on the trailing edge.
 *
 * The popup is miuix's [WindowListPopup] rather than an `Overlay*` variant: those need miuix's
 * `Scaffold.popupHost`, and this app draws its own shell, so an Overlay popup would silently render
 * nothing. It is declared inside the row's trailing slot because that slot is what it anchors to.
 *
 * @param items the options, in display order.
 * @param selectedIndex the chosen option. Out of range is clamped rather than trusted: a caller
 *   whose list shrank between frames would otherwise index past the end of it.
 * @param onItemSelected called with the new index, after the popup closes.
 * @param onClick an extra action fired when the row is tapped, before the popup opens — for a row
 *   that also logs or tracks the interaction.
 */
@Composable
fun SegmentedDropdownItem(
    title: String,
    items: List<String>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val colors = DshTheme.colors
    val haptic = LocalHapticFeedback.current
    var expanded by remember { mutableStateOf(false) }

    val hasItems = items.isNotEmpty()
    val safeIndex = if (hasItems) selectedIndex.coerceIn(0, items.lastIndex) else -1
    val interactive = enabled && hasItems

    SegmentedListItem(
        modifier = modifier,
        enabled = interactive,
        onClick = if (interactive) {
            {
                onClick?.invoke()
                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                expanded = true
            }
        } else {
            null
        },
        leading = icon?.let { vector -> { Icon(vector, title, tint = colors.labelPrimary) } },
        headline = { Text(text = title, style = DshType.rowTitle, color = colors.labelPrimary) },
        supporting = summary?.let { line ->
            { Text(text = line, style = DshType.rowSummary, color = colors.labelSecondary) }
        },
        trailing = {
            if (safeIndex >= 0) {
                Text(
                    text = items[safeIndex],
                    style = DshType.rowSummary,
                    textAlign = TextAlign.End,
                    color = if (enabled) colors.accent else colors.labelTertiary,
                    // Shrinks before the arrow does, so a long value gets an ellipsis instead of
                    // pushing the arrow off the row.
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            DropdownArrowEndAction(
                actionColor = if (interactive) colors.labelSecondary else colors.labelTertiary,
            )
            WindowListPopup(
                show = expanded,
                // No dim: a dropdown is a value picker on top of a settings list, not a modal. The
                // dimming would read as "answer this before you continue", which is the opposite of
                // what a dropdown means.
                enableWindowDim = false,
                onDismissRequest = { expanded = false },
            ) {
                val dismiss = LocalDismissState.current
                Column(modifier = Modifier.padding(vertical = DshSpacing.sm)) {
                    items.forEachIndexed { index, label ->
                        // The content overload, not the (title, summary) one: the checked row has to
                        // be *painted* differently, and that overload has no way to say so.
                        BasicComponent(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                onItemSelected(index)
                                expanded = false
                                // Both, not either: `expanded` drives the animation out, and the
                                // dismiss state is what the popup host needs to release the window.
                                dismiss?.invoke()
                            },
                        ) {
                            Text(
                                text = label,
                                style = DshType.rowTitle,
                                color = if (index == safeIndex) colors.accent else colors.labelPrimary,
                            )
                        }
                    }
                }
            }
        },
    )
}

/** A single-choice row: the radio is the state, the whole row is the target. */
@Composable
fun SegmentedRadioItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
) {
    val colors = DshTheme.colors
    val haptic = LocalHapticFeedback.current
    SegmentedListItem(
        modifier = modifier,
        enabled = enabled,
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onClick()
        },
        leading = { RadioButton(selected = selected, onClick = null, enabled = enabled) },
        headline = { Text(text = title, style = DshType.rowTitle, color = colors.labelPrimary) },
        supporting = summary?.let { line ->
            { Text(text = line, style = DshType.rowSummary, color = colors.labelSecondary) }
        },
    )
}

/** A multi-choice row. */
@Composable
fun SegmentedCheckboxItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
) {
    val colors = DshTheme.colors
    val haptic = LocalHapticFeedback.current
    SegmentedListItem(
        modifier = modifier,
        enabled = enabled,
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onCheckedChange(!checked)
        },
        leading = {
            Checkbox(
                state = if (checked) ToggleableState.On else ToggleableState.Off,
                // The row toggles; the box would otherwise toggle a second time.
                onClick = null,
                enabled = enabled,
                // 24dp matches the icon column above it, so a card of mixed rows keeps one leading
                // edge instead of four.
                modifier = Modifier.size(24.dp),
            )
        },
        headline = { Text(text = title, style = DshType.rowTitle, color = colors.labelPrimary) },
        supporting = summary?.let { line ->
            { Text(text = line, style = DshType.rowSummary, color = colors.labelSecondary) }
        },
    )
}

/**
 * A row whose value is edited in place.
 *
 * The field is what takes focus, and focusing it scrolls the row into view: a text row is usually
 * near the bottom of a card and the keyboard is what would cover it. `bringIntoView` rather than
 * `animateScrollToItem` because the row does not know which list it is in.
 *
 * @param label a caption drawn above the value; blank means none. It turns the error colour rather
 *   than the value, so the text being corrected stays readable.
 * @param placeholder shown while the value is empty. The default `"-"` reads as "unset", which for a
 *   host or a port is a different statement from "empty string".
 * @param isError marks the row's label as failed validation. The field itself keeps its normal
 *   colour: a red value on top of a red label is unreadable at the moment you need to fix it.
 */
@Composable
fun SegmentedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    summary: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    textStyle: TextStyle = DshType.rowTitle,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    cursorBrush: Brush? = null,
    placeholder: String? = "-",
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = DshTheme.colors
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val resolvedCursor = cursorBrush ?: SolidColor(colors.accent)

    SegmentedListItem(
        modifier = modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .focusRequester(focusRequester),
        enabled = enabled,
        onClick = { focusRequester.requestFocus() },
        leading = leading,
        trailing = trailing,
        headline = {
            Column {
                if (label.isNotEmpty()) {
                    Text(
                        text = label,
                        style = DshType.micro,
                        color = if (isError) colors.error else colors.labelSecondary,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged {
                            if (it.isFocused) {
                                coroutineScope.launch { bringIntoViewRequester.bringIntoView() }
                            }
                        },
                    enabled = enabled,
                    readOnly = readOnly,
                    textStyle = textStyle.copy(color = colors.labelPrimary),
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    singleLine = singleLine,
                    maxLines = maxLines,
                    minLines = minLines,
                    visualTransformation = visualTransformation,
                    onTextLayout = onTextLayout,
                    interactionSource = interactionSource,
                    cursorBrush = resolvedCursor,
                    decorationBox = { innerTextField ->
                        if (value.isEmpty() && placeholder != null) {
                            // Drawn instead of the (empty) field rather than beside it, so the
                            // placeholder disappears the moment one character is typed.
                            Box(contentAlignment = Alignment.CenterStart) {
                                Text(
                                    text = placeholder,
                                    style = textStyle,
                                    color = colors.labelTertiary,
                                )
                            }
                        }
                        innerTextField()
                    },
                )
            }
        },
        supporting = summary?.let { line ->
            { Text(text = line, style = DshType.rowSummary, color = colors.labelSecondary) }
        },
    )
}