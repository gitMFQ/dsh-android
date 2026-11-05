// Vendored from chenaizhang/KernelSU-Style-UI-Kit (GPL-3.0) — see NOTICE.md.
// Upstream path: app/src/main/java/com/example/kernelsustyleuikit/ui/component/bottombar/NavigationRailMiuix.kt
// Change: package renamed; the app's SettingsRepository persistence replaced by an explicit
// //       expanded/onExpandedChange pair, and the colour comes from DshTheme.

package uk.xa0.dsh.ui.nav

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.NavigationRailState
import top.yukonga.miuix.kmp.basic.NavigationRailValue
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState
import uk.xa0.dsh.ui.glass.LocalGlassBarBackdrop
import uk.xa0.dsh.ui.glass.liquidPane
import uk.xa0.dsh.ui.theme.DshRadius
import uk.xa0.dsh.ui.theme.DshTheme

/**
 * The rail a wide window gets instead of the bar.
 *
 * Expanded/collapsed is passed in and reported back rather than remembered here: it is a user
 * preference that has to survive the process, and a composable in a design module has no business
 * owning storage. The caller keeps it wherever it keeps settings; this only draws it.
 *
 * The rail is a miuix component, so it paints itself from `MiuixTheme`, which [DshTheme] provides
 * once at the app root. No second `MiuixTheme` is wrapped here — that would open an independent
 * palette inside the first.
 */
@Composable
fun SideRail(
    items: List<BottomBarItem>,
    selectedIndex: Int,
    onSelect: (index: Int) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DshTheme.colors
    val backdrop = LocalGlassBarBackdrop.current
    val state = rememberNavigationRailState(
        initialValue = if (expanded) NavigationRailValue.Expanded else NavigationRailValue.Collapsed,
    )
    // One-way on purpose: miuix owns the rail's own expand gesture, so the caller hears about it
    // and persists it, but nothing here drives the state back from the parameter. Feeding the
    // parameter back in is what makes a rail twitch between its two widths on a recomposition.
    ReportRailExpanded(state, onExpandedChange)

    NavigationRail(
        modifier = modifier.liquidPane(
            backdrop,
            RoundedCornerShape(DshRadius.card),
            flatFill = Color.Transparent,
        ),
        state = state,
        // The rail is a full-height column behind everything else, so it takes the first layer
        // rather than the floating-surface token: `menu` would make it read as a popover.
        color = colors.bgLayer1,
        expandContentDescription = "Expand navigation",
        collapseContentDescription = "Collapse navigation",
    ) {
        items.forEachIndexed { index, item ->
            NavigationRailItem(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                icon = item.icon,
                label = item.label,
            )
        }
    }
}

@Composable
private fun ReportRailExpanded(
    state: NavigationRailState,
    onExpandedChange: (Boolean) -> Unit,
) {
    LaunchedEffect(state.isExpanded) {
        onExpandedChange(state.isExpanded)
    }
}
