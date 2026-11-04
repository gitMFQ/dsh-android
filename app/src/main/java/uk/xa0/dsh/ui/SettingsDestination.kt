package uk.xa0.dsh.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uk.xa0.dsh.R
import uk.xa0.dsh.ui.clickableNoRipple
import uk.xa0.dsh.ui.components.DshIconVectors
import uk.xa0.dsh.ui.glass.GlassBarBackdrop
import uk.xa0.dsh.ui.glass.glassLift
import uk.xa0.dsh.ui.glass.liquidPane
import uk.xa0.dsh.ui.settings.MeSettingsActions
import uk.xa0.dsh.ui.settings.MeSettingsPage
import uk.xa0.dsh.ui.settings.MeSettingsState
import uk.xa0.dsh.ui.theme.DshRadius
import uk.xa0.dsh.ui.theme.DshSpacing
import uk.xa0.dsh.ui.theme.DshTheme
import uk.xa0.dsh.ui.theme.DshType

/**
 * Settings, as a page with a hierarchy rather than a sheet over the drawer or a tab
 * strip inside Me.
 *
 * ## Why the rows are levels
 *
 * The reported defect was that the levels were wrong: tapping a row did not enter a
 * page, it changed what a strip of tabs above the list was showing — so "where am I"
 * and "how do I go back" were both the row you had just tapped, and Back left Settings
 * entirely instead of going up one level. Here a row enters a page: the page has a back
 * control, Back goes up one level, and the list you came from is where you land.
 *
 * ## The two groups
 *
 * The split is the one the objective names. Local settings are facts about this phone
 * that the host has no schema for; everything else maps onto a real settings/
 * namespace the host publishes. Keeping them apart is what stops a client preference
 * from looking like a host one that failed to save.
 */
internal enum class SettingsSection(
    val id: String,
    /** The row's own label, as a resource: a section name is the app's voice. */
    @StringRes val title: Int,
    /** The one line under the label, also a resource — never a second copy of it. */
    @StringRes val subtitle: Int,
    val icon: ImageVector,
) {
    LOCAL(
        id = "local",
        title = R.string.settings_section_local_title,
        subtitle = R.string.settings_section_local_subtitle,
        icon = Icons.Rounded.Memory,
    ),
    GENERAL(
        id = "general",
        title = R.string.settings_section_general_title,
        subtitle = R.string.settings_section_general_subtitle,
        icon = DshIconVectors.SlidersTwoOutline,
    ),
    APPEARANCE(
        id = "appearance",
        title = R.string.settings_section_appearance_title,
        subtitle = R.string.settings_section_appearance_subtitle,
        icon = Icons.Rounded.Palette,
    ),
    MODEL(
        id = "model",
        title = R.string.settings_section_model_title,
        subtitle = R.string.settings_section_model_subtitle,
        icon = DshIconVectors.AgentPresetOutline,
    ),
    AGENT(
        id = "agent",
        title = R.string.settings_section_agent_title,
        subtitle = R.string.settings_section_agent_subtitle,
        icon = Icons.Rounded.Inventory2,
    ),
    TERMINAL(
        id = "terminal",
        title = R.string.settings_section_terminal_title,
        subtitle = R.string.settings_section_terminal_subtitle,
        icon = DshIconVectors.CodeOutline,
    ),
    PLUGINS(
        id = "plugins",
        title = R.string.settings_section_plugins_title,
        subtitle = R.string.settings_section_plugins_subtitle,
        icon = DshIconVectors.PluginPinwheelOutline,
    ),
    CRASHES(
        id = "crashes",
        title = R.string.settings_section_crashes_title,
        subtitle = R.string.settings_section_crashes_subtitle,
        icon = Icons.Rounded.BugReport,
    ),
}

/**
 * The Settings page: a bar, then either the section list or the section itself.
 *
 * [onOpenAbout] is a callback rather than a section because About is not a setting — it
 * is a document about the host, and the sheet it opens is the same one the old drawer
 * footer offered.
 */
@Composable
internal fun SettingsDestination(
    state: MeSettingsState,
    actions: MeSettingsActions,
    onOpenAbout: () -> Unit,
    backdrop: GlassBarBackdrop? = null,
    modifier: Modifier = Modifier,
) {
    var section by rememberSaveable { mutableStateOf<String?>(null) }
    val open = SettingsSection.entries.firstOrNull { it.id == section }

    Column(modifier.fillMaxSize()) {
        SettingsBar(
            title = open?.title ?: R.string.settings_title,
            onBack = if (open != null) {
                { section = null }
            } else {
                null
            },
            backdrop = backdrop,
        )
        if (open == null) {
            SettingsList(
                onSelect = { section = it.id },
                onOpenAbout = onOpenAbout,
                modifier = Modifier.weight(1f),
            )
        } else {
            MeSettingsPage(
                id = open.id,
                state = state,
                actions = actions,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * One glass bar at the top of the page.
 *
 * Its own bar rather than the shell's: the shell's is the conversation's, and a page
 * that is not the conversation should not be wearing its title. [onBack] is null on the
 * list, which is what makes the leading circle a back control only where there is
 * somewhere to go back to.
 */
@Composable
private fun SettingsBar(
    @StringRes title: Int,
    onBack: (() -> Unit)?,
    backdrop: GlassBarBackdrop?,
) {
    val colors = DshTheme.colors
    // Resolved here, not inside the semantics block: stringResource is a composable
    // read and the lambda below is not composable.
    val backDescription = stringResource(R.string.settings_back)
    Row(
        Modifier
            .fillMaxWidth()
            // No `statusBarsPadding()` here: the shell publishes the status-bar inset
            // as `chromeDp` for every non-conversation page, and this page is given
            // that as top padding. Adding it again would reserve the clock strip twice
            // and push the bar 24dp down for no reason.
            .height(56.dp)
            .padding(horizontal = DshSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                Modifier
                    .size(40.dp)
                    .glassLift(CircleShape)
                    .liquidPane(backdrop, CircleShape, flatFill = colors.bgLayer1)
                    .clip(CircleShape)
                    .clickableNoRipple(onClick = onBack)
                    // A description, not a visible label: TalkBack reads it, so it is
                    // translated like any other word on the screen.
                    .semantics { contentDescription = backDescription },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    DshIconVectors.ChevronLeftOutline,
                    contentDescription = null,
                    tint = colors.labelPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(DshSpacing.sm))
        }
        Text(
            text = stringResource(title),
            style = DshType.messageBody.copy(fontWeight = FontWeight.Medium),
            color = colors.labelPrimary,
            maxLines = 1,
        )
    }
}

/** The list of sections: settings groups, then the About door. */
@Composable
private fun SettingsList(
    onSelect: (SettingsSection) -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DshSpacing.xl)
            .padding(bottom = DshSpacing.xxxl),
        verticalArrangement = Arrangement.spacedBy(DshSpacing.sm),
    ) {
        Spacer(Modifier.height(DshSpacing.md))
        SettingsSection.entries.forEach { entry ->
            SettingsRow(
                title = entry.title,
                subtitle = entry.subtitle,
                icon = entry.icon,
                onClick = { onSelect(entry) },
            )
        }
        Spacer(Modifier.height(DshSpacing.md))
        SettingsRow(
            title = R.string.settings_about_row_title,
            subtitle = R.string.settings_about_row_subtitle,
            icon = DshIconVectors.InfoOutline,
            onClick = onOpenAbout,
        )
    }
}

/**
 * One row: an icon, a title, a one-line explanation, and a chevron.
 *
 * A card rather than bare text because the list is what a page *is* here — the same
 * rounded shape the settings cards inside each section already wear, so entering a
 * section does not change the material under the reader's finger.
 */
@Composable
private fun SettingsRow(
    @StringRes title: Int,
    @StringRes subtitle: Int,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val colors = DshTheme.colors
    val shape = RoundedCornerShape(DshRadius.lg)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.codeBlock, shape)
            .clickableNoRipple(onClick = onClick)
            .padding(horizontal = DshSpacing.lg, vertical = DshSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = colors.labelSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(DshSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(title),
                style = DshType.messageBody.copy(fontWeight = FontWeight.Medium),
                color = colors.labelPrimary,
                maxLines = 1,
            )
            Text(
                text = stringResource(subtitle),
                style = DshType.bodySmall,
                color = colors.labelTertiary,
                maxLines = 2,
            )
        }
        Icon(
            DshIconVectors.ChevronRightOutline,
            contentDescription = null,
            tint = colors.labelCaption,
            modifier = Modifier.size(18.dp),
        )
    }
}