package uk.xa0.dsh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uk.xa0.dsh.ui.components.DshIconVectors
import uk.xa0.dsh.ui.glass.LocalGlassBarBackdrop
import uk.xa0.dsh.ui.glass.liquidPane
import uk.xa0.dsh.ui.nav.BottomBarItem
import uk.xa0.dsh.ui.theme.DshRadius
import uk.xa0.dsh.ui.theme.DshSpacing
import uk.xa0.dsh.ui.theme.DshTheme
import uk.xa0.dsh.ui.theme.DshType

/**
 * The four places the bottom bar can take you.
 *
 * ## Why this is not [ChatView] any more (3.3)
 *
 * 2.4 put the three *conversation views* — Chat, Trajectory and Shell — in the bottom
 * bar, and recorded the rule that made it work: **the item count is a constant**, because
 * a bar that grows or shrinks under the reader's thumb moves every seat sideways.
 *
 * That rule was never the problem. *What* was in the bar was: Trajectory and Shell are
 * not destinations, they are two other readings of the session already on screen.
 * Promoting Files and Settings to real destinations and demoting the two views to a
 * pill inside the conversation is what fixes the level. The invariant is kept: four
 * values, all unconditional, no member added or removed at runtime.
 *
 * The names are the bar's own vocabulary; the labels are what the reader sees.
 */
internal enum class Destination(val label: String) {
    CHAT("Chat"),
    FILES("Files"),
    PLUGINS("Plugins"),
    SETTINGS("Settings"),
}

/**
 * What the bar draws — one entry per [Destination], in the enum's order.
 *
 * Material icons rather than miuix's set, for the reason 2.4 gave: miuix's icons are not
 * in use anywhere else yet, and mixing the two sets on one bar is a visual decision
 * nobody has made.
 */
internal val DESTINATION_ITEMS: List<BottomBarItem> = listOf(
    BottomBarItem(Destination.CHAT.name, Destination.CHAT.label, DshIconVectors.SendOutline),
    BottomBarItem(Destination.FILES.name, Destination.FILES.label, DshIconVectors.FolderOpenOutline),
    BottomBarItem(Destination.PLUGINS.name, Destination.PLUGINS.label, DshIconVectors.PluginPinwheelOutline),
    BottomBarItem(Destination.SETTINGS.name, Destination.SETTINGS.label, DshIconVectors.SettingsOutline),
)

/**
 * The conversation's own view switcher, as a pill.
 *
 * Three seats in a rounded track, the selected one filled. It replaced the *bottom bar's*
 * first three items, so it sits where the reader last saw them: at the top of the
 * conversation, directly under the header.
 *
 * Deliberately not the bottom bar's component. That one is a navigation surface — it
 * owns the glass, the sliding highlight and the destination semantics; this is a
 * segmented control inside one page, and reusing the bar would make a view switch look
 * like a place you travel to.
 */
@Composable
internal fun ViewPill(
    items: List<BottomBarItem>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DshTheme.colors
    val backdrop = LocalGlassBarBackdrop.current
    Row(
        modifier = modifier
            .liquidPane(backdrop, RoundedCornerShape(DshRadius.pill), flatFill = colors.bgLayer1)
            .padding(DshSpacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(DshSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val active = item.key == selectedKey
            Row(
                Modifier
                    .clip(RoundedCornerShape(DshRadius.pill))
                    .background(if (active) colors.bgLayer3 else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSelect(item.key) }
                    .padding(horizontal = DshSpacing.lg, vertical = DshSpacing.sm)
                    .semantics {
                        selected = active
                        contentDescription = item.label
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    item.icon,
                    contentDescription = null,
                    tint = if (active) colors.labelPrimary else colors.labelSecondary,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(DshSpacing.xs))
                Text(
                    text = item.label,
                    style = DshType.bodySmall.copy(
                        fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                    ),
                    color = if (active) colors.labelPrimary else colors.labelSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The Files destination's shell, until it becomes the tree browser it is meant to be.
 *
 * Kept as a placeholder on purpose: the real page is a VSCode-shaped explorer, and that
 * is a feature with its own PR. What this fixes now is the *level* — Files is a place,
 * not a panel that opens over the conversation.
 */
@Composable
internal fun PlaceholderTab(
    title: String,
    summary: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val colors = DshTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(DshSpacing.xxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = colors.labelTertiary,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(DshSpacing.lg))
        Text(title, style = DshType.titleMedium, color = colors.labelPrimary)
        Spacer(Modifier.height(DshSpacing.sm))
        Text(
            text = summary,
            style = DshType.bodySmall,
            color = colors.labelTertiary,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A settings tab's own page: a back bar and the tab's content.
 *
 * The back bar is part of the design, not a navigation fallback: a settings sub-page is
 * reachable from exactly one place, and it has to say how to leave it.
 *
 * Kept here although the Me page it was written for is gone: `ChatPanel` still composes
 * it for a tab's page and the screenshot baselines still capture it, so deleting it here
 * would be a call-site change in two files this file does not own.
 */
@Composable
internal fun MeSubPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * A slot at the far end of the back bar, for a page that has one more place to go.
     * Null draws nothing: a bar with a dead trailing seat reads as a control that failed
     * to load, and most pages have nowhere further to send the reader.
     */
    trailing: (@Composable () -> Unit)? = null,
    /**
     * The page body, as a [ColumnScope] slot so a long page can take the height the back
     * bar leaves instead of claiming the whole column and pushing the bar off screen.
     */
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = DshTheme.colors
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = DshSpacing.lg, vertical = DshSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Only the arrow is the target, not the whole bar: the trailing slot sits on
            // the same row, and a bar that leaves the page wherever it is tapped would
            // swallow the slot's own click.
            Icon(
                // A left arrow, not the list's chevron: the chevron points *at* a page and
                // this one points *out* of it.
                DshIconVectors.ChevronLeftOutline,
                contentDescription = "Back",
                tint = colors.labelSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(DshRadius.sm))
                    .clickable(onClick = onBack)
                    .padding(DshSpacing.sm)
                    .size(20.dp),
            )
            Spacer(Modifier.width(DshSpacing.xs))
            Text(title, style = DshType.titleMedium, color = colors.labelPrimary)
            if (trailing != null) {
                Spacer(Modifier.weight(1f))
                trailing()
            }
        }
        content()
    }
}
