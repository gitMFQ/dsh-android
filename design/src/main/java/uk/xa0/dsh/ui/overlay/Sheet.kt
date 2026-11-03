// Adds a window-level sheet to the vendored material. See NOTICE.md. This file is the
// `:design` seam for it, the same shape `Dialog.kt` gives the dialog: `:app` calls
// [DshSheet] and never names a `miuix.kmp.window` type.
package uk.xa0.dsh.ui.overlay

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import uk.xa0.dsh.ui.theme.DshRadius
import uk.xa0.dsh.ui.theme.DshTheme

/**
 * The bottom sheet, as the vendored kit draws one.
 *
 * ## Why this exists rather than the Material sheet
 *
 * Material's `ModalBottomSheet` was the one overlay surface this app had not
 * re-skinned, so a screen built from vendored cards opened a sheet drawn in a
 * different design language — its own corner radius, its own drag handle, its own
 * dim. This is the same sheet in the shell's own vocabulary.
 *
 * ## The trap that made this look impossible, and the way through it
 *
 * `docs/PHASE-2-PLAN.md` §2.6 warned that `OverlayBottomSheet` and `OverlayDialog`
 * **must hang under a Miuix `Scaffold.popupHost`**, which this app does not have:
 * those two draw through `MiuixPopupUtils`'s own host, so adopting them would mean
 * bringing in a miuix scaffold and re-parenting every screen. That is why the ported
 * `Dialog.kt` chose `WindowDialog` instead.
 *
 * `WindowBottomSheet` is the third option and the right one. It is **window level** —
 * a platform `Dialog` with a bottom-anchored surface — so it needs no popup host, and
 * it keeps miuix's drag handle, corner radius and dim animation. Checked by
 * decompiling the artifact rather than from release notes: `WindowBottomSheetKt`
 * references `androidx.compose.ui.window.Dialog`, while `OverlayBottomSheetKt`
 * references `MiuixPopupUtils`.
 *
 * ## `show` is not a parameter here
 *
 * Every call site in this app composes its sheet **conditionally** — the sheet's
 * presence *is* the state — so this always draws, exactly as `ModalBottomSheet` did
 * from those same call sites. Hoisting a `show` flag into all seven for an exit
 * animation is a change to seven call sites and their state; it belongs to a step
 * about *that*, not to the re-skin.
 *
 * ## The body is the caller's
 *
 * No padding is applied here. Every existing sheet pads its own elements, and the
 * app's page inset is 16dp — adding a second inset would indent the whole sheet by
 * 32dp and shift every row.
 */
@Composable
fun DshSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable () -> Unit,
) {
    val colors = DshTheme.colors
    WindowBottomSheet(
        show = true,
        onDismissRequest = onDismissRequest,
        modifier = modifier.fillMaxWidth(),
        title = title,
        backgroundColor = colors.bgBase,
        // The shell's own panel radius, so a sheet and a card are the same family.
        cornerRadius = DshRadius.panel,
        // A dim behind it: a sheet over the conversation has to read as *over* it, and
        // without the dim the flat tier loses the separation the material gives.
        enableWindowDim = true,
    ) {
        content()
    }
}