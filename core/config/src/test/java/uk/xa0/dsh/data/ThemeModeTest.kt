package uk.xa0.dsh.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The appearance list, which is now the one place that decides what the theme draws.
 *
 * These are pure functions of a stored string and a system setting, so they run on a
 * plain JVM — no Robolectric, no Compose. That is deliberate: this is the logic the
 * settings sheet and the theme both lean on, and a mistake here is silent (the app
 * opens in the wrong appearance) rather than a crash.
 */
class ThemeModeTest {

    @Test
    fun `every listed mode parses back to itself`() {
        for (mode in ThemeMode.all) {
            assertEquals(mode, ThemeMode.parse(mode))
        }
    }

    @Test
    fun `the list has no duplicates`() {
        assertEquals(ThemeMode.all.size, ThemeMode.all.toSet().size)
    }

    @Test
    fun `an unknown stored id falls back to system rather than throwing`() {
        // A preference written by a newer build, or edited under `run-as` for a bug
        // report. The app still has to start, in some appearance.
        assertEquals(ThemeMode.SYSTEM, ThemeMode.parse("chameleon"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.parse(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.parse(""))
    }

    @Test
    fun `a fixed mode ignores the system setting`() {
        for (systemDark in listOf(true, false)) {
            assertTrue(ThemeMode.isDark(ThemeMode.DARK, systemDark))
            assertTrue(ThemeMode.isDark(ThemeMode.AMOLED, systemDark))
            assertTrue(ThemeMode.isDark(ThemeMode.MONET_DARK, systemDark))
            assertFalse(ThemeMode.isDark(ThemeMode.LIGHT, systemDark))
            assertFalse(ThemeMode.isDark(ThemeMode.MONET_LIGHT, systemDark))
        }
    }

    @Test
    fun `a following mode takes the system setting`() {
        assertTrue(ThemeMode.isDark(ThemeMode.SYSTEM, systemDark = true))
        assertFalse(ThemeMode.isDark(ThemeMode.SYSTEM, systemDark = false))
        assertTrue(ThemeMode.isDark(ThemeMode.MONET_SYSTEM, systemDark = true))
        assertFalse(ThemeMode.isDark(ThemeMode.MONET_SYSTEM, systemDark = false))
    }

    @Test
    fun `only the monet modes ask for wallpaper colours`() {
        assertTrue(ThemeMode.isMonet(ThemeMode.MONET_SYSTEM))
        assertTrue(ThemeMode.isMonet(ThemeMode.MONET_LIGHT))
        assertTrue(ThemeMode.isMonet(ThemeMode.MONET_DARK))
        assertFalse(ThemeMode.isMonet(ThemeMode.SYSTEM))
        assertFalse(ThemeMode.isMonet(ThemeMode.AMOLED))
        assertFalse(ThemeMode.isMonet(ThemeMode.DARK))
    }

    @Test
    fun `the three modes the web shares are still spelled the same`() {
        // The host's `ui-theme.preference` enum and the web client use these ids. If
        // these drifted, a session's appearance would stop matching between clients.
        assertEquals("system", ThemeMode.SYSTEM)
        assertEquals("dark", ThemeMode.DARK)
        assertEquals("light", ThemeMode.LIGHT)
    }
}
