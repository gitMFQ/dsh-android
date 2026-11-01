package uk.xa0.dsh.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDark
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLight
import uk.xa0.dsh.data.ThemeMode

/**
 * The palette bridge, asserted without a composition.
 *
 * ## The failure this file exists to prevent
 *
 * Handing `MiuixTheme` no arguments paints miuix's own accent, `#3482FF`. This app's light
 * accent is `#4176E6`. Two accents on one screen is exactly the bug the seven appearances
 * exist to prevent, and **it does not fail** — the 2.3 probe rendered a `SwitchPreference`
 * in one blue beside a screen in another and nothing logged a word
 * (`docs/PHASE-2-PLAN.md` §2.2). So the first test here is not "the adapter runs"; it is
 * "the accent is the app's, for every one of the seven appearances".
 *
 * ## What cannot be asserted here
 *
 * Whether it *looks* right. That is the screenshot baselines' job. What is asserted is
 * that the values miuix reads are the values the palette resolved — the whole distance
 * between "compiles" and "follows the appearance".
 */
class MiuixPaletteTest {

    /** A wallpaper palette, so the Monet appearances have something to resolve to. */
    private val monet = MonetPalette(
        darkColorScheme(
            primary = Color(0xFF00FF00),
            background = Color(0xFF101010),
            surface = Color(0xFF202020),
            surfaceVariant = Color(0xFF303030),
        ),
    )

    /** Every appearance, resolved the way `DshTheme` resolves it. */
    private fun everyAppearance(): List<Pair<String, DshColors>> =
        ThemeMode.all.map { mode ->
            mode to dshPalette(mode, systemDark = false, monet = monet)
        }

    // ── The one that 2.3 measured going wrong ──────────────────────────────────

    @Test
    fun everyAppearanceCarriesItsOwnAccentIntoMiuix() {
        for ((mode, palette) in everyAppearance()) {
            val miuix = palette.toMiuixColors()
            assertEquals(
                "miuix accent for $mode must be the app's accent, not miuix's #3482FF",
                palette.accent,
                miuix.primary,
            )
        }
    }

    @Test
    fun theLightAccentIsTheAppsNotMiuixsDefault() {
        val light = dshPalette(ThemeMode.LIGHT, systemDark = false)
        // The two constants the 2.3 gate compared. Pinned literally: if either side of
        // this ever changes, the reason this adapter exists has to be re-read rather
        // than silently re-baselined.
        assertEquals(Color(0xFF4176E6), light.accent)
        assertNotEquals(
            "the adapter must not fall back to miuix's default accent",
            miuixLight().primary,
            light.toMiuixColors().primary,
        )
        assertEquals(Color(0xFF3482FF), miuixLight().primary)
    }

    @Test
    fun theDarkAccentIsTheDarkPalettes() {
        val dark = dshPalette(ThemeMode.DARK, systemDark = false)
        assertEquals(dark.accent, dark.toMiuixColors().primary)
        assertEquals(Color(0xFF679EFE), dark.accent)
    }

    @Test
    fun monetMovesTheMiuixAccentWithTheWallpaper() {
        // The point of a *derived* appearance: nothing here names a hex, and the accent
        // arrives from the wallpaper scheme. A mapping that hard-coded the brand blue
        // would pass every other test in this file and fail this one.
        val monetLight = dshPalette(ThemeMode.MONET_LIGHT, systemDark = false, monet = monet)
        assertEquals(Color(0xFF00FF00), monetLight.toMiuixColors().primary)
    }

    @Test
    fun amoledIsBlackerThanDarkInEveryMiuixSurface() {
        // The property, not a literal: AMOLED is the dark palette with every surface
        // taken to black (`DshColors.toAmoled()`), so each miuix surface role must land
        // *darker* than the same role under Dark. Asserting `== Color.Black` on all of
        // them would have been wrong about the design — the raised steps are deliberately
        // one shade off black so a card still reads as a surface and not as a hole.
        val amoled = dshPalette(ThemeMode.AMOLED, systemDark = true).toMiuixColors()
        val dark = dshPalette(ThemeMode.DARK, systemDark = false).toMiuixColors()
        assertEquals("the base background is pure black", Color.Black, amoled.background)
        // #0A0A0A, not black: AMOLED_SURFACE is deliberately one step off so a card reads as
        // a surface rather than a hole (`DshTheme.kt:365-366`). Asserting `Color.Black` here
        // would have been wrong about the design.
        assertEquals(Color(0xFF0A0A0A), amoled.surface)
        val surfaces = mapOf(
            "surface" to (amoled.surface to dark.surface),
            "surfaceVariant" to (amoled.surfaceVariant to dark.surfaceVariant),
            "surfaceContainer" to (amoled.surfaceContainer to dark.surfaceContainer),
            "surfaceContainerHighest" to (amoled.surfaceContainerHighest to dark.surfaceContainerHighest),
        )
        for ((name, pair) in surfaces) {
            val (a, d) = pair
            assertTrue(
                "$name must be darker under AMOLED than under Dark (amoled=$a dark=$d)",
                a.red + a.green + a.blue < d.red + d.green + d.blue,
            )
        }
    }

    // ── The ladder miuix draws text and rows with ──────────────────────────────

    @Test
    fun textRolesComeFromThePalette() {
        for ((mode, palette) in everyAppearance()) {
            val miuix = palette.toMiuixColors()
            assertEquals("onBackground for $mode", palette.labelPrimary, miuix.onBackground)
            assertEquals("onSurface for $mode", palette.labelPrimary, miuix.onSurface)
            assertEquals("summary for $mode", palette.labelSecondary, miuix.onSurfaceVariantSummary)
            assertEquals("error for $mode", palette.error, miuix.error)
        }
    }

    @Test
    fun theUncheckedSwitchTrackIsNotTheAccent() {
        // `Switch` reads `secondary` for an unchecked track and `primary` for a checked
        // one (`basic/Switch.kt:235-236`). Mapping `secondary` to the accent would make
        // the two states indistinguishable — a wrong picture, not a crash.
        for ((mode, palette) in everyAppearance()) {
            val miuix = palette.toMiuixColors()
            assertNotEquals("unchecked track for $mode", miuix.primary, miuix.secondary)
        }
    }

    // ── miuix's own invariant: disabled roles must be opaque ───────────────────

    @Test
    fun everyTranslucentRoleIsFlattenedToOpaque() {
        // miuix's internal mapping composites these over a surface precisely because its
        // components paint them as if opaque (`theme/MonetMapping.kt:90-100`). A mapping
        // that passed the alphas through would render disabled switches washed out.
        for ((mode, palette) in everyAppearance()) {
            val m = palette.toMiuixColors()
            val roles = mapOf(
                "disabledPrimary" to m.disabledPrimary,
                "disabledOnPrimary" to m.disabledOnPrimary,
                "disabledPrimaryButton" to m.disabledPrimaryButton,
                "disabledOnPrimaryButton" to m.disabledOnPrimaryButton,
                "disabledPrimarySlider" to m.disabledPrimarySlider,
                "disabledSecondary" to m.disabledSecondary,
                "disabledOnSecondary" to m.disabledOnSecondary,
                "disabledSecondaryVariant" to m.disabledSecondaryVariant,
                "disabledOnSecondaryVariant" to m.disabledOnSecondaryVariant,
                "disabledOnSurface" to m.disabledOnSurface,
                "onSurfaceSecondary" to m.onSurfaceSecondary,
                "onSurfaceContainerHigh" to m.onSurfaceContainerHigh,
                "sliderBackground" to m.sliderBackground,
            )
            for ((name, colour) in roles) {
                assertEquals("$name must be opaque for $mode", 1f, colour.alpha, 0.0001f)
            }
        }
    }

    @Test
    fun theTwoSecondaryLabelStepsDoNotCollapseIntoOne() {
        // `onSurfaceSecondary` is `onSurface` at 80%; `onSurfaceVariantSummary` is the
        // secondary label. Mapping the first from an *opaque* colour short-circuits the
        // composite and makes the two identical — two rungs of miuix's label ladder becoming
        // one, with the "every translucent role is opaque" test above still green.
        for ((mode, palette) in everyAppearance()) {
            val m = palette.toMiuixColors()
            assertNotEquals(
                "onSurfaceSecondary and onSurfaceVariantSummary must stay distinct for $mode",
                m.onSurfaceVariantSummary,
                m.onSurfaceSecondary,
            )
        }
    }

    @Test
    fun aDisabledSwitchTrackIsLighterThanAnEnabledOne() {
        // The pixel bug the mapping audit caught: `borderL3` is *already* translucent, so
        // knocking its alpha back by assignment rather than by compositing made the disabled
        // track three to four times heavier than the enabled one — a disabled switch that
        // shouted louder than an enabled one. Asserted as the ordering, on both palettes,
        // because the wrong version happened to look plausible in isolation.
        fun luminance(c: Color) = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.AMOLED)) {
            val m = dshPalette(mode, systemDark = false).toMiuixColors()
            val enabled = luminance(m.secondary)
            val disabled = luminance(m.disabledSecondary)
            val base = luminance(m.background)
            // "Recedes from the surface" means closer to the surface, whichever side it is on.
            assertTrue(
                "disabled track must sit closer to the surface than the enabled track for $mode " +
                    "(enabled=$enabled disabled=$disabled surface=$base)",
                kotlin.math.abs(disabled - base) < kotlin.math.abs(enabled - base),
            )
        }
    }

    @Test
    fun theSelectedRowSurfaceIsTheAccentTintNotTheWarningTint() {
        // miuix paints a selected dropdown row with `tertiaryContainer`/`onTertiaryContainer`
        // (`basic/Dropdown.kt:428,513`). Pointing that pair at the warning colours would make
        // every selection read as an alarm, and put `warn` on `warnTertiary` — 1.99:1.
        for ((mode, palette) in everyAppearance()) {
            val m = palette.toMiuixColors()
            assertEquals("selected row fill for $mode", palette.accentTertiary, m.tertiaryContainer)
            assertEquals("selected row text for $mode", palette.accent, m.onTertiaryContainer)
            assertNotEquals("must not be the warning tint for $mode", palette.warnTertiary, m.tertiaryContainer)
        }
    }

    /** WCAG relative luminance, for the two contrast assertions below. */
    private fun luminance(c: Color): Float {
        fun lin(v: Float) = if (v <= 0.03928f) v / 12.92f else
            Math.pow(((v + 0.055) / 1.055).toDouble(), 2.4).toFloat()
        return 0.2126f * lin(c.red) + 0.7152f * lin(c.green) + 0.0722f * lin(c.blue)
    }

    private fun contrast(a: Color, b: Color): Float {
        val hi = maxOf(luminance(a), luminance(b))
        val lo = minOf(luminance(a), luminance(b))
        return (hi + 0.05f) / (lo + 0.05f)
    }

    @Test
    fun errorTextOnAnErrorFillBeatsEveryOtherCandidate() {
        // miuix draws badge text with `onError` over `error`. The draft used `inkInverted`,
        // which is #353638 on the dark palette — 3.68:1 against `error` #F25A5A. Asserting a
        // *choice* rather than a number: whatever the palette's error token is, the mapping
        // must pick the most legible of the tokens available to it.
        for ((mode, palette) in everyAppearance()) {
            val m = palette.toMiuixColors()
            val chosen = contrast(m.onError, m.error)
            val alternatives = mapOf(
                "inkInverted" to palette.inkInverted,
                // Included precisely because it looks like the right token and is not:
                // Monet remaps it with the wallpaper, so it can land at 3.99:1.
                "sendGlyph" to palette.sendGlyph,
                "white" to Color.White,
            )
            for ((name, candidate) in alternatives) {
                assertTrue(
                    "onError ($mode) must be at least as legible as $name " +
                        "(chosen=$chosen vs ${contrast(candidate, m.error)})",
                    chosen >= contrast(candidate, m.error) - 0.001f,
                )
            }
        }
    }

    @Test
    fun errorTextClearsTheThresholdsTheAppCanActuallyMeet() {
        // Every appearance must clear the full 4.5, and the two that used to fail are the
        // reason this assertion is a loop rather than a sample:
        //  * **light** — the palette's `error` #EC1313 caps *white* ink at 4.4976:1, so any
        //    rule that reaches for the light end misses by a hair. Black reaches 4.66:1.
        //  * **Monet dark** — `sendGlyph` follows the accent, which follows the wallpaper, so
        //    both candidate inks collapsed onto the light end at 3.99:1.
        for ((mode, palette) in everyAppearance()) {
            val m = palette.toMiuixColors()
            assertTrue(
                "onError over error must reach 4.5:1 for $mode (was ${contrast(m.onError, m.error)})",
                contrast(m.onError, m.error) >= 4.5f,
            )
        }
    }

    @Test
    fun theDisabledAccentIsStillTheAppsAccentNotMiuixs() {
        // The derived roles are the easiest place to leak miuix's defaults back in:
        // they are computed, so a mapping that started from `lightColorScheme()` and
        // forgot to override these would look right in every other assertion.
        val light = dshPalette(ThemeMode.LIGHT, systemDark = false).toMiuixColors()
        val miuixDefaultDisabled = miuixLight().disabledPrimary
        assertNotEquals(
            "disabled accent must derive from the app's accent",
            miuixDefaultDisabled,
            light.disabledPrimary,
        )
    }

    // ── The base scheme is chosen by the appearance, not by miuix ──────────────

    @Test
    fun theUntouchedRolesFollowTheLightDarkDivide() {
        // Roles this app has no opinion about (windowDimming, sliderKeyPoint) are left
        // at miuix's own values — but they must be *the right side's* values. Starting
        // every appearance from `lightColorScheme()` would leave a dark screen with a
        // 0.3 dim and light-side key points.
        val light = dshPalette(ThemeMode.LIGHT, systemDark = false).toMiuixColors()
        val dark = dshPalette(ThemeMode.DARK, systemDark = false).toMiuixColors()
        assertEquals(miuixLight().windowDimming, light.windowDimming)
        assertEquals(miuixDark().windowDimming, dark.windowDimming)
        assertNotEquals(light.windowDimming, dark.windowDimming)
    }

    @Test
    fun theAdapterIsAPureFunctionOfThePalette() {
        // Called twice with the same palette it must produce the same values: `DshTheme`
        // remembers the result, so a mapping that read a clock or a composition local
        // would make that memoisation wrong rather than merely slow.
        val palette = dshPalette(ThemeMode.DARK, systemDark = false)
        val first = palette.toMiuixColors()
        val second = palette.toMiuixColors()
        assertEquals(first.primary, second.primary)
        assertEquals(first.background, second.background)
        assertEquals(first.dividerLine, second.dividerLine)
    }

    /** A reminder of the shape of the contract: 53 roles, all of them assigned. */
    @Test
    fun theMappingProducesAWholeColorsObject() {
        val miuix: Colors = dshPalette(ThemeMode.LIGHT, systemDark = false).toMiuixColors()
        // Spot-check one role from each family, so a `copy` that dropped a named
        // argument (silently keeping miuix's default) fails here rather than on a phone.
        assertEquals(Color(0xFF4176E6), miuix.primary)
        assertEquals(miuix.primary, miuix.sliderKeyPoint)
        assertTrue("divider must be a hairline, not transparent", miuix.dividerLine.alpha > 0f)
    }
}
