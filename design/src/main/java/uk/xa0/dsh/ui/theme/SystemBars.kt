package uk.xa0.dsh.ui.theme

import android.app.Activity
import androidx.core.view.WindowCompat

/**
 * The system bars' *appearance*, decided by the app's own palette.
 *
 * ## The bug this fixes
 *
 * `MainActivity` calls `enableEdgeToEdge()` with no arguments. That overload picks the
 * status-bar style with `SystemBarStyle.auto(…)`, whose dark-mode detector reads the
 * **system's** night setting — not the appearance the reader chose in this app. The two
 * disagree in four of the seven appearances.
 *
 * That detector is `SystemBarStyle$Companion$auto$1`, and it is worth stating exactly,
 * because the whole class rests on it: its `invoke(Resources)` does
 * `resources.configuration.uiMode & 0x30 == 0x20` — that is `UI_MODE_NIGHT_MASK` and
 * `UI_MODE_NIGHT_YES` — and returns the answer. Read out of the decompiled
 * `activity-1.9.0` bytecode, not from documentation. It never consults the app.
 *
 * Which appearances are wrong:
 *
 * | Appearance | System | Icons today | Correct |
 * |---|---|---|---|
 * | AMOLED | light | **dark on near-black** | light |
 * | Dark | light | **dark on `#151517`** | light |
 * | Light | dark | **light on white** | dark |
 * | Monet light | dark | **light on a pale surface** | dark |
 *
 * So a reader who forces Dark while their phone is in light mode gets status-bar icons
 * that are dark-on-dark — effectively invisible. Nothing crashes and no test noticed,
 * because the window's chrome is not part of the Compose tree the baselines capture
 * (the same structural blind spot `WindowBackground` exists for).
 *
 * ## Why the colour is not set here
 *
 * Since API 35, `Window.setStatusBarColor` is a no-op: the bars are always transparent
 * and the app draws behind them. This app already does the right thing with the area —
 * `MainActivity`'s root `Box` paints `DshTheme.colors.bgBase` and each screen applies
 * `statusBarsPadding()` — so the bar shows the palette's base surface with no extra
 * drawing. What was missing was only the *icon* side, which is why this class sets
 * nothing but the two flags. On API 26–34 the same is true of the status bar
 * (`enableEdgeToEdge` already makes it transparent); the navigation bar keeps a scrim
 * from `enableEdgeToEdge` itself.
 *
 * ## Why it lives beside [WindowBackground]
 *
 * Same reason, same shape: this is chrome that exists before and outside Compose, it is
 * resolved from the same pure `dshPalette` the theme uses, and it has to be re-applied
 * when the appearance changes. `MainActivity` calls the two from the same two places.
 */
object SystemBars {

    /**
     * Whether the bars want a **light** background — that is, whether the icons are dark.
     *
     * ## The naming trap this method exists to pin down
     *
     * The platform flag is `isAppearanceLightStatusBars`, and despite the word "icons" in
     * most prose about it, `true` means *"this bar's background is light, so draw the icons
     * dark"*. It describes the **background**. An earlier version of this file read it as
     * "the icons are light" and inverted the palette, which put light icons on white — the
     * exact bug the class was written to fix, caught by CI because this box cannot compile
     * `:app`'s tests (`docs/BUILD-AND-CI.md` §0).
     *
     * So: a light palette means a light bar means `true` means dark icons. This is the
     * palette's `isDark` **negated once**, and the single place that negation lives.
     */
    fun lightBackground(themeMode: String, systemDark: Boolean, monet: MonetPalette? = null): Boolean =
        !dshPalette(themeMode, systemDark, monet).isDark

    /**
     * Apply the palette's icon appearance to [activity]'s system bars.
     *
     * [systemDark] is supplied for the same reason [WindowBackground.apply] takes it: a
     * window has no composition to ask, and following the system is one of the seven
     * appearances.
     */
    fun apply(
        activity: Activity,
        themeMode: String,
        systemDark: Boolean = WindowBackground.isSystemDark(activity),
    ) {
        val monet = monetPalette(activity, themeMode, systemDark)
        apply(activity, lightBackground(themeMode, systemDark, monet))
    }

    /**
     * The platform call itself, separated from the decision so the decision can be tested
     * without an Activity and this can be tested with one.
     *
     * `WindowCompat` rather than the raw API: the flag has three different spellings below
     * API 30 (a `View` system-UI flag), on 30+ (`WindowInsetsController`), and the compat
     * wrapper is the only one that covers all of them — this app's minSdk is 26.
     */
    fun apply(activity: Activity, lightBackground: Boolean) {
        val window = activity.window ?: return
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = lightBackground
            // The navigation bar gets the same answer for the same reason. It is not
            // painted by this app (enableEdgeToEdge's scrim is under it), but its icons
            // were wrong in exactly the same four appearances.
            isAppearanceLightNavigationBars = lightBackground
        }
    }
}
