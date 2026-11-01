package uk.xa0.dsh.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * The bridge from this app's palette to miuix's.
 *
 * ## Why this file has to exist
 *
 * miuix paints its components from `MiuixTheme.colorScheme`, a [Colors] of 53 roles that
 * **defaults to miuix's own accent `#3482FF`** (`Colors.kt` `lightColorScheme(primary = …)`).
 * This app's light accent is `#4176E6` (`LightColors.accent`). Handing `MiuixTheme` no
 * arguments therefore draws a miuix component in one blue beside an app component in
 * another — and it does not fail, log, or look broken enough to notice in review. That
 * was measured, not assumed: the 2.3 probe (`ui/MiuixProbe.kt` before this file existed)
 * rendered a `SwitchPreference` whose thumb was `#3482FF` while the surrounding screen was
 * `#4176E6`. See `docs/PHASE-2-PLAN.md` §2.2.
 *
 * ## How it is built
 *
 * Two rules, in this order:
 *
 * 1. **Semantic roles come from `DshColors`.** Anything miuix names after a *meaning* this
 *    app already has an opinion about — accent, error, the surface ladder, the label
 *    ladder, the divider — is assigned from the palette, so all seven appearances
 *    (Light / Dark / System / AMOLED / Monet×3) drive miuix with no second table.
 * 2. **The rest is derived the way miuix itself derives it.** miuix ships an `internal`
 *    `mapMd3RolesToMiuixColorsCommon` (`theme/MonetMapping.kt`) that it *cannot* be made to
 *    call from outside. Its rules are reproduced here rather than invented: in particular
 *    the nine derived disabled roles are "the normal role at some alpha, composited over a
 *    surface until opaque", because miuix draws disabled states by painting those colours
 *    as if they were opaque. (`disabledOnSurface` is the tenth `disabled*` role and miuix
 *    passes `onSurface` straight through for it — `theme/MonetMapping.kt:142` — but this
 *    mapping knocks it back instead, which is more useful and is called out where it is
 *    assigned.)
 *
 * ## What it is not
 *
 * Not a Monet/seed generator: this app has no `ColorScheme` to start from, it has
 * `DshColors`, which is deliberately *not* Material's vocabulary (`bgLayer1`,
 * `labelPrimaryDimmed`, `synKeyword`). Handing miuix Material's roles would have repainted
 * miuix's own components in hues chosen for a different set of names. The mapping goes
 * from the app's semantics outward.
 *
 * Not `@Composable`: a pure function of one argument, so it is testable on the JVM and so
 * `DshTheme` can `remember` it — the 53-role object is not free and must not be rebuilt on
 * every recomposition.
 */
fun DshColors.toMiuixColors(): Colors {
    // The surface miuix's translucent colours are flattened onto. miuix's own mapping uses
    // its light/dark scheme's own defaults around the same values; `bgBase` is this app's
    // equivalent, and it is what the components sit on.
    val base = bgBase
    val baseContainerHigh = if (isDark) menu else tip

    /**
     * miuix composites every disabled/translucent role over a surface so it stays opaque.
     *
     * The final `copy(alpha = 1f)` is not decoration. `compositeOver` returns the blended
     * result *including* the accumulated alpha, and miuix paints these roles as if they were
     * opaque, so anything short of 1f is a colour that is not the one miuix intended. It
     * happens to be harmless today only because `bgBase` is opaque in all seven appearances
     * — an implicit premise, which is exactly the kind that breaks when a translucent
     * surface is added later.
     */
    fun opaque(foreground: Color, background: Color): Color =
        if (foreground.alpha >= 1f) foreground else foreground.compositeOver(background).copy(alpha = 1f)

    /**
     * The ink that reads best on [fill], by WCAG relative luminance.
     *
     * `onError` needs this rather than a borrowed token, and the reason is measurable:
     * `sendGlyph` follows the accent, and under Monet the accent follows the wallpaper —
     * a wallpaper-tinted glyph on the error fill measured **3.99:1** on Monet dark. On
     * AMOLED the dark palette's `sendGlyph` is *not* the best available ink either, because
     * that appearance's base is pure black and beats it.
     *
     * So the rule is stated once, here: pick the more legible of the app's light ink and
     * pure black.
     *
     * **The dark end is `Color.Black` on purpose**, and it is the second thing measurement
     * changed. The obvious candidate — `sendGlyph`, the app's own "glyph on a coloured
     * fill" token — is *not stable*: under Monet it is remapped to `scheme.onPrimary`,
     * which is a light colour, so both candidates collapse onto the light end and the
     * contrast drops to **3.99:1** on Monet dark. An error fill is a saturated surface where
     * the most legible ink is what matters, and that is the one place where naming black
     * outright beats borrowing a token that moves with the wallpaper. It also happens to
     * clear 4.5:1 on the light palette (4.66:1), which no lighter ink can.
     */
    fun inkOn(fill: Color): Color {
        fun luminance(c: Color): Float {
            fun lin(v: Float) = if (v <= 0.03928f) v / 12.92f else
                Math.pow(((v + 0.055) / 1.055).toDouble(), 2.4).toFloat()
            return 0.2126f * lin(c.red) + 0.7152f * lin(c.green) + 0.0722f * lin(c.blue)
        }
        fun contrast(a: Color, b: Color): Float {
            val hi = maxOf(luminance(a), luminance(b))
            val lo = minOf(luminance(a), luminance(b))
            return (hi + 0.05f) / (lo + 0.05f)
        }
        val light = if (isDark) labelPrimary else Color.White
        val dark = Color.Black
        return if (contrast(light, fill) >= contrast(dark, fill)) light else dark
    }

    // The scheme whose *non-semantic* defaults are miuix's own; the app then overrides
    // every role it has an opinion about. Starting from the right side of the light/dark
    // divide matters for the roles left alone (outline, windowDimming, sliderKeyPoint).
    val base53 = if (isDark) darkColorScheme() else lightColorScheme()

    val disabledPrimary = opaque(accent.copy(alpha = 0.38f), base)
    val disabledOnPrimary = opaque(sendGlyph.copy(alpha = 0.38f), disabledPrimary)
    val disabledPrimaryButton = opaque(accent.copy(alpha = 0.38f), base)
    val disabledOnPrimaryButton = opaque(sendGlyph.copy(alpha = 0.6f), disabledPrimaryButton)
    val disabledPrimarySlider = opaque(accent.copy(alpha = 0.38f), base)
    // Flattened *before* the alpha is knocked back, and the order is the whole point:
    // `borderL3` is itself translucent (12% white on dark, 12% black on light), so
    // `borderL3.copy(alpha = 0.5f)` would *replace* its alpha with 50% instead of
    // multiplying it — making the disabled track three to four times heavier than the
    // enabled one it is supposed to recede from. Measured before this fix: disabled
    // #7F7F7F light / #8A8A8B dark against enabled #E0E0E0 / #464647.
    val disabledSecondary = opaque(opaque(borderL3, base).copy(alpha = 0.5f), base)
    val disabledOnSecondary = opaque(labelPrimary.copy(alpha = 0.38f), disabledSecondary)
    val disabledSecondaryVariant = opaque(baseContainerHigh.copy(alpha = 0.6f), base)
    val disabledOnSecondaryVariant = opaque(labelPrimary.copy(alpha = 0.38f), disabledSecondaryVariant)

    return base53.copy(
        // ── The accent: the one thing 2.3 measured going wrong ────────────────────
        primary = accent,
        onPrimary = sendGlyph,
        primaryVariant = accentDeep,
        onPrimaryVariant = accentLight,
        primaryContainer = accentTertiary,
        onPrimaryContainer = accent,

        // ── Error ────────────────────────────────────────────────────────────────
        error = error,
        // Not `inkInverted` (3.68:1 against the dark palette's error) and not `sendGlyph`
        // either — that one follows the accent, so Monet moves it with the wallpaper and it
        // measured 3.99:1 there. Derived from the fill instead; see [inkOn].
        onError = inkOn(error),
        // Derived from `error` over the palette's own base rather than two literal hexes:
        // literals would not follow AMOLED or Monet, which is precisely what this file
        // exists to guarantee. 12% is the app's own rule for an error tint
        // (`ui/TrajectoryScreen.kt:570,574`).
        errorContainer = opaque(error.copy(alpha = 0.12f), base),
        onErrorContainer = error,

        // ── Disabled (derived exactly as miuix derives them) ─────────────────────
        disabledPrimary = disabledPrimary,
        disabledOnPrimary = disabledOnPrimary,
        disabledPrimaryButton = disabledPrimaryButton,
        disabledOnPrimaryButton = disabledOnPrimaryButton,
        disabledPrimarySlider = disabledPrimarySlider,
        disabledSecondary = disabledSecondary,
        disabledOnSecondary = disabledOnSecondary,
        disabledSecondaryVariant = disabledSecondaryVariant,
        disabledOnSecondaryVariant = disabledOnSecondaryVariant,

        // ── Secondary: miuix uses this pair for an *unchecked* switch track/thumb ─
        secondary = borderL3,
        onSecondary = labelSecondary,
        secondaryVariant = baseContainerHigh,
        onSecondaryVariant = labelPrimary,
        secondaryContainer = active,
        onSecondaryContainer = labelSecondary,
        secondaryContainerVariant = bgLayer3,
        onSecondaryContainerVariant = labelTertiary,

        // ── Tertiary ─────────────────────────────────────────────────────────────
        // miuix paints a *selected* dropdown row with this pair (`basic/Dropdown.kt:428,513`),
        // so it is the app's selection tint, not its warning tint. Mapping the warning
        // surface here would put a selected row on `warnTertiary` and its text in
        // `warn` — 1.99:1 on the light palette, and a selection that reads as an alarm.
        tertiaryContainer = accentTertiary,
        onTertiaryContainer = accent,
        tertiaryContainerVariant = accentTertiary,

        // ── Surfaces: the app's own ladder, not Material's ───────────────────────
        background = bgBase,
        onBackground = labelPrimary,
        // miuix's own default here is a grey-violet (`Colors.kt:374`), and the only reader is
        // `SmallTitle` — the group headings. `MonetMapping.kt:135` quirks it onto the accent
        // instead, which is *not* copied on purpose: every group heading in the app would turn
        // blue and compete with the accent for attention. The tertiary label is the honest
        // equivalent and measures better against the base than miuix's default does.
        onBackgroundVariant = labelTertiary,
        surface = bgLayer1,
        onSurface = labelPrimary,
        surfaceVariant = bgLayer2,
        // `onSurface` at 80%, following miuix's own rule (`theme/MonetMapping.kt:89`), and
        // deliberately *not* `labelSecondary`: that is opaque, so `opaque()` would short-circuit
        // and this role would come out identical to `onSurfaceVariantSummary` below — two
        // label steps collapsing into one, with the test that checks alpha passing anyway.
        onSurfaceSecondary = opaque(labelPrimary.copy(alpha = 0.8f), bgLayer1),
        onSurfaceVariantSummary = labelSecondary,
        onSurfaceVariantActions = labelTertiary,
        disabledOnSurface = opaque(labelPrimary.copy(alpha = 0.38f), base),
        surfaceContainer = bgLayer1,
        onSurfaceContainer = labelPrimary,
        onSurfaceContainerVariant = labelSecondary,
        surfaceContainerHigh = baseContainerHigh,
        onSurfaceContainerHigh = opaque(labelSecondary, baseContainerHigh),
        surfaceContainerHighest = bgLayer3,
        onSurfaceContainerHighest = labelPrimary,

        // ── Strokes ──────────────────────────────────────────────────────────────
        // Drawn at 0.75dp (`basic/Divider.kt:71`). `borderL3` is the app's visible hairline
        // and `dividerLine` is the same idea one step softer — the same relative order
        // miuix's own defaults use, so they must not be swapped.
        outline = borderL3,
        dividerLine = borderL2,

        // ── Overlays and sliders ────────────────────────────────────────────────
        // miuix's own values are 0.6/0.3 black; kept, because the dim is over the app's
        // own surfaces and a palette-derived dim would double the darkening.
        windowDimming = if (isDark) Color.Black.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.3f),
        sliderKeyPoint = accent,
        sliderKeyPointForeground = baseContainerHigh,
        sliderBackground = opaque(accent.copy(alpha = 0.2f), base),
    )
}
