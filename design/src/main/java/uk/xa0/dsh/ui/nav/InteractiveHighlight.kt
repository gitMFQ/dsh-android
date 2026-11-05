// Vendored from chenaizhang/KernelSU-Style-UI-Kit (GPL-3.0) — see NOTICE.md.
// Upstream path: app/src/main/java/com/example/kernelsustyleuikit/ui/component/miuix/animation/InteractiveHighlight.kt
// Change: package renamed to `uk.xa0.dsh.ui.nav`; guarded so the AGSL shader is only ever
// //       built on API 33+ (upstream constructs it unconditionally), and made to degrade to
// //       the flat wash on the lower tiers instead of touching RuntimeShader at all.

package uk.xa0.dsh.ui.nav

import android.annotation.SuppressLint
import android.graphics.RuntimeShader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.fastCoerceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.intellij.lang.annotations.Language

/**
 * A soft radial light that follows the finger while the bar is held.
 *
 * Two pieces, deliberately separate so a caller can take one without the other:
 *  - [modifier] paints the light, and is added to whatever surface should glow;
 *  - [gestureModifier] is the input side that moves it, added to whatever surface should
 *    be draggable.
 *
 * On API 33+ the light is a runtime shader whose falloff is a real `smoothstep`; below
 * that there is no `RuntimeShader`, and the class paints the flat wash only. The shader is
 * built lazily rather than in the constructor so that constructing this object — which the
 * bar does on every tier — cannot throw `NoClassDefFoundError` on an API 26 device.
 */
@SuppressLint("NewApi")
class InteractiveHighlight(
    val animationScope: CoroutineScope,
    val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset }
) {

    private val pressProgressAnimationSpec =
        spring(0.5f, 300f, 0.001f)
    private val positionAnimationSpec =
        spring(0.5f, 300f, Offset.VisibilityThreshold)

    private val pressProgressAnimation =
        Animatable(0f, 0.001f)
    private val positionAnimation =
        Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

    private var startPosition = Offset.Zero

    @Language("AGSL")
    private val shaderSource: String =
        """
    uniform float2 size;
    layout(color) uniform half4 color;
    uniform float radius;
    uniform float2 position;
    
    half4 main(float2 coord) {
        float dist = distance(coord, position);
        float intensity = smoothstep(radius, radius * 0.5, dist);
        return color * intensity;
    }"""

    /**
     * Built once, on first draw of a tier that can draw it. See the class comment.
     *
     * The class-level `@SuppressLint("NewApi")` covers this: `RuntimeShader` is API 33 and the
     * linter cannot see that [canRunShader] is what gates it, but the delegate itself must stay
     * unannotated — Kotlin has no annotation target for a delegated property, and moving the
     * suppression to the class is the only shape that is both honest and legal.
     */
    private val shader: RuntimeShader? by lazy(LazyThreadSafetyMode.NONE) {
        if (!canRunShader()) null else runCatching { RuntimeShader(shaderSource) }.getOrNull()
    }

    /**
     * Whether the radial shader may run.
     *
     * The last shader that was not gated, and easy to miss because it only runs while a tab
     * is *pressed* — but it is a `RuntimeShader` compiled from AGSL and bound on the render
     * thread, which is exactly the class of call the levels exist to eliminate. With it
     * still live, "works at off, crashes at blur" could not distinguish the backdrop from
     * the shader, and that ambiguity is what made two rounds of bisection inconclusive.
     *
     * A plain `var` set from composition rather than a read inside the draw lambda:
     * `drawWithContent` is not a composable scope, so a `compositionLocalOf.current` there
     * is a compile error — and a static local would be worse, since the level can change
     * under a running composition.
     */
    var shaderEnabled: Boolean = true

    val modifier: Modifier =
        Modifier.drawWithContent {
            val progress = pressProgressAnimation.value
            if (progress > 0f && shaderEnabled) {
                // The wash is always drawn: it is what makes the glow read as *press* even
                // where the radial falloff is unavailable.
                drawRect(
                    Color.White.copy(0.06f * progress),
                    blendMode = BlendMode.Plus
                )
                val shader = shader
                if (shader != null) {
                    shader.apply {
                        val position = position(size, positionAnimation.value)
                        setFloatUniform("size", size.width, size.height)
                        setColorUniform("color", Color.White.copy(0.12f * progress).toArgb())
                        setFloatUniform("radius", size.minDimension * 1.2f)
                        setFloatUniform(
                            "position",
                            position.x.fastCoerceIn(0f, size.width),
                            position.y.fastCoerceIn(0f, size.height)
                        )
                    }
                    drawRect(
                        ShaderBrush(shader),
                        blendMode = BlendMode.Plus
                    )
                }
            }

            drawContent()
        }

    val gestureModifier: Modifier =
        Modifier.pointerInput(animationScope) {
            inspectDragGestures(
                onDragStart = { down ->
                    startPosition = down.position
                    animationScope.launch {
                        launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
                        launch { positionAnimation.snapTo(startPosition) }
                    }
                },
                onDragEnd = {
                    animationScope.launch {
                        launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                        launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                    }
                },
                onDragCancel = {
                    animationScope.launch {
                        launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                        launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                    }
                }
            ) { change, _ ->
                animationScope.launch { positionAnimation.snapTo(change.position) }
            }
        }
}

/**
 * Whether the radial shader half of [InteractiveHighlight] may be used.
 *
 * Reads `Build.VERSION` rather than `miuix-blur`'s `isRuntimeShaderSupported()` for the
 * same reason `ui/glass/LiquidGlass.kt` does: this predicate must be answerable *without*
 * loading the miuix-blur classes, or an API 26 device fails inside the very check that was
 * meant to protect it. The two agree, because that helper is itself an API-level test.
 *
 * Deliberately *not* folded into `ui/glass/GlassBarBackdrop.kt#glassBarSupported`: that one asks
 * a different question. It also requires `isRenderEffectSupported()`, because a backdrop is drawn
 * through a RenderEffect, while this shader is drawn through a plain `ShaderBrush` and needs only
 * the API-33 `RuntimeShader` class to exist. Two questions, two predicates — collapsing them would
 * make the highlight go flat on a device whose backdrop still works.
 */
@SuppressLint("NewApi")
internal fun canRunShader(): Boolean =
    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
