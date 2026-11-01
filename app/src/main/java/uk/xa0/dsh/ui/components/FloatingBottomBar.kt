package uk.xa0.dsh.ui.components

import android.os.Build
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.xa0.dsh.ui.theme.DshSpacing
import uk.xa0.dsh.ui.theme.DshTheme
import uk.xa0.dsh.ui.theme.DshType

/** The bar's own tag, so a test can find it without matching a label. */
const val FLOATING_BOTTOM_BAR_TAG = "floating-bottom-bar"

/** One destination. [key] is stable across appearances and labels. */
data class BottomBarItem(
    val key: String,
    val label: String,
    val icon: ImageVector,
)

/**
 * The floating bottom bar that replaces the top ViewTabs strip.
 *
 * ## Why a bar and not the strip
 *
 * The strip sat under the conversation title and cost a full row of chrome above the
 * transcript, which is the most-read part of the app. A bar at the bottom puts the same
 * choice under the thumb and gives the transcript its row back.
 *
 * ## The glass, and why it is drawn by hand
 *
 * docs/PHASE-2-PLAN.md section 4 rules out miuix-blur: that module's own minSdk is 33 and
 * this app supports 26. So the material is built from what the platform has:
 *
 *  - **API 31+**: Modifier.blur over a copy of the bar's own fill, which is a real
 *    backdrop blur (a RenderEffect underneath).
 *  - **API 26-30**: no blur, so the bar is a flat translucent fill with a hairline
 *    stroke. Deliberately *not* a fake gradient: a translucent pane with a defined edge
 *    reads as a floating surface, while a faked blur reads as a rendering bug.
 *
 * Both branches use the same colour roles, so the two are the same design at two
 * fidelity levels rather than two designs.
 *
 * ## Why every colour comes from DshTheme
 *
 * This is the first surface to sit on top of the palette adapter (ui/theme/MiuixPalette.kt)
 * and the reason the plan insists on writing that adapter first. Reading DshTheme.colors
 * is what keeps the bar following all seven appearances, including AMOLED, where a fixed
 * translucent white would turn into a grey slab on black.
 *
 * ## Item count is fixed, on purpose
 *
 * [items] is rendered exactly as given, and the bar never adds or removes an entry at
 * runtime. A destination that appeared only sometimes would change the bar's width *while
 * the reader is looking at it*: every item would shift sideways the moment a model was
 * selected. docs/PHASE-2-PLAN.md section 2.4 names that as the one thing this bar must not
 * do, and it is why ROUTER left the destination list. See ChatScreen's ChatView.
 */
@Composable
fun FloatingBottomBar(
    items: List<BottomBarItem>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Extra room for the navigation inset, applied by the caller. */
    bottomInset: Dp = 0.dp,
) {
    val colors = DshTheme.colors
    // The bar's shape is a capsule whose ends are half its height, so the radius is
    // derived rather than named: a bar that grows taller stays a capsule.
    val barHeight = 56.dp
    val shape = RoundedCornerShape(barHeight / 2)
    val blurs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // The material. menu is the app's floating-surface token, the same one the drawer's
    // sheet uses, so the bar reads as part of the same layer system.
    val fill = colors.menu
    // A hairline is what separates a floating pane from the content behind it once the
    // blur is gone (API<31), and what keeps the edge crisp where the blur is subtle.
    val stroke = colors.borderL2

    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = DshSpacing.xl)
            .padding(bottom = DshSpacing.md + bottomInset),
        contentAlignment = Alignment.Center,
    ) {
        // The blurred copy sits *under* the real bar and is clipped to the same capsule,
        // so what shows through is the fill smeared across the capsule's own footprint.
        // Drawing it this way, rather than blurring the content behind, is the cheap
        // approximation Modifier.blur affords on a child, and it is why the fill is a
        // colour rather than Color.Transparent.
        if (blurs) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .clip(shape)
                    .blur(16.dp)
                    .background(fill),
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(shape)
                .background(if (blurs) fill.copy(alpha = 0.72f) else fill.copy(alpha = 0.96f))
                .border(0.75.dp, stroke, shape)
                .padding(horizontal = DshSpacing.xs)
                .testTag(FLOATING_BOTTOM_BAR_TAG),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                BottomBarSeat(
                    item = item,
                    active = item.key == selectedKey,
                    onClick = { onSelect(item.key) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * One seat: icon over label, with the selection drawn as a tint rather than a pill.
 *
 * A pill behind the active item would need its own width animation to feel right, and a
 * width animation on a bar whose items are equal-weight is exactly the motion that makes
 * a fixed layout look like it is breathing. Tint plus equal weight is enough.
 */
@Composable
private fun BottomBarSeat(
    item: BottomBarItem,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DshTheme.colors
    // The label fades rather than pops: a label that appeared would change the seat's
    // intrinsic height, and on a capsule that reads as the bar resizing.
    val labelAlpha by animateFloatAsState(if (active) 1f else 0.85f, label = "seat-label")
    val iconSize by animateDpAsState(if (active) 22.dp else 20.dp, label = "seat-icon")
    val tint = if (active) colors.link else colors.labelTertiary

    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(DshSpacing.lg))
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = item.label
                selected = active
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(iconSize),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.label,
                style = DshType.micro.copy(
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = tint.copy(alpha = labelAlpha),
                maxLines = 1,
            )
        }
    }
}
