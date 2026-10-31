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
import top.yukonga.miuix.kmp.theme.MiuixTheme
import uk.xa0.dsh.ui.theme.DshSpacing
import uk.xa0.dsh.ui.theme.DshTheme

/** The tag the gate's own test looks for, so a probe needs no text matching. */
const val MIUIX_PROBE_TAG = "miuix-probe"

/**
 * **The 2.3 gate: can miuix be drawn inside this app's existing theme stack?**
 *
 * This is a spike, not a feature. \`docs/PHASE-2-PLAN.md\` §1.4 records the question
 * it answers and why it has to be answered *before* 2.4 rather than during it: miuix
 * has been on the classpath since 1.4 with **zero call sites**, so nothing has ever
 * proved that \`MiuixTheme\` and this app's \`DshTheme\` compose without fighting. If
 * they fight, four later PRs (2.5 / 2.7 / 2.8 / 2.9) take the self-drawn route
 * instead, and finding that out mid-2.4 would be the expensive way.
 *
 * ## What it demonstrates, specifically
 *
 * 1. **Nesting order.** \`MiuixTheme\` goes *inside* \`DshTheme\`, so miuix's own
 *    composition locals are provided where a miuix component reads them, while the
 *    app's palette stays the one above it. The reverse (miuix outside) would make
 *    every \`DshTheme.colors\` read above the miuix boundary either throw or fall back
 *    to the default local — that is the failure this test would show as a wrong
 *    colour rather than a crash.
 * 2. **A real component draws.** \`SwitchPreference\` is a miuix preference row, the
 *    exact shape 2.8 wants to reuse for settings. It exercises miuix's own text
 *    styles, its switch and its row metrics — not just a \`Text\` that would pass
 *    under any theme.
 * 3. **The palette crosses.** The switch's \`Colors\` are built from \`DshTheme.colors\`
 *    rather than miuix's defaults, so an accent change in the app's palette is
 *    visible here. That is the property that decides whether a miuix shell can be
 *    re-skinned by the seven appearances at all.
 *
 * ## Why it is a separate screen and not a settings row
 *
 * Because it must be **deletable without a trace**. If this answers "yes", the next
 * PRs build on it and this file goes away; if it answers "no", it goes away with
 * nothing else to unpick. It is deliberately reachable from nowhere in the app — see
 * \`MiuixGateScreen\`'s only caller, the screenshot baseline test.
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
        // Inside, not outside: the app's palette stays the outermost authority, and
        // miuix only supplies what its own components read.
        MiuixTheme {
            SwitchPreference(
                title = "Keep the connection standing",
                checked = on,
                onCheckedChange = { on = it },
            )
        }
    }
}
