package uk.xa0.dsh.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.xa0.dsh.data.ThemeMode

/**
 * DSH's own design tokens.
 *
 * Names mirror the CSS custom properties in the web client (`--dsw-alias-*`,
 * `--dsw-specific-*`) rather than Material roles, so the mapping stays checkable
 * against the source. Values are the resolved hexes from `docs/research/design-system.md`.
 *
 * Two traps this encodes, both easy to get wrong:
 *  - `brand`/`ink` is NOT blue; the blue accent is [accent] (`state-business-primary`).
 *  - Assistant messages have no bubble — only user messages use [bubble].
 */
data class DshColors(
    val isDark: Boolean,
    // Backgrounds
    val bgBase: Color,
    val bgLayer1: Color,
    val bgLayer2: Color,
    val bgLayer3: Color,
    val sidebarFill: Color,
    val inputMajor: Color,
    val menu: Color,
    val tip: Color,
    val selector: Color,
    // Text
    val labelPrimary: Color,
    val labelSecondary: Color,
    val labelTertiary: Color,
    val labelCaption: Color,
    val labelDimmed: Color,
    /**
     * `--dsw-alias-label-primary-dimmed`. Despite the name it is *not* the dark
     * grey of [labelDimmed]: it is the primary label barely knocked back, so it
     * stays legible as body copy. It is what the queue preview and the goal
     * objective are painted with.
     */
    val labelPrimaryDimmed: Color,
    val ink: Color,
    val inkInverted: Color,
    val link: Color,
    val accent: Color,
    val accentTertiary: Color,
    /** `--dsw-static-deepseek-200`; the bright band inside the running shimmer. */
    val accentLight: Color,
    /** `--dsw-static-deepseek-500`; the shimmer's base, one step below [accent]. */
    val accentDeep: Color,
    // Borders
    val borderL1: Color,
    val borderL2: Color,
    val borderL3: Color,
    val borderL4: Color,
    // States
    val success: Color,
    val error: Color,
    val warn: Color,
    val warnTertiary: Color,
    /** `--dsw-static-deepseek-450`; the running-dot chase colour, not an alias token. */
    val ongoing: Color,
    // Fills
    val hover: Color,
    val active: Color,
    val bubble: Color,
    val bubbleHighlight: Color,
    val sidebarItemHover: Color,
    val sidebarItemActive: Color,
    val sendFill: Color,
    val sendGlyph: Color,
    // Code / markdown surfaces
    val codeBlock: Color,
    val codeBanner: Color,
    val inlineCode: Color,
    val scrollbar: Color,
    // Syntax
    val synConstant: Color,
    val synString: Color,
    val synComment: Color,
    val synKeyword: Color,
    val synParameter: Color,
    val synFunction: Color,
    val synPunctuation: Color,
)

internal val DarkColors = DshColors(
    isDark = true,
    bgBase = Color(0xFF151517),
    bgLayer1 = Color(0xFF232324),
    bgLayer2 = Color(0xFF2C2C2E),
    bgLayer3 = Color(0xFF353638),
    sidebarFill = Color(0xFF1B1B1C),
    inputMajor = Color(0xFF2C2C2E),
    menu = Color(0xFF353638),
    tip = Color(0xFF353638),
    selector = Color(0xFF353638),
    labelPrimary = Color(0xFFF9FAFB),
    labelSecondary = Color(0xFFCFD3D6),
    labelTertiary = Color(0xFFADB2B8),
    labelCaption = Color(0xFF81858C),
    labelDimmed = Color(0xFF43454A),
    labelPrimaryDimmed = Color(0xFFEBEEF2),
    ink = Color(0xFFF9FAFB),
    inkInverted = Color(0xFF353638),
    link = Color(0xFF679EFE),
    accent = Color(0xFF679EFE),
    accentTertiary = Color(0xFF34415B),
    accentLight = Color(0xFFD3E2FF),
    accentDeep = Color(0xFF4176E6),
    borderL1 = Color(0x0FFFFFFF),
    borderL2 = Color(0x1FFFFFFF),
    borderL3 = Color(0x29FFFFFF),
    borderL4 = Color(0x33FFFFFF),
    success = Color(0xFF22C55E),
    error = Color(0xFFF25A5A),
    warn = Color(0xFFF59E0B),
    warnTertiary = Color(0xFF27241F),
    ongoing = Color(0xFF5686FE),
    hover = Color(0x14FFFFFF),
    active = Color(0x24FFFFFF),
    bubble = Color(0xFF2C2C2E),
    bubbleHighlight = Color(0xFF43454A),
    sidebarItemHover = Color(0xFF2C2C2E),
    sidebarItemActive = Color(0xFF43454A),
    sendFill = Color(0xFF679EFE),
    sendGlyph = Color(0xFF0F1115),
    codeBlock = Color(0xFF1B1B1C),
    codeBanner = Color(0xFF2C2C2E),
    inlineCode = Color(0xFF292929),
    scrollbar = Color(0xFF3C3C3D),
    synConstant = Color(0xFF4DABF7),
    synString = Color(0xFF69DB7C),
    synComment = Color(0xFFADB5BD),
    synKeyword = Color(0xFFFAA2C1),
    synParameter = Color(0xFFFFA94D),
    synFunction = Color(0xFFB197FC),
    synPunctuation = Color(0xFFCED4DA),
)

internal val LightColors = DshColors(
    isDark = false,
    bgBase = Color(0xFFFFFFFF),
    bgLayer1 = Color(0xFFFFFFFF),
    bgLayer2 = Color(0xFFFFFFFF),
    bgLayer3 = Color(0xFFFFFFFF),
    sidebarFill = Color(0xFFF9FAFB),
    inputMajor = Color(0xFFFFFFFF),
    menu = Color(0xFFFFFFFF),
    tip = Color(0xFFF9FAFB),
    selector = Color(0xFFF9FAFB),
    labelPrimary = Color(0xFF0F1115),
    labelSecondary = Color(0xFF61666B),
    labelTertiary = Color(0xFF81858C),
    labelCaption = Color(0xFFADB2B8),
    labelDimmed = Color(0xFFE1E5EE),
    labelPrimaryDimmed = Color(0xFF151517),
    ink = Color(0xFF0F1115),
    inkInverted = Color(0xFFFFFFFF),
    link = Color(0xFF4176E6),
    accent = Color(0xFF4176E6),
    accentTertiary = Color(0xFFE4EDFD),
    accentLight = Color(0xFFD3E2FF),
    accentDeep = Color(0xFF4176E6),
    borderL1 = Color(0x0A000000),
    borderL2 = Color(0x1A000000),
    borderL3 = Color(0x1F000000),
    borderL4 = Color(0x29000000),
    success = Color(0xFF22C55E),
    error = Color(0xFFEC1313),
    warn = Color(0xFFF59E0B),
    warnTertiary = Color(0xFFFEF5E7),
    ongoing = Color(0xFF5686FE),
    hover = Color(0x0F263148),
    active = Color(0x1A263148),
    bubble = Color(0xFFEDF3FE),
    bubbleHighlight = Color(0xFFD3E2FF),
    sidebarItemHover = Color(0xFFF1F3F5),
    sidebarItemActive = Color(0xFFEBEEF2),
    sendFill = Color(0xFF4176E6),
    sendGlyph = Color(0xFFFFFFFF),
    codeBlock = Color(0xFFF9FAFB),
    codeBanner = Color(0xFFF9FAFB),
    inlineCode = Color(0xFFFAFAFA),
    scrollbar = Color(0xFFE5E5E5),
    synConstant = Color(0xFF1C7ED6),
    synString = Color(0xFF2F9E44),
    synComment = Color(0xFF868E96),
    synKeyword = Color(0xFFD6336C),
    synParameter = Color(0xFFE8590C),
    synFunction = Color(0xFF6741D9),
    synPunctuation = Color(0xFF495057),
)

/** Spacing lives on the 2dp grid the web client uses. */
object DshSpacing {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 6.dp
    val md: Dp = 8.dp
    val lg: Dp = 12.dp
    val xl: Dp = 16.dp
    val xxl: Dp = 24.dp
    val xxxl: Dp = 32.dp
}

/** Border-radius inventory, verbatim from the CSS. */
object DshRadius {
    val sm: Dp = 6.dp
    val md: Dp = 8.dp
    val lg: Dp = 10.dp
    val card: Dp = 12.dp
    val thumb: Dp = 16.dp
    val bubble: Dp = 22.dp
    val panel: Dp = 24.dp
    val pill: Dp = 999.dp
}

private val DshTypography = Typography(
    // The markdown "content ladder". The web default is 14/24.
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 20.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 24.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 26.sp,
        lineHeight = 32.sp,
    ),
)

/** Named styles the transcript and chrome use directly. */
object DshType {
    val messageBody = TextStyle(fontSize = 14.sp, lineHeight = 24.sp)
    val userBubble = TextStyle(fontSize = 14.sp, lineHeight = 24.sp)
    val bodyLarge = TextStyle(fontSize = 14.sp, lineHeight = 24.sp)
    val bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 20.sp)
    val bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp)
    val labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
    val labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
    val titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)
    val titleLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)
    val titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
    val rowTitle = TextStyle(fontSize = 13.sp, lineHeight = 24.sp, color = Color.Unspecified)
    val rowSummary = TextStyle(fontSize = 13.sp, lineHeight = 24.sp)
    val meta = TextStyle(fontSize = 13.sp, lineHeight = 24.sp)
    val micro = TextStyle(fontSize = 11.sp, lineHeight = 14.sp)
    val code = TextStyle(fontSize = 11.sp, lineHeight = 19.sp, fontFamily = FontFamily.Monospace)
    val codeSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontFamily = FontFamily.Monospace)
    val inlineCode = TextStyle(fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    val heading1 = TextStyle(fontSize = 21.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
    val heading2 = TextStyle(fontSize = 18.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
    val heading3 = TextStyle(fontSize = 16.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
}

val LocalDshColors: ProvidableCompositionLocal<DshColors> = staticCompositionLocalOf { DarkColors }

/**
 * The AMOLED appearance: the dark palette with every surface taken to black.
 *
 * A transform rather than a third hand-written palette. `DshColors` has fifty colour
 * fields, and the seven appearances multiply that into a table nobody can review; the
 * modes differ in a handful of *rules*, not in three hundred independent choices, so
 * they are expressed as the rules.
 *
 * Only the surfaces and borders move. Text, the accent and every semantic colour stay
 * exactly as the dark palette has them — that is what keeps "AMOLED" an appearance
 * rather than a second design. The borders are lifted slightly instead of darkened:
 * on pure black the dark palette's translucent white strokes are the only thing
 * separating a card from the void, and scaling them with the surface would erase the
 * edges the layout depends on.
 */
internal fun DshColors.toAmoled(): DshColors = copy(
    isDark = true,
    bgBase = Color.Black,
    bgLayer1 = AMOLED_SURFACE,
    bgLayer2 = AMOLED_LIFTED,
    bgLayer3 = AMOLED_LIFTED,
    sidebarFill = AMOLED_SURFACE,
    inputMajor = AMOLED_SURFACE,
    menu = AMOLED_LIFTED,
    tip = AMOLED_LIFTED,
    selector = AMOLED_LIFTED,
    bubble = AMOLED_SURFACE,
    bubbleHighlight = AMOLED_LIFTED,
    sidebarItemHover = AMOLED_SURFACE,
    sidebarItemActive = AMOLED_LIFTED,
    codeBlock = Color.Black,
    codeBanner = AMOLED_SURFACE,
    inlineCode = AMOLED_SURFACE,
    scrollbar = AMOLED_OUTLINE,
    borderL1 = Color(0x0AFFFFFF),
    borderL2 = Color(0x14FFFFFF),
    borderL3 = Color(0x1FFFFFFF),
    borderL4 = Color(0x2EFFFFFF),
)

/** #0A0A0A — one step off black, so a card reads as a surface and not a hole. */
private val AMOLED_SURFACE = Color(0xFF0A0A0A)

/** #141414 — the raised step: menus, selected rows, the second layer. */
private val AMOLED_LIFTED = Color(0xFF141414)

/** #1A1A1A — scrollbar and hairlines, which must stay visible against black. */
private val AMOLED_OUTLINE = Color(0xFF1A1A1A)

/**
 * The Monet appearance: the wallpaper's colours, on top of the same layout.
 *
 * Deliberately *not* "replace the palette with Material You's". `DshColors` names the
 * web client's own aliases — `bgLayer1`, `labelPrimaryDimmed`, `synKeyword` — and
 * Material You has no opinion about any of them; swapping in Google's scheme wholesale
 * would repaint the transcript's code syntax and the tool-status colours with hues
 * chosen for a completely different vocabulary.
 *
 * So exactly two things come from the wallpaper: the **accent family** (which the web
 * itself treats as the one adjustable colour) and the **base surfaces**. Everything
 * semantic — success, warning, error, the syntax palette, the bubble — keeps the
 * app's own values, because those carry meaning a wallpaper cannot know.
 *
 * Below API 31 there is no wallpaper palette to read; [dshPalette] then passes
 * `null` and the already-chosen light or dark base comes through untouched.
 *
 * Not `@Composable`: the scheme is handed in rather than looked up, which is what
 * lets the window (which has no composition) resolve the same palette Compose does.
 */
private fun DshColors.toMonet(dark: Boolean, monet: MonetPalette): DshColors {
    val scheme = monet.scheme
    return copy(
        accent = scheme.primary,
        link = scheme.primary,
        // The send button is the primary action; it follows the accent, and its glyph
        // has to follow the fill or it disappears into it.
        sendFill = scheme.primary,
        sendGlyph = scheme.onPrimary,
        accentLight = scheme.primaryContainer,
        accentTertiary = scheme.secondaryContainer,
        bgBase = scheme.background,
        bgLayer1 = scheme.surfaceVariant,
        bgLayer2 = scheme.surfaceVariant,
        bgLayer3 = scheme.surfaceContainerHighest,
        sidebarFill = scheme.surface,
        inputMajor = scheme.surfaceVariant,
        menu = scheme.surfaceContainerHighest,
        tip = scheme.surfaceContainerHigh,
        selector = scheme.surfaceContainerHighest,
        bubble = scheme.surfaceVariant,
        bubbleHighlight = scheme.secondaryContainer,
        sidebarItemHover = scheme.surfaceVariant,
        sidebarItemActive = scheme.secondaryContainer,
        // Text keeps the app's own ladder rather than Material's on/onVariant pair:
        // the transcript has six label steps and Material supplies two.
        borderL3 = scheme.outline,
        borderL4 = scheme.outlineVariant,
    )
}

/** Material roles mapped from DSH tokens for the few M3 components in use. */
private fun DshColors.toMaterialScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = sendGlyph,
        background = bgBase,
        onBackground = labelPrimary,
        surface = bgBase,
        onSurface = labelPrimary,
        surfaceVariant = menu,
        onSurfaceVariant = labelSecondary,
        outline = borderL3,
        error = error,
        onError = Color.White,
    )
}

object DshTheme {
    val colors: DshColors
        @Composable get() = LocalDshColors.current
}

/**
 * The palette an appearance resolves to, outside composition.
 *
 * This is the same decision [DshTheme] makes, written down where something that is
 * *not* a composable can reach it — the Android window, which is painted before the
 * first frame and has no composition to read a token from. The window and Compose are
 * then looking at one function instead of two copies of the same `when`.
 *
 * [monet] is supplied by the caller because wallpaper colours only exist on a
 * `Context` and only from API 31; `null` means "this appearance, without the
 * wallpaper part", which is exactly what a phone below API 31 would render anyway.
 * Passing it in also keeps this function a pure function of its arguments, which is
 * what makes it testable on the JVM.
 */
fun dshPalette(
    themeMode: String,
    systemDark: Boolean,
    monet: MonetPalette? = null,
): DshColors {
    // `parse`, not the raw string: a stored id from another build must resolve to an
    // appearance rather than fall through every branch below.
    val mode = ThemeMode.parse(themeMode)
    val dark = ThemeMode.isDark(mode, systemDark)
    val base = if (dark) DarkColors else LightColors
    return when {
        mode == ThemeMode.AMOLED -> base.toAmoled()
        monet != null && ThemeMode.isMonet(mode) -> base.toMonet(dark, monet)
        else -> base
    }
}

/**
 * The wallpaper colours [dshPalette] needs, read once on a [android.content.Context].
 *
 * A plain holder rather than the `ColorScheme` itself so that the two things Monet
 * actually takes — which side of the light/dark divide, and the Material scheme built
 * for it — travel together into the pure resolver.
 */
class MonetPalette(val scheme: ColorScheme)

/**
 * The wallpaper colours for [themeMode], or `null` when there is nothing to read.
 *
 * Two cases return `null`, and they are the same case from the caller's side: an
 * appearance that is not wallpaper-derived, and a phone below API 31 where Material
 * You does not exist. [dshPalette] then draws the plain light or dark palette on the
 * side the reader asked for, which is a valid appearance rather than a missing one.
 *
 * Not `@Composable` on purpose: the Android window has to be painted from the same
 * answer before Compose exists, and the wallpaper lookup never needed a composition —
 * only a `Context`.
 */
fun monetPalette(context: Context, themeMode: String, systemDark: Boolean): MonetPalette? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val mode = ThemeMode.parse(themeMode)
    if (!ThemeMode.isMonet(mode)) return null
    return MonetPalette(
        if (ThemeMode.isDark(mode, systemDark)) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        },
    )
}

@Composable
fun DshTheme(
    themeMode: String = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val context = LocalContext.current
    // Remembered, not recomputed: building a Material scheme from the wallpaper is not
    // free, and the answer only changes when the appearance or the system's light/dark
    // setting does.
    val monet = remember(themeMode, systemDark, context) {
        monetPalette(context, themeMode, systemDark)
    }
    val palette = dshPalette(themeMode, systemDark, monet)
    CompositionLocalProvider(LocalDshColors provides palette) {
        MaterialTheme(
            colorScheme = palette.toMaterialScheme(),
            typography = DshTypography,
            content = content,
        )
    }
}
