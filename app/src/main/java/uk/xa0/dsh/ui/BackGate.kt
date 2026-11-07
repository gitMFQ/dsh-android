package uk.xa0.dsh.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import uk.xa0.dsh.ui.overlay.ConfirmDialogHost
import uk.xa0.dsh.ui.overlay.ConfirmDialogVisuals

/**
 * The root's two Back-shaped decisions: what a press with nothing left to dismiss
 * does, and what the drawer's last row asks before it closes the screen.
 *
 * Android's Back is a *stack* gesture and this app's screens are states rather than
 * activities, so a press with nothing open used to finish the Activity outright —
 * including from a chat whose Files panel was open, which is the press a reader most
 * often means as "close this". Those surfaces consume Back themselves (see
 * `ChatPanel`'s handler, and the drawer's and the sheets' own); what reaches here is
 * only the press with nothing left to dismiss.
 *
 * That press now sends the task to the background, the way Back behaves in every
 * other app on the phone: the reader lands on the home screen and the session, the
 * client and the connection service keep running behind it, ready to be reopened from
 * Recents. It deliberately no longer asks first. A dialog on the Back gesture made the
 * commonest way out of an app a two-step one, and it guarded `finish()` — which is not
 * what Back means here anyway: [uk.xa0.dsh.DshApplication]'s process-wide state, the
 * client, its event socket, [uk.xa0.dsh.AttentionCenter] and the widget's writer all
 * outlive the screen, and the connection service keeps the process alive with no
 * Activity at all.
 *
 * Closing the screen is therefore an explicit act: the drawer's last row asks for it,
 * and [confirming] is the caller's state rather than this composable's. The root owns
 * the dialog because the root owns `finish()`; the drawer only knows that somebody
 * wants to leave. **Close** calls [onClose], and Back, a tap outside or **Stay** all
 * mean "keep the screen open".
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
    onBackground: () -> Unit,
    onDismiss: () -> Unit,
    onClose: () -> Unit,
) {
    // Back with nothing left to dismiss. Not a dialog: this is the gesture every other
    // app on the phone answers by leaving the task in the background, and it is
    // reversible in one tap from Recents.
    //
    // While the confirmation is up, Back means "keep the screen open", not "leave":
    // the check is here as well as on the dialog's own back handling because that
    // handling lives in the dialog window's dispatcher, and a stray press that reaches
    // the Activity's must not background the task out from under the question.
    BackHandler(enabled = true) {
        if (confirming) onDismiss() else onBackground()
    }

    // The vendored dialog rather than Material's `AlertDialog`, so a confirmation is
    // drawn in the same design language as the sheets it sits over. The labels are
    // passed rather than defaulted: `ConfirmDialogHost` falls back to the platform's
    // English "OK"/"Cancel", and a "Close" that turns into "OK" on the one screen where
    // the word is load-bearing would be a regression, not a re-skin.
    ConfirmDialogHost(
        show = confirming,
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
