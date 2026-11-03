// Vendored from chenaizhang/KernelSU-Style-UI-Kit (GPL-3.0) — see NOTICE.md.
// Upstream path: app/src/main/java/com/example/kernelsustyleuikit/ui/component/dialog/Dialog.kt + DialogMiuix.kt
// Change: merged into one Miuix-only file — the Material sibling, the LocalUiMode branching and the Parcelize saver are gone; the renderer and the strings are parameters, and the pinned-button layout is kept.

package uk.xa0.dsh.ui.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog
import uk.xa0.dsh.ui.theme.DshSpacing
import uk.xa0.dsh.ui.theme.DshTheme
import kotlin.coroutines.resume

/**
 * The dialog vocabulary: a handle the caller owns, and the composable that paints it.
 *
 * Upstream split this across three files — a Material sibling, a Miuix sibling and the handle layer
 * that branched between them on `LocalUiMode`. This app has one visual language, so the branch, the
 * sibling and the composition local are all gone: what is left is one handle layer and one Miuix
 * painter. Nothing here reads a composition local to decide how to look.
 *
 * ## Why the handle exists at all
 *
 * A caller needs to open a confirm dialog and *wait* for the answer —
 * [ConfirmDialogHandle.awaitConfirm] suspends until a button is pressed — and it needs to do that
 * from an event handler, not from a composition. Keeping the visible state, the coroutine scope and
 * the result channel in one remembered object is what makes that possible without threading state
 * through every screen.
 */

/**
 * One confirm dialog's text, separated from its showing/hiding state.
 *
 * Upstream made this a `Parcelable` because it was also the saveable payload. Being immutable data
 * with a [mapSaver] over its fields does the same job here and does not need the `kotlin-parcelize`
 * plugin, which this module does not apply.
 *
 * `isMarkdown`/`isHtml` are **not** carried: they existed because upstream bundled a markdown
 * renderer. This module has none, so the renderer is a parameter of [ConfirmDialogHost] and the
 * visuals stay data.
 */
@Immutable
data class ConfirmDialogVisuals(
    val title: String = "",
    val content: String? = null,
    val confirm: String? = null,
    val dismiss: String? = null,
    /**
     * Whether the confirm button can be pressed.
     *
     * The kit's confirm had no such flag, and one caller needs it: a deletion that is
     * already in flight says "Deleting…" and must not be pressed twice. Leaving it out
     * forced that caller to keep Material's `AlertDialog`, which is the split this
     * dialog exists to remove.
     */
    val confirmEnabled: Boolean = true,
    /**
     * How loud the confirm button is.
     *
     * The kit drew every confirm in the accent colour, which is right for "Rename" and
     * wrong for "Force quit": this app's own rule is that a destructive action wears the
     * error tint and a dangerous-but-reversible one wears warning (see the Full-access
     * option and the truncated-entry note). Without this the re-skin would have quietly
     * repainted every destructive confirmation in the brand colour — a regression a
     * baseline would not catch, because dialogs are not in any capture.
     */
    val confirmTone: ConfirmTone = ConfirmTone.Primary,
) {
    /**
     * Whether there is anything to show.
     *
     * [ConfirmDialogHandle.show] refuses an empty one: an untitled, bodyless dialog is a bug in the
     * caller, and drawing it yields a stray card with two buttons and no explanation.
     */
    val isEmpty: Boolean
        get() = title.isEmpty() && content.isNullOrEmpty()
}

/** Which of the palette's semantic roles the confirm button wears. */
enum class ConfirmTone {
    /** The ordinary "yes": rename, continue, save. Painted in the accent. */
    Primary,

    /** Dangerous but reversible: a full-access escalation, a discard. Warning. */
    Warning,

    /** Destructive: force quit, delete. Error. */
    Error,
}

/** What the user did to a confirm dialog. */
sealed interface ConfirmResult {
    object Confirmed : ConfirmResult
    object Canceled : ConfirmResult
}

/** A dialog a caller can open and close imperatively. */
interface DialogHandle {
    val isShown: Boolean

    /** A stable name for logging and tests; `toString()` returns it. */
    val dialogType: String

    fun show()
    fun hide()
}

/** A dialog that also runs work while it is up. */
interface LoadingDialogHandle : DialogHandle {
    /** Shows the dialog, runs [block], and hides it however [block] ends. */
    suspend fun <R> withLoading(block: suspend () -> R): R

    fun showLoading()
}

/** A confirm dialog a caller can open and close imperatively. */
interface ConfirmDialogHandle : DialogHandle {
    val visuals: ConfirmDialogVisuals

    fun showConfirm(
        title: String,
        content: String? = null,
        confirm: String? = null,
        dismiss: String? = null,
    )

    /** Opens the dialog and suspends until a button is pressed. */
    suspend fun awaitConfirm(
        title: String,
        content: String? = null,
        confirm: String? = null,
        dismiss: String? = null,
    ): ConfirmResult
}

typealias NullableCallback = (() -> Unit)?

/**
 * The two callbacks a confirm dialog fires, read lazily.
 *
 * A plain pair of lambdas would be captured into the remembered handle once; providers keep the
 * latest ones reachable through [rememberUpdatedState], so a screen that passes a new `onConfirm`
 * on every recomposition still fires the current one.
 */
interface ConfirmCallback {
    val onConfirm: NullableCallback
    val onDismiss: NullableCallback

    val isEmpty: Boolean get() = onConfirm == null && onDismiss == null

    companion object {
        operator fun invoke(
            onConfirmProvider: () -> NullableCallback,
            onDismissProvider: () -> NullableCallback,
        ): ConfirmCallback = object : ConfirmCallback {
            override val onConfirm: NullableCallback get() = onConfirmProvider()
            override val onDismiss: NullableCallback get() = onDismissProvider()
        }
    }
}

private class LoadingDialogHandleImpl(
    private val visible: MutableState<Boolean>,
    private val coroutineScope: CoroutineScope,
) : LoadingDialogHandle {
    override val isShown: Boolean get() = visible.value
    override val dialogType: String get() = "LoadingDialog"

    override suspend fun <R> withLoading(block: suspend () -> R): R = try {
        show()
        block()
    } finally {
        hide()
    }

    override fun showLoading(): Unit = show()

    override fun show() {
        coroutineScope.launch { visible.value = true }
    }

    override fun hide() {
        coroutineScope.launch { visible.value = false }
    }

    override fun toString(): String = dialogType
}

private class ConfirmDialogHandleImpl(
    private val visible: MutableState<Boolean>,
    private val coroutineScope: CoroutineScope,
    private val callback: ConfirmCallback,
    initialVisuals: ConfirmDialogVisuals = ConfirmDialogVisuals(),
    private val resultFlow: ReceiveChannel<ConfirmResult>,
) : ConfirmDialogHandle {
    private val visualsState = mutableStateOf(initialVisuals)

    override val visuals: ConfirmDialogVisuals get() = visualsState.value
    override val isShown: Boolean get() = visible.value
    override val dialogType: String get() = "ConfirmDialog"

    /**
     * The suspended [ConfirmDialogHandle.awaitConfirm], if there is one.
     *
     * One slot, not a queue: a second concurrent `awaitConfirm` would make "confirmed" ambiguous,
     * and the dialog is a single surface — the second caller would be waiting on a dialog it can no
     * longer see.
     */
    private var awaitContinuation: CancellableContinuation<ConfirmResult>? = null

    private val isCallbackEmpty = callback.isEmpty

    init {
        coroutineScope.launch {
            // The collector lives as long as the handle. Every result closes the dialog first and
            // then notifies: a callback that opens another dialog must not fight this one for the
            // screen. consumeEach rather than a plain for loop, because a bare ReceiveChannel is
            // not iterable without an opt-in this module does not carry.
            resultFlow.consumeEach { result ->
                awaitContinuation?.let { continuation ->
                    awaitContinuation = null
                    if (continuation.isActive) continuation.resume(result)
                }
                visible.value = false
                when (result) {
                    ConfirmResult.Confirmed -> callback.onConfirm?.invoke()
                    ConfirmResult.Canceled -> callback.onDismiss?.invoke()
                }
            }
        }
    }

    private suspend fun awaitResult(): ConfirmResult = suspendCancellableCoroutine { continuation ->
        awaitContinuation = continuation.also {
            // With no callbacks the caller is the only one who can be told anything, so losing the
            // waiter must also take the dialog down; otherwise a cancelled screen leaves a modal
            // card behind that nothing is listening to.
            if (isCallbackEmpty) {
                it.invokeOnCancellation { visible.value = false }
            }
        }
    }

    override fun show() {
        if (visuals.isEmpty) {
            throw UnsupportedOperationException(
                "confirm dialog needs a title or content before it can be shown",
            )
        }
        visible.value = true
    }

    override fun showConfirm(
        title: String,
        content: String?,
        confirm: String?,
        dismiss: String?,
    ) {
        // Set the visuals before making it visible: the host reads them in the same frame the
        // dialog starts animating in, so the previous dialog's text must already be gone.
        visualsState.value = ConfirmDialogVisuals(title, content, confirm, dismiss)
        show()
    }

    override suspend fun awaitConfirm(
        title: String,
        content: String?,
        confirm: String?,
        dismiss: String?,
    ): ConfirmResult {
        showConfirm(title, content, confirm, dismiss)
        return awaitResult()
    }

    fun hideNow() {
        visible.value = false
    }

    override fun hide() {
        coroutineScope.launch { hideNow() }
    }

    override fun toString(): String = "$dialogType(visuals: $visuals)"

    companion object {
        /**
         * Restores only what the user can see: whether it was up, and its text.
         *
         * The scope, the channel and the callbacks are runtime objects, so they are taken from the
         * *current* composition rather than saved — which is why this is a factory taking them, not
         * a serializer over the handle.
         */
        fun saver(
            visible: MutableState<Boolean>,
            coroutineScope: CoroutineScope,
            callback: ConfirmCallback,
            resultChannel: ReceiveChannel<ConfirmResult>,
        ): Saver<ConfirmDialogHandle, Any> = mapSaver<ConfirmDialogHandle>(
            // Only non-null entries: a saver's map holds non-null values, so "absent" and "null"
            // collapse into one meaning — the field was never set.
            save = { handle ->
                buildMap<String, Any> {
                    put("shown", handle.isShown)
                    put("title", handle.visuals.title)
                    handle.visuals.content?.let { put("content", it) }
                    handle.visuals.confirm?.let { put("confirm", it) }
                    handle.visuals.dismiss?.let { put("dismiss", it) }
                }
            },
            restore = { saved ->
                visible.value = saved["shown"] as Boolean
                ConfirmDialogHandleImpl(
                    visible = visible,
                    coroutineScope = coroutineScope,
                    callback = callback,
                    initialVisuals = ConfirmDialogVisuals(
                        title = saved["title"] as String,
                        content = saved["content"] as String?,
                        confirm = saved["confirm"] as String?,
                        dismiss = saved["dismiss"] as String?,
                    ),
                    resultFlow = resultChannel,
                )
            },
        )
    }
}

/**
 * The one dialog surface both hosts draw on.
 *
 * A thin wrapper over miuix's [WindowDialog] rather than a hand-rolled `Dialog` plus scale
 * animation: `WindowDialog` renders at window level (so it needs no `Scaffold.popupHost` — this app
 * does not use miuix's `Scaffold`) and its enter animation *is* the scale-in upstream's contract
 * describes: the content scales from 0.8 to 1 on a spring while a dim fades in over 300ms.
 * Reproducing that by hand would be a second animation that drifts from the first.
 */
@Composable
fun DshDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    WindowDialog(
        show = show,
        // The dialog is a window-level surface, so the system bars are its business too: without
        // this a long body can slide under the status bar on a gesture-navigation phone.
        modifier = modifier.windowInsetsPadding(
            WindowInsets.systemBars.only(WindowInsetsSides.Top),
        ),
        title = title?.takeIf { it.isNotEmpty() },
        onDismissRequest = onDismissRequest,
        content = content,
    )
}

/**
 * The loading dialog: a spinner and one line.
 *
 * [label] is a parameter rather than a string resource because this module ships no resources;
 * `android.R.string.cancel` and `android.R.string.ok` are the only strings it can name itself, and
 * the platform has none for "processing".
 */
@Composable
fun LoadingDialogHost(
    show: Boolean,
    label: String = "Processing",
    modifier: Modifier = Modifier,
) {
    if (!show) return
    val colors = DshTheme.colors
    DshDialog(
        show = true,
        // Not dismissible: the work behind it has not finished, and a tap that hides the spinner
        // would only hide the fact that something is still running.
        onDismissRequest = {},
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                InfiniteProgressIndicator(color = colors.labelPrimary)
                Text(
                    modifier = Modifier.padding(start = DshSpacing.lg),
                    text = label,
                    fontWeight = FontWeight.Medium,
                    color = colors.labelPrimary,
                )
            }
        }
    }
}

/**
 * The confirm dialog: title, optional body, and two buttons pinned under it.
 *
 * [renderContent] is how a caller supplies rich body text — the transcript's markdown renderer, for
 * instance. It is a slot rather than a flag because this module must not depend on the app's
 * renderer, and because a renderer taking a `String` is the whole contract such a caller needs.
 *
 * The body is capped at "whatever is left after the buttons" by the [Layout] below. That is
 * upstream's rule and it is worth keeping: a long change-log in a confirm dialog otherwise pushes
 * the buttons past the bottom of the screen, and the dialog becomes unanswerable.
 */
@Composable
fun ConfirmDialogHost(
    show: Boolean,
    visuals: ConfirmDialogVisuals,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    renderContent: (@Composable (String) -> Unit)? = null,
) {
    val colors = DshTheme.colors
    // Hoisted so the null check inside the content lambda narrows: a parameter of function type
    // does not smart-cast once a composable lambda captures it.
    val renderer = renderContent
    DshDialog(
        show = show,
        onDismissRequest = onDismiss,
        title = visuals.title,
        modifier = modifier,
    ) {
        Layout(
            content = {
                visuals.content?.takeIf { it.isNotEmpty() }?.let { body ->
                    if (renderer != null) {
                        renderer(body)
                    } else {
                        Text(text = body, color = colors.labelPrimary)
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(top = DshSpacing.lg),
                ) {
                    TextButton(
                        text = visuals.dismiss ?: stringResource(android.R.string.cancel),
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColors(),
                    )
                    Spacer(Modifier.width(20.dp))
                    TextButton(
                        text = visuals.confirm ?: stringResource(android.R.string.ok),
                        onClick = onConfirm,
                        enabled = visuals.confirmEnabled,
                        modifier = Modifier.weight(1f),
                        colors = when (visuals.confirmTone) {
                            ConfirmTone.Primary -> ButtonDefaults.textButtonColorsPrimary()
                            ConfirmTone.Warning -> ButtonDefaults.textButtonColors(color = colors.warn)
                            ConfirmTone.Error -> ButtonDefaults.textButtonColors(color = colors.error)
                        },
                    )
                }
            },
        ) { measurables, constraints ->
            // The button row is always the last child; anything before it is body. Measuring the
            // buttons first is what makes the cap below possible — their height is the budget the
            // body has to fit into, not a value discovered after it is already too late.
            if (measurables.size < 2) {
                val buttons = measurables.last().measure(constraints)
                layout(constraints.maxWidth, buttons.height) {
                    buttons.place(0, 0)
                }
            } else {
                val buttons = measurables.last().measure(constraints)
                val body = measurables.first().measure(
                    constraints.copy(
                        maxHeight = (constraints.maxHeight - buttons.height).coerceAtLeast(0),
                    ),
                )
                layout(constraints.maxWidth, body.height + buttons.height) {
                    body.place(0, 0)
                    buttons.place(0, body.height)
                }
            }
        }
    }
}

/** Remembers a handle for [LoadingDialogHost] and draws the dialog. */
@Composable
fun rememberLoadingDialog(label: String = "Processing"): LoadingDialogHandle {
    val visible = remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    LoadingDialogHost(show = visible.value, label = label)
    return remember { LoadingDialogHandleImpl(visible, coroutineScope) }
}

/**
 * Remembers a handle for [ConfirmDialogHost] and draws the dialog.
 *
 * Both the handle and the "is it up" flag are saveable: the flag through [rememberSaveable], the
 * handle through [ConfirmDialogHandleImpl.saver] built from the current composition. `remember`
 * alone would leave a dialog that survived a configuration change with no handle pointing at it, or
 * one whose handle pointed at stale state.
 */
@Composable
private fun rememberConfirmDialogInternal(
    callback: ConfirmCallback,
    renderContent: (@Composable (String) -> Unit)?,
): ConfirmDialogHandle {
    val visible = rememberSaveable { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val resultChannel = remember { Channel<ConfirmResult>(Channel.UNLIMITED) }

    val handle = rememberSaveable(
        saver = ConfirmDialogHandleImpl.saver(visible, coroutineScope, callback, resultChannel),
        init = {
            ConfirmDialogHandleImpl(
                visible = visible,
                coroutineScope = coroutineScope,
                callback = callback,
                resultFlow = resultChannel,
            )
        },
    )

    ConfirmDialogHost(
        show = visible.value,
        visuals = handle.visuals,
        onConfirm = { coroutineScope.launch { resultChannel.send(ConfirmResult.Confirmed) } },
        onDismiss = {
            coroutineScope.launch { resultChannel.send(ConfirmResult.Canceled) }
            // Also close on the way in: the collector above does it too, but an outside tap must
            // feel immediate, and one frame is worth it here.
            visible.value = false
        },
        renderContent = renderContent,
    )

    return handle
}

/** Binds [onConfirm]/[onDismiss] so the remembered handle always calls the latest pair. */
@Composable
fun rememberConfirmCallback(
    onConfirm: NullableCallback,
    onDismiss: NullableCallback,
): ConfirmCallback {
    val currentOnConfirm by rememberUpdatedState(newValue = onConfirm)
    val currentOnDismiss by rememberUpdatedState(newValue = onDismiss)
    return remember { ConfirmCallback({ currentOnConfirm }, { currentOnDismiss }) }
}

/** A confirm dialog for callers that only need callbacks. */
@Composable
fun rememberConfirmDialog(
    onConfirm: NullableCallback = null,
    onDismiss: NullableCallback = null,
    renderContent: (@Composable (String) -> Unit)? = null,
): ConfirmDialogHandle =
    rememberConfirmDialogInternal(rememberConfirmCallback(onConfirm, onDismiss), renderContent)

/** A confirm dialog for callers that build their own [ConfirmCallback]. */
@Composable
fun rememberConfirmDialog(
    callback: ConfirmCallback,
    renderContent: (@Composable (String) -> Unit)? = null,
): ConfirmDialogHandle = rememberConfirmDialogInternal(callback, renderContent)
