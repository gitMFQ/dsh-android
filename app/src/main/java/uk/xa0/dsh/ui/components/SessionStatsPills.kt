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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import uk.xa0.dsh.R
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
 * Output speed, e.g. `123.4 tok/s` — present for the whole conversation, not only while
 * the host happens to be reporting a decode window.
 *
 * ## Why it never disappears
 *
 * The figure is a projection: a new turn's first block can arrive without `decodeMs` and
 * a fresh session has no timing at all, so a pill gated on the figure blinked out and back
 * every time the projection moved — reported from the device as "sometimes shown,
 * sometimes not". A reading that is *always* in the rail is the point of putting it there,
 * so this holds the last figure the host reported and, before the first one, states zero.
 * The rail's width changes with the number; its height does not, which is what keeps the
 * transcript from moving.
 */
@Composable
fun SessionSpeedPill(
    stats: SessionStats?,
    backdrop: GlassBarBackdrop?,
    modifier: Modifier = Modifier,
    /**
     * False until this session has something to measure — the host's reading is the
     * conversation's instrument, and a session that has not been asked anything yet has
     * no conversation to instrument. Defaulted to true so every existing caller keeps
     * the always-present seat; [ComposerAccessoryRail] forwards its own gate here.
     */
    visible: Boolean = true,
    /**
     * What identifies the conversation these readings belong to; the held reading is
     * forgotten when it changes. Null (the default) holds for the life of the
     * composition, which is what a preview or a single-session host wants.
     */
    sessionKey: Any? = null,
) {
    if (!visible) return
    // The last **whole** reading, not just the last figure. The pill's own number was
    // already held across a projection that resets to null; the panel behind it was not,
    // so a reader who opened the statistics while the host was between blocks got an
    // empty popover, or none at all. Holding the reading is what makes the panel the same
    // instrument as the pill: whatever it last said, it can still be read.
    val shown = rememberLastReading(stats, sessionKey)
    // The last measured figure, per composition of this rail. `SideEffect` rather than an
    // assignment in the body so the state write happens after composition settles, the same
    // discipline `rememberUpdatedState` follows.
    var lastSpeed by remember(sessionKey) { mutableStateOf<String?>(null) }
    val reported = stats?.tokensPerSecondText
    SideEffect {
        if (reported != null && reported != lastSpeed) lastSpeed = reported
    }
    val speed = reported ?: lastSpeed ?: "0"
    var open by remember { mutableStateOf(false) }
    // A held reading still opens: that is the point of holding it. Only a session that
    // has never reported timing stays a plain reading.
    val canOpen = shown?.hasTiming == true

    Box(modifier.testTag(STATS_SPEED_PILL_TAG)) {
        GlassPill(
            backdrop = backdrop,
            onClick = if (canOpen) ({ open = !open }) else null,
            pillDescription = stringResource(R.string.stats_speed_pill_description, speed),
        ) {
            Icon(
                imageVector = DshIconVectors.GaugeOutline,
                contentDescription = null,
                tint = DshTheme.colors.labelCaption,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(DshSpacing.sm))
            Text(
                text = stringResource(R.string.stats_tokens_per_second, speed),
                style = DshType.bodyMedium,
                color = DshTheme.colors.labelSecondary,
                maxLines = 1,
            )
        }
        if (open && shown != null) {
            PillPopover(onDismiss = { open = false }) {
                StatPanel(title = stringResource(R.string.stats_session_statistics)) {
                    // A row is absent, not zeroed: an untimed figure is not the same
                    // fact as a figure that measured zero.
                    if (shown.llmMs > 0) {
                        StatRow(stringResource(R.string.stats_llm_time), formatCompactDuration(shown.llmMs))
                    }
                    if (shown.toolMs > 0) {
                        StatRow(stringResource(R.string.stats_tool_time), formatCompactDuration(shown.toolMs))
                    }
                    shown.averageTtftMs?.let {
                        StatRow(stringResource(R.string.stats_avg_ttft), formatCompactDuration(it))
                    }
                    if (shown.decodeMs > 0) {
                        StatRow(
                            label = stringResource(R.string.stats_tps),
                            value = stringResource(R.string.stats_tokens_per_second, speed),
                        )
                    }
                    if (shown.turns > 0) {
                        StatRow(stringResource(R.string.stats_turns), shown.turns.toString())
                    }
                    if (shown.steps > 0) {
                        StatRow(stringResource(R.string.stats_steps), shown.steps.toString())
                    }
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
    /** Same gate as [SessionSpeedPill]: no conversation, no readings. */
    visible: Boolean = true,
    /** Same memory key as [SessionSpeedPill]. */
    sessionKey: Any? = null,
) {
    if (!visible) return
    // The last reading that actually carried billing, held the same way the speed pill
    // holds its own: the panel behind this pill is the session's only view of tokens and
    // its estimated cost, and it emptied every time the host's projection blinked.
    val shown = rememberLastReading(stats, sessionKey) { it.visible && it.hasTokenActivity }
    if (shown == null) return
    val cacheHit = shown.cacheHitPercent ?: return
    var open by remember { mutableStateOf(false) }

    Box(modifier.testTag(STATS_CACHE_PILL_TAG)) {
        GlassPill(
            backdrop = backdrop,
            onClick = { open = !open },
            pillDescription = stringResource(R.string.stats_cache_pill_description, cacheHit),
        ) {
            Icon(
                imageVector = DshIconVectors.DatabaseOutline,
                contentDescription = null,
                tint = DshTheme.colors.labelCaption,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(DshSpacing.sm))
            Text(
                text = stringResource(R.string.stats_cache_percent, cacheHit),
                style = DshType.bodyMedium,
                color = DshTheme.colors.labelSecondary,
                maxLines = 1,
            )
        }
        if (open) {
            PillPopover(onDismiss = { open = false }) {
                StatPanel(
                    title = stringResource(R.string.stats_token_usage),
                    headline = stringResource(
                        R.string.stats_token_count,
                        formatExactTokens(shown.totalTokens),
                    ),
                ) {
                    // The value is a bare percentage: the figure is the same in every
                    // language, and the word that names it is the row's label.
                    StatRow(stringResource(R.string.stats_cache_hit), "$cacheHit%")
                    StatRow(
                        stringResource(R.string.stats_uncached_input),
                        exactCount(shown.uncachedInputTokens),
                    )
                    StatRow(
                        stringResource(R.string.stats_cached_input),
                        exactCount(shown.cacheReadTokens),
                    )
                    // A session that never wrote cache drops the row.
                    if (shown.cacheWriteTokens != 0L) {
                        StatRow(
                            stringResource(R.string.stats_cache_write),
                            exactCount(shown.cacheWriteTokens),
                        )
                    }
                    StatRow(stringResource(R.string.stats_output), exactCount(shown.outputTokens))
                    // The session's own estimate: the rows above are what the host
                    // counted, this one is arithmetic on a published price list that can
                    // change.
                    val priced = rates?.let {
                        costRange(
                            cacheHitTokens = shown.cacheReadTokens,
                            // A cache write is an input token that did not come from
                            // the cache, and the published list has no third input rate.
                            cacheMissTokens = shown.uncachedInputTokens + shown.cacheWriteTokens,
                            outputTokens = shown.outputTokens,
                            rates = it,
                        )
                    }
                    if (priced != null) {
                        StatRow(
                            stringResource(R.string.stats_cost_range),
                            formatCostRange(priced),
                        )
                    }

                    // The family, under the session's own rows and never in place of
                    // them. Only drawn when there is a family.
                    if (family != null && family.members > 0) {
                        StatRow(family.label, exactCount(family.tokens))
                        StatRow(stringResource(R.string.stats_subagents), family.memberCountText)
                        family.cost?.let {
                            StatRow(
                                stringResource(R.string.stats_family_cost),
                                formatCostRange(it),
                            )
                        }
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
 * The last reading a pill can still show, held for the life of this composition.
 *
 * ## Why a whole reading rather than its figure
 *
 * The host's projection is not monotonic: a new turn's first block arrives without
 * `decodeMs`, and a session switch hands the rail `null` for a frame. The speed pill
 * used to hold only its own number, so its *panel* — the same measurement, one tap
 * deeper — went blank exactly when the pill kept its figure, and a reader who opened
 * the statistics between blocks found an empty card. Holding the whole reading makes
 * the panel as durable as the pill it belongs to.
 *
 * [accept] is the pill's own gate (the cache pill wants a billing-bearing reading, the
 * speed pill any timed one); a reading that fails it is not worth remembering.
 */
@Composable
private fun rememberLastReading(
    stats: SessionStats?,
    sessionKey: Any?,
    accept: (SessionStats) -> Boolean = { true },
): SessionStats? {
    // Keyed on the session: the memory is there to survive the *projection* blinking
    // inside one conversation, never to carry a previous conversation's figures into
    // the next one.
    var last by remember(sessionKey) { mutableStateOf<SessionStats?>(null) }
    SideEffect {
        if (stats != null && accept(stats) && stats != last) last = stats
    }
    return if (stats != null && accept(stats)) stats else last
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
@Composable
private fun exactCount(value: Long): String =
    stringResource(R.string.stats_token_count, formatExactTokens(value))

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
            text = stringResource(R.string.stats_balance_action),
            style = DshType.bodySmall,
            color = colors.link,
            modifier = Modifier.weight(1f),
        )
        Text("›", style = DshType.bodySmall, color = colors.link)
    }
}