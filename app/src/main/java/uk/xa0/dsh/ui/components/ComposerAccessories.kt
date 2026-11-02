package uk.xa0.dsh.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Security
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import kotlinx.coroutines.delay
import uk.xa0.dsh.ContextBreakdown
import uk.xa0.dsh.model.FamilyUsage
import uk.xa0.dsh.model.SessionStats
import uk.xa0.dsh.model.TokenRates
import uk.xa0.dsh.ui.ModelTriggerState
import uk.xa0.dsh.ui.clickableNoRipple
import uk.xa0.dsh.ui.glass.GlassBarBackdrop
import uk.xa0.dsh.ui.glass.glassLift
import uk.xa0.dsh.ui.glass.liquidPane
import uk.xa0.dsh.ui.theme.DshRadius
import uk.xa0.dsh.ui.theme.DshSpacing
import uk.xa0.dsh.ui.theme.DshTheme
import uk.xa0.dsh.ui.theme.DshType
import java.util.Locale

/**
 * The composer's controls, out of the input and above the bottom bar — one pill each.
 *
 * ## Why they left the card
 *
 * The card used to carry the controls beside the text: attach, access, plan, the model
 * trigger and the context ring, all on the same row as send. That row could only be kept
 * from overflowing by weighting the model chip and squeezing its label. Pulling them out
 * into their own row above the dock gives send the whole trailing edge of the bar and
 * leaves the input the `+` and the text.
 *
 * ## Why so few words
 *
 * The rail is read at a glance, not studied. The access mode and the model are
 * **icon-only** — the shield and the brain are the two controls whose identity does not
 * depend on the current value, and the full sentence (which mode, which route) is the
 * sheet they open. The context gauge keeps its ring and its number because that *is* its
 * reading. The two session readings are one figure each: output speed and cache share.
 *
 * Each pill is its own sheet of the same liquid material as the bar and the input
 * ([liquidPane]), not a tinted rectangle: on a phone the row floats over the transcript,
 * so the glass has something to refract.
 */
@Composable
fun ComposerAccessoryRail(
    backdrop: GlassBarBackdrop?,
    permissionLabel: String?,
    onPermissionClick: () -> Unit,
    planActive: Boolean,
    onExitPlan: () -> Unit,
    modelTrigger: ModelTriggerState?,
    onModelClick: () -> Unit,
    contextPercent: Int,
    contextTokens: Int,
    contextWindow: Int,
    contextBreakdown: ContextBreakdown?,
    stats: SessionStats?,
    rates: TokenRates?,
    family: FamilyUsage?,
    onBalance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            // Scrolls rather than squeezes: the row grows with the readings (speed and
            // cache appear mid-turn), and a translated label must move the row rather
            // than shrink a target below a thumb.
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = DshSpacing.xl),
        horizontalArrangement = Arrangement.spacedBy(DshSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (permissionLabel != null) {
            GlassPill(
                backdrop = backdrop,
                onClick = onPermissionClick,
                horizontalPadding = 8.dp,
                pillDescription = "Change access mode, current $permissionLabel",
            ) {
                Icon(
                    Icons.Rounded.Security,
                    contentDescription = null,
                    tint = DshTheme.colors.labelCaption,
                    modifier = Modifier.size(15.dp),
                )
            }
        }

        if (planActive) {
            GlassPill(
                backdrop = backdrop,
                onClick = onExitPlan,
                tint = DshTheme.colors.accentTertiary.copy(alpha = 0.55f),
                pillDescription = "Leave plan mode",
            ) {
                Text(
                    text = "Plan",
                    style = DshType.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = DshTheme.colors.accent,
                )
                Spacer(Modifier.width(DshSpacing.sm))
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = null,
                    tint = DshTheme.colors.accent,
                    modifier = Modifier.size(12.dp),
                )
            }
        }

        if (modelTrigger != null) {
            // The brain is the model's identity, not its current value: the route's name
            // and its "next turn" / "unavailable" state are the sheet's job, and an
            // icon-only pill keeps the rail to one screen. A route that cannot run tints
            // the glyph as a fault rather than growing words.
            GlassPill(
                backdrop = backdrop,
                onClick = onModelClick,
                horizontalPadding = 8.dp,
                pillDescription = modelTrigger.description,
            ) {
                Icon(
                    Icons.Rounded.Psychology,
                    contentDescription = null,
                    tint = if (modelTrigger.alert) DshTheme.colors.error else DshTheme.colors.labelSecondary,
                    modifier = Modifier.size(15.dp),
                )
            }
        }

        ContextPill(
            backdrop = backdrop,
            percent = contextPercent,
            usedTokens = contextTokens,
            windowTokens = contextWindow,
            breakdown = contextBreakdown,
        )

        SessionSpeedPill(stats = stats, backdrop = backdrop)
        SessionCachePill(
            stats = stats,
            backdrop = backdrop,
            rates = rates,
            family = family,
            onBalance = onBalance,
        )
    }
}

/** The pill's height: a thumb-sized target that still reads as a chip, not a button. */
private val PillHeight = 32.dp

/**
 * The one glass chip shape every control in the rail wears.
 *
 * 16dp radius over a 32dp height — a capsule. Built once here so the add, access, plan,
 * model and context treatments cannot drift into five slightly different pills.
 */
@Composable
internal fun GlassPill(
    backdrop: GlassBarBackdrop?,
    /** Null keeps the pill a reading rather than a button, as the old static chip did. */
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    pillDescription: String? = null,
    horizontalPadding: Dp = 10.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(DshRadius.thumb)
    Row(
        modifier
            .height(PillHeight)
            // The lift first, as the bar does: a shadow is drawn outside the pane, and on a
            // light appearance `glassLift` declines to draw one at all.
            .glassLift(shape)
            .liquidPane(backdrop, shape, tint)
            .then(
                if (pillDescription != null) {
                    Modifier.semantics { contentDescription = pillDescription }
                } else {
                    Modifier
                },
            )
            .then(
                if (onClick != null) Modifier.clickableNoRipple(onClick = onClick) else Modifier,
            )
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * Context-window gauge as a pill: the ring plus the percentage, and the same ephemeral
 * popover the old in-card ring opened.
 *
 * The popover is the web meter's shape — a small anchored card, no scrim, dismissed by an
 * outside tap — and it closes itself after a glance rather than leaving something to
 * dismiss.
 */
@Composable
private fun ContextPill(
    backdrop: GlassBarBackdrop?,
    percent: Int,
    usedTokens: Int,
    windowTokens: Int,
    breakdown: ContextBreakdown?,
) {
    val colors = DshTheme.colors
    val tint = when {
        percent >= 90 -> colors.error
        percent >= 75 -> colors.warn
        else -> colors.accent
    }
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        if (open) {
            delay(4000)
            open = false
        }
    }

    Box {
        GlassPill(
            backdrop = backdrop,
            onClick = { open = !open },
            pillDescription = "Context window, $percent percent used",
        ) {
            Canvas(Modifier.size(18.dp)) {
                val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                val inset = stroke.width / 2f
                val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
                drawArc(
                    color = colors.borderL3,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = stroke,
                )
                drawArc(
                    color = tint,
                    startAngle = -90f,
                    sweepAngle = 360f * (percent.coerceIn(0, 100) / 100f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = stroke,
                )
            }
            Spacer(Modifier.width(DshSpacing.sm))
            Text(
                text = "$percent%",
                style = DshType.micro.copy(fontWeight = FontWeight.Medium),
                color = colors.labelSecondary,
                maxLines = 1,
            )
        }

        if (open) {
            Popup(
                onDismissRequest = { open = false },
                popupPositionProvider = remember {
                    object : PopupPositionProvider {
                        override fun calculatePosition(
                            anchorBounds: IntRect,
                            windowSize: IntSize,
                            layoutDirection: LayoutDirection,
                            popupContentSize: IntSize,
                        ): IntOffset = IntOffset(
                            // Right-aligned to the pill and sitting just above it, clamped
                            // so it never leaves the window.
                            x = (anchorBounds.right - popupContentSize.width)
                                .coerceIn(8, (windowSize.width - popupContentSize.width - 8).coerceAtLeast(8)),
                            y = (anchorBounds.top - popupContentSize.height - 8).coerceAtLeast(8),
                        )
                    }
                },
            ) {
                ContextCard(
                    percent = percent,
                    usedTokens = usedTokens,
                    windowTokens = windowTokens,
                    breakdown = breakdown,
                )
            }
        }
    }
}

@Composable
private fun ContextCard(
    percent: Int,
    usedTokens: Int,
    windowTokens: Int,
    breakdown: ContextBreakdown?,
) {
    val colors = DshTheme.colors
    val shape = RoundedCornerShape(DshRadius.card)
    Column(
        Modifier
            .width(220.dp)
            .shadow(6.dp, shape)
            .clip(shape)
            .background(colors.menu)
            .border(0.5.dp, colors.borderL1, shape)
            .padding(DshSpacing.lg),
    ) {
        Text(
            text = if (windowTokens > 0) {
                "$percent% · ${compactTokens(usedTokens)} / ${compactTokens(windowTokens)}"
            } else {
                "No context window reported"
            },
            style = DshType.bodySmall,
            color = colors.labelPrimary,
        )
        if (breakdown != null && windowTokens > 0) {
            Spacer(Modifier.height(DshSpacing.md))
            MeterRow("System", breakdown.system, windowTokens, colors.accent)
            MeterRow("Tools", breakdown.tools, windowTokens, colors.synFunction)
            MeterRow("Messages", breakdown.messages, windowTokens, colors.success)
        }
    }
}

@Composable
private fun MeterRow(label: String, tokens: Int, window: Int, tint: Color) {
    val colors = DshTheme.colors
    val fraction = (tokens.toFloat() / window).coerceIn(0f, 1f)
    Column(Modifier.padding(vertical = 3.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = DshType.micro, color = colors.labelSecondary, modifier = Modifier.weight(1f))
            Text(compactTokens(tokens), style = DshType.micro, color = colors.labelTertiary)
        }
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.borderL1),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(tint),
            )
        }
    }
}

private fun compactTokens(tokens: Int): String = when {
    tokens >= 1_000_000 -> String.format(Locale.US, "%.1fM", tokens / 1_000_000.0)
    tokens >= 1_000 -> String.format(Locale.US, "%.0fk", tokens / 1_000.0)
    else -> tokens.toString()
}