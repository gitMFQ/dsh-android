package uk.xa0.dsh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Person
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
import uk.xa0.dsh.ui.list.SegmentedColumn
import uk.xa0.dsh.ui.list.SegmentedListItem
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
    ME("Me"),
}

/**
 * What the bar draws — one entry per [Destination], in the enum's order.
 *
 * Material icons rather than miuix's set, for the reason 2.4 gave: miuix's icons are not
 * in use anywhere else yet, and mixing the two sets on one bar is a visual decision
 * nobody has made.
 */
internal val DESTINATION_ITEMS: List<BottomBarItem> = listOf(
    BottomBarItem(Destination.CHAT.name, Destination.CHAT.label, Icons.Rounded.ChatBubble),
    BottomBarItem(Destination.FILES.name, Destination.FILES.label, Icons.Rounded.Folder),
    BottomBarItem(Destination.PLUGINS.name, Destination.PLUGINS.label, Icons.Rounded.Extension),
    BottomBarItem(Destination.ME.name, Destination.ME.label, Icons.Rounded.Person),
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
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(DshRadius.pill))
            .background(colors.bgLayer1)
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

/** One row of the Me page: a settings tab, named but not yet filled. */
internal data class MeEntry(
    val id: String,
    val title: String,
    val summary: String,
    val icon: ImageVector,
)

/**
 * The Me page: who you are on top, the settings tabs under it.
 *
 * The order is deliberate and is the reader's, not the code's: identity first (whose
 * account is this, what is left on it), then the tabs. A settings list with no account
 * header reads as a preferences screen; the header is what makes it "me".
 *
 * Each entry opens a sub-page — that is the shape the reader asked for ("点进去就是设置
 * 页签内容"), and it is why the rows carry a chevron rather than a control: a row that
 * opens a page must not look like a row that toggles a value.
 *
 * ## Why the rows are cards now (3.5b)
 *
 * The bare list this page shipped with said *what the tabs are*; it did not say the tabs
 * were finished, and a row with nothing on it but a chevron reads as a stub. The rows are
 * the vendored [SegmentedColumn] vocabulary (`design/.../ui/list/`) grouped by subject, so
 * the page is the same list language as the pages behind it. The header stays a header:
 * it is identity, not a setting, and it must not look like a row you can open.
 */
@Composable
internal fun MeTab(
    username: String,
    balanceText: String?,
    entries: List<MeEntry>,
    onOpen: (MeEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DshTheme.colors
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = DshSpacing.xl, vertical = DshSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colors.bgLayer2),
                contentAlignment = Alignment.Center,
            ) {
                val initial = username.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                Text(initial, style = DshType.titleLarge, color = colors.labelSecondary)
            }
            Spacer(Modifier.width(DshSpacing.lg))
            Column(Modifier.weight(1f)) {
                Text(
                    text = username.ifBlank { "Not signed in" },
                    style = DshType.titleMedium,
                    color = colors.labelPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                balanceText?.let {
                    Text(it, style = DshType.bodySmall, color = colors.labelTertiary, maxLines = 1)
                }
            }
        }
        // One card per group, so a heading can say *why* these tabs sit together. The
        // grouping is the reader's: what the app looks like and how it behaves, then what
        // powers it, then the rare or dangerous, then the facts.
        ME_GROUPS.forEach { group ->
            val rows = entries.filter { it.id in group.ids }
            if (rows.isEmpty()) return@forEach
            SegmentedColumn(
                title = group.title,
                modifier = Modifier.padding(horizontal = DshSpacing.xl, vertical = DshSpacing.sm),
                content = rows.map { entry ->
                    { MeEntryRow(entry) { onOpen(entry) } }
                },
            )
        }
        Spacer(Modifier.height(DshSpacing.xxl))
    }
}

/** One settings tab on the Me page: its icon, its name, what is behind it, and a chevron. */
@Composable
private fun MeEntryRow(entry: MeEntry, onClick: () -> Unit) {
    val colors = DshTheme.colors
    SegmentedListItem(
        onClick = onClick,
        leading = {
            Icon(
                entry.icon,
                contentDescription = null,
                tint = colors.labelSecondary,
                modifier = Modifier.size(20.dp),
            )
        },
        headline = {
            Text(entry.title, style = DshType.rowTitle, color = colors.labelPrimary)
        },
        supporting = {
            Text(
                text = entry.summary,
                style = DshType.rowSummary,
                color = colors.labelTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailing = {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = colors.labelTertiary,
                modifier = Modifier.size(18.dp),
            )
        },
    )
}

/**
 * How the Me page's tabs are grouped, by [MeEntry.id].
 *
 * A group is a sentence about the tabs rather than a folder: "what it looks like and how
 * it behaves" is a different question from "what powers it", and the archived list is not
 * a setting at all. Ids not named by any group are not drawn, which is deliberate — this
 * list is the page's table of contents, and a tab added to [ME_ENTRIES] without a home
 * here should show up as a missing page in review rather than silently at the bottom.
 */
internal val ME_GROUPS: List<MeGroup> = listOf(
    MeGroup("This device", listOf("appearance", "general", "terminal")),
    MeGroup("What answers", listOf("model", "agent")),
    MeGroup("Rare and dangerous", listOf("plugins", "archived", "crashes")),
    MeGroup("Facts", listOf("about")),
)

/** One card of the Me page: a heading and the tab ids it holds, in [ME_ENTRIES] order. */
internal data class MeGroup(val title: String, val ids: List<String>)

/**
 * A settings tab's own page: a back bar and the tab's content.
 *
 * The back bar is part of the design, not a navigation fallback: a settings sub-page is
 * reachable from exactly one place, and it has to say how to leave it.
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
                // this one points *out* of it. The old icon was the right-pointing chevron
                // beside a "Back" label, which read as an offer to go further in.
                Icons.Rounded.ArrowBack,
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