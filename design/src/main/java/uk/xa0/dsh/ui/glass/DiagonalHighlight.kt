// The one diagonal specular every liquid pane in this app wears.
//
// See NOTICE.md: this package is the only place in the repository allowed to name a
// miuix-blur symbol, which is why the light model lives here rather than at the call sites.
package uk.xa0.dsh.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.yukonga.miuix.kmp.blur.highlight.LightSource
import uk.xa0.dsh.ui.theme.DshTheme

/**
 * The **diagonal** edge highlight: one bright rim running from the top-left corner,
 * the shadowed opposite corner falling away.
 *
 * ## Why the panes were flat without it
 *
 * The bar has always drawn a specular edge (`FloatingBottomBarLiquid`'s
 * `iosIndicatorSpecular`), and every other liquid surface — the composer, the round
 * pencil, each accessory pill, the to-dos and goal cards, the drawer — drew the blur and
 * the lens and then stopped. Photographed side by side the difference is not subtle: the
 * bar reads as a sheet of glass with light falling across it, and the rest read as
 * frosted rectangles that happen to be translucent. "输入框和药丸等所有液态玻璃组件应该
 * 也像底栏那样有阴影和对角的高光效果" is that comparison, made by someone looking at
 * both on one screen.
 *
 * ## What makes an edge read as *diagonal*
 *
 * `BloomStroke` lights a rim from a 3D point: `(x - 0.5, y - 0.7, z)` is normalized into a
 * direction, and the rim is lit by that direction's dot product with the surface normal.
 * So a light above and to the *left* of the reference origin brightens the top-left rim
 * and leaves the bottom-right in shadow — no gradient, no fudge: the shader's own
 * hemisphere does it, which is why the bright arc follows the corner radius exactly and a
 * linear gradient would not.
 *
 * `z < 0` is deliberate: it puts the light *behind* the surface plane, which is what lights
 * the inward-facing side of the rim. Positive `z` would light the outside and the stroke
 * would read as a drawn outline instead of a lit edge.
 *
 * Two lights rather than one, both on the top-left: the primary carries the corner, the
 * secondary a weaker, broader wash along the top edge so a wide pill's middle does not go
 * dark between two bright ends. `dualPeak = false` — two peaks per light is Apple's
 * sweeping specular, which reads as a highlight *sliding* across the pane; a static pane
 * wants the fixed rim the bottom bar's own indicator uses.
 *
 * The stroke colour carries its own alpha (`0.16`): the bar's preset uses `0.12` and this
 * one is drawn on much smaller surfaces, where the same alpha is visible over a shorter
 * arc and reads weaker. Measured against the transcript, not chosen.
 */
@Composable
fun rememberDiagonalHighlight(): Highlight {
    val dark = DshTheme.colors.isDark
    return remember(dark) {
        Highlight(
            width = 1.dp,
            alpha = 1f,
            style = BloomStroke(
                // On a dark appearance a white rim at this alpha is the only thing that
                // separates a pane from a near-black page, so it is allowed to be brighter.
                color = Color.White.copy(alpha = if (dark) 0.14f else 0.16f),
                innerBlurRadius = 2.4.dp,
                primaryLight = LightSource(
                    // Top-left, behind the plane. The reference origin is (0.5, 0.7), so
                    // "above and left" is a smaller x and a smaller y.
                    position = LightPosition(0.0f, 0.0f, -0.5f),
                    color = Color.White,
                    intensity = 1f,
                ),
                secondaryLight = LightSource(
                    // Weaker and closer to the reference, so the wash along the top edge
                    // is broad rather than a second hotspot.
                    position = LightPosition(0.25f, 0.1f, -0.45f),
                    color = Color.White,
                    intensity = 0.45f,
                ),
                dualPeak = false,
            ),
        )
    }
}
