package uk.xa0.dsh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import uk.xa0.dsh.diag.DiagScrollColumn
import uk.xa0.dsh.diag.diagDrag
import uk.xa0.dsh.ui.settings.AgentSettingCards
import uk.xa0.dsh.ui.settings.AppearanceSettingCard
import uk.xa0.dsh.ui.settings.ArchivedSettingCards
import uk.xa0.dsh.ui.settings.BusyEnterSettingCard
import uk.xa0.dsh.ui.settings.ConversationSettingCard
import uk.xa0.dsh.ui.settings.HostSettingsNotes
import uk.xa0.dsh.ui.settings.MeSettingsActions
import uk.xa0.dsh.ui.settings.MeSettingsState
import uk.xa0.dsh.ui.settings.ModelSettingCards
import uk.xa0.dsh.ui.settings.PermissionSettingCard
import uk.xa0.dsh.ui.settings.PluginSettingCards
import uk.xa0.dsh.ui.settings.SettingNote
import uk.xa0.dsh.ui.theme.DshRadius
import uk.xa0.dsh.ui.theme.DshSpacing
import uk.xa0.dsh.ui.theme.DshTheme
import uk.xa0.dsh.ui.theme.DshType

/**
 * The port of the web Settings modal, as one scrolled document.
 *
 * The web shell is a left nav rail over five sections, in registration order:
 * **General** (0) / **Models** (10) / **Plugins** (15) / **Agent presets** (20) /
 * **Archived sessions** (25). A phone sheet cannot afford a 188px rail, so the sections
 * stack vertically in that same order, each with its nav label as the heading.
 *
 * ## What changed in 3.5b
 *
 * This file used to *own* every row: the host-write rules, the seven appearances, the
 * preset roster, the archived paging. The Me page's tabs then needed the same rows as
 * their own pages, and two copies of a settings document is the shape this repository
 * has spent three phases deleting — the write rules in particular (`writable`,
 * `applies`, `secrets`) are subtle enough that a second copy would drift on the first
 * schema change. So the rows moved to `ui/settings/SettingsCards.kt`, and this sheet is
 * now the *arrangement*: the web's section order over the same cards the tabs draw.
 *
 * The consequence worth naming: a change to a row is now a change to one card, and both
 * surfaces get it. The sheet is the complete document (every section, in the web's
 * order); a tab is one section with room to breathe. Neither is a second implementation.
 *
 * ## What is not here
 *
 * The client's own **Connection** rows — the optional LAN address, the live route, and
 * the certificate this host is pinned to — are facts about the host rather than settings,
 * so they live on the About sheet with the host's other facts (`AboutScreen.kt`).
 */
@Composable
fun SettingsContent(
    state: MeSettingsState,
    actions: MeSettingsActions,
    onDismiss: () -> Unit,
) {
    val colors = DshTheme.colors
    val scroll = rememberScrollState()
    DiagScrollColumn("settings", scroll)
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(scroll)
            .diagDrag("settings") { scroll.value }
            .padding(bottom = DshSpacing.xxxl),
    ) {
        // The web header: title + Close (`settings.title` / `settings.close`).
        Row(
            Modifier.fillMaxWidth().padding(horizontal = DshSpacing.xxl, vertical = DshSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Settings",
                style = DshType.heading2,
                color = colors.labelPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Close",
                style = DshType.bodyMedium,
                color = colors.link,
                modifier = Modifier
                    .clip(RoundedCornerShape(DshRadius.sm))
                    .clickableNoRipple(onClick = onDismiss)
                    .padding(DshSpacing.sm),
            )
        }

        // Web General registration order: permission (-20), language (0), appearance (10),
        // font-size (11), transcript-view (12), composer-enter (20). The Language row is
        // omitted: this client has no locale setting. Appearance is a section of its own
        // in the web's rail too, so it keeps its own heading here even though the Me page
        // gives it a tab.
        SectionHeading("General")
        HostSettingsNotes(state)
        PermissionSettingCard(state, actions)
        AppearanceSettingCard(state, actions)
        ConversationSettingCard(state, actions)
        BusyEnterSettingCard(state, actions)

        SectionHeading("Models")
        ModelSettingCards(state)

        SectionHeading("Plugins")
        PluginSettingCards()

        SectionHeading("Agent presets")
        AgentSettingCards(state, actions)

        SectionHeading("Archived sessions")
        ArchivedSettingCards(state, actions)
    }
}

/**
 * A section break and its heading, `internal` rather than private because the About sheet
 * draws the same breaks: the two surfaces sit side by side in the drawer, so a second
 * heading style would read as two different screens.
 */
@Composable
internal fun SectionHeading(title: String) {
    val colors = DshTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = DshSpacing.xl)) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.borderL1))
        Text(
            text = title,
            style = DshType.labelLarge,
            color = colors.labelPrimary,
            modifier = Modifier.padding(horizontal = DshSpacing.xxl, vertical = DshSpacing.lg),
        )
    }
}

/** A section's own prose (the web's `intro` / read-only notices). Shared with About. */
@Composable
internal fun SectionNote(text: String) = SettingNote(text)

/**
 * A failure note. The text is always somebody else's own wording — the host's refusal
 * message or the transport's failure — because a client paraphrase of a schema or CAS
 * error hides the one detail the reader needs.
 */
@Composable
internal fun SectionErrorNote(text: String) = uk.xa0.dsh.ui.settings.SettingErrorNote(text)

/**
 * The web's `displayPermissionPreset` (`presentation.ts`): a known machine value renders
 * under its product label only while the host's own name is that value or the product
 * label — a host that names `read-only` itself keeps its wording. `Unavailable` is the
 * web's own empty-selection copy, for a client that has no value at all.
 *
 * `internal`, not private: the About sheet names the open session's access mode with the
 * same function, and a second copy would drift from the host's wording.
 */
internal fun permissionDisplay(
    value: String,
    options: List<uk.xa0.dsh.PermissionOption>,
): String {
    if (value.isBlank()) return "Unavailable"
    val name = options.firstOrNull { it.value == value }?.name ?: value
    val product = uk.xa0.dsh.model.PERMISSION_PRESET_LABELS[value]
    return if (product != null && (name == value || name == product)) {
        product
    } else {
        uk.xa0.dsh.model.displayPresetName(name)
    }
}
