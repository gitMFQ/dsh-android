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
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import uk.xa0.dsh.ui.theme.DshTheme

/**
 * A pane of the shell's glass: the liquid material on API 33+ when the setting allows it,
 * the flat material everywhere else.
 *
 * ## The flat branch is the screenshot contract
 *
 * [flatFill] reproduces a surface's old fill exactly and the no-[flatFill] branch is
 * [glassSurface]. Both are byte-identical to what this function drew before the liquid
 * branch came back, so the recorded baselines — which pin `LocalGlassEnabled = false` —
 * still pass with zero changed screenshots. A surface that used to sit flush with the page
 * passes its old colour as [flatFill]; at the liquid tier that fill is *replaced* by the
 * blur rather than painted underneath it.
 *
 * ## Why the liquid branch came back, and what the risk is
 *
 * `77fb9de` took the material off every pane after a measured `SIGSEGV (SEGV_MAPERR)` on
 * the RenderThread: a pane is composed the moment its card appears and disposed when it
 * goes, and each appearance re-binds a runtime-shader effect against a layer that is being
 * re-recorded. The bar — fixed geometry, always composed — never had that problem, which is
 * why it kept the material (`liquidBarPane`, #68).
 *
 * The rollout asked for the material on every surface anyway, with the degradation net from
 * #66 as the answer to that crash rather than a switch that keeps panes flat: a fresh install
 * starts at OFF, `LiquidGlassLevel.BAR` turns off `LocalGlassPaneEnabled`, and
 * `LaunchGuard` steps the level down on any native crash. The exposure is opt-in and
 * self-limiting; the measured crash is accepted, not denied.
 *
 * ## The gate
 *
 * Liquid needs a real backdrop (null on API < 33 and under Robolectric), the material on,
 * panes allowed, and [currentGlassTier] == [GlassTier.LIQUID] — the NOTICE.md rule that
 * every new blur execution point is tier-gated. All blur code lives in this package; no
 * caller outside `:design` names a miuix-blur type. The effect chain is the bar's own
 * [liquidGlassEffects], so a pane cannot drift from it. There is no specular rim: the bar's
 * rim needs the gravity/tilt machinery in `FloatingBottomBarLiquid.kt`, and taking the rim
 * away changed nothing about the crash.
 */
@Composable
fun Modifier.liquidPane(
    backdrop: GlassBarBackdrop?,
    shape: Shape,
    tint: Color? = null,
    flatFill: Color? = null,
): Modifier {
    val liquid = backdrop != null &&
        LocalGlassEnabled.current &&
        LocalGlassPaneEnabled.current &&
        currentGlassTier == GlassTier.LIQUID
    if (!liquid) {
        // Byte-identical to the old flat-only body, including the tint layer.
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
    val colors = DshTheme.colors
    // Read once in composition, not in the per-frame effects lambda.
    val shaderEnabled = LocalGlassShaderEnabled.current
    val noHighlight = remember { Highlight() }
    return this
        .drawBackdrop(
            backdrop = backdrop.layer,
            shape = { shape },
            effects = { liquidGlassEffects(shaderEnabled) },
            highlight = { noHighlight },
            onDrawSurface = {
                drawRect(colors.bgLayer1.copy(alpha = LiquidContainerAlpha))
                if (tint != null) drawRect(tint)
            },
        )
        .clip(shape)
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
