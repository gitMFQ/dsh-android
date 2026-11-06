// Vendored from chenaizhang/KernelSU-Style-UI-Kit (GPL-3.0) — see NOTICE.md.
// Upstream path: app/src/main/java/com/example/kernelsustyleuikit/ui/component/FloatingBottomBar.kt
// Change: package renamed, colours from DshTheme, the UiMode/blur locals replaced by GlassTier,
// //       and the miuix-blur half split into FloatingBottomBarLiquid.kt so API 26 cannot reach it.

package uk.xa0.dsh.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.xa0.dsh.ui.glass.GlassBarBackdrop
import uk.xa0.dsh.ui.glass.GlassTier
import uk.xa0.dsh.ui.glass.LocalGlassBarEnabled
import uk.xa0.dsh.ui.glass.currentGlassTier
import uk.xa0.dsh.ui.glass.glassLift
import uk.xa0.dsh.ui.glass.glassSurface
import uk.xa0.dsh.ui.theme.DshTheme

/** One destination. [key] is stable across appearances and labels. */
data class BottomBarItem(
    val key: String,
    val label: String,
    val icon: ImageVector,
)

/**
 * The panel's height, and the only place it is written down.
 *
 * The pressed pill is taller than the resting one (78/56, see [DampedDragAnimation]), which is
 * why the panel is 64 while the pill inside is 56: the difference is the room the squeeze needs
 * before it starts clipping.
 *
 * **Public on purpose**: a caller that floats the bar over its own content has to reserve room
 * for it, and the honest way to do that is to read the number the bar actually draws rather
 * than to copy `64.dp` into another module and let the two drift apart.
 */
val BarHeight = 64.dp
internal val PillHeight = 56.dp

/** The pill's own inset from the panel edge, on each side. */
internal val BarInset = 4.dp

/**
 * The width one tab asks for before the row distributes spare room.
 *
 * Public for the same reason [BarHeight] is: a caller laying the bar out beside something
 * else has to know how wide it will be, and copying `64.dp` into `:app` lets the two drift.
 */
val BarTabMinWidth = 64.dp

/**
 * The width the bar measures itself to for [count] tabs: every tab at its minimum, plus
 * the content inset on both sides.
 *
 * A function rather than a value because the bar's width follows its item count, and that
 * count is the caller's — the dock beside it reserves exactly this much before the two
 * panes start to morph.
 */
fun barNaturalWidth(count: Int): Dp = BarTabMinWidth * count.coerceAtLeast(1) + BarInset * 2

/**
 * Per-tab scale, provided by the bar to whatever it is laying out.
 *
 * This is the one composition local the vendor keeps, and it is not the kind rule 6 is about:
 * the upstream globals were *configuration* — a local that changed what the bar meant and that
 * any ancestor could rewrite. This carries a value that exists only while one bar is composed
 * (its current press progress) and is meaningless outside it. The alternative is to fold the
 * scale into [FloatingBottomBarItem]'s parameters, which would make every caller thread a float
 * it cannot compute. It stays `internal`, so it is not part of the module's API.
 */
internal val LocalFloatingBottomBarTabScale = staticCompositionLocalOf { { 1f } }

/**
 * The floating tab bar: a capsule of icons-and-labels with one sliding highlight.
 *
 * ## Three fidelities, one design
 *
 * The painting is chosen by [currentGlassTier], and the *motion* is identical on all three:
 * [FloatingBarState] is shared, so a device that cannot blur still gets the same slide, the same
 * rubber band and the same squeeze. That is the point of the tier split in LiquidGlass.kt — the
 * tiers are three renderings of one material, not three bars.
 *
 *  - **LIQUID** (API 33+): real backdrop blur with refraction and a tilt-lit specular edge, in
 *    [LiquidBottomBar]. That file is the only one in this module that both *names and executes*
 *    miuix-blur code; here the backdrop arrives as an opaque [GlassBarBackdrop], so this file
 *    names none of it and an API 26 device never enters the branch that would.
 *  - **TRANSLUCENT** (31-32) and **FLAT** (26-30): [Modifier.glassSurface] — the same
 *    fill-and-hairline material every other floating surface in the app uses, at the alpha its
 *    own tier prescribes.
 *
 * ## Why the highlight slides instead of fading
 *
 * A highlight that faded in place would leave the eye to work out *which* tab just changed.
 * Sliding it from the old index to the new one is the entire affordance — and it is why the tab
 * width is measured rather than derived: the highlight is positioned in pixels, so a wrong width
 * puts it visibly off-centre.
 *
 * ## Item count
 *
 * [tabsCount] is whatever the caller laid out, and the bar never adds or removes an item at
 * runtime. A destination that appeared only sometimes would change the bar's width *while the
 * reader is looking at it*.
 */
@Composable
fun FloatingBottomBar(
    modifier: Modifier = Modifier,
    selectedIndex: () -> Int,
    onSelected: (index: Int) -> Unit,
    tabsCount: Int,
    backdrop: GlassBarBackdrop? = null,
    content: @Composable RowScope.() -> Unit
) {
    // The tier check comes first and returns, rather than choosing a modifier inside the
    // layout. Since 3.2b this file does not name a single miuix-blur type — the backdrop
    // arrives as a [GlassBarBackdrop] and is unwrapped in the branch below — so what the
    // split guarantees is now stronger than before: the only *execution* of miuix-blur code
    // on any tier happens inside `LiquidBottomBar`, in its own file, behind this check.
    val tier = currentGlassTier
    if (tier == GlassTier.LIQUID && backdrop != null && LocalGlassBarEnabled.current) {
        LiquidBottomBar(
            modifier = modifier,
            selectedIndex = selectedIndex,
            onSelected = onSelected,
            tabsCount = tabsCount,
            // The one place the handle is unwrapped. Everything above this line — every
            // caller, this file, the overload below — deals only in GlassBarBackdrop.
            backdrop = backdrop.layer,
            content = content,
        )
        return
    }

    FlatBottomBar(
        modifier = modifier,
        selectedIndex = selectedIndex,
        onSelected = onSelected,
        tabsCount = tabsCount,
        content = content,
    )
}

/**
 * The 26-32 rendering: one translucent capsule and a tinted pill behind the active tab.
 *
 * Nothing here touches miuix-blur, in names or in execution: the backdrop the caller passes is
 * a [GlassBarBackdrop], [uk.xa0.dsh.ui.glass.glassBarLayer] is what turns it into a recorded
 * layer, and this function is never handed either — [FloatingBottomBar] drops the backdrop on
 * the way here, which is right, because on a tier that cannot sample one there is nothing to
 * sample.
 *
 * The execution part of the rule still matters, because the dependency ships in the APK on every
 * device: miuix-blur is an ordinary compile dependency, so its classes are present even where
 * they cannot run. Invoking one of them below API 33 is the `NoClassDefFoundError` this
 * arrangement is built to avoid, and it is avoided by the early return in [FloatingBottomBar]
 * rather than by anything in this function.
 */
@Composable
private fun FlatBottomBar(
    modifier: Modifier,
    selectedIndex: () -> Int,
    onSelected: (Int) -> Unit,
    tabsCount: Int,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = DshTheme.colors
    val shape = CircleShape
    val state = rememberFloatingBarState(tabsCount, selectedIndex, onSelected)

    Box(
        modifier = modifier.width(IntrinsicSize.Min),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            Modifier
                .onGloballyPositioned { coords -> state.onBarMeasured(coords.size.width) }
                // The shadow is drawn on every tier: it is a Compose-drawn shadow, not a
                // platform effect, and without it the capsule has no separation from the
                // transcript behind it on a light appearance.
                // `glassLift` and not a shadow written here: drawn centred, the shadow sits
                // under the translucent fill and shows through it, which measured as a grey
                // capsule on a white page. See `glassLift` for the arithmetic.
                .glassLift(shape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                // Fill, hairline stroke and clip, from the module's one glass function: the
                // alpha and the stroke colour are the tier's business, not this file's.
                .glassSurface(shape)
                .height(BarHeight)
                .padding(BarInset),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )

        if (state.tabWidthPx > 0f) {
            // Drawn after the row, but only the tinted fill: the icons stay legible because
            // the fill is an alpha wash rather than an opaque pill.
            Box(
                Modifier
                    .padding(horizontal = BarInset)
                    .graphicsLayer {
                        val progressOffset = state.damped.value * state.tabWidthPx
                        translationX = if (state.isLtr) {
                            progressOffset + state.panelOffset
                        } else {
                            -progressOffset + state.panelOffset
                        }
                    }
                    .then(state.damped.modifier)
                    .width(state.tabWidth)
                    .height(PillHeight)
                    .background(colors.accent.copy(alpha = 0.15f), shape),
            )
        }
    }
}

/**
 * One tab: icon over label, weighting its share of the row.
 *
 * The weight is what makes the layout fixed: every tab gets the same width whatever its label
 * says, so selecting one never moves the others.
 */
@Composable
fun RowScope.FloatingBottomBarItem(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val scale = LocalFloatingBottomBarTabScale.current
    Column(
        modifier
            .widthIn(min = BarTabMinWidth)
            // The scale is read inside a `graphicsLayer` block, not passed as a modifier
            // argument, so the press animation changes a layer property instead of
            // recomposing every tab on every frame.
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
            }
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .semantics {
                contentDescription = label
                this.selected = selected
            }
            .fillMaxHeight()
            .weight(1f),
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

/**
 * The convenience shape: a list of destinations in, a key out.
 *
 * A thin wrapper over the slot-based composable — it exists so the app does not re-implement
 * icon-over-label four times, and so the item colours are decided once. Adding a destination is
 * a change to the list, not to the bar.
 */
@Composable
fun FloatingBottomBar(
    items: List<BottomBarItem>,
    selectedKey: String,
    onSelect: (key: String) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: GlassBarBackdrop? = null,
) {
    val colors = DshTheme.colors
    // One lookup table per list identity: a caller that rebuilt the list on every
    // recomposition would otherwise restart the highlight on every frame.
    val keys = remember(items) { items.map { it.key } }
    val indexOfKey = remember(keys) { keys.withIndex().associate { it.value to it.index } }
    val selectedIndex = indexOfKey[selectedKey] ?: 0

    FloatingBottomBar(
        modifier = modifier,
        selectedIndex = { indexOfKey[selectedKey] ?: selectedIndex },
        onSelected = { index -> keys.getOrNull(index)?.let(onSelect) },
        tabsCount = items.size,
        backdrop = backdrop,
    ) {
        items.forEachIndexed { index, item ->
            val active = index == selectedIndex
            val tint = if (active) colors.accent else colors.labelSecondary
            FloatingBottomBarItem(
                onClick = { onSelect(item.key) },
                label = item.label,
                selected = active,
            ) {
                // Material's Icon/Text rather than miuix's: this module does not depend on the
                // miuix components, and the two would draw the same glyph anyway. The tint
                // comes from DshTheme, so all seven appearances drive it.
                Icon(imageVector = item.icon, contentDescription = null, tint = tint)
                Text(
                    text = item.label,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = tint,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible
                )
            }
        }
    }
}
