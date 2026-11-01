// Vendored from chenaizhang/KernelSU-Style-UI-Kit (GPL-3.0) — see NOTICE.md.
// Upstream path: app/src/main/java/com/example/kernelsustyleuikit/ui/util/BlurExt.kt (rememberBlurBackdrop) and ui/MainActivity.kt (the inline rememberLayerBackdrop)
// Change: fused into one opaque handle so a caller can wire a backdrop without naming a
// //       miuix-blur type, and gated on a platform capability check, not a bare SDK compare.

package uk.xa0.dsh.ui.glass

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import uk.xa0.dsh.ui.theme.DshTheme

/**
 * A backdrop the bar can sample, handed to callers as an opaque handle.
 *
 * ## Why this type exists at all
 *
 * The bar needs the content behind it to be *recorded* into a layer, and only the caller knows
 * what that content is (a pager, a list, a whole screen). So the caller has to wire it up — but
 * wiring it up with miuix-blur's own types would put a blur symbol in `:app`, and the rule
 * recorded in NOTICE.md and PHASE-3-PLAN §2 is that those names live only in `ui/glass/`. This
 * class is the seam: the caller handles [GlassBarBackdrop] and [glassBarLayer], this file handles
 * [LayerBackdrop], and neither side has to know the other's vocabulary.
 *
 * ## Why it is nullable
 *
 * Because a handle that exists on a device which cannot blur is worse than no handle: every
 * call site downstream would then need its own "…but is this real?" test. Null *is* the answer
 * "this platform paints the flat material", and [glassBarLayer] turns that into a no-op modifier
 * while [uk.xa0.dsh.ui.nav.FloatingBottomBar] turns it into the flat bar.
 */
@Stable
class GlassBarBackdrop internal constructor(
    internal val layer: LayerBackdrop,
)

/**
 * Whether this device can record and sample a backdrop.
 *
 * Two conditions, and the second is the interesting one:
 *
 *  - **API 33+** for the runtime shader that does the refraction. The blur alone would be
 *    available from 31, but a bar that blurs and does not lens is not this design, and the
 *    miuix-blur module itself declares minSdk 33 (see LiquidGlass.kt).
 *  - **[isRenderEffectSupported]** for the platform actually having the render-effect path.
 *    Reading it *instead of* comparing versions is deliberate: it is the capability the
 *    dependency itself asks about, so the two can never disagree about what this device can do.
 *
 * `isRenderEffectSupported` is safe to call on any API level — its holder class declares that
 * one boolean accessor and nothing else, so loading it resolves no API-33 type. That is *not*
 * true of the neighbouring `isRuntimeShaderSupported`, whose holder also declares methods that
 * return `android.graphics.RuntimeShader`; that one is deliberately not used here. Where a
 * runtime shader is actually built — the bar's press highlight — the check is spelled out again
 * locally (see `ui/nav/InteractiveHighlight.kt`) rather than borrowed from this file.
 *
 * ## The one place this can surprise you
 *
 * This is a capability test, not an "is this a test run" test, so under Robolectric at
 * `@Config(sdk = [34])` it answers **true**: the shadowed platform reports SDK 34, and
 * `isRenderEffectSupported()` is nothing but an SDK comparison underneath. A screenshot
 * baseline that goes through [rememberGlassBarBackdrop] therefore *will* take the liquid path
 * unless something stops it — and under Robolectric there is no real RenderEffect to draw with.
 *
 * That is why [rememberGlassBarBackdrop] takes an `enabled` flag. The honest fix is not to
 * teach this function what a test is (it has no business knowing); it is to let the one caller
 * that needs a pinned material say so. The flag is opt-out, defaults to on, and exists for
 * exactly one kind of caller: a baseline that has to be byte-identical on every machine.
 */
fun glassBarSupported(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && isRenderEffectSupported()

/**
 * The backdrop for a glass bar, or null when this device cannot sample one.
 *
 * [enabled] is the opt-out described above: `false` returns null on every device, which is what a
 * screenshot baseline that must not vary by platform passes.
 *
 * The recorded content is the subtree the caller marks with [glassBarLayer]; this lambda paints
 * the app's base colour underneath it first, so the blurred result has an opaque floor instead of
 * compositing against whatever the window happened to show. Without that, a translucent bar over a
 * transparent pager would blur nothing and read as a grey slab.
 *
 * Remembered per appearance: the fill is baked into the recorded layer, so the layer has to be
 * rebuilt when the palette changes — which is what keeps the bar following AMOLED, Monet and the
 * rest of the seven appearances.
 */
@Composable
fun rememberGlassBarBackdrop(enabled: Boolean = true): GlassBarBackdrop? {
    if (!enabled || !glassBarSupported()) return null
    val surfaceColor = DshTheme.colors.bgBase
    val layer = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
    return remember(layer) { GlassBarBackdrop(layer) }
}

/**
 * Marks the subtree the bar samples.
 *
 * A no-op when [backdrop] is null, which is the whole point: the caller writes the same two lines
 * on every device and the platform decides how much of them means anything.
 */
fun Modifier.glassBarLayer(backdrop: GlassBarBackdrop?): Modifier =
    if (backdrop == null) this else this.layerBackdrop(backdrop.layer)
