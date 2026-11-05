// Adds a general-purpose pane to the vendored material. See NOTICE.md: this package is
// the only place in the repository allowed to name a miuix-blur symbol, so the generic
// surface lives here and every caller outside `:design` handles only the opaque
// [GlassBarBackdrop] handle — or null, which means "paint the flat tier".
package uk.xa0.dsh.ui.glass

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import uk.xa0.dsh.ui.theme.DshTheme

@Composable
fun Modifier.liquidPane(
    backdrop: GlassBarBackdrop?,
    shape: Shape,
    tint: Color? = null,
    flatFill: Color? = null,
): Modifier {
    // The pane wears the **flat** material, on every device and at every level.
    //
    // ## Why the panes stopped sampling a backdrop
    //
    // Measured on the device, level by level: the bar with the full material (backdrop,
    // AGSL blur, lens, rim, lift) runs; the panes with the same material die on the
    // RenderThread a few seconds after the transcript renders. Taking the rim away did not
    // help, and at the BLUR level the two effect chains are otherwise identical — so the
    // difference is not *what* is drawn, it is *when*.
    //
    // The bar is a fixed-size capsule: one DrawBackdropNode, one GraphicsLayer, geometry
    // that never changes. A pane is composed the moment its card appears — the To-dos card
    // when the agent first writes a list, the goal bar when a goal is set, the queue when
    // something is queued — and removed again the same way. Each appearance re-binds a
    // runtime-shader effect against a layer that is being re-recorded, and the failure is
    // native: SIGSEGV on the RenderThread, no Java frame, no tombstone on this ROM.
    //
    // 8ee8983 learned half of this already: it moved the To-dos card *out of the pager*
    // because it was sampling a layer it was painted into. That fixed which layer it
    // samples; it did not fix that the card appears and disappears while the layer is
    // live. The only shape that does is not sampling at all.
    //
    // What is lost is the blur behind a pane. What is kept is the fill, the hairline, the
    // lift and the layout — the same flat tier every API 26-32 device draws, which the
    // screenshot baselines already prove looks like this app rather than like a fallback.
    // The bar keeps the liquid material, where it has never crashed.
    //
    // [backdrop] is still accepted so every call site keeps its signature. A
    // *stable-geometry* surface can now opt into the real material with `liquidBarPane`
    // (see LiquidBarPane.kt); the panes here stay flat because they appear and disappear
    // with the transcript, which is the measured crash.
    //
    // [flatFill] is honoured exactly as before: a surface that was flush with the page
    // paints its old fill and no hairline, so a capture recorded before the adoption is
    // still byte-identical after it.
    if (flatFill != null) {
        val base = if (flatFill == Color.Transparent) {
            this
        } else {
            this.background(flatFill, shape).clip(shape)
        }
        return if (tint == null) base else base.background(tint, shape)
    }
    val base = glassSurface(shape)
    return if (tint == null) base else base.background(tint, shape)
}

/**
 * The default container tint of the liquid material, shared with `liquidBarPane`.
 *
 * 0.4 over the *blur*, not over the wallpaper: the blur has already mixed the content
 * behind the pane, so this only nudges it towards `bgLayer1`. Measured against the
 * transcript, the same way the bar's container was: denser and the text behind it stops
 * registering as motion.
 */
internal const val LiquidContainerAlpha = 0.4f