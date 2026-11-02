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
 * The lift a floating glass pane wears: a soft shadow *below* it, never under it.
 *
 * ## Why the offset is the whole point (measured, light appearance)
 *
 * The shadow used to be drawn centred (`offset = DpOffset.Zero`) at a 10dp radius. That is
 * right for an opaque pane and wrong for a translucent one: the shadow lands *inside* the
 * pane's footprint and shows straight through the fill. Measured off a recorded baseline — a
 * light appearance on a `#FFFFFF` page — the capsule's interior sampled **`#F8F8F8`**, which is
 * exactly the arithmetic: a 10%-black shadow over white is `#E6E6E6`, and a 72%-white fill over
 * that is `#F8F8F8`. A grey bar, produced by a shadow that was never meant to be visible there.
 *
 * Pushing it down and shortening its radius moves the shadow's mass out from under the pane, so
 * the middle keeps the fill's own colour and only the lower edge carries the separation. On a
 * dark appearance the same bleed existed and was invisible, which is why it took a light
 * appearance and a pixel probe to find it.
 *
 * One function rather than a copy per bar because the bar and the composer are the same material
 * at two places on one screen; a divergence between them is the kind of thing nobody notices
 * until a screenshot compares the two.
 */
@Composable
fun Modifier.glassLift(shape: Shape): Modifier = dropShadow(
    shape = shape,
    shadow = Shadow(
        radius = 6.dp,
        // Down, not centred: see above.
        offset = DpOffset(0.dp, 4.dp),
        color = Color.Black,
        // Lighter on a light appearance, where a shadow reads as dirt faster than it reads as
        // depth; denser on the dark one, where it is also what separates the pane from a
        // near-black page.
        alpha = if (DshTheme.colors.isDark) 0.22f else 0.12f,
    ),
)

