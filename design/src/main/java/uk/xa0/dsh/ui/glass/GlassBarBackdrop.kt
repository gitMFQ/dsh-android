// Vendored from chenaizhang/KernelSU-Style-UI-Kit (GPL-3.0) — see NOTICE.md.
// Upstream path: app/src/main/java/com/example/kernelsustyleuikit/ui/util/BlurExt.kt (rememberBlurBackdrop) and ui/MainActivity.kt (the inline rememberLayerBackdrop)
// Change: fused into one opaque handle so a caller can wire a backdrop without naming a
// //       miuix-blur type, and gated on a platform capability check, not a bare SDK compare.

package uk.xa0.dsh.ui.glass

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
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
/**
 * Whether glass is painted at all, for a surface that cannot paint it.
 *
 * The precedent is [uk.xa0.dsh.ui.components.LocalAnimatedDots], and the reason is the
 * same one: Robolectric reports the capacity (see [glassBarSupported]) but has no GPU
 * pipeline behind it, so the recording of a layer there does not fail loudly — it fails
 * *at composition*, which takes the whole screen down with it. Measured, not assumed:
 * with glass on, `chat-hero` and `chat-transcript` died inside `setContent` the moment
 * this wiring landed.
 *
 * So the switch belongs to the *surface*, not to the capability test — that function has
 * no business knowing what a test is, and a caller that cannot record a layer is not the
 * same thing as a platform that cannot blur. Defaults to on; only a baseline turns it off.
 */
val LocalGlassEnabled = compositionLocalOf { true }

/**
 * Whether the floating panes may draw their **lift** — the `dropShadow` stack.
 *
 * ## Why this is not the same switch as [LocalGlassEnabled]
 *
 * The two answer different questions, and the difference was measured rather than
 * reasoned about: folding the shadow into [LocalGlassEnabled] changed **twelve** recorded
 * baselines, because every screenshot fixture passes `LocalGlassEnabled = false` to mean
 * "no GPU pipeline here" — and the shadow is drawn by Compose in software, so it had
 * always survived that flag. The baselines were right; the flag was being asked a
 * question it does not answer.
 *
 * So: [LocalGlassEnabled] is the *material* (backdrop, blur, lens, specular rim), and
 * this is the *lift*. A device that cannot sample a backdrop still gets its shadows —
 * that is the flat and translucent tiers, and they have always looked that way. A reader
 * who turns the material off to find a render crash gets both off, because a bisect that
 * left half the RenderNodes in place would exclude nothing.
 *
 * Defaults to on, and the only caller that ever says otherwise is the app's own setting.
 */
val LocalGlassLiftEnabled = compositionLocalOf { true }

/**
 * Whether the panes may draw their **AGSL runtime shaders** — the lens and the specular rim.
 *
 * ## Why this is a third flag
 *
 * Because the two shader call sites are the only place this app hands a driver a compiled
 * fragment shader to run against a recorded layer, and that is a different class of request
 * from "blur this". A device can blur happily and still die the moment a `RuntimeShader`
 * is bound — the crash on the test handset was a SIGSEGV on the RenderThread with nothing
 * in the Java stack, which is what a driver fault looks like from up here.
 *
 * So the material has three independently switchable layers, and the settings page exposes
 * them as one ordered choice:
 *
 *  - backdrop + blur            → [LocalGlassEnabled]
 *  - AGSL lens and rim          → this local
 *  - the `dropShadow` lift      → [LocalGlassLiftEnabled]
 *
 * A reader whose app dies on the full material can step down one layer at a time and the
 * answer says which layer to fix. Defaults to on: a device that has never crashed should
 * never see the difference.
 */
val LocalGlassShaderEnabled = compositionLocalOf { true }

@Composable
fun rememberGlassBarBackdrop(enabled: Boolean = true): GlassBarBackdrop? {
    if (!enabled || !LocalGlassEnabled.current || !glassBarSupported()) return null
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
