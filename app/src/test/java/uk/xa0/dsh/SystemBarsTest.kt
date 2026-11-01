package uk.xa0.dsh

import android.app.Activity
import androidx.core.view.WindowCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import uk.xa0.dsh.data.ThemeMode
import uk.xa0.dsh.scroll.HarnessApplication
import uk.xa0.dsh.ui.theme.MonetPalette
import uk.xa0.dsh.ui.theme.SystemBars
import uk.xa0.dsh.ui.theme.dshPalette

/**
 * The system bars' icon appearance follows the appearance the reader chose, not the
 * phone's night setting.
 *
 * ## Why this is a test and not a screenshot
 *
 * The status bar is chrome: it is not part of the Compose tree, so no baseline in
 * `app/src/test/screenshots/` can see it. That is the same structural blind spot
 * `WindowBackgroundTest` covers for the window colour, and it is why the bug this file
 * pins went unnoticed: `enableEdgeToEdge()` with no arguments picks the bar style from
 * `Configuration.UI_MODE_NIGHT_MASK`, so forcing Dark on a light-mode phone drew
 * **dark icons on a `#151517` bar**. Nothing crashed; the icons were simply invisible.
 *
 * ## The four appearances that were wrong
 *
 * Every appearance where the app's choice disagrees with the system's answer. They are
 * enumerated rather than sampled, because a fix that only handled the default pair would
 * pass a test written around "dark mode" alone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = HarnessApplication::class)
class SystemBarsTest {

    /**
     * The platform flag as the window holds it: `true` means a **light bar**, dark icons.
     *
     * Named for what the flag means rather than for the icons, because reading it the other
     * way is the mistake this whole file was written after — see `SystemBars.lightBackground`.
     */
    private fun lightBackground(activity: Activity): Boolean =
        WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            .isAppearanceLightStatusBars

    private fun withActivity(block: (Activity) -> Unit) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        try {
            block(activity)
        } finally {
            activity.finish()
        }
    }

    // ── The decision, without a window ─────────────────────────────────────────

    @Test
    fun aLightAppearanceAsksForALightBarAndSoDarkIcons() {
        // The whole point of the corrected direction: a light palette → a light bar →
        // `isAppearanceLightStatusBars = true` → **dark** icons, which is what you can see
        // against white. The first version of this file had these four assertions inverted
        // and CI caught it; they are spelled out per appearance so the direction is stated
        // rather than implied.
        assertTrue("Light palette is a light bar", SystemBars.lightBackground(ThemeMode.LIGHT, false))
        assertTrue("Monet light", SystemBars.lightBackground(ThemeMode.MONET_LIGHT, true))
        assertFalse("Dark palette is a dark bar", SystemBars.lightBackground(ThemeMode.DARK, false))
        assertFalse("AMOLED", SystemBars.lightBackground(ThemeMode.AMOLED, true))
    }

    @Test
    fun followingTheSystemFollowsTheSystem() {
        // Dark system → dark bar → light icons → the flag is false.
        assertFalse(SystemBars.lightBackground(ThemeMode.SYSTEM, systemDark = true))
        assertTrue(SystemBars.lightBackground(ThemeMode.SYSTEM, systemDark = false))
    }

    @Test
    fun theForcedAppearancesIgnoreTheSystemSetting() {
        // The bug in one assertion: forcing Dark on a phone in light mode must still get
        // light icons. Before this change the bar followed `systemDark`, so this returned
        // the system's answer instead of the appearance's.
        assertFalse("Dark forced on a light phone", SystemBars.lightBackground(ThemeMode.DARK, systemDark = false))
        assertFalse("AMOLED forced on a light phone", SystemBars.lightBackground(ThemeMode.AMOLED, systemDark = false))
        assertTrue("Light forced on a dark phone", SystemBars.lightBackground(ThemeMode.LIGHT, systemDark = true))
    }

    @Test
    fun anUnknownStoredAppearanceFallsBackToFollowingTheSystem() {
        // A stored id from another build must not leave the bars in a random state.
        assertFalse(SystemBars.lightBackground("from-a-future-build", systemDark = true))
        assertTrue(SystemBars.lightBackground("from-a-future-build", systemDark = false))
    }

    // ── The platform call ──────────────────────────────────────────────────────

    @Test
    fun aLightBarIsWrittenToTheWindow() {
        withActivity { activity ->
            SystemBars.apply(activity, lightBackground = true)
            assertTrue(
                "a light bar must set `isAppearanceLightStatusBars` (dark icons)",
                lightBackground(activity),
            )
        }
    }

    @Test
    fun aDarkBarIsWrittenToTheWindow() {
        withActivity { activity ->
            SystemBars.apply(activity, lightBackground = false)
            assertFalse(
                "a dark bar must clear `isAppearanceLightStatusBars` (light icons)",
                lightBackground(activity),
            )
        }
    }

    @Test
    fun theTwoFlagsMoveTogether() {
        // The navigation bar had the same defect and the same fix. Asserting it separately
        // because a future edit could reasonably touch one and forget the other.
        withActivity { activity ->
            SystemBars.apply(activity, lightBackground = true)
            val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            assertEquals(
                controller.isAppearanceLightStatusBars,
                controller.isAppearanceLightNavigationBars,
            )
        }
    }

    @Test
    fun theAppearanceOverloadAgreesWithThePalette() {
        // The end-to-end shape, and the one that would have caught the original bug: given
        // an appearance, the window ends up with the *palette's* answer — because it goes
        // through `dshPalette` rather than a constant.
        withActivity { activity ->
            SystemBars.apply(activity, ThemeMode.AMOLED, systemDark = false)
            assertFalse("AMOLED is a dark bar, so icons are light", lightBackground(activity))

            SystemBars.apply(activity, ThemeMode.LIGHT, systemDark = true)
            assertTrue("Light is a light bar, so icons are dark", lightBackground(activity))
        }
    }

    @Test
    fun reapplyingAfterAnAppearanceChangeMovesTheBar() {
        // The reader switching appearance at runtime, which is the case the
        // `LaunchedEffect(ui.themeMode, systemDark)` in MainActivity serves.
        withActivity { activity ->
            SystemBars.apply(activity, ThemeMode.DARK, systemDark = false)
            assertFalse(lightBackground(activity))
            SystemBars.apply(activity, ThemeMode.LIGHT, systemDark = false)
            assertTrue(lightBackground(activity))
            SystemBars.apply(activity, ThemeMode.AMOLED, systemDark = false)
            assertFalse(lightBackground(activity))
        }
    }

    @Test
    fun theNavigationBarColourIsThePalettesBaseSurface() {
        // API 26–28 paint the navigation bar from a colour this app supplies, because the
        // platform computes its own scrim from the *system's* night setting — the same bug
        // as the icons, one layer down. The colour must therefore be the palette's base,
        // which is the surface the rest of the chrome already uses.
        for (mode in ThemeMode.all) {
            for (systemDark in listOf(true, false)) {
                val expected = dshPalette(mode, systemDark).bgBase.toArgb()
                assertEquals(
                    "navigation bar colour for $mode (systemDark=$systemDark)",
                    expected,
                    SystemBars.navigationBarColor(mode, systemDark),
                )
            }
        }
    }

    @Test
    fun theNavigationBarColourFollowsAMonetWallpaper() {
        // A palette-derived colour, not a constant: Monet moves the base surface, so the
        // bar has to move with it or an API 26–28 phone on a wallpaper theme would get a
        // strip that matches nothing.
        val wallpaper = MonetPalette(
            androidx.compose.material3.darkColorScheme(
                background = Color(0xFF123456),
            ),
        )
        val expected = dshPalette(ThemeMode.MONET_DARK, systemDark = true, monet = wallpaper)
            .bgBase.toArgb()
        assertEquals(expected, SystemBars.navigationBarColor(ThemeMode.MONET_DARK, true, wallpaper))
    }

    @Test
    fun theFlagSurvivesAFreshReadAndIsNotJustAField() {
        // Guards the audit's question: is `isAppearanceLightStatusBars` read back from the
        // window, or from the same object the write went through? Reading it off a *newly
        // obtained* controller makes the round trip go through the window, which is what
        // the production code does too.
        withActivity { activity ->
            SystemBars.apply(activity, ThemeMode.LIGHT, systemDark = false)
            val reread = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            assertTrue("light bar must survive a fresh controller read", reread.isAppearanceLightStatusBars)
        }
    }
}
