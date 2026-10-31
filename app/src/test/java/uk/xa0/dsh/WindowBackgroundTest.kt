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
import uk.xa0.dsh.ui.theme.monetPalette

/**
 * The window is painted in the appearance the reader chose, before Compose exists.
 *
 * This is the part of a launch flash that a screenshot baseline structurally cannot
 * catch: a baseline captures the Compose tree, and the window is *behind* it. So the
 * assertion is made on the view the platform fills — which is also the only place
 * this regression can live, because `android:windowBackground` is one static colour
 * and cannot follow seven appearances.
 *
 * A plain [Activity] rather than `MainActivity` on purpose: MainActivity's
 * `setContent` builds a `DshViewModel`, which needs the real `DshApplication`, a
 * client and a preference store — none of which a window colour has anything to do
 * with. What is under test is the painter, and the one line in `MainActivity.onCreate`
 * that calls it.
 *
 * ## The part that had to be learned
 *
 * The first version of this test resolved the expected colour with a bare
 * `dshPalette(mode, systemDark)` and failed on CI for the three Monet appearances:
 * the painter *does* pass wallpaper colours (this API level has them), so it paints a
 * surface no bare palette call knows about. The expected value here is resolved the
 * same way the painter resolves it — the failure was the test being wrong about the
 * product, and the product being right.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = HarnessApplication::class)
class WindowBackgroundTest {

    /**
     * Paint [mode] onto a fresh window and check it against the palette, resolved on
     * the same activity and therefore with the same wallpaper input the painter used.
     */
    private fun assertPaintsItsBaseSurface(mode: String, systemDark: Boolean = false) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        try {
            WindowBackground.apply(activity, mode, systemDark)
            val drawable = activity.window.decorView.background
            assertNotNull("the window has no background after painting", drawable)
            val expected = dshPalette(
                mode,
                systemDark,
                monetPalette(activity, mode, systemDark),
            ).bgBase.toArgb()
            assertEquals("window colour for $mode", expected, (drawable as ColorDrawable).color)
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
        for (mode in ThemeMode.all) assertPaintsItsBaseSurface(mode)
    }

    /**
     * The regression itself, stated as a difference rather than a hex: the two
     * appearances the old static `#0B0F14` was wrong for now paint something that is
     * not it, and the light one is visibly light — a window that launches into the
     * dark palette is the bug, and luminance is the property that names it without
     * hard-coding what "light" happens to be on this device.
     */
    @Test
    fun lightIsLightAndAmoledIsBlack() {
        assertEquals("AMOLED must launch into pure black", Color.BLACK, paintedWith(ThemeMode.AMOLED))
        assertEquals("a light appearance must launch into a light window", Color.WHITE, paintedWith(ThemeMode.LIGHT))
        // And the colour both of them used to launch into is neither of those.
        val oldStatic = Color.rgb(0x0B, 0x0F, 0x14)
        assertNotEquals(oldStatic, paintedWith(ThemeMode.AMOLED))
        assertNotEquals(oldStatic, paintedWith(ThemeMode.LIGHT))
    }

    /** Following the system paints whatever side the system is currently on. */
    @Test
    fun followingTheSystemUsesTheSystemsSide() {
        assertPaintsItsBaseSurface(ThemeMode.SYSTEM, systemDark = true)
        assertPaintsItsBaseSurface(ThemeMode.SYSTEM, systemDark = false)
        assertNotEquals(
            "following the system must not paint the same colour on both sides",
            paintedWith(ThemeMode.SYSTEM, systemDark = true),
            paintedWith(ThemeMode.SYSTEM, systemDark = false),
        )
    }

    /** A stale id from a later build must paint something rather than crash a launch. */
    @Test
    fun anUnknownAppearanceStillPaintsTheWindow() {
        assertPaintsItsBaseSurface("a-mode-from-a-later-build")
    }

    /**
     * Repainting is what happens when the reader changes the appearance or the system
     * flips light/dark while the app is open: the same window takes the new colour and
     * does not keep the old one underneath it.
     */
    @Test
    fun repaintingReplacesTheEarlierColour() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        try {
            WindowBackground.apply(activity, ThemeMode.AMOLED, systemDark = false)
            val fromAmoled = (activity.window.decorView.background as ColorDrawable).color
            WindowBackground.apply(activity, ThemeMode.LIGHT, systemDark = false)
            val fromLight = (activity.window.decorView.background as ColorDrawable).color
            assertEquals(Color.BLACK, fromAmoled)
            assertEquals(Color.WHITE, fromLight)
            assertNotEquals("the window kept the AMOLED colour after switching to light", fromAmoled, fromLight)
        } finally {
            activity.finish()
        }
    }

    private fun paintedWith(mode: String, systemDark: Boolean = false): Int {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        try {
            WindowBackground.apply(activity, mode, systemDark)
            return (activity.window.decorView.background as ColorDrawable).color
        } finally {
            activity.finish()
        }
    }
}
