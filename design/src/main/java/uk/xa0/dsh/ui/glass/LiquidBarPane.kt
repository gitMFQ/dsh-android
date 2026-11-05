// The liquid material shared by the floating bottom bar and the conversation's three
// header seats. See NOTICE.md: this package is one of the two places in :design allowed
// to execute miuix-blur symbols, and every new call site must be gated by
// [currentGlassTier]. Nothing outside :design names a blur type.
package uk.xa0.dsh.ui.glass

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import uk.xa0.dsh.ui.theme.DshTheme

/**
 * The bar's effect chain, in the one place, so a second surface cannot drift from it.
 *
 * This is exactly what `FloatingBottomBarLiquid.kt` used to spell out twice (once for the
 * panel, once for the hidden accent-tinted copy the indicator refracts): a saturation
 * lift, a vanilla 4dp Gaussian blur, then a 24dp lens. The two `enabled` gates are the
 * BLUR-level bisect — at that level `LocalGlassShaderEnabled` is false, so no AGSL
 * shader is built at all. `vibrancy` and `lens` are the shaders; `blur` is the Skia
 * RenderEffect that survives.
 *
 * It is an extension on [BackdropEffectScope] because that is `drawBackdrop`'s effects
 * receiver, and a plain function because it runs at draw time — the shader flag is read
 * once in composition by the caller and passed in, not read per frame.
 *
 * Callers are gated: `FloatingBottomBar.kt` composes `LiquidBottomBar` only at
 * `GlassTier.LIQUID`, and `liquidBarPane` checks the tier itself. That is the
 * NOTICE.md discipline for every new blur execution point.
 */
fun BackdropEffectScope.liquidGlassEffects(shaderEnabled: Boolean) {
    vibrancy(enabled = shaderEnabled)
    // Vanilla Gaussian blur; the lens below is what makes the edge read as refraction
    // rather than as a frosted rectangle.
    blur(4.dp.toPx(), 4.dp.toPx())
    lens(
        refractionHeight = 24.dp.toPx(),
        refractionAmount = 24.dp.toPx(),
        enabled = shaderEnabled,
    )
}

/**
 * The bar's liquid material on a **stable-geometry** surface.
 *
 * ## Why this exists separately from `liquidPane`
 *
 * `LiquidPane.kt` records the measured crash in full: panes that come and go with the
 * transcript re-bind a runtime-shader effect against a layer that is being re-recorded,
 * and die with a native `SIGSEGV` on the RenderThread. That commit deliberately left
 * `backdrop` in the signature "so a future fix for **stable-geometry panes** does not
 * have to re-thread it". This is that fix.
 *
 * The conversation header's three seats are the case the crash was not about: while the
 * conversation page is up they are always composed, fixed 40dp capsules whose geometry
 * never changes, and the header is a `zIndex(1)` sibling *outside* the pager subtree that
 * `glassBarLayer` records — the same position in the graph as the bottom bar. The bar
 * already keeps three `drawBackdrop` nodes on this very `glassBackdrop` layer and has
 * never crashed. Reusing the handle means no second recording, and the seats refract the
 * same transcript the bar does.
 *
 * The one churn left is leaving the Chat destination, which disposes the whole header.
 * That is a user-driven screen change, not a pane appearing and disappearing with the
 * transcript while its layer re-records; `LaunchGuard` (#66) degrades the level on any
 * native crash, and fresh installs start at OFF, so the exposure is bounded.
 *
 * ## Which tiers take it
 *
 * The liquid branch needs a real backdrop (null under Robolectric and on API < 33),
 * the material switch on, panes allowed — so `LiquidGlassLevel.BAR` still means "bar
 * only" — and `currentGlassTier == GlassTier.LIQUID`, which is the NOTICE.md gate.
 * Every other case paints [flatFill] exactly as `liquidPane`'s flat branch does, so
 * the screenshot baselines, which pin `LocalGlassEnabled = false`, stay byte-identical.
 *
 * There is no specular rim: the bar's rim needs the gravity/tilt highlight machinery
 * in `FloatingBottomBarLiquid.kt`. The old liquid `liquidPane` was documented as
 * "the same liquid material the floating bar paints" with the rim deliberately absent
 * for panes, and removing it changed nothing about the crash — the difference was
 * *when* a pane is composed, not what it draws. `Highlight()` has zero width and
 * alpha, so `drawHighlight` returns before touching a shader and the BLUR bisect
 * stays honest.
 */
@Composable
fun Modifier.liquidBarPane(
    backdrop: GlassBarBackdrop?,
    shape: Shape,
    flatFill: Color? = null,
): Modifier {
    val liquid = backdrop != null &&
        LocalGlassEnabled.current &&
        LocalGlassPaneEnabled.current &&
        currentGlassTier == GlassTier.LIQUID
    if (!liquid) {
        // Byte-identical to `liquidPane`'s flat branch: an opaque capsule where one
        // was given, otherwise the fill-plus-hairline a floating surface wears. No
        // blur code is executed on this path.
        return if (flatFill != null) {
            if (flatFill == Color.Transparent) this else this.background(flatFill, shape).clip(shape)
        } else {
            glassSurface(shape)
        }
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
            onDrawSurface = { drawRect(colors.bgLayer1.copy(alpha = LiquidContainerAlpha)) },
        )
        .clip(shape)
}
