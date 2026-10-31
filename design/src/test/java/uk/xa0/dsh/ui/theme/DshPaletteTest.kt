package uk.xa0.dsh.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.xa0.dsh.data.ThemeMode

/**
 * The appearance-to-palette decision, exercised without a composition.
 *
 * This function exists because the Android window has to be painted before Compose
 * produces a frame, and a window cannot read a `CompositionLocal`. Everything here is
 * therefore asserted on `dshPalette` directly: it is the one place the seven
 * appearances become colours, and both the window and `DshTheme` read it.
 *
 * What these tests cannot say is whether anything *looks* right — that is the
 * screenshot baselines' job (see `app/src/test/java/uk/xa0/dsh/screenshot/`). What
 * they say is that the window and Compose agree about which of the seven appearances
 * is on screen, which is the entire reason a launch could flash the wrong colour.
 */
class DshPaletteTest {

    private val monet = MonetPalette(darkColorScheme(primary = Color(0xFF00FF00)))

    @Test
    fun lightAppearancesResolveToTheLightPalette() {
        assertFalse("Light", dshPalette(ThemeMode.LIGHT, systemDark = true).isDark)
        assertFalse("Monet light", dshPalette(ThemeMode.MONET_LIGHT, systemDark = true).isDark)
    }

    @Test
    fun darkAppearancesResolveToTheDarkPalette() {
        assertTrue("Dark", dshPalette(ThemeMode.DARK, systemDark = false).isDark)
        assertTrue("AMOLED", dshPalette(ThemeMode.AMOLED, systemDark = false).isDark)
        assertTrue("Monet dark", dshPalette(ThemeMode.MONET_DARK, systemDark = false).isDark)
    }

    @Test
    fun followingTheSystemFollowsTheSystem() {
        assertTrue(dshPalette(ThemeMode.SYSTEM, systemDark = true).isDark)
        assertFalse(dshPalette(ThemeMode.SYSTEM, systemDark = false).isDark)
        assertTrue(dshPalette(ThemeMode.MONET_SYSTEM, systemDark = true).isDark)
        assertFalse(dshPalette(ThemeMode.MONET_SYSTEM, systemDark = false).isDark)
    }

    /**
     * An id this build does not know is a cold start with a stale preference file, not
     * a crash and not a third appearance: it means the same as asking the system.
     */
    @Test
    fun anUnknownIdIsTreatedAsSystem() {
        val unknownInDark = dshPalette("some-mode-from-a-later-build", systemDark = true)
        assertEquals(dshPalette(ThemeMode.SYSTEM, systemDark = true), unknownInDark)
        assertEquals(
            dshPalette(ThemeMode.SYSTEM, systemDark = false),
            dshPalette("some-mode-from-a-later-build", systemDark = false),
        )
    }

    /** AMOLED is not "dark plus a bit": its base surface is the colour of an off pixel. */
    @Test
    fun amoledTakesTheBaseSurfaceToBlack() {
        assertEquals(Color.Black, dshPalette(ThemeMode.AMOLED, systemDark = false).bgBase)
        // And it does not depend on the system's setting, only on being chosen.
        assertEquals(Color.Black, dshPalette(ThemeMode.AMOLED, systemDark = true).bgBase)
    }

    /**
     * Below API 31 there are no wallpaper colours, and the honest fallback is the plain
     * light or dark palette on the side the reader asked for — not a missing appearance
     * and not a wallpaper-less grey.
     */
    @Test
    fun aMonetModeWithoutWallpaperColorsFallsBackToItsSideOfTheDivide() {
        val dark = dshPalette(ThemeMode.MONET_DARK, systemDark = false, monet = null)
        assertEquals(dshPalette(ThemeMode.DARK, systemDark = false), dark)
        val light = dshPalette(ThemeMode.MONET_LIGHT, systemDark = true, monet = null)
        assertEquals(dshPalette(ThemeMode.LIGHT, systemDark = true), light)
    }

    /** With wallpaper colours, only the accent family and the surfaces move. */
    @Test
    fun aMonetModeWithWallpaperColorsTakesTheAccentFromTheWallpaper() {
        val dark = dshPalette(ThemeMode.MONET_DARK, systemDark = false, monet = monet)
        assertEquals(Color(0xFF00FF00), dark.accent)
        // Semantic colours stay the app's own: a wallpaper cannot know what red means.
        assertEquals(dshPalette(ThemeMode.DARK, systemDark = false).error, dark.error)
        assertEquals(dshPalette(ThemeMode.DARK, systemDark = false).success, dark.success)
    }

    /** AMOLED wins over the wallpaper: it is the reader explicitly asking for black. */
    @Test
    fun amoledIsNotRepaintedByTheWallpaper() {
        assertEquals(Color.Black, dshPalette(ThemeMode.AMOLED, systemDark = false, monet = monet).bgBase)
    }
}
