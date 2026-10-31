package uk.xa0.dsh.ui.theme

import android.app.Activity
import android.content.res.Configuration
import androidx.compose.ui.graphics.toArgb

/**
 * Paints the activity's window in the appearance's base colour.
 *
 * ## Why this exists
 *
 * The window is filled by the platform *before* the first frame of Compose, from the
 * `android:windowBackground` its theme declares. That declaration is a static
 * resource, so it can only ever be one colour — and this app has seven appearances,
 * two of which (light and light Monet) are not dark at all. A light-appearance reader
 * therefore launched into a near-black rectangle and watched it flash away; an AMOLED
 * reader saw a grey-black one before a pure-black app. The two colours also disagreed
 * to begin with: the resource was `#0B0F14` and the dark palette's `bgBase` is
 * `#151517`.
 *
 * ## How
 *
 * Setting the window's background drawable paints the `decorView`, which sits *below*
 * the Compose content and is not part of it. So this cannot change a pixel the
 * screenshot baselines capture, and it cannot cover anything Compose draws. The colour
 * comes from [dshPalette], the same function `DshTheme` uses, so the window and the
 * first Compose frame agree by construction instead of by two edits kept in step.
 *
 * ## What it does not promise
 *
 * Not that nothing flashes. The window can only be repainted once the activity exists
 * and has read the stored appearance, which is why `MainActivity` does it in
 * `onCreate` before `setContent`. What is gone is the *wrong colour*: the static
 * resource is now only the fallback for the instant before that call, and it matches
 * the dark palette, so the common case does not move either.
 */
object WindowBackground {

    /**
     * Repaint [activity]'s window for [themeMode].
     *
     * [systemDark] has to be supplied because following the system is one of the
     * appearances and a window has no composition to ask. By default it is read from
     * the activity's own resources, which is the configuration the platform itself
     * would have used to inflate the window.
     */
    fun apply(activity: Activity, themeMode: String, systemDark: Boolean = isSystemDark(activity)) {
        val monet = monetPalette(activity, themeMode, systemDark)
        paint(activity, dshPalette(themeMode, systemDark, monet).bgBase.toArgb())
    }

    /**
     * The paint itself, separated from the decision.
     *
     * `decorView`, not `window.setBackgroundDrawable`: on the platform those are the
     * same act — `PhoneWindow.setBackgroundDrawable` hands the drawable to exactly
     * this view — but only the view is a thing this project can observe in a JVM test,
     * and the window call is also a no-op before the decor exists. One statement, one
     * behaviour, whether or not a composition has run yet.
     */
    fun paint(activity: Activity, argb: Int) {
        activity.window?.decorView?.setBackgroundColor(argb)
    }

    /** The platform's own answer to "is the system in dark mode right now". */
    fun isSystemDark(activity: Activity): Boolean =
        (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
}
