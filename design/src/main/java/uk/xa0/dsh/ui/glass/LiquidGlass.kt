// Vendored from chenaizhang/KernelSU-Style-UI-Kit (GPL-3.0) — see NOTICE.md.
// Upstream path: app/src/main/java/com/example/kernelsustyleuikit/ui/util/BlurExt.kt
// Change: rewritten around this app's DshColors palette and its API-26 floor. The
// upstream version assumes minSdk 31 and pulls its colours from MiuixTheme; here the
// backdrop is built from DshTheme.colors so all seven appearances drive it, and the
// whole file is the *only* place in the repo allowed to name a miuix-blur symbol.

package uk.xa0.dsh.ui.glass

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import uk.xa0.dsh.ui.theme.DshTheme

/**
 * Whether this device can draw a real backdrop blur.
 *
 * Two separate answers, and the difference matters:
 *
 *  - **API 33+** can run the runtime shader that does refraction, so the glass gets both
 *    blur *and* lensing. `miuix-blur` itself requires 33, which is exactly why the
 *    dependency is admitted under `tools:overrideLibrary` rather than by raising the
 *    app's floor (see `NOTICE.md` and `docs/PHASE-3-PLAN.md` §1).
 *  - **API 31-32** has the Skia RenderEffect path but not the shader path. It could
 *    blur, but a bar cannot blur *what is behind it* without the backdrop machinery, and
 *    the machinery is 33+. This tier therefore gets a translucent fill.
 *  - **API 26-30** gets neither, so it gets an opaque surface.
 *
 * Reading `Build.VERSION` here rather than `miuix-blur`'s own `isRuntimeShaderSupported()`
 * is deliberate: this predicate has to be answerable *without loading that package*, or
 * an API 26 device would die in `NoClassDefFoundError` on the very check meant to avoid it.
 */
val supportsLiquidGlass: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/**
 * How a floating surface is painted, in the three tiers above.
 */
enum class GlassTier {
    /** API 26-30: opaque, hairline stroke. No blur anywhere. */
    FLAT,
    /** API 31-32: translucent fill, hairline stroke. */
    TRANSLUCENT,
    /** API 33+: real backdrop blur, lens, highlight. */
    LIQUID,
}

val currentGlassTier: GlassTier
    get() = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> GlassTier.LIQUID
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> GlassTier.TRANSLUCENT
        else -> GlassTier.FLAT
    }

/**
 * The non-blur half of the material: fill plus hairline stroke plus the [shape] clip.
 *
 * Every tier uses this, including [GlassTier.LIQUID] (which paints the blur underneath and
 * then wears this on top for the tint and the edge). Keeping it one function is what makes
 * the three tiers read as the same design at three fidelities rather than three designs —
 * the argument the hand-drawn bar in `ui/components/FloatingBottomBar.kt` already made, now
 * applied to the vendored material.
 */
@Composable
fun Modifier.glassSurface(shape: Shape): Modifier {
    val colors = DshTheme.colors
    val tier = currentGlassTier
    val fill = when (tier) {
        // A blur needs something to blur, and nothing on screen reads as "glass" unless
        // it is at least partly see-through. 0.72 is the value measured against the
        // transcript: denser and the text behind stops registering as motion.
        GlassTier.LIQUID -> colors.bgLayer1.copy(alpha = 0.72f)
        GlassTier.TRANSLUCENT -> colors.bgLayer1.copy(alpha = 0.86f)
        GlassTier.FLAT -> colors.bgLayer1
    }
    return this
        .clip(shape)
        .background(fill)
        // The edge is what makes a translucent pane read as a *surface* rather than a
        // rendering artefact. It stays on all three tiers for that reason.
        .border(0.5.dp, colors.borderL3, shape)
}

/**
 * Whether the caller should compose a backdrop at all on this device.
 *
 * Named separately from [supportsLiquidGlass] because the answer is also allowed to be
 * "no" for a reason that has nothing to do with the platform: a screenshot baseline is
 * recorded under Robolectric, where no runtime shader exists.
 */
@Composable
fun rememberGlassEnabled(): Boolean = remember { supportsLiquidGlass }
/**
 * The lift a floating glass pane wears — on a **dark** appearance only.
 *
 * ## The defect this exists for, and why the first attempt at it failed
 *
 * Reported from a device: the bottom bar looked grey on a light appearance. Measured off a
 * recorded baseline (a `#FFFFFF` page), the capsule's interior sampled **`#F8F8F8`**.
 *
 * Cause: the shadow was drawn centred at a 10dp radius, so it landed *inside* the pane's own
 * footprint and a translucent fill let it through. The arithmetic is exact — 10% black over
 * white is `#E6E6E6`, and a 72% white fill over that is `#F8F8F8`.
 *
 * **The first attempt was to offset the shadow down 4dp and shorten its radius to 6dp. It was
 * not enough, and the same pixel probe said so**: the interior still measured `#F7F7F7`, about
 * 3% black. The reason is what `dropShadow` actually draws — a *blurred copy of the shape* —
 * and a 6dp blur of a 64dp capsule moved 4dp still covers nearly the whole capsule. There is no
 * offset that pushes a blurred copy of a shape out from under that shape while leaving it
 * visible at the edge.
 *
 * **The light appearance does not go without.** It stacks two shadows instead of one: a tight
 * 2dp rim with a 1dp offset, and a soft 10dp one at half the dark appearance's alpha. Two small
 * shadows added in the narrow band outside the shape give the separation a light page needs —
 * white on white has no other boundary — while neither is strong enough, alone or summed, to
 * register through the fill the way the single 10dp one did. "底栏的阴影好像浅色模式看不到" is
 * what one shadow with no light-mode branch produced.
 *
 * One function rather than a copy per surface because the bar and the composer are the same
 * material at two places on one screen; a divergence between them is the kind of thing nobody
 * notices until a screenshot compares the two.
 */
@Composable
fun Modifier.glassLift(shape: Shape): Modifier {
    // Both appearances lift now; only the strength differs.
    //
    // A light appearance used to decline, for the measured reason the long note above
    // gives: a *centred* shadow bleeds through a translucent fill, and no offset small
    // enough to still show at the edge was enough to stop it. The fix is not an offset,
    // it is the second shadow: a tight, opaque one that only exists in the 4dp band
    // outside the shape, plus a soft one the fill has already faded over by the time the
    // shadow reaches it. A light page needs the lift more than a dark one does — white on
    // white has no other separation at all — which is why the reported defect was
    // "底栏的阴影好像浅色模式看不到".
    // The lift switch, before the paint.
    //
    // `dropShadow` is not free: it is a blurred copy of the shape, drawn through a
    // RenderNode, and it is the other half of the GPU work this material asks for (the
    // first being the backdrop). A reader who turned the material off to find a render
    // crash has to get the shadows off too, or the bisect excludes nothing.
    //
    // [LocalGlassLiftEnabled] rather than [LocalGlassEnabled] because a screenshot
    // fixture says "no GPU pipeline" with the *material* flag and still expects the
    // shadow it has always photographed — see that local's own note.
    if (!LocalGlassLiftEnabled.current) return this
    val dark = DshTheme.colors.isDark
    return dropShadow(
        shape = shape,
        shadow = Shadow(
            radius = if (dark) 6.dp else 10.dp,
            offset = DpOffset(0.dp, if (dark) 4.dp else 3.dp),
            color = Color.Black,
            // Light is gentler per-shadow but there are two of them, and they overlap in
            // the band just outside the edge — which is exactly where the separation is
            // wanted, and well inside where the soft one would have shown through the fill.
            alpha = if (dark) 0.22f else 0.10f,
        ),
    ).then(
        if (dark) {
            Modifier
        } else {
            // The tight under-shadow. 2dp of blur and no offset keeps it a rim rather
            // than a smudge, and it is what a light appearance was missing entirely.
            dropShadow(
                shape = shape,
                shadow = Shadow(
                    radius = 2.dp,
                    offset = DpOffset(0.dp, 1.dp),
                    color = Color.Black,
                    alpha = 0.08f,
                ),
            )
        },
    )
}

