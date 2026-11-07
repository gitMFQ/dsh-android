package uk.xa0.dsh.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import uk.xa0.dsh.ui.overlay.ConfirmDialogHost
import uk.xa0.dsh.ui.overlay.ConfirmDialogVisuals

/**
 * The root's one Back-shaped decision: what the drawer's last row asks before it
 * closes the screen.
 *
 * Android's Back is a *stack* gesture and this app's screens are states rather than
 * activities. The surfaces that have something to dismiss consume Back themselves
 * (see `ChatPanel`'s handler, and the drawer's and the sheets' own); what is left is
 * the press with nothing open.
 *
 * For that press this composable deliberately registers **no enabled Back callback
 * at all**. That is the whole point: since Android 13 the system draws the
 * predictive-back animation — the home screen appearing under the finger as the
 * edge gesture progresses — only when the app has not claimed Back with an enabled
 * `OnBackInvokedCallback`. Registering even a callback that immediately calls
 * `moveTaskToBack(true)` makes the platform treat Back as app-handled, and the
 * animation disappears; the screen just jumps to the launcher. Handing Back to the
 * system instead gives the reader the normal, interruptible gesture, and on release
 * the platform finishes the Activity. The process, its client, its event socket,
 * [uk.xa0.dsh.AttentionCenter], the widget's writer and the connection service all
 * outlive the screen, so nothing is lost — the connection service keeps the process
 * alive with no Activity at all, ready to be reopened from Recents.
 *
 * Closing the screen is therefore an explicit act: the drawer's last row asks for
 * it, and [confirming] is the caller's state rather than this composable's. The root
 * owns the dialog because the root owns `finish()`; the drawer only knows that
 * somebody wants to leave. **Close** calls [onClose], and Back, a tap outside or
 * **Stay** all mean "keep the screen open".
 *
 * The one time this *does* register a callback is while [confirming]: Back must
 * then mean Stay, not "leave", and a dialog is a thing the app can dismiss.
 *
 * The label is **Close**, not "Exit". The control finishes the Activity and does
 * nothing else: the process, the connection and the agent carry on, so "Exit" would
 * promise a shutdown it never performs. A transport that really has to be rebuilt is
 * the About page's **Restart connection**, which says so.
 *
 * Composed before the content on purpose: `BackHandler`s run most-recently-registered
 * first, so anything the screen adds is asked before this.
 */
@Composable
fun BackGate(
    confirming: Boolean,
    onDismiss: () -> Unit,
    onClose: () -> Unit,
) {
    // The one place a Back callback is wanted. A disabled callback does not count as
    // an enabled one, so with nothing to confirm the dispatcher reports zero enabled
    // callbacks and the system takes the gesture — including its predictive animation.
    BackHandler(enabled = confirming) { onDismiss() }

    // Not composed at all when there is nothing to confirm. `WindowDialog` (which
    // `ConfirmDialogHost` is built on) installs a window-level navigation-event
    // scope; hiding it with `show = false` alone is not a guarantee that no Back
    // callback is registered, and even one enabled callback would suppress the
    // platform's predictive-back animation. This is the same guard
    // `LoadingDialogHost` uses in design/ui/overlay/Dialog.kt.
    if (confirming) {
        // The vendored dialog rather than Material's `AlertDialog`, so a confirmation
        // is drawn in the same design language as the sheets it sits over. The labels
        // are passed rather than defaulted: `ConfirmDialogHost` falls back to the
        // platform's English "OK"/"Cancel", and a "Close" that turns into "OK" on the
        // one screen where the word is load-bearing would be a regression, not a
        // re-skin.
        ConfirmDialogHost(
            show = true,
            visuals = ConfirmDialogVisuals(
                title = CLOSE_DSH_TITLE,
                content = CLOSE_DSH_BODY,
                confirm = CLOSE_DSH_CONFIRM,
                dismiss = CLOSE_DSH_STAY,
            ),
            onConfirm = onClose,
            // Back, a tap outside, or Stay: all three mean "keep the screen open".
            onDismiss = onDismiss,
        )
    }
}

/**
 * The dialog's words, named so a test can read exactly what is drawn rather than
 * retyping it — and so the one claim that matters is stated once: this closes the
 * screen, and the connection, the process and the agent carry on.
 */
const val CLOSE_DSH_TITLE: String = "Close DSH?"

const val CLOSE_DSH_BODY: String =
    "Only this screen closes. The connection stays open, so the agent keeps " +
        "working and a question waiting for you is not cancelled — reopen to see " +
        "where things got to."

const val CLOSE_DSH_CONFIRM: String = "Close"

const val CLOSE_DSH_STAY: String = "Stay"
