// Vendored from chenaizhang/KernelSU-Style-UI-Kit (GPL-3.0) — see NOTICE.md.
// Upstream path: app/src/main/java/com/example/kernelsustyleuikit/ui/component/liquid/Vibrancy.kt
// Change: package renamed to `uk.xa0.dsh.ui.glass`; no logic touched.
// Adapted from Kyant0/AndroidLiquidGlass — https://github.com/Kyant0/AndroidLiquidGlass (Apache 2.0).
// Mirrored from compose-miuix-ui example.

package uk.xa0.dsh.ui.glass

import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.colorControls

/**
 * The saturation lift the material applies to whatever is behind it.
 *
 * ## This is a runtime shader, and it was the one hiding
 *
 * Measured in miuix-blur 0.9.4's own bytecode: `colorControls` returns early when
 * brightness, contrast and saturation are all identity, and otherwise checks
 * `isRuntimeShaderSupported()` and builds a `RenderEffect` around a compiled AGSL
 * shader. With saturation 1.5 it is never identity — so **every pane in this app was
 * running a runtime shader**, even at the "blur only" level that was added to turn the
 * shaders off. The bisect had a hole in exactly the place it was meant to be sealed.
 *
 * [enabled] closes it: at the BLUR level the colour controls are skipped entirely, so that
 * level really is "backdrop plus Gaussian blur, no AGSL anywhere". The material loses a
 * little colour pop there; it keeps its structure, its blur and its edges.
 */
fun BackdropEffectScope.vibrancy(enabled: Boolean = true) {
    if (!enabled) return
    colorControls(
        brightness = 0f,
        contrast = 1f,
        saturation = 1.5f,
    )
}
