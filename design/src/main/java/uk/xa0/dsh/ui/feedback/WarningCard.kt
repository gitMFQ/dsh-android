// Vendored from chenaizhang/KernelSU-Style-UI-Kit (GPL-3.0) — see NOTICE.md.
// Upstream path: app/src/main/java/com/example/kernelsustyleuikit/ui/component/miuix/WarningCard.kt + ui/component/WarningLevel.kt
// Change: the dynamic-colour / isInDarkTheme() three-way branch is replaced by DshTheme tokens (the fill is mixed from the semantic colour), and WarningLevel moves into this file.

package uk.xa0.dsh.ui.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import uk.xa0.dsh.ui.glass.LocalGlassBarBackdrop
import uk.xa0.dsh.ui.glass.liquidPane
import uk.xa0.dsh.ui.theme.DshColors
import uk.xa0.dsh.ui.theme.DshRadius
import uk.xa0.dsh.ui.theme.DshTheme
import uk.xa0.dsh.ui.theme.DshType

/**
 * How loud a [WarningCard] is.
 *
 * [Error] is the destructive one — an action that loses data, removes a host, or signs everything
 * out. [Notice] is the same card carrying information rather than a consequence, which is why it
 * gets the warning colour instead of the error one: a red card that only says "here is a tip"
 * spends the reader's alarm on nothing, and the next red card is worth less for it.
 */
enum class WarningLevel { Error, Notice }

/**
 * The danger-action card: one sentence, optionally something to do about it.
 *
 * Upstream picked its colours from three sources — miuix's dynamic palette, `isInDarkTheme()` with
 * hand-mixed hexes, and a third set of hexes for light — with a different result in each mode. Here
 * that collapses to one derivation per level, so the card follows all seven appearances (including
 * AMOLED and Monet) with no second table and no mode branch to keep in sync.
 *
 * ## Why the fill is derived rather than written down
 *
 * A warning card is a *tonal* surface: a tint of the semantic colour, with text that reads on it.
 * The app has no "warning container" token, so the container is mixed from the semantic colour and
 * the surface it sits on. Mixing rather than a hand-picked hex is what keeps the card legible when
 * the appearance changes underneath it — a fixed `#F8E2E2` is unreadable on AMOLED and wrong in
 * seven ways once Monet supplies a different red.
 *
 * @param message the sentence. One line of intent; a paragraph belongs in a dialog.
 * @param level which semantics to paint with. See [WarningLevel].
 * @param color overrides the derived fill, for a caller that has a better tint (a card already
 *   inside another tinted surface, for instance). The ink still follows [level].
 * @param onClick makes the whole card tappable. `null` means the card is a statement, and it is the
 *   default because a card that looks pressable and does nothing is worse than a flat one.
 * @param action the trailing slot, typically one small button. Kept separate from [onClick] so a
 *   card can carry a "Dismiss" or "Undo" without making all of itself a target.
 */
@Composable
fun WarningCard(
    message: String,
    modifier: Modifier = Modifier,
    level: WarningLevel = WarningLevel.Error,
    color: Color? = null,
    onClick: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = DshTheme.colors
    val backdrop = LocalGlassBarBackdrop.current
    Card(
        // The Card paints its own opaque fill internally, so this overlay only ever mattered
        // on the liquid tier; panes stay flat now. Color.Transparent makes the flat branch
        // return this unchanged.
        modifier = modifier.liquidPane(
            backdrop,
            RoundedCornerShape(DshRadius.card),
            flatFill = Color.Transparent,
        ),
        onClick = onClick,
        showIndication = onClick != null,
        // Tilt is a "this is a thing you can do" gesture and reads as an invitation; a sink reads as
        // acknowledgement. A notice is something to acknowledge, a destructive action is something
        // to commit to, so the two levels differ here as well as in colour.
        pressFeedbackType = if (level == WarningLevel.Notice) {
            PressFeedbackType.Sink
        } else {
            PressFeedbackType.Tilt
        },
        colors = CardDefaults.defaultColors(
            color = color ?: level.containerColor(colors),
            contentColor = level.contentColor(colors),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                color = level.contentColor(colors),
                style = DshType.bodyMedium,
                modifier = Modifier.weight(1f, fill = false),
            )
            action?.invoke()
        }
    }
}

/**
 * The card's fill: the semantic colour mixed into the surface it sits on.
 *
 * 12% is the whole measurement. Lower and the card stops separating from the list behind it, so a
 * destructive action reads as an ordinary row; higher and the sentence on top loses contrast against
 * its own background, which is the opposite of what a warning is for.
 */
private fun WarningLevel.containerColor(colors: DshColors): Color {
    val tint = when (this) {
        WarningLevel.Error -> colors.error
        WarningLevel.Notice -> colors.warn
    }
    return tint.copy(alpha = 0.12f).compositeOver(colors.bgLayer1)
}

/** The ink: the same semantic colour at full strength, so it clears its own tinted fill. */
private fun WarningLevel.contentColor(colors: DshColors): Color = when (this) {
    WarningLevel.Error -> colors.error
    WarningLevel.Notice -> colors.warn
}
