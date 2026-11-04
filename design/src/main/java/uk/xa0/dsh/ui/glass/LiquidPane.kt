// Adds a general-purpose pane to the vendored material. See NOTICE.md: this package is
// the only place in the repository allowed to name a miuix-blur symbol, so the generic
// surface lives here and every caller outside `:design` handles only the opaque
// [GlassBarBackdrop] handle — or null, which means "paint the flat tier".
package uk.xa0.dsh.ui.glass

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import uk.xa0.dsh.ui.theme.DshTheme

/**
 * The same liquid material the floating bar paints, as a reusable surface.
 *
 * The bar's [uk.xa0.dsh.ui.nav.LiquidBottomBar] draws the whole panel and its moving
 * highlight in one place; anything *else* that should read as the same sheet of glass —
 * the composer's circle, its expanded bar, the controls above the dock — needs the
 * material without the bar's indicator machinery. Rather than growing a second copy of
 * the blur/lens/vibrancy recipe at each call site (and letting them drift), this is the
 * one function those surfaces call.
 *
 * ## Three tiers, one design
 *
 * [backdrop] is null on a device that cannot sample one (API < 33, or a baseline that
 * pins the flat material). The fallback is [glassSurface] — the fill, alpha and hairline
 * stroke every other floating surface already wears — so the caller's layout is identical
 * on every device and only the fidelity changes. That is the rule
 * `docs/PHASE-3-PLAN.md` §2 states for the bar, applied to the rest of the chrome.
 *
 * The shadow is not here: the caller applies [glassLift] *outside* this surface, so the
 * offset arithmetic lives with the other lift, and a light appearance still declines to
 * draw one.
 *
 * ## The liquid tier has no hairline
 *
 * The three tiers draw the same *design* but not the same *edges*. A flat or translucent
 * pane needs its 0.5dp stroke ([glassSurface]) because there is no other boundary between
 * it and the page. A liquid pane already has one: the lens refracts the content behind it
 * and the blur softens it, so a drawn line on top reads as a sticker outline rather than
 * the rim of a sheet of glass — measured against the reference app, whose liquid surfaces
 * carry no stroke at all, only that refracted rim. So the stroke is the flat tiers'
 * property, not the material's, and it is deliberately absent here.
 *
 * [tint] is an optional wash over the material's own container colour, for the one control
 * that has a state colour of its own (plan mode's accent). It is drawn on the surface
 * layer, under the text, not over it.
 */
@Composable
fun Modifier.liquidPane(
    backdrop: GlassBarBackdrop?,
    shape: Shape,
    tint: Color? = null,
    /**
     * What this surface looked like *before* it had a material, painted on the flat
     * tiers instead of [glassSurface].
     *
     * ## Why this seam exists
     *
     * Not every pane that wants the liquid tier wants `glassSurface`'s flat answer.
     * `glassSurface` is fill-plus-hairline, and that is right for a pane that floats: the
     * stroke is the only boundary between it and the page. But a surface that is already
     * flush with the page — the header, which spans the full width and sits in the layout
     * rather than over it — has no such boundary to draw, and an all-round hairline there
     * puts a 0.5dp line along the screen's top edge and both side edges. That is not a
     * surface, it is a frame.
     *
     * Adopting a material must not silently redraw a flat-tier screen. When a caller
     * passes this, the flat branch paints exactly this fill and **no hairline**, so a
     * capture recorded before the adoption is still byte-identical after it — which is
     * the only way "the liquid tier gained glass" can be stated without also claiming
     * "and every API 26 phone changed appearance".
     *
     * [Color.Transparent] is the honest value for a surface that was simply the page:
     * it says "there was nothing here", not "there was a colour that happened to match".
     */
    flatFill: Color? = null,
): Modifier {
    if (backdrop == null || !LocalGlassEnabled.current) {
        if (flatFill != null) {
            // Exactly what the surface had before it had a material, and nothing else:
            // no fill unless it was given one, no hairline. See the parameter's note.
            val base = if (flatFill == Color.Transparent) {
                this
            } else {
                this.background(flatFill, shape).clip(shape)
            }
            return if (tint == null) base else base.background(tint, shape)
        }
        val base = glassSurface(shape)
        // Flat tiers have no surface layer to tint; the wash still reads as the control's
        // state, so it is painted on top of the tier's own fill.
        return if (tint == null) base else base.background(tint, shape)
    }
    val colors = DshTheme.colors
    // The container tint is deliberately thin: the blur already carries the colour of
    // whatever is behind, so this only has to push the result towards the app's surface.
    // Denser and the transcript stops reading as motion behind the chrome; thinner and
    // labels lose contrast on a light appearance. Same value as the bar's own container.
    val highlight = rememberDiagonalHighlight()
    // Read once per composition, not per frame: the effects lambda runs on every draw and
    // a composition local read inside it would be a snapshot read per frame for a value
    // that only changes when a setting does.
    val shaderEnabled = LocalGlassShaderEnabled.current
    return this
        .drawBackdrop(
            backdrop = backdrop.layer,
            shape = { shape },
            effects = {
                vibrancy(enabled = shaderEnabled)
                // A pane this small needs the vanilla blur rather than a big lens; the
                // refraction is what makes the edge read as glass instead of frosting.
                blur(4.dp.toPx(), 4.dp.toPx())
                lens(
                    refractionHeight = 24.dp.toPx(),
                    refractionAmount = 24.dp.toPx(),
                    enabled = shaderEnabled,
                )
            },
            // The diagonal specular, i.e. the same one the bottom bar wears. Without it
            // every pane but the bar read as a frosted rectangle; see
            // [rememberDiagonalHighlight] for what makes the edge diagonal.
            highlight = { highlight },
            onDrawSurface = {
                drawRect(colors.bgLayer1.copy(alpha = LiquidContainerAlpha))
                if (tint != null) drawRect(tint)
            },
        )
        .clip(shape)
}

/**
 * The default container tint of a liquid pane.
 *
 * 0.4 over the *blur*, not over the wallpaper: the blur has already mixed the content
 * behind the pane, so this only nudges it towards `bgLayer1`. Measured against the
 * transcript, the same way the bar's container was: denser and the text behind it stops
 * registering as motion.
 */
private const val LiquidContainerAlpha = 0.4f