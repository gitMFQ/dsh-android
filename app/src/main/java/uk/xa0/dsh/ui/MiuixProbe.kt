package uk.xa0.dsh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import top.yukonga.miuix.kmp.preference.SwitchPreference
import uk.xa0.dsh.ui.theme.DshSpacing
import uk.xa0.dsh.ui.theme.DshTheme

/** The tag the probe's test looks for, so it needs no text matching. */
const val MIUIX_PROBE_TAG = "miuix-probe"

/**
 * **The 2.3 gate: can miuix be drawn inside this app's existing theme stack?**
 *
 * A spike, not a feature. \`docs/PHASE-2-PLAN.md\` §1.4 records the question and why it
 * has to be answered before 2.4 rather than during it: miuix has been on the
 * classpath since 1.4 with **zero call sites**, so nothing had ever proved that
 * \`MiuixTheme\` and this app's \`DshTheme\` compose together. If they do not, four
 * later PRs take the self-drawn route instead, and finding that out mid-2.4 is the
 * expensive way to learn it.
 *
 * ## The measured answer (this probe exists to keep it honest)
 *
 * Yes, with one prerequisite, and one limitation worth knowing before designing 2.5:
 *
 * 1. **It renders.** A real \`SwitchPreference\` — miuix's own text styles, switch and
 *    row metrics — draws inside \`DshTheme\`. That was recorded from CI, not assumed.
 * 2. **The prerequisite is the test JVM's version.** miuix 0.9.4 ships Java 21
 *    bytecode (class file 65). D8 desugars that into the APK without complaint — the
 *    debug APK built and contains 4 868 references to \`top/yukonga/miuix\` — but the
 *    JVM running the Robolectric tests has to be able to *load* it. On Java 17 that is
 *    \`UnsupportedClassVersionError\` the instant a miuix component is composed; on
 *    Java 21 it draws. So the gates run on 21 and nothing else changes.
 * 3. **The palette does not cross by itself.** With \`MiuixTheme\` given no arguments,
 *    the switch painted \`#3482FF\` — miuix's own default accent — while this app's
 *    light accent is \`#4176E6\`. Two accents on one screen is exactly the bug the
 *    seven appearances exist to prevent, so a miuix shell must be handed a \`Colors\`
 *    built from \`DshTheme.colors\` rather than relying on defaults. That adapter is
 *    \`ui/theme/MiuixPalette.kt\`, and \`DshTheme\` now applies it at the root — which
 *    is why the row below no longer wraps itself in a \`MiuixTheme\` of miuix's own
 *    defaults: the capture is now evidence that the *wiring* works, not only that
 *    miuix composes. A switch that still came out \`#3482FF\` would mean the root is
 *    not handing miuix the app's palette.
 *
 * ## Why a screen of its own
 *
 * So it can be deleted without a trace. It is reachable from nowhere in the app —
 * its only caller is the screenshot test in \`app/src/test/.../ScreenBaselineTest.kt\`.
 */
@Composable
fun MiuixProbe(modifier: Modifier = Modifier) {
    var on by remember { mutableStateOf(true) }
    Column(
        modifier
            .fillMaxSize()
            .background(DshTheme.colors.bgBase)
            .padding(DshSpacing.lg)
            .testTag(MIUIX_PROBE_TAG),
    ) {
        // No `MiuixTheme` wrapper any more, and that is the change worth capturing.
        //
        // 2.3 wrapped this row in `MiuixTheme(colors = lightColorScheme())` on purpose, so
        // the baseline would show miuix's *own* palette (`#3482FF`) beside the app's
        // (`#4176E6`) and prove point 3 above. `DshTheme` now provides the adapted
        // palette itself, so the same row drawn with no arguments at all is the evidence
        // that the adapter is wired: if this capture's switch still comes out `#3482FF`,
        // the root is not handing miuix the app's colours.
        SwitchPreference(
            title = "Keep the connection standing",
            checked = on,
            onCheckedChange = { on = it },
        )
    }
}
