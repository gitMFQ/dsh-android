package uk.xa0.dsh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import uk.xa0.dsh.model.FamilyUsage
import uk.xa0.dsh.model.PRICE_SOURCE
import uk.xa0.dsh.model.SessionStats
import uk.xa0.dsh.model.TokenRates
import uk.xa0.dsh.model.costRange
import uk.xa0.dsh.model.formatCompactDuration
import uk.xa0.dsh.model.formatCostRange
import uk.xa0.dsh.model.formatExactTokens
import uk.xa0.dsh.ui.clickableNoRipple
import uk.xa0.dsh.ui.glass.GlassBarBackdrop
import uk.xa0.dsh.ui.theme.DshRadius
import uk.xa0.dsh.ui.theme.DshSpacing
import uk.xa0.dsh.ui.theme.DshTheme
import uk.xa0.dsh.ui.theme.DshType

/** Test addresses for the two readings in the rail. */
const val STATS_SPEED_PILL_TAG = "stats-pill-speed"
const val STATS_CACHE_PILL_TAG = "stats-pill-cache"

/**
 * The session's two readings, as rail pills: output speed and cache-hit share.
 *
 * ## Why only one figure each
 *
 * The old row drew a whole sentence per chip — `25 turns 416 steps · 123.4 tok/s` and
 * `16.3K tok · Cache 48%` — because it was the composer card's footer and had room
 * for sentences. The control rail is a row of one-idea pills: the two readings a
 * reader glances at are the speed and the cache share, and the full figures — turns,
 * steps, LLM/tool time, TTFT, the token breakdown, the priced estimate, the subagent
 * family, the balance door — are one tap away in the same panels as before.
 *
 * ## Why pills rather than chips
 *
 * They sit in [ComposerAccessoryRail], the same liquid-glass vocabulary as the
 * composer's other controls. A transparent chip among glass pills read as a disabled
 * control; both readings now wear [GlassPill].
 *
 * ## Independence
 *
 * Each pill gates on its own reading, so a host block that carries `tokenUsage` and no
 * `sessionStats` (a real shape — the connected host serves it) draws the cache pill
 * alone, and a session with closed steps but no billing draws the speed pill alone.
 * Neither loses its position to the other: the rail is a scroll, not two anchored ends.
 */

/**
 * Output speed, e.g. `123.4 tok/s`.
 *
 * Gated on `decodeMs > 0`, which is what makes the figure exist at all: an untimed
 * session has no speed to show, and an empty pill claiming `0 tok/s` would be a
 * measurement the host never took.
 */
@Composable
fun SessionSpeedPill(
    stats: SessionStats?,
    backdrop: GlassBarBackdrop?,
    modifier: Modifier = Modifier,
) {
    if (stats == null || !stats.visible) return
    val speed = stats.tokensPerSecondText ?: return
    var open by remember { mutableStateOf(false) }

    Box(modifier.testTag(STATS_SPEED_PILL_TAG)) {
        GlassPill(
            backdrop = backdrop,
            onClick = if (stats.hasTiming) ({ open = !open }) else null,
            pillDescription = "Output speed, $speed tokens per second",
        ) {
            Icon(
                imageVector = Icons.Rounded.Speed,
                contentDescription = null,
                tint = DshTheme.colors.labelCaption,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(DshSpacing.sm))
            Text(
                text = "$speed tok/s",
                style = DshType.bodyMedium,
                color = DshTheme.colors.labelSecondary,
                maxLines = 1,
            )
        }
        if (open) {
            PillPopover(onDismiss = { open = false }) {
                StatPanel(title = "Session statistics") {
                    // A row is absent, not zeroed: an untimed figure is not the same
                    // fact as a figure that measured zero.
                    if (stats.llmMs > 0) StatRow("LLM time", formatCompactDuration(stats.llmMs))
                    if (stats.toolMs > 0) StatRow("Tool time", formatCompactDuration(stats.toolMs))
                    stats.averageTtftMs?.let {
                        StatRow("Avg time to first token (TTFT)", formatCompactDuration(it))
                    }
                    if (stats.decodeMs > 0) {
                        StatRow("Tokens per second (TPS)", "$speed tok/s")
                    }
                    if (stats.turns > 0) StatRow("Turns", stats.turns.toString())
                    if (stats.steps > 0) StatRow("Steps", stats.steps.toString())
                }
            }
        }
    }
}

/**
 * Whole-log cache-hit share, e.g. `Cache 99%`.
 *
 * The full token panel stays behind the tap: billed/uncached/cached/write/output
 * rows, the session's own priced estimate, the subagent family when there is one, and
 * the door to the account balance.
 */
@Composable
fun SessionCachePill(
    stats: SessionStats?,
    backdrop: GlassBarBackdrop?,
    rates: TokenRates? = null,
    family: FamilyUsage? = null,
    onBalance: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (stats == null || !stats.visible || !stats.hasTokenActivity) return
    val cacheHit = stats.cacheHitPercent ?: return
    var open by remember { mutableStateOf(false) }

    Box(modifier.testTag(STATS_CACHE_PILL_TAG)) {
        GlassPill(
            backdrop = backdrop,
            onClick = { open = !open },
            pillDescription = "Token usage, $cacheHit percent served from cache",
        ) {
            Icon(
                imageVector = Icons.Rounded.Storage,
                contentDescription = null,
                tint = DshTheme.colors.labelCaption,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(DshSpacing.sm))
            Text(
                text = "Cache $cacheHit%",
                style = DshType.bodyMedium,
                color = DshTheme.colors.labelSecondary,
                maxLines = 1,
            )
        }
        if (open) {
            PillPopover(onDismiss = { open = false }) {
                StatPanel(
                    title = "Token usage",
                    headline = "${formatExactTokens(stats.totalTokens)} tok",
                ) {
                    StatRow("Cache hit", "$cacheHit%")
                    StatRow("Uncached input", exactCount(stats.uncachedInputTokens))
                    StatRow("Cached input", exactCount(stats.cacheReadTokens))
                    // A session that never wrote cache drops the row.
                    if (stats.cacheWriteTokens != 0L) {
                        StatRow("Cache write", exactCount(stats.cacheWriteTokens))
                    }
                    StatRow("Output", exactCount(stats.outputTokens))
                    // The session's own estimate: the rows above are what the host
                    // counted, this one is arithmetic on a published price list that can
                    // change.
                    val priced = rates?.let {
                        costRange(
                            cacheHitTokens = stats.cacheReadTokens,
                            // A cache write is an input token that did not come from
                            // the cache, and the published list has no third input rate.
                            cacheMissTokens = stats.uncachedInputTokens + stats.cacheWriteTokens,
                            outputTokens = stats.outputTokens,
                            rates = it,
                        )
                    }
                    if (priced != null) StatRow("Cost (off-peak – peak)", formatCostRange(priced))

                    // The family, under the session's own rows and never in place of
                    // them. Only drawn when there is a family.
                    if (family != null && family.members > 0) {
                        StatRow(family.label, exactCount(family.tokens))
                        StatRow("Subagents", family.memberCountText)
                        family.cost?.let { StatRow("Family cost (off-peak – peak)", formatCostRange(it)) }
                        family.notes.forEach { StatNote(it) }
                    }

                    if (priced != null) StatNote(PRICE_SOURCE)
                    // The way up from a session's cost to the account's balance.
                    BalanceAction(onClick = onBalance)
                }
            }
        }
    }
}

/**
 * The popover a reading opens: anchored above its pill, clamped to the window,
 * dismissed by an outside tap. The same shape the old chips used.
 */
@Composable
private fun PillPopover(onDismiss: () -> Unit, panel: @Composable () -> Unit) {
    Popup(
        onDismissRequest = onDismiss,
        popupPositionProvider = remember {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize,
                ): IntOffset = IntOffset(
                    // Centred on the pill and sitting just above it, clamped so the
                    // panel keeps a viewport margin at either edge.
                    x = (anchorBounds.center.x - popupContentSize.width / 2)
                        .coerceIn(8, (windowSize.width - popupContentSize.width - 8).coerceAtLeast(8)),
                    y = (anchorBounds.top - popupContentSize.height - 8).coerceAtLeast(8),
                )
            }
        },
    ) { panel() }
}

/** Web `message.turnUsage.count`: `{count} tok`. */
private fun exactCount(value: Long): String = "${formatExactTokens(value)} tok"

/**
 * The shared stat-panel skin (`stat-dialog.module.css`): menu surface, 12dp
 * radius, prominent shadow, a heading with an optional right-hand headline value, a
 * hairline rule, then the label/value rows.
 */
@Composable
private fun StatPanel(
    title: String,
    headline: String? = null,
    rows: @Composable () -> Unit,
) {
    val colors = DshTheme.colors
    val shape = RoundedCornerShape(DshRadius.card)
    Column(
        Modifier
            .widthIn(min = 232.dp, max = 320.dp)
            .shadow(6.dp, shape)
            .clip(shape)
            .background(colors.menu)
            .border(0.5.dp, colors.borderL1, shape)
            .padding(DshSpacing.xl),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = DshType.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = colors.labelPrimary,
                modifier = Modifier.weight(1f),
            )
            if (headline != null) {
                Text(
                    text = headline,
                    style = DshType.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = colors.labelPrimary,
                    maxLines = 1,
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = DshSpacing.md, bottom = 10.dp)
                .height(0.5.dp)
                .background(colors.borderL2),
        )
        rows()
    }
}

/** One `dt`/`dd` pair: tertiary label left, tabular value right. */
@Composable
private fun StatRow(label: String, value: String) {
    val colors = DshTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = DshType.bodySmall,
            color = colors.labelTertiary,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(DshSpacing.xl))
        Text(
            text = value,
            style = DshType.bodySmall,
            color = colors.labelSecondary,
            maxLines = 1,
        )
    }
}

/** A footnote under the rows: where a figure came from, or what it is not. */
@Composable
private fun StatNote(text: String) {
    Text(
        text = text,
        style = DshType.bodySmall,
        color = DshTheme.colors.labelTertiary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = DshSpacing.xs),
    )
}

/** `Balance & top up` as a panel row. */
@Composable
private fun BalanceAction(onClick: () -> Unit) {
    val colors = DshTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DshRadius.sm))
            .clickableNoRipple(onClick = onClick)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Balance & top up",
            style = DshType.bodySmall,
            color = colors.link,
            modifier = Modifier.weight(1f),
        )
        Text("›", style = DshType.bodySmall, color = colors.link)
    }
}