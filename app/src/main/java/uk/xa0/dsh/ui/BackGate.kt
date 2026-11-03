package uk.xa0.dsh.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import uk.xa0.dsh.ui.overlay.ConfirmDialogHost
import uk.xa0.dsh.ui.overlay.ConfirmDialogVisuals

/**
 * The last line of defence against a Back that was not meant to close the screen.
 *
 * Android's Back is a *stack* gesture and this app's screens are states rather than
 * activities, so a press with nothing open used to finish the Activity outright —
 * including from a chat whose Files panel was open, which is the press a reader
 * most often means as "close this". Those surfaces consume Back themselves (see
 * `ChatScreen`'s handlers, and the drawer's and sheets' own); what reaches here is
 * only the press with nothing left to dismiss.
 *
 * That press asks, in a dialog. Closing the screen is not undoable, so a second
 * stray press is not allowed to do it by accident: Back while the dialog is up
 * dismisses it — pressing Back twice in a row therefore cancels — and only the
 * dialog's own **Close** closes.
 *
 * The label is **Close**, not "Exit". The control finishes the Activity and does
 * nothing else: [uk.xa0.dsh.DshApplication]'s process-wide state — the client, its
 * event socket, [uk.xa0.dsh.AttentionCenter] and the widget's writer — outlives the
 * screen, and the connection service keeps the process alive with no Activity at
 * all. "Exit" read as though it stopped that, and a wedged connection survived it,
 * so the word promised something the button never did. A transport that really has
 * to be rebuilt is the About page's **Restart connection**, which says so.
 *
 * Composed before the content on purpose: `BackHandler`s run most-recently-registered
 * first, so anything the screen adds is asked before this.
 */
@Composable
fun BackGate(onClose: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = true) { confirming = true }

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
        onConfirm = {
            confirming = false
            onClose()
        },
        // Back, a tap outside, or Stay: all three mean "keep the screen open".
        onDismiss = { confirming = false },
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
