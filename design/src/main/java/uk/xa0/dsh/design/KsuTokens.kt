package uk.xa0.dsh.design

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The shell's spatial and motion tokens, taken from the measured scale in
 * `docs/DESIGN-TOKENS.md` §1.1 and written here as originals.
 *
 * These exist because the reference project had no token layer at all: its spacing was
 * 155 loose literals, and this app inherited the same habit (542 raw `N.dp` in `:app`,
 * 164 direct `RoundedCornerShape(...)`). Phase 2 re-skins the shell, and re-skinning is
 * exactly when loose numbers become a problem — every screen has to be re-measured
 * because there is no single place to change a radius.
 *
 * ## Why this is separate from `DshSpacing` / `DshRadius`
 *
 * Those two describe the *web client's* scale, which the transcript mirrors section by
 * section and which the 244 `DshTheme.colors` call sites depend on. They are a
 * reproduction of an existing design. [KsuTokens] is the opposite: the shell's own
 * scale, chosen rather than copied, and free to move when the shell does.
 *
 * ## Why there is no `Space` here
 *
 * There was one, and it overlapped `DshSpacing` on five of its seven steps while
 * disagreeing on the other two (`xxl` 20 vs 24, `xxxl` 24 vs 32). Both are live in
 * the same composition — the transcript reads `DshSpacing`, the shell would have read
 * `Space` — so the two would have appeared on one screen with nothing to report it.
 * `DshSpacing` already covers every step `Space` had, plus the `2dp` and `32dp` ends,
 * so spacing is one object: `DshSpacing`, for the whole app. See
 * `docs/DESIGN-TOKENS.md` §4.2, option A.
 *
 * Radius and type will be in use at once, and that is intended: the transcript keeps
 * rendering at the web's measurements while the chrome around it takes these. Merging
 * them would force one to change whenever the other does, which is the problem phase 2
 * is meant to avoid. Spacing is the one exception, and the section above says why.
 *
 * ## Sizes are in dp, not px
 *
 * Every value here is a `Dp` or an `sp`. Nothing derives a pixel size from a density,
 * so a baseline recorded at 411×891 and a device at the same width agree.
 */
object KsuTokens {

    /**
     * Corner radii.
     *
     * [pill] is a large number rather than `50.dp` so that any height renders as a
     * capsule; `CircleShape` would clip a label that outgrows the height.
     */
    object Radius {
        val card: Dp = 12.dp
        val cardLarge: Dp = 16.dp
        val panel: Dp = 20.dp
        val sheet: Dp = 28.dp
        val pill: Dp = 100.dp
    }

    /**
     * Fixed dimensions the shell lays out against.
     *
     * [touchTarget] is the accessibility floor, distinct from [rowHeight]: a row is
     * 56dp because that is a comfortable list row, and a control inside a smaller
     * container still has to reach 48dp.
     */
    object Size {
        val rowHeight: Dp = 56.dp
        val barHeight: Dp = 60.dp
        val icon: Dp = 24.dp
        val touchTarget: Dp = 48.dp
    }

    /**
     * The shell's five type steps.
     *
     * Five, not the ten of a full scale, because the shell only has five jobs:
     * a bar label, body copy, a search field, a page title and a display number.
     * The transcript's own ladder stays in `DshType`.
     */
    object Type {
        val caption = TextStyle(fontSize = 11.sp, lineHeight = 14.sp)
        val body = TextStyle(fontSize = 15.sp, lineHeight = 24.sp)
        val bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium)
        val title = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold)
        val display = TextStyle(fontSize = 35.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold)
    }

    /**
     * Motion, named by what it is for rather than by what it looks like.
     *
     * [pageSpring] is deliberately under-damped (`dampingRatio < 1`) so a page settle
     * has a small overshoot; a critically damped spring reads as a cut rather than a
     * move at this distance. It is also the only spring here — a second one for the
     * selection indicator would drift out of sync with the pager it follows.
     */
    object Motion {
        val pageSpring = spring<Float>(
            dampingRatio = 0.75f,
            stiffness = Spring.StiffnessMediumLow,
        )

        /** 180ms: long enough to be seen, short enough not to delay the next tap. */
        val dialogIn = tween<Float>(durationMillis = 180, easing = FastOutSlowInEasing)

        /** A streaming paragraph's fade, which must not compete with the text. */
        val streamFade = tween<Float>(durationMillis = 120)
    }
}
