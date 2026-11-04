package uk.xa0.dsh.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import uk.xa0.dsh.GoalState
import uk.xa0.dsh.R
import uk.xa0.dsh.model.ApprovalAnswer
import uk.xa0.dsh.model.ApprovalCopy
import uk.xa0.dsh.model.ChatEntry
import uk.xa0.dsh.model.DisplayRow
import uk.xa0.dsh.model.JobObservation
import uk.xa0.dsh.model.LiveAttempt
import uk.xa0.dsh.model.MessageAttachment
import uk.xa0.dsh.model.NoticeSeverity
import uk.xa0.dsh.model.TodoItem
import uk.xa0.dsh.model.ToolCallNode
import uk.xa0.dsh.model.backgroundJobIdOf
import uk.xa0.dsh.model.shellCommandOf
import uk.xa0.dsh.ui.clickableNoRipple
import uk.xa0.dsh.ui.shortenToolPath
import uk.xa0.dsh.ui.markdown.MarkdownText
import uk.xa0.dsh.ui.glass.GlassBarBackdrop
import uk.xa0.dsh.ui.glass.glassLift
import uk.xa0.dsh.ui.glass.liquidPane
import uk.xa0.dsh.ui.theme.DshRadius
import uk.xa0.dsh.ui.theme.DshSpacing
import uk.xa0.dsh.ui.theme.DshTheme
import uk.xa0.dsh.ui.theme.DshType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * User turns are right-aligned capsules in `--dsw-specific-bubble`; plugin-injected
 * notices (background-job results, hook output) reuse the row but read as system
 * text rather than something the human typed.
 */
@Composable
fun UserMessageRow(
    entry: ChatEntry.UserMessage,
    /**
     * Resolves an image attachment to a drawable. Passed in rather than wrapped in
     * a CompositionLocal so the row keeps no dependency on the RPC client.
     */
    loadImage: suspend (String) -> ImageBitmap? = { null },
) {
    val colors = DshTheme.colors
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        Box(
            Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(DshRadius.bubble))
                .background(if (entry.fromPlugin) colors.tip else colors.bubble)
                // Sent-but-unclaimed: the host has not echoed it into the journal
                // yet, so it reads as in-flight rather than as a settled turn.
                .alpha(if (entry.pending) 0.7f else 1f)
                .padding(horizontal = DshSpacing.xl, vertical = 10.dp),
        ) {
            Column {
                // Bound to a local first: `entry.summary` is a public API property
                // from :core:model, and Kotlin will not smart-cast across a module
                // boundary. Same condition, same value.
                val pluginSummary = entry.summary
                if (entry.fromPlugin && pluginSummary != null) {
                    Text(
                        text = pluginSummary,
                        style = DshType.micro,
                        color = colors.labelTertiary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(DshSpacing.xs))
                }
                // Web order is attachments *then* text: the picture is the first
                // thing you see, and the caption reads under it.
                if (entry.attachments.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(DshSpacing.sm)) {
                        entry.attachments.forEach { attachment ->
                            MessageAttachmentBlock(attachment, loadImage)
                        }
                    }
                    if (entry.text.isNotBlank()) Spacer(Modifier.height(DshSpacing.md))
                }
                if (entry.text.isNotBlank()) {
                    Text(
                        text = entry.text,
                        style = DshType.userBubble,
                        color = if (entry.fromPlugin) colors.labelSecondary else colors.labelPrimary,
                    )
                }
                if (entry.pending) {
                    if (entry.text.isNotBlank() || entry.attachments.isNotEmpty()) {
                        Spacer(Modifier.height(DshSpacing.sm))
                    }
                    Text(
                        text = stringResource(R.string.chat_sending),
                        style = DshType.micro,
                        color = colors.labelTertiary,
                    )
                }
            }
        }
        // A pending row has no host timestamp yet; its actions would be pointless.
        val stamp = if (entry.pending) "" else formatClock(entry.time)
        if (stamp.isNotEmpty()) {
            Spacer(Modifier.height(DshSpacing.xs))
            // The turn rail runs down the right edge, so the action row is pulled in
            // clear of it. Reserving the same space in the transcript's padding
            // instead would make the content column asymmetric against the composer.
            MessageActions(
                text = entry.text,
                time = entry.time,
                clockFirst = true,
                modifier = Modifier.padding(end = 34.dp),
            )
        }
    }
}

/**
 * One attachment on a user message.
 *
 * An `image` block draws the picture itself — that is the whole point of
 * attaching one, and a filename chip is not "seeing" it. A `file` block is a chip:
 * the host keeps the two kinds apart (only images are readable back as bytes), so
 * a screenshot sent as a file is a file.
 */
@Composable
private fun MessageAttachmentBlock(
    attachment: MessageAttachment,
    loadImage: suspend (String) -> ImageBitmap?,
) {
    if (attachment.kind != "image") {
        MessageAttachmentChip(attachment)
        return
    }

    val colors = DshTheme.colors
    val local = attachment.localData
    // The row rebuilds under the same empty id when a message is edited, so the
    // payload is a key as well: same id, different bytes must not reuse a decode.
    val state by produceState<AttachmentImage>(
        initialValue = AttachmentImage.Loading,
        key1 = attachment.attachmentId,
        key2 = local,
    ) {
        val staged = local
        value = when {
            // A staged picture is already on this device. Decoding a full photo is
            // CPU work on the critical path of the send animation, so it runs off
            // the frame's dispatcher; nothing is cached, since the composition owns
            // the only reference it needs.
            !staged.isNullOrEmpty() ->
                withContext(Dispatchers.Default) { decodeStagedImage(staged) }
                    ?.let { AttachmentImage.Loaded(it) }
                    ?: AttachmentImage.Failed

            attachment.attachmentId.isNotEmpty() ->
                loadImage(attachment.attachmentId)
                    ?.let { AttachmentImage.Loaded(it) }
                    ?: AttachmentImage.Failed

            // Neither the bytes nor an address to read them from: this block can
            // never resolve, and the old icon made that look like loading forever.
            else -> AttachmentImage.Failed
        }
    }

    val shape = RoundedCornerShape(DshRadius.md)
    Box(
        Modifier
            .widthIn(max = 220.dp)
            .heightIn(max = 220.dp)
            .clip(shape)
            .background(colors.bgBase)
            .border(0.5.dp, colors.borderL1, shape)
            // The placeholder is a deliberately sized block rather than an icon, so
            // the bubble already has its final shape; animating the size carries it
            // the rest of the way when a photo of a different shape lands.
            .animateContentSize(tween(durationMillis = 180)),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(
            targetState = state,
            animationSpec = tween(durationMillis = 160),
            label = "attachment-image",
        ) { current ->
            when (current) {
                is AttachmentImage.Loaded -> Image(
                    bitmap = current.bitmap,
                    contentDescription = attachment.name,
                    contentScale = ContentScale.Fit,
                    // Matching the picture's own ratio keeps a 220dp width cap
                    // without letterboxing a portrait into a landscape slot.
                    modifier = Modifier
                        .widthIn(max = 220.dp)
                        .aspectRatio(aspectRatioOf(current.bitmap)),
                )

                AttachmentImage.Failed -> AttachmentLoadFailed()
                AttachmentImage.Loading -> AttachmentSkeleton(attachment.name)
            }
        }
    }
}

/** What an image block is showing: bytes still arriving, the picture, or a dead end. */
private sealed interface AttachmentImage {
    data object Loading : AttachmentImage
    data class Loaded(val bitmap: ImageBitmap) : AttachmentImage
    data object Failed : AttachmentImage
}

/**
 * The loading fill: the same 180×120 footprint as the failure state, breathing
 * slowly between two surface tones. It is quiet on purpose — a placeholder that
 * moves enough to notice competes with the text it sits above.
 */
@Composable
private fun AttachmentSkeleton(name: String) {
    val colors = DshTheme.colors
    val transition = rememberInfiniteTransition(label = "attachment-skeleton")
    val breath by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )
    Box(
        Modifier
            .size(width = 180.dp, height = 120.dp)
            .background(colors.bgLayer2.copy(alpha = breath)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Image,
            contentDescription = stringResource(R.string.chat_attachment_loading, name),
            tint = colors.labelTertiary,
            modifier = Modifier
                .size(20.dp)
                .alpha(0.6f),
        )
    }
}

/**
 * The failure state, deliberately the same footprint as the skeleton so nothing
 * reflows, but static and captioned: a decode that failed must not keep wearing
 * the loading costume.
 */
@Composable
private fun AttachmentLoadFailed() {
    val colors = DshTheme.colors
    Column(
        Modifier.size(width = 180.dp, height = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.BrokenImage,
            contentDescription = null,
            tint = colors.labelCaption,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.height(DshSpacing.sm))
        Text(
            text = stringResource(R.string.chat_attachment_load_failed),
            style = DshType.micro,
            color = colors.labelTertiary,
        )
    }
}

/** The picture's own width/height, with a square fallback for a degenerate decode. */
private fun aspectRatioOf(bitmap: ImageBitmap): Float =
    if (bitmap.height > 0) bitmap.width.toFloat() / bitmap.height else 1f

/**
 * Decodes the inline base64 a staged attachment carries. Null covers both a
 * malformed payload and bytes `BitmapFactory` refuses, which the caller renders
 * identically.
 */
private fun decodeStagedImage(encoded: String): ImageBitmap? = runCatching {
    val bytes = Base64.decode(encoded, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}.getOrNull()

@Composable
private fun MessageAttachmentChip(attachment: MessageAttachment) {
    val colors = DshTheme.colors
    val shape = RoundedCornerShape(DshRadius.md)
    Row(
        Modifier
            .clip(shape)
            .background(colors.bgBase)
            .border(0.5.dp, colors.borderL1, shape)
            .padding(horizontal = DshSpacing.md, vertical = DshSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (attachment.kind == "image") Icons.Rounded.Image else DshIconVectors.BrowseOutline,
            contentDescription = null,
            tint = colors.labelTertiary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(DshSpacing.sm))
        Text(
            text = attachment.name,
            style = DshType.bodyMedium,
            color = colors.labelPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 176.dp),
        )
        Spacer(Modifier.width(DshSpacing.md))
        Text(
            text = fileSizeText(attachment.bytes),
            style = DshType.micro,
            color = colors.labelTertiary,
        )
    }
}

/**
 * The message action row. The web component's render order is
 * `[clock] [copy] [extras] [branch] [usage] [clock]`, with the clock appearing
 * before copy for user/steering rows and at the end for the assistant turn tail.
 *
 * Copy swaps to a check for 1000 ms with no toast, and repeat clicks inside that
 * window are ignored — both as in the web client.
 *
 * [onBranch] fills the web's `[branch]` slot, and is null on every row the host
 * cannot cut a fork at — the web's `onBranch === undefined`, which draws no button
 * rather than a dead one. Its label is the web's own `message.branch` string, so
 * the screen reader says what the action does instead of naming an icon.
 */
@Composable
fun MessageActions(
    text: String,
    time: Long,
    modifier: Modifier = Modifier,
    clockFirst: Boolean = false,
    durationMs: Long? = null,
    onBranch: (() -> Unit)? = null,
) {
    val colors = DshTheme.colors
    val clipboard = LocalClipboardManager.current
    var copied by remember(text) { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1000)
            copied = false
        }
    }

    val stamp = formatClock(time)

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (clockFirst && stamp.isNotEmpty()) {
            Text(stamp, style = DshType.micro, color = colors.labelTertiary)
            Spacer(Modifier.width(DshSpacing.xs))
        }

        if (durationMs != null && durationMs > 0) {
            Icon(
                DshIconVectors.AlarmClockOutline,
                contentDescription = null,
                tint = colors.labelCaption,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(DshSpacing.xs))
            Text(
                stringResource(R.string.chat_ran_for, formatDuration(durationMs)),
                style = DshType.micro,
                color = colors.labelTertiary,
            )
            Spacer(Modifier.width(DshSpacing.md))
        }

        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickableNoRipple {
                    if (copied) return@clickableNoRipple
                    clipboard.setText(AnnotatedString(text))
                    copied = true
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (copied) DshIconVectors.CheckOutline else DshIconVectors.CopyOutline,
                contentDescription = stringResource(
                    if (copied) R.string.chat_copied else R.string.chat_copy,
                ),
                tint = if (copied) colors.success else colors.labelTertiary,
                modifier = Modifier.size(14.dp),
            )
        }

        if (onBranch != null) {
            // Same 28dp target as Copy, and immediately after it: the web's action
            // row is copy → branch → clock, and an affordance that moves between
            // turns is one the reader has to hunt for each time.
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickableNoRipple(onClick = onBranch),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = DshIconBranch16,
                    contentDescription = stringResource(R.string.chat_branch),
                    tint = colors.labelTertiary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        if (!clockFirst && stamp.isNotEmpty()) {
            Spacer(Modifier.width(DshSpacing.xs))
            Text(stamp, style = DshType.micro, color = colors.labelTertiary)
        }
    }
}

private val clockHm = SimpleDateFormat("HH:mm", Locale.getDefault())
private val clockMd = SimpleDateFormat("M/d HH:mm", Locale.getDefault())
private val clockYmd = SimpleDateFormat("yyyy-M-d HH:mm", Locale.getDefault())

/**
 * `formatMessageClock`: `HH:mm` for today, `M/D HH:mm` earlier this year, and
 * `Y-M-D HH:mm` beyond it.
 */
internal fun formatClock(time: Long): String {
    if (time <= 0L) return ""
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = time }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    return when {
        sameDay -> clockHm.format(Date(time))
        now.get(Calendar.YEAR) == then.get(Calendar.YEAR) -> clockMd.format(Date(time))
        else -> clockYmd.format(Date(time))
    }
}

private fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total / 60) % 60
    val seconds = total % 60
    // Matches the host's `formatRunDuration`: minutes and seconds are zero-padded
    // whenever a larger unit is present ("2m 09s"), bare seconds are not.
    return when {
        hours > 0 -> "${hours}h ${pad2(minutes)}m ${pad2(seconds)}s"
        minutes > 0 -> "${minutes}m ${pad2(seconds)}s"
        else -> "${seconds}s"
    }
}

private fun pad2(value: Long): String = if (value < 10) "0$value" else value.toString()

/**
 * The running-turn status line: **"Deep diving..."** in a brand-blue text shimmer,
 * with a clock that only appears once the turn has clearly been running a while.
 *
 * Mirrors `ChatView.tsx#TurnStatus`: the row is turn-scoped (anchored to
 * `turn/start`) so it rides the whole turn — first-token wait, tool execution and
 * streaming alike — and never flickers per step. There is no streaming caret in
 * the web UI; this row is the live signal.
 */
@Composable
fun TurnStatusRow(elapsedMs: Long?, modifier: Modifier = Modifier) {
    val colors = DshTheme.colors
    val transition = rememberInfiniteTransition(label = "turn-status")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep",
    )

    // The web paints this as `background-size: 250% 100%` with the position sliding
    // from 100% to 0, so the bright segment covers 40-60% of a gradient 2.5x the
    // text width. That makes the wash wide and gentle; a narrow band with a strong
    // colour step (the first attempt) reads as a streak instead.
    var textWidth by remember { mutableStateOf(0f) }
    val measured = if (textWidth > 0f) textWidth else 320f
    val span = measured * 2.5f
    val origin = -(span - measured) * (1f - sweep)
    val shimmer = Brush.linearGradient(
        colorStops = arrayOf(
            0f to colors.accentDeep,
            0.4f to colors.accentDeep,
            0.5f to colors.accentLight,
            0.6f to colors.accentDeep,
            1f to colors.accentDeep,
        ),
        start = Offset(origin, 0f),
        end = Offset(origin + span, 0f),
    )

    Row(
        modifier.height(26.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.chat_turn_status_deep_diving),
            style = DshType.messageBody.copy(fontWeight = FontWeight.SemiBold, brush = shimmer),
            maxLines = 1,
            modifier = Modifier.onSizeChanged { textWidth = it.width.toFloat() },
        )
        if (elapsedMs != null && elapsedMs >= 15_000) {
            Spacer(Modifier.width(DshSpacing.md))
            Text(
                text = formatDuration(elapsedMs),
                style = DshType.bodyMedium.copy(
                    fontWeight = FontWeight.Normal,
                    fontFeatureSettings = "tnum",
                ),
                color = colors.labelCaption,
                maxLines = 1,
            )
        }
    }
}

/**
 * Assistant turns are plain full-width markdown — no bubble, no avatar. Reasoning
 * is folded into a collapsed disclosure above the answer, exactly as the web UI does.
 *
 * [showFooter] marks the row that closes a completed turn: in the web UI the
 * assistant action row hangs off a separate `turn-tail` node and is omitted
 * entirely when a turn has no closing text answer.
 *
 * [onBranch] rides that same row, because it is the row the web's `turn-tail` puts
 * its branch button on. It is null when the reducer could not name the fork
 * boundary for this turn, which is the mobile form of the web's
 * `branchUnavailable`.
 */
@Composable
fun AssistantMessageRow(
    entry: ChatEntry.AssistantMessage,
    showFooter: Boolean = false,
    durationMs: Long? = null,
    onBranch: (() -> Unit)? = null,
    /** Translates this turn’s reasoning when the reader opens it; see [ReasoningRow]. */
    translate: ((String, (String?) -> Unit) -> Unit)? = null,
    /**
     * Told when the reader opens or shuts this turn’s reasoning, so the host’s
     * pinned collapse bar can appear. See [ReasoningRow].
     */
    onReasoningExpanded: ((Boolean) -> Unit)? = null,
    /** A tick from [ExpandedProcessState.collapseAll]; see [ReasoningRow]. */
    collapseEpoch: Int = 0,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DshSpacing.xl)) {
        if (entry.reasoning.isNotBlank()) {
            ReasoningRow(
                reasoning = entry.reasoning,
                running = false,
                onExpandedChange = onReasoningExpanded,
                translate = translate,
                collapseEpoch = collapseEpoch,
            )
        }
        if (entry.text.isNotBlank()) {
            MarkdownText(entry.text)
        }
        if (showFooter) {
            // margin-left: -6px, so the 28dp icon target optically aligns with the
            // text column it belongs to.
            MessageActions(
                text = entry.text,
                time = entry.time,
                durationMs = durationMs,
                onBranch = onBranch,
                modifier = Modifier.offset(x = (-6).dp),
            )
        }
    }
}

/** The in-flight attempt: live reasoning, live answer text, plus a streaming dot. */
@Composable
fun LiveAttemptRow(
    attempt: LiveAttempt,
    modifier: Modifier = Modifier,
    /** Translates the in-flight reasoning on open; see [ReasoningRow]. */
    translate: ((String, (String?) -> Unit) -> Unit)? = null,
    /** Told when the in-flight reasoning opens or shuts; see [ReasoningRow]. */
    onReasoningExpanded: ((Boolean) -> Unit)? = null,
) {
    val colors = DshTheme.colors
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DshSpacing.xl),
    ) {
        val reasoning = attempt.reasoning
        if (reasoning.isNotBlank()) {
            ReasoningRow(
                reasoning = reasoning,
                running = !attempt.finished,
                onExpandedChange = onReasoningExpanded,
                translate = translate,
            )
        }
        val text = attempt.text
        if (text.isNotBlank()) {
            MarkdownText(text)
        }
        val pending = attempt.toolNames
        if (pending.isNotEmpty() && text.isBlank() && reasoning.isBlank()) {
            Text(
                text = stringResource(R.string.chat_calling_tool, pending.last()),
                style = DshType.bodyMedium,
                color = colors.labelTertiary,
            )
        }
        // No streaming caret: the web UI's live signal is the turn-scoped
        // "Deep diving..." row at the bottom of the column (see TurnStatusRow).
    }
}

/**
 * Whether this conversation has been spoken to yet — the first user message has landed.
 *
 * The composer's session readings (speed, cache share) are instruments for a
 * conversation; before the reader has said anything they measure nothing, and a rail that
 * states "0 tok/s" on a blank session is reporting on a conversation that does not exist.
 * The judgement needs the transcript, which the rail cannot see, so it lives here next to
 * the rows and the caller passes the answer down as `statsVisible`.
 *
 * A notice or a tool row does not count: those arrive *because* a turn was asked for.
 */
fun hasConversationStarted(entries: List<ChatEntry>): Boolean =
    entries.any { it is ChatEntry.UserMessage }

/**
 * How tall an expanded Think / tool body may grow before it scrolls inside its own row.
 *
 * The header is the reader's only way back out of an expanded row, so it must not be
 * pushed off the screen by the row's own content. A cap keeps the whole disclosure — the
 * 24dp header plus this body — inside a thumb's reach of wherever the row sits, and the
 * body's own scroll is what takes the reader through a long thought.
 *
 * A host that pins the header itself (see [ExpandedProcessCollapseBar]) gets the same
 * guarantee from the other side: whatever is expanded, its own collapse control is drawn
 * over the transcript.
 */
internal val ExpandedProcessBodyMax = 280.dp

/**
 * Which disclosure the reader has open, and the way to shut it from outside the row.
 *
 * A row's own header is inside the row, and the row scrolls; a long thought can carry
 * that header off the screen. Capping the body
 * ([ExpandedProcessBodyMax]) bounds the damage but cannot stop the row itself from
 * scrolling away, and a pinned control needs two things the row does not publish on its
 * own: *which* row is open, and a way to close it without tapping it.
 *
 * The list owner holds one of these, hands [collapseEpoch] to every disclosure row and
 * draws [ExpandedProcessCollapseBar] while [label] is set. Rows report through
 * `onExpandedChange`, and a bumped epoch is how [collapseAll] reaches rows that are
 * currently off screen (they are collapsed when they come back).
 */
class ExpandedProcessState {
    /** The open row's title, or null when nothing is expanded. */
    var label by mutableStateOf<String?>(null)
        private set

    /** Bumped by [collapseAll]; every disclosure row collapses when it changes. */
    var collapseEpoch by mutableStateOf(0)
        private set

    /**
     * A row reports its own toggle. [title] is what the pinned bar reads back, so the
     * control says which disclosure it is about to close.
     */
    fun onRowExpanded(expanded: Boolean, title: String) {
        label = if (expanded) title else null
    }

    /** Shut whatever is open, including a row that is currently scrolled off screen. */
    fun collapseAll() {
        collapseEpoch += 1
        label = null
    }
}

/** Remembers one [ExpandedProcessState] for a transcript. */
@Composable
fun rememberExpandedProcessState(): ExpandedProcessState =
    remember { ExpandedProcessState() }

/**
 * The pinned collapse control a conversation can draw while a disclosure is expanded.
 *
 * ## Why this exists as a public surface
 *
 * The header lives inside its row, and a row scrolls: open a long thought, scroll up into
 * it, and the chevron that shuts it leaves the screen with the header. Capping the body
 * ([ExpandedProcessBodyMax]) removes the worst of that, but the row itself can still
 * scroll away. So the list owner can also draw this bar at the top of the transcript,
 * outside the scrolling content, for as long as anything is open — and the reader can
 * always shut it. The `onExpandedChange` parameter of [ReasoningRow] and [ToolCallRow] is
 * how a row tells its host it is the open one.
 */
@Composable
fun ExpandedProcessCollapseBar(
    label: String,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    /** The transcript's backdrop: this bar floats over it, so it wears the same glass. */
    backdrop: GlassBarBackdrop? = null,
) {
    val colors = DshTheme.colors
    val shape = RoundedCornerShape(DshRadius.thumb)
    Row(
        modifier
            .glassLift(shape)
            .liquidPane(backdrop, shape)
            .clickableNoRipple(onClick = onCollapse)
            .padding(start = DshSpacing.lg, end = DshSpacing.md, top = DshSpacing.sm, bottom = DshSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Lightbulb,
            contentDescription = null,
            tint = colors.labelTertiary,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(DshSpacing.sm))
        Text(
            text = label,
            style = DshType.micro,
            color = colors.labelSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 200.dp),
        )
        Spacer(Modifier.width(DshSpacing.sm))
        Icon(
            DshIconVectors.ChevronUpOutline,
            contentDescription = stringResource(R.string.chat_collapse),
            tint = colors.labelTertiary,
            modifier = Modifier.size(14.dp),
        )
    }
}

/**
 * "Think" disclosure: one 24dp header row collapsing to a summary, expanding to
 * pre-wrapped secondary text.
 */
@Composable
fun ReasoningRow(
    reasoning: String,
    running: Boolean,
    modifier: Modifier = Modifier,
    /**
     * Told when the reader opens or shuts this row, so a host that draws a sticky
     * collapse bar knows which disclosure is open. Null (the default) keeps the row a
     * self-contained disclosure with no host involvement.
     */
    onExpandedChange: ((Boolean) -> Unit)? = null,
    /**
     * A tick from [ExpandedProcessState.collapseAll]. When it changes, this row shuts —
     * including a row whose own header has been scrolled off the screen.
     */
    collapseEpoch: Int = 0,
    /**
     * Translates one thought, or null to draw no translation at all.
     *
     * Fired **once**, the first time the reader opens this row, and never again for
     * this text: the host keeps a translation of an exact fragment in the session log,
     * so a second ask for the same thought is answered from its own record rather than
     * from a provider. The state lives in the row rather than in the host because it is
     * per-fragment and dies with the row that owns it — a translation of a thought from
     * three turns ago is not something the panel has any reason to hold.
     *
     * The reader is not made to wait: the original is drawn immediately and the
     * translation appears above it when it arrives.
     */
    translate: ((String, (String?) -> Unit) -> Unit)? = null,
) {
    val colors = DshTheme.colors
    var expanded by rememberSaveable(reasoning.take(32)) { mutableStateOf(false) }
    LaunchedEffect(collapseEpoch) {
        if (collapseEpoch > 0 && expanded) expanded = false
    }
    // Keyed on the text, so a row recycled for a different thought does not show the
    // previous one’s translation — which is the whole hazard of a slot in a lazy list.
    var translation by remember(reasoning) { mutableStateOf<String?>(null) }
    var translating by remember(reasoning) { mutableStateOf(false) }
    LaunchedEffect(expanded, reasoning) {
        val ask = translate ?: return@LaunchedEffect
        if (!expanded || translation != null || translating) return@LaunchedEffect
        translating = true
        ask(reasoning) { result ->
            translating = false
            if (result != null) translation = result
        }
    }
    val summary = remember(reasoning) {
        reasoning.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    }

    Column(modifier.fillMaxWidth()) {
        // The collapse header. It stays *outside* the scroller below, so once the reader
        // is scrolling up through a long thought this row — title and chevron — is still
        // on screen and still the way out of it.
        Row(
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clip(RoundedCornerShape(DshRadius.sm))
                .clickableNoRipple {
                    expanded = !expanded
                    onExpandedChange?.invoke(expanded)
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                if (expanded) {
                    Icon(
                        DshIconVectors.ChevronDownOutline,
                        contentDescription = null,
                        tint = colors.labelTertiary,
                        modifier = Modifier.size(14.dp),
                    )
                } else {
                    Icon(
                        Icons.Rounded.Lightbulb,
                        contentDescription = null,
                        tint = colors.labelTertiary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Spacer(Modifier.width(DshSpacing.sm))
            Text(
                text = stringResource(if (running) R.string.chat_thinking else R.string.chat_think),
                style = DshType.rowTitle,
                color = colors.labelSecondary,
                maxLines = 1,
            )
            if (!expanded && summary.isNotEmpty()) {
                Spacer(Modifier.width(DshSpacing.md))
                Box(
                    Modifier
                        .size(2.dp)
                        .clip(CircleShape)
                        .background(colors.labelCaption),
                )
                Spacer(Modifier.width(DshSpacing.md))
                Text(
                    text = summary,
                    style = DshType.rowSummary,
                    color = colors.labelTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (running) Modifier.alpha(0.8f) else Modifier,
                )
            }
        }

        if (expanded) {
            // A body that scrolls *inside* the row, capped so the header above can never
            // be pushed out by the reader's own content. A thought can be thousands of
            // words; unbounded it made this row a page of its own, and the only way back
            // was to scroll up past all of it — the reported complaint. Capped, the
            // chevron that opened it is always the next thing under the thumb.
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = ExpandedProcessBodyMax)
                    .verticalScroll(rememberScrollState()),
            ) {
                // Markdown, not a plain `Text` — which is what this was, and why a link in
                // thinking was dead. Reasoning is where this model puts most of its URLs
                // ("Let me check https://…/issues/1448"): measured on one session, the
                // assistant's own prose carried them in reasoning, and the plain text here
                // made every one of them unclickable. The renderer is the same one the
                // answer body uses, so a link taps the same way in both.
                // The translation sits *above* the original, not instead of it.
                //
                // Reasoning is evidence: the reader opened this row to see what the
                // model actually did, and replacing that with a translation would hide
                // the thing they came for. Above rather than below because the
                // translation is what they can read — putting it under a page of
                // source text means scrolling to find it.
                translation?.let { translated ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 22.dp, top = DshSpacing.xs, bottom = DshSpacing.md),
                    ) {
                        Text(
                            text = stringResource(R.string.chat_translated),
                            style = DshType.micro,
                            color = colors.labelCaption,
                        )
                        Spacer(Modifier.height(DshSpacing.xxs))
                        MarkdownText(
                            text = translated,
                            color = colors.labelSecondary,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(DshSpacing.md))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(colors.borderL1),
                        )
                    }
                }
                if (translating) {
                    Text(
                        text = stringResource(R.string.chat_translating),
                        style = DshType.micro,
                        color = colors.labelCaption,
                        modifier = Modifier.padding(start = 22.dp, bottom = DshSpacing.xs),
                    )
                }
                MarkdownText(
                    text = reasoning.trim(),
                    color = colors.labelTertiary,
                    modifier = Modifier.padding(start = 22.dp, top = DshSpacing.xs, bottom = DshSpacing.xs),
                )
            }
        }
    }
}

/** Tool calls: collapsed summary row, expanding to a mono IN/OUT card. */
@Composable
fun ToolCallRow(
    entry: ChatEntry.ToolCall,
    /**
     * Resolves a host-held attachment id to a drawable. Only the read-image
     * card consumes it; defaulted so every other caller (and preview) needs no
     * client dependency.
     */
    loadImage: suspend (String) -> ImageBitmap? = { null },
    /**
     * Live output of whichever background job is expanded, or null. Only the row
     * for a call that *started* a background job consumes it; defaulted so the
     * nested-call renderer and previews need no wiring.
     */
    jobOutput: JobObservation? = null,
    /** Opens — or closes — that observation for the job this call started. */
    onObserveJob: (String?) -> Unit = {},
    /**
     * Told when the reader opens or shuts this row, so a host drawing a sticky collapse
     * bar knows which disclosure is open. Null keeps the row self-contained. Last in the
     * list on purpose: existing callers pass `entry` and the image loader positionally.
     */
    onExpandedChange: ((Boolean) -> Unit)? = null,
    /** A tick from [ExpandedProcessState.collapseAll]; see [ReasoningRow]. */
    collapseEpoch: Int = 0,
) {
    val colors = DshTheme.colors
    // The ask-question call owns a transcript row of its own: its args and result
    // pair into a question/answer card rather than an IN/OUT dump.
    val askModel = remember(entry.callId, entry.arguments, entry.result, entry.isError, entry.errorCode) {
        if (entry.name == "ask_user_question") {
            askQuestionRowModel(entry.arguments, entry.result, entry.isError, entry.errorCode)
        } else {
            null
        }
    }
    if (askModel != null) {
        AskQuestionCallRow(entry, askModel)
        return
    }
    // A standard shell call draws the terminal card; anything the card model
    // declines (errors, background or persistent shells) keeps the generic row.
    val bashModel = remember(entry.callId, entry.arguments, entry.result, entry.isError) {
        if (entry.name == "bash" || entry.name == "pwsh") {
            bashTerminalModel(entry.arguments, entry.result, entry.isError)
        } else {
            null
        }
    }
    if (bashModel != null) {
        BashCallRow(entry, bashModel)
        return
    }
    val readModel = remember(entry.callId, entry.arguments, entry.result, entry.isError, entry.meta) {
        if (entry.name == "read" || entry.name == "read_file") {
            readCardModel(entry.arguments, entry.result, entry.isError, entry.meta)
        } else {
            null
        }
    }
    if (readModel != null) {
        ReadCallRow(entry, readModel)
        return
    }
    // A `read` on a picture, or a `read_image` call: the result carries image
    // blocks rather than a file envelope, so the read card above declined.
    val imageModel = remember(entry.callId, entry.arguments, entry.result, entry.isError, entry.meta, entry.images) {
        if (entry.name == "read" || entry.name == "read_image") {
            readImageCardModel(entry.arguments, entry.result, entry.isError, entry.meta, entry.images)
        } else {
            null
        }
    }
    if (imageModel != null) {
        ReadImageCallRow(entry, imageModel, loadImage)
        return
    }
    val searchModel = remember(entry.callId, entry.arguments, entry.result, entry.isError, entry.meta) {
        if (entry.name == "grep" || entry.name == "glob") {
            searchCardModel(entry.name, entry.arguments, entry.result, entry.isError, entry.meta)
        } else {
            null
        }
    }
    if (searchModel != null) {
        SearchCallRow(entry, searchModel)
        return
    }
    val webModel = remember(entry.callId, entry.isError, entry.meta) {
        if (entry.name == "web_search" || entry.name == "web_fetch") {
            webCardModel(entry.name, entry.isError, entry.meta)
        } else {
            null
        }
    }
    if (webModel != null) {
        WebCallRow(entry, webModel)
        return
    }
    val diffModel = remember(entry.callId, entry.arguments, entry.result, entry.isError, entry.meta) {
        if (entry.name == "write" || entry.name == "edit") {
            diffCardModel(entry.name, entry.arguments, entry.result, entry.isError, entry.meta)
        } else {
            null
        }
    }
    if (diffModel != null) {
        DiffCallRow(entry, diffModel)
        return
    }
    val planModel = remember(entry.arguments, entry.isError, entry.errorCode) {
        if (entry.name == "todo_write") {
            planRowModel(entry.arguments, entry.isError, entry.errorCode)
        } else {
            null
        }
    }
    if (planModel != null) {
        PlanCallRow(entry, planModel)
        return
    }
    var expanded by rememberSaveable(entry.callId) { mutableStateOf(false) }
    LaunchedEffect(collapseEpoch) {
        if (collapseEpoch > 0 && expanded) expanded = false
    }
    val summary = remember(entry.name, entry.arguments) { toolSummary(entry.name, entry.arguments) }
    // Not remembered on the key: the title is a resource now, and a cached string
    // would keep the old locale's word after an in-app language switch.
    val title = toolTitle(entry.name)
    // The background job this call started, when it started one.
    //
    // A shell call sent to the background answers with the literal `started
    // background job <id>`, so the row that launched the job is also the chat-log
    // entry that can watch it — and it exists from the moment the job starts, which
    // a completion notice does not. That is what makes the chat log a place to see
    // *live* output rather than only a post-mortem.
    val backgroundJob = remember(entry.callId, entry.result) { backgroundJobIdOf(entry.result) }
    val observing = backgroundJob != null && jobOutput?.jobId == backgroundJob
    // The panel is headed with the command, as the web heads it with `job.label`;
    // a background call's arguments are where that command lives until the roster
    // row arrives to name it properly.
    val backgroundCommand = remember(entry.arguments) { shellCommandOf(entry.arguments) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clip(RoundedCornerShape(DshRadius.sm))
                .clickableNoRipple {
                    expanded = !expanded
                    onExpandedChange?.invoke(expanded)
                    // Opening the row is what opens the job's stream; shutting it
                    // releases the stream, as collapsing does in the web's panel.
                    if (backgroundJob != null) onObserveJob(if (expanded) backgroundJob else null)
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                if (expanded) {
                    Icon(
                        DshIconVectors.ChevronDownOutline,
                        contentDescription = null,
                        tint = colors.labelTertiary,
                        modifier = Modifier.size(14.dp),
                    )
                } else {
                    Icon(
                        imageVector = toolIcon(entry.name),
                        contentDescription = null,
                        tint = colors.labelTertiary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Spacer(Modifier.width(DshSpacing.sm))
            Text(
                text = title,
                style = DshType.rowTitle,
                color = colors.labelSecondary,
                maxLines = 1,
            )
            // An empty summary drops the separator with it (ToolRow.tsx:212), so a
            // row that is only its title shows no trailing dot. A blank summary on
            // an in-flight call still says "running", since the row has no other
            // signal that the call has started.
            // The state words are resources; the summary itself is the model's own
            // words and stays runtime data.
            val rowSummary = when {
                summary.isNotBlank() && entry.result == null ->
                    stringResource(R.string.chat_summary_running, summary)
                summary.isNotBlank() -> summary
                entry.result == null -> stringResource(R.string.chat_running)
                else -> ""
            }
            if (rowSummary.isNotBlank()) {
                Spacer(Modifier.width(DshSpacing.md))
                Box(
                    Modifier
                        .size(2.dp)
                        .clip(CircleShape)
                        .background(colors.labelCaption),
                )
                Spacer(Modifier.width(DshSpacing.md))
                Text(
                    text = rowSummary,
                    style = DshType.rowSummary,
                    color = if (entry.isError) colors.error else colors.labelTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (expanded) {
            Spacer(Modifier.height(DshSpacing.xs))
            // Deliberately not wrapped in a scroller of its own: the IN/OUT card already
            // caps each section at 150dp with its own scroll, and the job panel below
            // caps itself at 224dp. A third, outer scroll around two inner ones would be
            // three nested scrollers fighting for one vertical drag — the same gesture
            // ambiguity `JobsCard` refuses for horizontal scrolling. The header stays
            // reachable because the body is already bounded, which is what the cap is for.
            ToolIoCard(
                sections = buildList {
                    add(IN_KEY to entry.arguments)
                    entry.result?.let { add(OUT_KEY to it) }
                },
                isError = entry.isError,
            )
            // The job's own live output under the call that started it: the IN/OUT
            // card above holds the *launch* (the command, and the one-line result
            // naming the job), and this holds what the command is printing now.
            if (observing) {
                Spacer(Modifier.height(DshSpacing.xs))
                JobOutputPanel(
                    command = backgroundCommand ?: entry.arguments,
                    view = jobOutput,
                    running = jobOutput?.streaming == true,
                    jobId = backgroundJob,
                )
            }
            Spacer(Modifier.height(DshSpacing.xs))
        }
    }
}

/**
 * The two section keys the IN/OUT card is built from.
 *
 * They are identifiers rather than copy: three cards in this package build sections
 * with them (`ChatRows`, `PlanRow`, `AskQuestionRow`) and the card keys the error
 * tint on [OUT_KEY]. Only the word drawn in the gutter is translated
 * (`tool_in_label` / `tool_out_label`), so a locale can widen it without moving logic.
 */
internal const val IN_KEY = "IN"
internal const val OUT_KEY = "OUT"

/**
 * The IN/OUT card: 0.5dp hairline, 12dp radius, code surface, 11/16 mono, each
 * section independently capped and scrollable.
 */
@Composable
internal fun ToolIoCard(sections: List<Pair<String, String>>, isError: Boolean) {
    val colors = DshTheme.colors
    val shape = RoundedCornerShape(DshRadius.card)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = DshSpacing.xs)
            .clip(shape)
            .background(colors.codeBlock)
            .border(0.5.dp, colors.borderL1, shape),
    ) {
        sections.forEachIndexed { index, (label, body) ->
            if (index > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(colors.borderL2),
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 150.dp)
                    // The cap without a scroller hid everything past 150dp with
                    // no way to reach it; the web's `.ioSection` scrolls.
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = DshSpacing.xl, vertical = DshSpacing.lg),
            ) {
                Text(
                    text = when (label) {
                        IN_KEY -> stringResource(R.string.tool_in_label)
                        OUT_KEY -> stringResource(R.string.tool_out_label)
                        else -> label
                    },
                    style = DshType.micro,
                    color = colors.labelCaption,
                    // A minimum rather than a fixed width: a locale may name the
                    // section with a longer word than "OUT", and a hard 30dp would
                    // clip it. The body beside it is weighted, so the row still
                    // gives the value the rest of the line.
                    modifier = Modifier.widthIn(min = 30.dp),
                )
                // Wrapped, not panned. The terminal card did this first and for the
                // same reason — the widest lines in a tool's own input or output are
                // the ones worth reading rather than steering — but a *background*
                // bash call never reaches that card: `bashTerminalModel` declines it
                // (it has no exit status to draw), so it lands here, and here the
                // body still scrolled sideways.
                Text(
                    text = body.ifEmpty { "—" },
                    style = DshType.codeSmall,
                    color = if (isError && label == OUT_KEY) colors.error else colors.labelSecondary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * One tool call and whatever the host nested under it (`ToolCallTree.tsx`).
 *
 * The web paints a root call and its recursive sub-calls through the same row
 * component, indenting the children behind a 0.5px connector rail
 * (`ToolCallTree.module.css` `.subCalls`: `margin-left: 22px`, `padding-left:
 * 8px`, `border-left: 0.5px`). It has no caret — a mouse user sees the whole
 * subtree at once. A phone cannot afford that: a `run_code` program can nest
 * hundreds of dispatches, and the transcript has no hover to preview them, so
 * the subtree starts folded behind a caret that carries the count.
 *
 * A node with no children renders exactly the flat [ToolCallRow] it replaced, so
 * non-PTC calls are untouched.
 */
@Composable
fun ToolCallTreeRow(
    node: ToolCallNode,
    /**
     * Resolves a host-held attachment id to a drawable; forwarded unchanged to
     * every row in the subtree.
     */
    loadImage: suspend (String) -> ImageBitmap? = { null },
    /** Forwarded to the root row; see [ToolCallRow]. */
    onExpandedChange: ((Boolean) -> Unit)? = null,
    /** Forwarded to every row in the subtree; see [ReasoningRow]. */
    collapseEpoch: Int = 0,
) {
    if (node.children.isEmpty()) {
        ToolCallRow(
            node.entry,
            loadImage,
            onExpandedChange = onExpandedChange,
            collapseEpoch = collapseEpoch,
        )
        return
    }
    var expanded by rememberSaveable(node.entry.callId) { mutableStateOf(false) }
    LaunchedEffect(collapseEpoch) {
        if (collapseEpoch > 0 && expanded) expanded = false
    }
    Column(Modifier.fillMaxWidth()) {
        ToolCallRow(
            node.entry,
            loadImage,
            onExpandedChange = onExpandedChange,
            collapseEpoch = collapseEpoch,
        )
        NestedCallDisclosure(
            count = node.descendantCount,
            expanded = expanded,
            onToggle = { expanded = !expanded },
        )
        if (expanded) NestedCalls(node.children, loadImage, collapseEpoch)
    }
}

/**
 * The connector rail and the nested rows it hangs: web `.subCalls`' geometry,
 * with the rail drawn behind instead of as a border so the column keeps its
 * intrinsic height.
 */
@Composable
private fun NestedCalls(
    children: List<ToolCallNode>,
    loadImage: suspend (String) -> ImageBitmap?,
    collapseEpoch: Int = 0,
) {
    val colors = DshTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 22.dp)
            .drawBehind {
                drawRect(
                    color = colors.borderL2,
                    topLeft = Offset.Zero,
                    size = Size(0.5.dp.toPx(), size.height),
                )
            }
            .padding(start = DshSpacing.md, top = DshSpacing.xs, bottom = DshSpacing.xxs),
        verticalArrangement = Arrangement.spacedBy(DshSpacing.xs),
    ) {
        for (child in children) {
            key(child.entry.callId) {
                ToolCallTreeRow(child, loadImage, collapseEpoch = collapseEpoch)
            }
        }
    }
}

/**
 * The collapsed-subtree affordance: a 24dp disclosure line seated under the
 * parent's own 16dp leading slot, labelled with the web's subtool vocabulary
 * (`ui-trajectory/locales.ts` `kind.subtool` / `details.subtoolCalls`).
 */
@Composable
private fun NestedCallDisclosure(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    val colors = DshTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(24.dp)
            .clip(RoundedCornerShape(DshRadius.sm))
            .clickableNoRipple(onClick = onToggle)
            .padding(start = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (expanded) DshIconVectors.ChevronDownOutline else DshIconVectors.ChevronRightOutline,
            contentDescription = stringResource(
                if (expanded) R.string.tool_collapse_subcalls else R.string.tool_expand_subcalls,
            ),
            tint = colors.labelTertiary,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(DshSpacing.sm))
        Text(
            text = pluralStringResource(R.plurals.tool_subcall_count, count, count),
            style = DshType.rowSummary,
            color = colors.labelTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The to-do dock, mirroring `TodoPanel.tsx`.
 *
 * Starts **collapsed** (the web panel's `useState(true)`) and its chevron points
 * *up* while collapsed, because the panel grows upward from the composer. The
 * header carries the checklist glyph, the title "To-dos", and a done/active/pending
 * tally; the list only exists while expanded.
 */
@Composable
fun TodoCard(
    items: List<TodoItem>,
    modifier: Modifier = Modifier,
    backdrop: GlassBarBackdrop? = null,
) {
    if (items.isEmpty()) return
    val colors = DshTheme.colors
    var collapsed by rememberSaveable { mutableStateOf(true) }
    val shape = RoundedCornerShape(DshRadius.card)

    Column(
        modifier
            .fillMaxWidth()
            // One of the floating cards over the transcript now, so it wears the same
            // liquid material as the dock it is lifted above rather than an opaque
            // `tip` slab beside it.
            .glassLift(shape)
            .liquidPane(backdrop, shape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickableNoRipple { collapsed = !collapsed }
                // Extra trailing inset so the chevron clears the turn rail's
                // gesture column, which overlays the transcript's right edge.
                .padding(start = DshSpacing.lg, end = 30.dp, top = DshSpacing.md, bottom = DshSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                DshIconVectors.ChecklistOutline,
                contentDescription = null,
                tint = colors.labelSecondary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(DshSpacing.sm))
            Text(
                text = stringResource(R.string.chat_todos_title),
                style = DshType.rowTitle.copy(fontWeight = FontWeight.Medium),
                color = colors.labelPrimary,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = todoProgressLabel(items),
                style = DshType.micro,
                color = colors.labelTertiary,
                maxLines = 1,
            )
            Spacer(Modifier.width(DshSpacing.md))
            Icon(
                imageVector = if (collapsed) DshIconVectors.ChevronUpOutline else DshIconVectors.ChevronDownOutline,
                contentDescription = stringResource(
                    if (collapsed) R.string.chat_expand_todos else R.string.chat_collapse_todos,
                ),
                tint = colors.labelTertiary,
                modifier = Modifier.size(14.dp),
            )
        }

        if (!collapsed) {
            Column(
                Modifier
                    .padding(
                        start = DshSpacing.lg,
                        end = DshSpacing.lg,
                        bottom = DshSpacing.md,
                    )
                    // The composer below cannot shrink, so an unbounded list here
                    // pushes it off the screen once the keyboard is up. The web
                    // caps its panel lists the same way.
                    .heightIn(max = 168.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                items.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            TodoGlyph(isDone = item.isDone, isActive = item.isActive)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = item.content,
                            style = DshType.bodySmall,
                            color = colors.labelSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * `progressLabel`: only non-zero buckets, joined with en-space · en-space because
 * HTML/Compose both collapse runs of ASCII spaces.
 *
 * `@Composable` because each bucket is a plural resource: English inflects the noun
 * and Chinese does not, which is exactly what a resource fixes and a concatenated
 * sentence cannot.
 */
@Composable
private fun todoProgressLabel(items: List<TodoItem>): String {
    val done = items.count { it.isDone }
    val active = items.count { it.isActive }
    val pending = items.size - done - active
    // One plural per bucket rather than one sentence: the buckets that are zero are
    // dropped, so each has to be a complete phrase on its own. The list is built by
    // hand rather than with `buildList`, because the figures are read from resources
    // and a composable call belongs in this function's own scope.
    val parts = mutableListOf<String>()
    if (done > 0) parts.add(pluralStringResource(R.plurals.chat_todos_completed, done, done))
    if (active > 0) parts.add(pluralStringResource(R.plurals.chat_todos_in_progress, active, active))
    if (pending > 0) parts.add(pluralStringResource(R.plurals.chat_todos_pending, pending, pending))
    return parts.joinToString("\u2002·\u2002")
}

/**
 * Non-conversational rows: plugin/job notices, context compaction, and errors.
 *
 * Notices are compact `context` rows like the web UI's, not chat bubbles — one
 * summary line with an icon, and the body only when the row is expanded. Errors
 * keep the warning card treatment.
 */
@Composable
fun NoticeRow(
    entry: ChatEntry.Notice,
    /**
     * The live output of whichever job is expanded anywhere in the UI, or null when
     * none is. Passed in rather than fetched, because the observation is one stream
     * shared by the jobs sheet and every chat-log entry.
     */
    jobOutput: JobObservation? = null,
    /** Opens — or closes — that observation for [ChatEntry.Notice.jobId]. */
    onObserveJob: (String?) -> Unit = {},
) {
    val colors = DshTheme.colors

    if (entry.severity == NoticeSeverity.ERROR) {
        Text(
            text = entry.text.ifBlank { entry.kind },
            style = DshType.bodySmall,
            color = colors.error,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(DshRadius.md))
                .background(colors.warnTertiary)
                .padding(DshSpacing.lg),
        )
        return
    }

    var expanded by rememberSaveable(entry.seq) { mutableStateOf(false) }
    val isCompaction = entry.kind.startsWith("compaction")
    // Whether *this* row owns the shared observation, rather than a local flag: the
    // jobs sheet can claim a job's stream while this row is still on screen, and a
    // local flag would leave the panel here painting another job's bytes.
    val observing = entry.jobId != null && jobOutput?.jobId == entry.jobId

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(DshRadius.sm))
                .clickableNoRipple(enabled = entry.detail != null || entry.jobId != null) {
                    expanded = !expanded
                    // Opening asks for the job's stream, shutting releases it — keyed
                    // on the new disclosure state rather than on `observing`, so a row
                    // the sheet has taken the stream from still behaves as it reads.
                    if (entry.jobId != null) onObserveJob(if (expanded) entry.jobId else null)
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isCompaction) DshIconVectors.InfoOutline else DshIconVectors.AgentPresetOutline,
                contentDescription = null,
                tint = colors.labelCaption,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(DshSpacing.sm))
            Text(
                text = entry.text.ifBlank { entry.kind },
                style = DshType.micro,
                color = colors.labelTertiary,
                maxLines = if (expanded) 4 else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (entry.detail != null || entry.jobId != null) {
                Spacer(Modifier.width(DshSpacing.sm))
                Icon(
                    imageVector = if (expanded) DshIconVectors.ChevronUpOutline else DshIconVectors.ChevronDownOutline,
                    contentDescription = stringResource(
                        if (expanded) R.string.chat_hide_details else R.string.chat_show_details,
                    ),
                    tint = colors.labelTertiary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        if (expanded) {
            entry.detail?.let { detail ->
                Spacer(Modifier.height(DshSpacing.xs))
                Text(
                    text = detail,
                    style = DshType.bodySmall,
                    color = colors.labelSecondary,
                    modifier = Modifier.padding(start = 21.dp),
                )
            }
        }
        // Drawing follows the disclosure as well as the stream: a row the reader has
        // shut must not keep a terminal open under it just because the sheet is
        // holding the stream.
        if (expanded && observing) {
            Spacer(Modifier.height(DshSpacing.xs))
            JobOutputPanel(
                // The notice's own body names the command; the panel wants the command,
                // not the notice's prose.
                command = entry.detail ?: entry.text,
                view = jobOutput,
                running = false,
                jobId = entry.jobId,
                modifier = Modifier.padding(start = 21.dp),
            )
        }
    }
}

/**
 * Approval prompt for a host `approval/request` waterfall.
 *
 * This is a hard requirement for a usable client: while a waterfall is pending
 * the agent is blocked, so the answer has to come from a human tap.
 *
 * The tap does not answer the card away. [answer] carries what has actually
 * happened since: nothing ([ApprovalAnswer.Waiting]), an answer on its way, an
 * answer the host has taken, or an answer that did not get through. The accepted
 * reply is what settles the card — it is up for a bounded grace and then gone, and a
 * host `cancel` takes it sooner — so every state here has to be legible on its own,
 * and the buttons are inert for the two that have nothing left for a tap to do.
 *
 * ## The card does not move when the state does
 *
 * The words live in [uk.xa0.dsh.model.ApprovalCopy] and are drawn in
 * [ApprovalStatusStrip], which is one fixed line tall and is laid out in *every*
 * state, [ApprovalAnswer.Waiting] included. That is the fix for the reported defect:
 * the state used to be a sentence in a region that only existed once something had
 * happened, so the card grew on the tap, grew again — to a second line — when the
 * host took the answer, and shrank when the card was cleared a second and a half
 * later. Two jumps per approval, in and out.
 *
 * Nothing else here varies with the state: the banner's word is one line whatever it
 * says, and the buttons' `enabled` only changes their alpha and whether a tap is
 * taken. So no state can change this card's geometry — see [ApprovalStatusStrip] for
 * why the strip itself is proof against a future copy change rather than merely
 * shorter today.
 */
@Composable
fun ApprovalCard(
    toolName: String,
    reason: String?,
    answer: ApprovalAnswer,
    onAllow: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DshTheme.colors
    val shape = RoundedCornerShape(20.dp)
    val tappable = answer is ApprovalAnswer.Waiting || answer is ApprovalAnswer.Unsent
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.inputMajor)
            .border(1.dp, colors.warn, shape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.warnTertiary)
                .padding(horizontal = DshSpacing.xl, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                DshIconVectors.WarningTriangleOutline,
                contentDescription = null,
                tint = colors.warn,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(DshSpacing.md))
            Text(
                stringResource(
                    if (answer is ApprovalAnswer.Sent) {
                        R.string.chat_approval_sent
                    } else {
                        R.string.chat_approval_required
                    },
                ),
                style = DshType.meta,
                color = colors.warn,
                // One line whatever it says: this banner is the other thing on the
                // card that changes with the state, and a second line here would
                // move the card just as the status line used to.
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Column(Modifier.padding(DshSpacing.xl)) {
            Text(
                text = stringResource(R.string.chat_approval_tool_wants_to_run, toolName),
                style = DshType.titleMedium,
                color = colors.labelPrimary,
            )
            if (!reason.isNullOrBlank()) {
                Spacer(Modifier.height(DshSpacing.sm))
                Text(reason, style = DshType.meta, color = colors.labelSecondary)
            }
            Spacer(Modifier.height(DshSpacing.sm))
            ApprovalStatusStrip(answer)
            Spacer(Modifier.height(DshSpacing.xl))
            Row(horizontalArrangement = Arrangement.spacedBy(DshSpacing.lg)) {
                PillButton(
                    label = stringResource(R.string.chat_approval_reject),
                    primary = false,
                    enabled = tappable,
                    onClick = onReject,
                )
                PillButton(
                    label = stringResource(R.string.chat_approval_allow_once),
                    primary = true,
                    enabled = tappable,
                    onClick = onAllow,
                )
            }
        }
    }
}

/**
 * The card's one state-dependent region, sized so that no state can move it.
 *
 * It is laid out in every state, [ApprovalAnswer.Waiting] included, and it is exactly
 * one `DshType.meta` line tall at whatever font scale the reader has chosen. That
 * combination is the structural guarantee rather than a shorter sentence:
 *
 *  * the height is *fixed*, not a minimum, so a label can never enlarge the region —
 *    it is clipped by it instead;
 *  * `maxLines = 1` with ellipsis means the visible text cannot wrap, so the label
 *    stays inside the line it is given;
 *  * the region exists even when there is nothing to say, so the card is already
 *    this size before the first tap and after the last one.
 *
 * What varies between states is therefore only what cannot affect layout: the tint,
 * and the glyph of a mark drawn at a fixed size. The *words* vary too, and that is
 * safe precisely because the region is one line — a wider word has nowhere to push
 * anything, since the card is already as wide as the screen.
 *
 * Not colour alone: each state also has its own mark — a clock for an answer in
 * flight, an outbound arrow for one the host has taken, a warning triangle for one
 * that did not get through — and its own words, so a reader who cannot separate the
 * tints, or who is looking at a dimmed screen, still has the claim. There is
 * deliberately no `stateDescription` here: the drawer sets one because its blue
 * carries the state on its own, while these words already say it, and a state
 * description equal to the visible text is only read out twice.
 */
@Composable
private fun ApprovalStatusStrip(
    answer: ApprovalAnswer,
    modifier: Modifier = Modifier,
) {
    val status = ApprovalCopy.status(answer)
    val tint = answer.statusTint()
    val mark = answer.statusMark()
    // The style's own line height, at the reader's font scale: one sp of line height
    // is one dp of box. (`Density` has no `TextUnit.toDp()`, so this is the round
    // trip through px, which is exact: px = sp x fontScale x density.)
    val line = with(LocalDensity.current) { DshType.meta.lineHeight.toPx().toDp() }
    Row(
        modifier
            .fillMaxWidth()
            .height(line)
            .testTag(APPROVAL_STATUS_STRIP_TAG),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The mark's seat is laid out in every state, so the claim starts at the same
        // x whether or not there is a mark to draw.
        Box(Modifier.size(ApprovalMarkSize), contentAlignment = Alignment.Center) {
            if (mark != null) {
                Icon(
                    mark,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(ApprovalMarkSize),
                )
            }
        }
        Spacer(Modifier.width(DshSpacing.md))
        Text(
            text = status ?: "",
            style = DshType.meta,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** The strip's mark: a shape per state, so the state is never carried by tint alone. */
private fun ApprovalAnswer.statusMark(): ImageVector? = when (this) {
    ApprovalAnswer.Waiting -> null
    is ApprovalAnswer.Answering -> DshIconVectors.AlarmClockOutline
    is ApprovalAnswer.Sent -> DshIconVectors.SendOutline
    is ApprovalAnswer.Unsent -> DshIconVectors.WarningTriangleOutline
}

/** The strip's tint. It reinforces the claim; it never has to make it on its own. */
@Composable
private fun ApprovalAnswer.statusTint(): Color = when (this) {
    ApprovalAnswer.Waiting -> DshTheme.colors.labelSecondary
    is ApprovalAnswer.Answering -> DshTheme.colors.labelSecondary
    is ApprovalAnswer.Sent -> DshTheme.colors.success
    is ApprovalAnswer.Unsent -> DshTheme.colors.error
}

/** Fixed size for the strip's mark, in every state, so the seat cannot move. */
private val ApprovalMarkSize = 15.dp

/**
 * Names the strip for `ApprovalCardTest`, which measures it: the region's declared
 * height is the one part of "it does not reflow" this harness can actually see.
 */
internal const val APPROVAL_STATUS_STRIP_TAG = "approval-status-strip"

@Composable
private fun PillButton(
    label: String,
    primary: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = DshTheme.colors
    val shape = RoundedCornerShape(DshRadius.pill)
    Box(
        Modifier
            .clip(shape)
            .background(if (primary) colors.sendFill else colors.tip)
            .border(0.5.dp, if (primary) colors.sendFill else colors.borderL3, shape)
            .clickableNoRipple(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f)
            .padding(horizontal = DshSpacing.xxl, vertical = DshSpacing.md),
    ) {
        Text(
            text = label,
            style = DshType.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = if (primary) colors.sendGlyph else colors.labelPrimary,
        )
    }
}

/**
 * Goal bar: the docked strip above the composer.
 *
 * Like the to-do dock it starts collapsed, so the composer area stays compact on a
 * phone; the objective is still legible in the header. Expanding reveals the full
 * objective and the lifecycle actions, and `complete` goals render nothing at all.
 */
@Composable
fun GoalBar(
    goal: GoalState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEdit: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The transcript's backdrop, so the bar wears the same liquid material as the to-do
     * card beside it. Null paints the flat tier, which is the whole material story on a
     * device that cannot sample a backdrop.
     */
    backdrop: GlassBarBackdrop? = null,
) {
    val colors = DshTheme.colors
    var collapsed by rememberSaveable(goal.id) { mutableStateOf(true) }
    var editing by remember(goal.id) { mutableStateOf(false) }
    var draft by remember(goal.id) { mutableStateOf(goal.objective) }
    // Edit swaps a plain Text for a plain BasicTextField, which is invisible
    // without the caret and the keyboard: tapping Edit looked like it did
    // nothing. The field takes focus, so the caret and the keyboard appear.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(editing) {
        if (editing) runCatching { focusRequester.requestFocus() }
    }

    val label = stringResource(
        when (goal.phase) {
            "paused" -> R.string.chat_goal_phase_paused
            "blocked" -> R.string.chat_goal_phase_blocked
            "active" -> R.string.chat_goal_phase_active
            else -> R.string.chat_goal_phase_none
        },
    )
    val accent = when (goal.phase) {
        "paused", "blocked" -> colors.warn
        else -> colors.accent
    }

    val shape = RoundedCornerShape(DshRadius.card)
    Column(
        modifier
            .fillMaxWidth()
            // The same floating material as the To-dos card it shares a seat with: one
            // of the cards lifted above the dock, not an opaque slab beside them.
            .glassLift(shape)
            .liquidPane(backdrop, shape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickableNoRipple { collapsed = !collapsed }
                // Trailing inset clears the turn rail's gesture column, exactly as
                // the to-do card's does. The goal bar is dock-only today, so this is
                // for consistency and for the day goal updates render inline.
                .padding(start = DshSpacing.lg, end = 30.dp, top = DshSpacing.md, bottom = DshSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                DshIconVectors.GoalOutline,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(DshSpacing.sm))
            Text(label, style = DshType.rowTitle, color = accent, maxLines = 1)
            Spacer(Modifier.width(DshSpacing.md))
            Text(
                text = goal.objective,
                style = DshType.bodySmall,
                color = colors.labelSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // `revision` is the host's CAS revision and bumps on every mutation
            // (edit/pause/resume), so it is not a round counter; the budget counts
            // `roundsStarted`, which the host advances to the admitted round when
            // that round's `user/message` folds — so while round N runs it is N.
            goal.maxRounds?.let { max ->
                Spacer(Modifier.width(DshSpacing.sm))
                Text(
                    text = "${goal.roundsStarted}/$max",
                    style = DshType.micro,
                    color = colors.labelCaption,
                )
            }
            Spacer(Modifier.width(DshSpacing.md))
            Icon(
                imageVector = if (collapsed) DshIconVectors.ChevronUpOutline else DshIconVectors.ChevronDownOutline,
                contentDescription = stringResource(
                    if (collapsed) R.string.chat_goal_expand else R.string.chat_goal_collapse,
                ),
                tint = colors.labelTertiary,
                modifier = Modifier.size(14.dp),
            )
        }

        if (!collapsed) {
            Column(
                Modifier
                    .padding(
                        start = DshSpacing.lg,
                        end = DshSpacing.lg,
                        bottom = DshSpacing.md,
                    )
                    // Same reason as the to-do list: a long objective must not
                    // push the composer off the screen.
                    .heightIn(max = 168.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (editing) {
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        textStyle = DshType.bodySmall.copy(color = colors.labelPrimary),
                        cursorBrush = SolidColor(colors.accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .clip(RoundedCornerShape(DshRadius.md))
                            .background(colors.inputMajor)
                            .border(0.5.dp, colors.borderL2, RoundedCornerShape(DshRadius.md))
                            .padding(DshSpacing.md),
                    )
                    Spacer(Modifier.height(DshSpacing.md))
                    Row(horizontalArrangement = Arrangement.spacedBy(DshSpacing.lg)) {
                        GoalAction(stringResource(R.string.chat_goal_save), primary = true) {
                            editing = false
                            onEdit(draft)
                        }
                        GoalAction(stringResource(R.string.chat_goal_cancel), primary = false) {
                            editing = false
                        }
                    }
                } else {
                    Text(
                        text = goal.objective,
                        style = DshType.bodySmall,
                        color = colors.labelSecondary,
                    )
                    Spacer(Modifier.height(DshSpacing.md))
                    Row(horizontalArrangement = Arrangement.spacedBy(DshSpacing.lg)) {
                        if (goal.phase == "active") {
                            GoalAction(
                                stringResource(R.string.chat_goal_pause),
                                primary = false,
                                onClick = onPause,
                            )
                        } else {
                            GoalAction(
                                stringResource(R.string.chat_goal_resume),
                                primary = false,
                                onClick = onResume,
                            )
                        }
                        GoalAction(stringResource(R.string.chat_goal_edit), primary = false) {
                            draft = goal.objective
                            editing = true
                        }
                        GoalAction(
                            stringResource(R.string.chat_goal_clear),
                            primary = false,
                            onClick = onClear,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalAction(label: String, primary: Boolean, onClick: () -> Unit) {
    val colors = DshTheme.colors
    Text(
        text = label,
        style = DshType.micro,
        color = if (primary) colors.accent else colors.labelTertiary,
        modifier = Modifier
            .clip(RoundedCornerShape(DshRadius.sm))
            .clickableNoRipple(onClick = onClick)
            .padding(horizontal = DshSpacing.sm, vertical = DshSpacing.xxs),
    )
}

/**
 * The folded summary row for a finished turn: one 24dp disclosure line reading
 * "N tool calls · N messages". The web UI crossfades a tool glyph into a chevron
 * on hover; touch has no hover, so the chevron is always shown.
 */
@Composable
fun TurnProcessRow(row: DisplayRow.TurnProcess, onToggle: () -> Unit) {
    val colors = DshTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(24.dp)
            .clip(RoundedCornerShape(DshRadius.sm))
            .clickableNoRipple(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (row.expanded) DshIconVectors.ChevronDownOutline else DshIconVectors.ChevronRightOutline,
                contentDescription = stringResource(
                    if (row.expanded) R.string.tool_collapse_process else R.string.tool_expand_process,
                ),
                tint = colors.labelTertiary,
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(Modifier.width(DshSpacing.sm))
        Text(
            text = row.label,
            style = DshType.rowTitle,
            color = colors.labelSecondary,
            maxLines = 1,
        )
    }
}

// ------------------------------------------------------------------ helpers

private fun toolIcon(name: String): ImageVector = when (name.lowercase()) {
    "bash", "shell", "terminal", "run_command" -> DshIconVectors.CodeOutline
    "read", "read_file" -> DshIconVectors.BrowseOutline
    "write", "write_file" -> DshIconVectors.EditOutline
    "edit", "str_replace", "apply_patch" -> DshIconVectors.EditOutline
    "grep", "search" -> DshIconVectors.SearchOutline
    "glob", "ls", "list" -> DshIconVectors.FolderOpenOutline
    "web_search" -> DshIconVectors.GlobeOutline
    "web_fetch" -> DshIconVectors.DownloadOutline
    "task", "subagent", "delegate" -> DshIconVectors.AgentPresetOutline
    "todo_write", "todos" -> DshIconVectors.ChecklistOutline
    "present" -> DshIconVectors.InspectOutline
    "ask_user_question", "ask" -> DshIconVectors.QuestionOutline
    else -> Icons.Rounded.Build
}

/**
 * The word a reader sees for a tool key.
 *
 * The keys themselves are protocol and stay in code; the titles are copy, so the
 * ten that the web client names are resources. An unknown key — a host plugin's own
 * tool, which no resource can know — still falls back to the key prettified, which
 * is what the old `when` did for everything outside this list.
 */
@Composable
private fun toolTitle(name: String): String = when (name.lowercase()) {
    "bash" -> stringResource(R.string.tool_title_bash)
    "read", "read_file" -> stringResource(R.string.tool_title_read)
    "write", "write_file" -> stringResource(R.string.tool_title_write)
    "edit" -> stringResource(R.string.tool_title_edit)
    "grep" -> stringResource(R.string.tool_title_grep)
    "glob" -> stringResource(R.string.tool_title_glob)
    "web_search" -> stringResource(R.string.tool_title_web_search)
    "web_fetch" -> stringResource(R.string.tool_title_web_fetch)
    "ask_user_question" -> stringResource(R.string.tool_title_question)
    "todo_write" -> stringResource(R.string.tool_title_plan)
    else -> name.replaceFirstChar { it.uppercase() }.replace('_', ' ')
}

/** Best-effort one-line summary of a tool call, preferring the model's own description. */
internal fun toolSummary(name: String, arguments: String): String {
    if (arguments.isBlank()) return ""
    val json = runCatching { JSONObject(arguments) }.getOrNull() ?: return arguments.take(120)
    val preferred = listOf(
        "description", "file_path", "path", "command", "pattern", "query", "url", "prompt", "name",
    )
    val pathKeys = setOf("file_path", "path", "cwd", "directory", "notebook_path")
    for (key in preferred) {
        val value = json.optString(key)
        if (value.isEmpty()) continue
        val first = value.lineSequence().first()
        // A full workspace path eats the whole row and pushes the tool's own name
        // off screen, so path-bearing keys are abbreviated before the length cap.
        return if (key in pathKeys) shortenToolPath(first) else first.take(160)
    }
    val first = json.keys().asSequence().firstOrNull() ?: return ""
    return json.optString(first).take(160)
}
