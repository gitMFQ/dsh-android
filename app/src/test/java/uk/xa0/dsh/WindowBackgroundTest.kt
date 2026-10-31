package uk.xa0.dsh

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uk.xa0.dsh.data.ThemeMode
import uk.xa0.dsh.scroll.HarnessApplication
import uk.xa0.dsh.ui.theme.WindowBackground
import uk.xa0.dsh.ui.theme.dshPalette

/**
 * The window is painted in the appearance the reader chose, before Compose exists.
 *
 * This is the part of a launch flash that a screenshot baseline structurally cannot
 * catch: a baseline captures the Compose tree, and the window is *behind* it. So the
 * assertion is made on the view the platform fills — which is also the only place
 * this regression can live, because `android:windowBackground` is one static colour
 * and cannot follow seven appearances.
 *
 * A plain [Activity] rather than [MainActivity] on purpose: MainActivity's `setContent`
 * builds a `DshViewModel`, which needs the real `DshApplication`, a client and a
 * preference store — none of which a window colour has anything to do with. What is
 * under test is the painter, and the one line in `MainActivity.onCreate` that calls
 * it. That line is covered by the ordinary functional suite compiling and running; a
 * colour assertion here does not become more true by dragging a view model in.
 *
 * The appearance itself is read from the palette rather than written as a hex literal:
 * a test that restated `#151517` would pass while the two drifted apart, which is
 * exactly the bug this PR exists to remove.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = HarnessApplication::class)
class WindowBackgroundTest {

    /** Paint [mode] onto a fresh window and read the colour back. */
    private fun paintWith(mode: String, systemDark: Boolean = false): Int {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        try {
            WindowBackground.apply(activity, mode, systemDark)
            val drawable = activity.window.decorView.background
            assertNotNull("the window has no background after painting", drawable)
            return (drawable as ColorDrawable).color
        } finally {
            activity.finish()
        }
    }

    /**
     * Every one of the seven appearances paints the palette's own `bgBase` — the same
     * token `DshTheme` fills the screen with — rather than a second literal kept in
     * step by hand. Compared as ARGB integers because that is what reaches the window.
     */
    @Test
    fun everyAppearancePaintsItsOwnBaseSurface() {
        for (mode in ThemeMode.all) {
            assertEquals(
                "window colour for $mode",
                dshPalette(mode, systemDark = false).bgBase.toArgb(),
                paintWith(mode),
            )
        }
    }

    /**
     * The regression itself, as a difference: the two appearances the old static
     * `#0B0F14` was wrong for now paint the right thing — white for light, black for
     * AMOLED — and neither of them is that colour.
     */
    @Test
    fun lightAndAmoledAreNotTheOldStaticColour() {
        val oldStatic = Color.rgb(0x0B, 0x0F, 0x14)
        assertEquals("light must not launch into a dark window", Color.WHITE, paintWith(ThemeMode.LIGHT))
        assertEquals("AMOLED must not launch into the dark palette's grey", Color.BLACK, paintWith(ThemeMode.AMOLED))
        assertNotEquals(oldStatic, paintWith(ThemeMode.LIGHT))
        assertNotEquals(oldStatic, paintWith(ThemeMode.AMOLED))
    }

    /** Following the system paints whatever side the system is currently on. */
    @Test
    fun followingTheSystemUsesTheSystemsSide() {
        assertEquals(
            dshPalette(ThemeMode.SYSTEM, systemDark = true).bgBase.toArgb(),
            paintWith(ThemeMode.SYSTEM, systemDark = true),
        )
        assertEquals(
            dshPalette(ThemeMode.SYSTEM, systemDark = false).bgBase.toArgb(),
            paintWith(ThemeMode.SYSTEM, systemDark = false),
        )
    }

    /** A stale id from a later build must paint something rather than crash a launch. */
    @Test
    fun anUnknownAppearanceStillPaintsTheWindow() {
        assertEquals(
            dshPalette(ThemeMode.SYSTEM, systemDark = false).bgBase.toArgb(),
            paintWith("a-mode-from-a-later-build"),
        )
    }

    /**
     * Repainting is what happens when the reader changes the appearance or the system
     * flips light/dark while the app is open: the same window must take the new colour,
     * and must not keep the old one underneath it.
     */
    @Test
    fun repaintingReplacesTheEarlierColour() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        try {
            WindowBackground.apply(activity, ThemeMode.DARK, systemDark = false)
            val fromDark = (activity.window.decorView.background as ColorDrawable).color
            WindowBackground.apply(activity, ThemeMode.LIGHT, systemDark = false)
            val fromLight = (activity.window.decorView.background as ColorDrawable).color
            assertEquals(dshPalette(ThemeMode.LIGHT, false).bgBase.toArgb(), fromLight)
            assertNotEquals(
                "the window kept the dark colour after switching to light",
                fromDark,
                fromLight,
            )
        } finally {
            activity.finish()
        }
    }
}
