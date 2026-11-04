// Vendored from chenaizhang/KernelSU-Style-UI-Kit (GPL-3.0) — see NOTICE.md.
// Upstream path: app/src/main/java/com/example/kernelsustyleuikit/ui/component/FloatingBottomBar.kt
// Change: split out of the bar so every miuix-blur symbol lives in one file reachable only when
// //       GlassTier is LIQUID; colours from DshTheme; the blur on/off flag is gone (the tier is
// //       the flag). Motion and geometry are unchanged.

package uk.xa0.dsh.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.yukonga.miuix.kmp.blur.highlight.LightSource
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.sensor.rememberDeviceTilt
import uk.xa0.dsh.ui.glass.InnerShadow
import uk.xa0.dsh.ui.glass.LocalGlassShaderEnabled
import uk.xa0.dsh.ui.glass.glassLift
import uk.xa0.dsh.ui.glass.innerShadow
import uk.xa0.dsh.ui.glass.lens
import uk.xa0.dsh.ui.glass.rememberCombinedBackdrop
import uk.xa0.dsh.ui.glass.vibrancy
import uk.xa0.dsh.ui.theme.DshTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

private val iosIndicatorSpecular: Highlight = Highlight(
    width = 1.dp,
    alpha = 1f,
    style = BloomStroke(
        color = Color.White.copy(alpha = 0.12f),
        innerBlurRadius = 2.0.dp,
        primaryLight = LightSource(
            position = LightPosition(0.5f, -0.3f, -0.05f),
            color = Color.White,
            intensity = 1f,
        ),
        secondaryLight = LightSource(
            position = LightPosition(0.5f, 0.8f, -0.5f),
            color = Color.White,
            intensity = 0.4f,
        ),
        dualPeak = true,
    ),
)

// Mirrors miuix-blur HighlightStyle's LIGHT_REF — keep in sync.
private const val LIGHT_REF_X = 0.5f
private const val LIGHT_REF_Y = 0.7f
private const val GRAVITY_DIR_THRESHOLD_SQ = 0.01f // |g_xy| > 0.1, ≈ 6° tilt

/**
 * Tracks gravity for a `dualPeak` highlight's primary light, with an extra UV-clockwise offset.
 *
 * The specular edge is the one part of the material that cannot be faked from a gradient: it has
 * to move with the phone, because that is the only cue that says "this pane is glass and the light
 * is above it". Below the tilt threshold the light is pinned straight up rather than following a
 * noisy accelerometer, or the edge would jitter while the phone lies flat on a table.
 */
@Composable
private fun rememberGravityRotatedHighlight(
    base: Highlight,
    extraDegrees: Float = 0f,
): Highlight {
    val baseStyle = base.style as BloomStroke
    val tilt by rememberDeviceTilt()
    val rotatedPrimary = remember(tilt, baseStyle.primaryLight, extraDegrees) {
        val basePrimary = baseStyle.primaryLight
        val gx = tilt.gravityX
        val gy = tilt.gravityY
        val gMagSq = gx * gx + gy * gy
        val (lx0, ly0) = if (gMagSq > GRAVITY_DIR_THRESHOLD_SQ) {
            val invMag = 1f / sqrt(gMagSq)
            (gx * invMag) to (gy * invMag)
        } else {
            0f to -1f
        }
        val rad = extraDegrees * PI / 180.0
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        val lx = c * lx0 - s * ly0
        val ly = s * lx0 + c * ly0
        basePrimary.copy(
            position = LightPosition(
                x = LIGHT_REF_X + lx,
                y = LIGHT_REF_Y + ly,
                z = basePrimary.position.z,
            ),
        )
    }
    return remember(base, rotatedPrimary) {
        base.copy(style = baseStyle.copy(primaryLight = rotatedPrimary))
    }
}

/**
 * The API 33+ rendering: a real backdrop blur with refraction, a specular edge and a lensing
 * highlight that follows the press.
 *
 * Called only from [FloatingBottomBar] after it has established that
 * `currentGlassTier == GlassTier.LIQUID`. That check is the *only* thing keeping this file off an
 * API 26 device's execution path, which is why the file exists separately: an unreachable
 * `if` in a shared file still leaves the miuix-blur class names in that file's constant pool,
 * and loading them is what LiquidGlass.kt's long comment says must never happen below 33.
 *
 * The three layers, bottom to top, and why each is drawn twice:
 *
 *  1. **The panel** — blur + lens + vibrancy, with the container tint painted on its surface.
 *  2. **A hidden copy of the row**, tinted to the accent and captured into `tabsBackdrop`
 *     ([layerBackdrop]). This is what the highlight lens refracts: the indicator must bend the
 *     *icons*, not the wallpaper behind them, and a backdrop can only sample what was recorded
 *     into it on a previous frame.
 *  3. **The indicator** — a pill that lenses the combined backdrop and draws an inner shadow that
 *     grows with press progress, so the tab looks pressed into the glass.
 */
@Composable
internal fun LiquidBottomBar(
    modifier: Modifier,
    selectedIndex: () -> Int,
    onSelected: (Int) -> Unit,
    tabsCount: Int,
    backdrop: Backdrop,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = DshTheme.colors
    val isInDark = colors.isDark
    // One read for the whole bar: the effects lambdas below run per frame, and this is the
    // flag that keeps every AGSL shader in this file off at the BLUR level. See
    // LocalGlassShaderEnabled for why the shader is its own layer of the material.
    val shaderEnabled = LocalGlassShaderEnabled.current
    val pillShape = remember { CircleShape }
    val accentColor = colors.accent
    // 0.4 over the blur, not over the wallpaper: the blur already carries the colour of what is
    // behind, so the tint only has to push it towards this app's surface. Denser and the
    // transcript stops reading as motion behind the bar; thinner and the labels lose contrast
    // on a light appearance.
    val containerColor = colors.bgLayer1.copy(alpha = 0.4f)

    val tabsBackdrop = rememberLayerBackdrop()
    val state = rememberFloatingBarState(tabsCount, selectedIndex, onSelected)

    val interactiveHighlight = remember(state.animationScope, state.tabWidthPx) {
        InteractiveHighlight(
            animationScope = state.animationScope,
            position = { size, _ ->
                Offset(
                    if (state.isLtr) {
                        (state.damped.value + 0.5f) * state.tabWidthPx + state.panelOffset
                    } else {
                        size.width - (state.damped.value + 0.5f) * state.tabWidthPx + state.panelOffset
                    },
                    size.height / 2f
                )
            }
        )
    }

    val baseHighlight = rememberGravityRotatedHighlight(iosIndicatorSpecular, extraDegrees = -45f)
    val pillHighlight = rememberGravityRotatedHighlight(iosIndicatorSpecular, extraDegrees = 90f)
    // The "no rim" value, built once. `Highlight()` is width 0 and alpha 0 by
    // construction, and miuix's own `drawHighlight` returns before touching a shader when
    // either is non-positive — so this keeps the call site and guarantees no RuntimeShader
    // is created for it. That is what makes the BLUR level an honest bisect.
    val noHighlight = remember { Highlight() }

    val combinedBackdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop)

    Box(
        modifier = modifier.width(IntrinsicSize.Min),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            Modifier
                .onGloballyPositioned { coords -> state.onBarMeasured(coords.size.width) }
                .graphicsLayer { translationX = state.panelOffset }
                // Same lift as the flat bar's, for the same measured reason: a centred
                // shadow under a translucent pane shows through it. `glassLift` carries the
                // arithmetic and the offset.
                .glassLift(pillShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { pillShape },
                    effects = {
                        vibrancy()
                        // Vanilla Gaussian blur; the lens below is what makes the edge read as
                        // refraction rather than as a frosted rectangle.
                        blur(4.dp.toPx(), 4.dp.toPx())
                        lens(
                            refractionHeight = 24.dp.toPx(),
                            refractionAmount = 24.dp.toPx(),
                            enabled = shaderEnabled,
                        )
                    },
                    highlight = {
                        if (shaderEnabled) baseHighlight.copy(alpha = 0.75f) else noHighlight
                    },
                    layerBlock = {
                        // The whole panel swells slightly while a tab is held. Scaled by the
                        // panel's own width so a wide tablet does not get a bigger bulge than a
                        // phone for the same 16dp.
                        val width = size.width.coerceAtLeast(1f)
                        val s = lerp(1f, 1f + 16.dp.toPx() / width, state.damped.pressProgress)
                        scaleX = s
                        scaleY = s
                    },
                    onDrawSurface = { drawRect(containerColor) },
                )
                .then(interactiveHighlight.modifier)
                .height(BarHeight)
                .padding(BarInset),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )

        // The hidden, accent-tinted copy that the indicator refracts. Alpha 0 so it is never
        // seen directly — only through the layer it records into.
        CompositionLocalProvider(
            LocalFloatingBottomBarTabScale provides {
                lerp(1f, 1.2f, state.damped.pressProgress)
            }
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = state.panelOffset }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { pillShape },
                        effects = {
                            vibrancy()
                            blur(4.dp.toPx(), 4.dp.toPx())
                            lens(
                                refractionHeight = 24.dp.toPx(),
                                refractionAmount = 24.dp.toPx(),
                                enabled = shaderEnabled,
                            )
                        },
                        onDrawSurface = { drawRect(containerColor) },
                    )
                    .then(interactiveHighlight.modifier)
                    .height(PillHeight)
                    .padding(horizontal = BarInset)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }

        if (state.tabWidthPx > 0f) {
            Box(
                Modifier
                    .padding(horizontal = BarInset)
                    .graphicsLayer {
                        val progressOffset = state.damped.value * state.tabWidthPx
                        translationX = if (state.isLtr) {
                            progressOffset + state.panelOffset
                        } else {
                            -progressOffset + state.panelOffset
                        }
                    }
                    .then(interactiveHighlight.gestureModifier)
                    .then(state.damped.modifier)
                    .drawBackdrop(
                        backdrop = combinedBackdrop,
                        shape = { pillShape },
                        effects = {
                            // Refraction that exists only while the finger is down: at rest the
                            // pill is a tint, and pressing it turns it into a lens. Scaling the
                            // amount rather than animating a separate alpha is what keeps the
                            // distortion attached to the press instead of fading in late.
                            val progress = state.damped.pressProgress
                            lens(
                                refractionHeight = 10.dp.toPx() * progress,
                                refractionAmount = 14.dp.toPx() * progress,
                                depthEffect = true,
                                chromaticAberration = 0.5f,
                                enabled = shaderEnabled,
                            )
                        },
                        highlight = {
                            if (shaderEnabled) {
                                pillHighlight.copy(alpha = state.damped.pressProgress)
                            } else {
                                noHighlight
                            }
                        },
                        layerBlock = {
                            scaleX = state.damped.scaleX
                            scaleY = state.damped.scaleY
                            // Velocity squash: the pill stretches along the drag and thins
                            // across it. Divided by 10 first because raw velocity in index
                            // units per second is far larger than the ±0.2 the clamp allows.
                            val velocity = state.damped.velocity / 10f
                            scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                            scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                        },
                        onDrawSurface = {
                            val progress = state.damped.pressProgress
                            // ## The rest-state veil is *white*, not black (measured)
                            //
                            // This used to paint `Color.Black.copy(alpha = 0.1f)` on a light
                            // appearance, to "give the pill a defined edge". On a `#FFFFFF`
                            // page that is a 10% grey wash over the selected seat — which is
                            // the grey the reader reported, and it is *only* on the selected
                            // tab, which is why it read as a smudge rather than as a tint.
                            //
                            // A light appearance needs no darkening to define an edge: the
                            // pill already sits on a darker translucent pane and carries its
                            // own highlight. So the veil lifts instead of darkening, by a
                            // third of the old weight — enough to read as "this seat is the
                            // filled one", not enough to become a visible tone.
                            drawRect(
                                color = if (!isInDark) {
                                    Color.White.copy(alpha = 0.55f)
                                } else {
                                    Color.White.copy(alpha = 0.10f)
                                },
                                alpha = 1f - progress,
                            )
                            // The pressed state darkens a hair on both appearances: pressing
                            // is a shadow-side gesture, and on a light appearance a pressed
                            // seat that *brightened* would read as a hover.
                            drawRect(Color.Black.copy(alpha = 0.03f * progress))
                        },
                    )
                    .innerShadow(shape = pillShape) {
                        InnerShadow(
                            radius = 8.dp * state.damped.pressProgress,
                            color = Color.Black.copy(alpha = 0.15f),
                            alpha = state.damped.pressProgress,
                        )
                    }
                    .height(PillHeight)
                    .width(state.tabWidth)
            )
        }
    }
}
