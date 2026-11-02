package uk.xa0.dsh.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uk.xa0.dsh.AgentPresetOption
import uk.xa0.dsh.ModelOption
import uk.xa0.dsh.data.BusyEnter
import uk.xa0.dsh.data.ThemeMode
import uk.xa0.dsh.model.GeneralSettings
import uk.xa0.dsh.model.SessionItem
import uk.xa0.dsh.model.SettingsChoice
import uk.xa0.dsh.model.SettingsField
import uk.xa0.dsh.term.TerminalCellSize
import uk.xa0.dsh.ui.agentPresetDescription
import uk.xa0.dsh.ui.agentPresetLabel
import uk.xa0.dsh.ui.clickableNoRipple
import uk.xa0.dsh.ui.components.DshTextField
import uk.xa0.dsh.ui.isBuiltInPreset
import uk.xa0.dsh.ui.list.SegmentedColumn
import uk.xa0.dsh.ui.list.SegmentedDropdownItem
import uk.xa0.dsh.ui.list.SegmentedListItem
import uk.xa0.dsh.ui.list.SegmentedRadioItem
import uk.xa0.dsh.ui.list.SegmentedSwitchItem
import uk.xa0.dsh.ui.search.SessionSearchCopy
import uk.xa0.dsh.ui.theme.DshRadius
import uk.xa0.dsh.ui.theme.DshSpacing
import uk.xa0.dsh.ui.theme.DshTheme
import uk.xa0.dsh.ui.theme.DshType

/**
 * The settings vocabulary, shared by the Settings sheet and the Me page's tabs.
 *
 * ## Why the cards live in one place
 *
 * Phase 3.5b turned the Me page's tabs from placeholders into pages, and the sheet was
 * already drawing the same five sections. Two renderings of one settings document is the
 * exact shape this repository has spent three phases deleting: the host-write rules
 * (writable, applies, secrets) are subtle enough that a second copy would drift on the
 * first schema change. So the sections became *cards* — one composable per subject,
 * taking [MeSettingsState] and [MeSettingsActions] — and both surfaces compose them.
 *
 * ## The row vocabulary
 *
 * Rows are the vendored [SegmentedColumn] set (design/.../ui/list/), which is miuix's
 * BasicComponent inside a card: the same 56dp row, leading icon column and end-action
 * measure that miuix's own preferences use. That is why a card here has no hand-drawn
 * dividers, no per-row shape and no fill of its own — the card owns all three.
 *
 * ## What a row may offer
 *
 * Nothing here decides that a setting is editable. [SettingsField] carries the host's own
 * answer and [writerFor] refuses to hand back a control when the answer is no: a row the
 * host would refuse stays a plain reading rather than a control that silently fails. That
 * rule is inherited verbatim from the sheet this replaces, and it is the reason these
 * cards take the whole state rather than a handful of values.
 *
 * ## Why every card builds its rows into a local list
 *
 * [SegmentedColumn] takes its rows as a List of composable lambdas rather than a trailing
 * content lambda, because the card has to know how many rows it holds to draw separators
 * *between* them. A trailing lambda cannot bind to a List parameter, so each call site
 * names its rows first. That is the whole reason for the local vals below.
 */

// ------------------------------------------------------------------ page cards

/**
 * Appearance: the seven shapes this device can draw.
 *
 * The list is the client's, not the host's appearance.choices: the schema's enum is
 * light/dark/system, while AMOLED and the three Monet variants describe *this device* — an
 * OLED panel and a wallpaper — which no host can know about. A successful local change is
 * still mirrored to the host for the three modes it can express.
 */
@Composable
internal fun AppearanceSettingCard(state: MeSettingsState, actions: MeSettingsActions) {
    val appearance = state.hostSettings?.field(
        GeneralSettings.THEME_NS,
        GeneralSettings.THEME_PREFERENCE_FIELD,
    )
    val appearanceWrite = writerFor(appearance, state, actions)
    val index = APPEARANCE_CHOICES.indexOfFirst { it.value == state.themeMode }
    val rows: List<@Composable () -> Unit> = listOf(
        {
            SegmentedDropdownItem(
                title = "Theme",
                summary = "Light and dark follow the system; AMOLED and Monet are this device's own.",
                icon = Icons.Rounded.Palette,
                items = APPEARANCE_CHOICES.map { it.label },
                selectedIndex = index,
                onItemSelected = { picked ->
                    val mode = APPEARANCE_CHOICES[picked].value
                    actions.onTheme(mode)
                    // Mirror only what the host can represent: sending "amoled" would be
                    // refused by a schema whose enum is light/dark/system, and the reader
                    // already sees the appearance they picked.
                    if (mode in HOST_THEME_MODES) appearanceWrite?.invoke(mode)
                },
            )
        },
    )
    SegmentedColumn(title = "Appearance", content = rows)
}

/**
 * General: the host's own document state, as notes above the rows.
 *
 * The order is the web's registration order (permission -20, appearance 10, font-size 11,
 * transcript-view 12, composer-enter 20). Appearance is its own tab here rather than a row
 * in this card, so the remaining rows keep their relative order.
 */
@Composable
internal fun HostSettingsNotes(state: MeSettingsState) {
    // "Wired" is decided by the caller handing the panel anything at all: with every host
    // fact absent the rows render the client-local preferences they always did, so a
    // caller that has not adopted settings/describe is not left with five dead rows.
    val wired = state.hostSettings != null || state.hostSettingsLoading ||
        state.hostSettingsError != null
    if (wired) {
        when {
            state.hostSettingsError != null -> SettingErrorNote(state.hostSettingsError)
            state.hostSettings == null -> SettingNote("Reading settings…")
            // The web's own read-only copy (settings.models.readOnly); the fact is the
            // same one on this panel.
            !state.hostSettings.writable ->
                SettingNote("The settings document is read-only in this deployment.")
        }
    }
    // The refusal is about an action the user just took, so it keeps the host's own
    // wording — a schema error paraphrased by the client would hide which value the host
    // rejected.
    state.settingsWriteError?.let { SettingErrorNote(it) }
}

/** Permission: what a *new* session starts at, not the open session's own mode. */
@Composable
internal fun PermissionSettingCard(state: MeSettingsState, actions: MeSettingsActions) {
    val field = state.hostSettings?.field(
        GeneralSettings.PERMISSION_NS,
        GeneralSettings.PERMISSION_FIELD,
    )
    val write = writerFor(field, state, actions)
    val hostLabels = field?.choices?.map { it.label }.orEmpty()
    // The host's option set when it has one; otherwise the roster the app already holds,
    // which is what the sheet drew before the read existed.
    val items = hostLabels.ifEmpty { state.permissionOptions.map { it.name } }
    val hostValue = field?.value
    val selected = if (hostValue != null && hostLabels.isNotEmpty()) {
        field.choices.indexOfFirst { it.value == hostValue }
    } else {
        state.permissionOptions.indexOfFirst { it.value == state.defaultPermission }
    }
    val rows: List<@Composable () -> Unit> = listOf(
        {
            SegmentedDropdownItem(
                title = "Default mode",
                summary = "What new sessions start at. The open session's own mode is on About.",
                items = items,
                selectedIndex = selected,
                enabled = write != null,
                onItemSelected = { picked ->
                    pickedSettingValue(
                        hostChoices = field?.choices.orEmpty(),
                        fallback = state.permissionOptions.map { it.value },
                        picked = picked,
                    )?.let { value -> write?.invoke(value) }
                },
            )
        },
    )
    SegmentedColumn(title = "Permission", content = rows)
}

/** Conversation display: the web's ui-chat.transcriptView, and what it folds. */
@Composable
internal fun ConversationSettingCard(state: MeSettingsState, actions: MeSettingsActions) {
    val field = state.hostSettings?.field(
        GeneralSettings.CHAT_NS,
        GeneralSettings.TRANSCRIPT_FIELD,
    )
    val write = writerFor(field, state, actions)
    val hostLabels = field?.choices?.map { it.label }.orEmpty()
    val items = hostLabels.ifEmpty { listOf("Compact", "Normal") }
    val selected = field?.choices?.indexOfFirst { it.value == field.value }
        ?.takeIf { it >= 0 }
        ?: if (state.transcriptView == "normal") 1 else 0
    val rows: List<@Composable () -> Unit> = listOf(
        {
            SegmentedDropdownItem(
                title = "Completed turns",
                summary = "Compact folds a finished turn behind one summary line; " +
                    "normal keeps every row.",
                items = items,
                selectedIndex = selected,
                enabled = write != null,
                onItemSelected = { picked ->
                    // No client-side fallback here: the transcript view is the host's own
                    // setting and this client has no local copy of it, so an unwritable
                    // host has nothing to write (the row is disabled in that case).
                    pickedSettingValue(
                        hostChoices = field?.choices.orEmpty(),
                        fallback = emptyList(),
                        picked = picked,
                    )?.let { value -> write?.invoke(value) }
                },
            )
        },
    )
    SegmentedColumn(title = "Conversation display", content = rows)
}

/** Send behaviour while the agent is running: queue the message, or steer the turn. */
@Composable
internal fun BusyEnterSettingCard(state: MeSettingsState, actions: MeSettingsActions) {
    val field = state.hostSettings?.field(
        GeneralSettings.CONVERSATION_NS,
        GeneralSettings.BUSY_ENTER_FIELD,
    )
    val write = writerFor(field, state, actions)
    val wired = state.hostSettings != null
    val choices = field?.choices.orEmpty().ifEmpty { BUSY_ENTER_CHOICES }
    val current = field?.value ?: state.busyEnter
    val selected = choices.indexOfFirst { it.value == current }
    val rows: List<@Composable () -> Unit> = listOf(
        {
            SegmentedDropdownItem(
                title = "While the agent is running",
                summary = "What Enter and Send do mid-turn; Cmd/Ctrl+Enter uses the other.",
                items = choices.map { it.label },
                selectedIndex = selected,
                // A wired-but-unwritable host keeps the row as a reading; an unwired one
                // stays live on the client-local preference, as it always was.
                enabled = !wired || (write != null && state.settingsSaving != field?.key),
                onItemSelected = { picked ->
                    val value = choices.getOrNull(picked)?.value ?: return@SegmentedDropdownItem
                    if (write != null) write(value) else actions.onBusyEnter(value)
                },
            )
        },
    )
    SegmentedColumn(title = "Send behavior while busy", content = rows)
}

/** Models: the host's catalog, grouped by provider, as a reading rather than a picker. */
@Composable
internal fun ModelSettingCards(state: MeSettingsState) {
    val providers = remember(state.models, state.providerOrder) {
        // providerOrder is built from each entry's *display* name, so keying the sort on
        // the machine id (as this once did) matched nothing and the cards fell back to
        // catalog order — the composer's own sheet sorts by name.
        state.models.groupBy { it.provider }
            .toList()
            .sortedBy { (_, options) ->
                val name = options.firstOrNull()
                    ?.let { it.providerName.ifBlank { it.provider } }
                    .orEmpty()
                state.providerOrder.indexOf(name).let { if (it < 0) Int.MAX_VALUE else it }
            }
    }

    SettingNote("Enter your API keys to use models from the following providers.")
    // A remote phone is handed the memory settings scope, which the web marks read-only;
    // the notice is the web's own settings.models.readOnly copy.
    SettingNote("The settings document is read-only in this deployment.")

    if (providers.isEmpty()) {
        SettingNote("The host has not reported any model providers for this session.")
        return
    }

    providers.forEach { (_, options) ->
        // The provider's own display name is on every route it advertises.
        val name = options.firstOrNull()
            ?.let { it.providerName.ifBlank { it.provider } }
            .orEmpty()
        val rows: List<@Composable () -> Unit> = options.map { model ->
            { ModelRow(model, state.selectedModel) }
        }
        SegmentedColumn(title = name, content = rows)
    }
}

/** One catalog route: its display name, its machine id, and whether it is the one answering. */
@Composable
private fun ModelRow(model: ModelOption, selected: ModelOption?) {
    val colors = DshTheme.colors
    val current = selected?.provider == model.provider && selected.model == model.model
    SegmentedListItem(
        headline = {
            Text(
                text = model.name,
                style = DshType.rowTitle,
                color = colors.labelPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supporting = {
            Text(
                text = model.model,
                style = DshType.rowSummary,
                color = colors.labelTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        // A route is not a control here: choosing one is the composer's model chip, where
        // the effort that goes with it is chosen in the same sheet. This page answers
        // "what can answer", not "switch to it".
        trailing = if (current) {
            { SettingTag("Current", colors.accent) }
        } else {
            null
        },
    )
}

/** Agent: the preset roster, and the picker's own state. */
@Composable
internal fun AgentSettingCards(state: MeSettingsState, actions: MeSettingsActions) {
    SettingNote(
        "A preset is the plugin composition one session's agent runs — its tools, " +
            "prompt, and capabilities. Duplicate an existing one and make it yours, " +
            "or let the agent draft one for you in Creator mode.",
    )

    // The picker switch is a settings/update, so a remote client cannot flip it; it
    // renders the app's real state disabled.
    val pickerRows: List<@Composable () -> Unit> = listOf(
        {
            SegmentedSwitchItem(
                title = "Allow switching Agent modes",
                summary = "When enabled, new tasks can choose Standard, PTC, Creator, " +
                    "Minimal, and custom modes. Only affects new tasks.",
                checked = state.agentModePickerEnabled,
                onCheckedChange = {},
                enabled = false,
            )
        },
    )
    SegmentedColumn(content = pickerRows)

    if (state.agentPresetOptions.isEmpty()) {
        SettingNote("Could not load agent presets.")
        return
    }

    // The row the web tags inUse = 'New task default': the explicit new-session pick, else
    // the host's own default.
    val defaultId = state.agentPreset.ifBlank {
        state.agentPresetOptions.firstOrNull { it.isDefault }?.id ?: ""
    }
    val builtIn = state.agentPresetOptions.filter { isBuiltInPreset(it.id) }
    val custom = state.agentPresetOptions.filterNot { isBuiltInPreset(it.id) }

    if (builtIn.isNotEmpty()) {
        val rows: List<@Composable () -> Unit> = builtIn.map { option ->
            { PresetRow(option, option.id == defaultId, state, actions) }
        }
        SegmentedColumn(title = "Built-in", content = rows)
    }
    if (custom.isNotEmpty()) {
        val rows: List<@Composable () -> Unit> = custom.map { option ->
            { PresetRow(option, option.id == defaultId, state, actions) }
        }
        SegmentedColumn(title = "Custom", content = rows)
    }
}

/**
 * One preset: the radio is the default, the row is the target.
 *
 * "Set as default" is client-local here (the stored new-session pick), so it stays live;
 * the web's own action is a settings write a phone cannot make. A broken preset cannot be
 * chosen at all — the host would refuse it — so its row is disabled and carries the reason.
 */
@Composable
private fun PresetRow(
    option: AgentPresetOption,
    isDefault: Boolean,
    state: MeSettingsState,
    actions: MeSettingsActions,
) {
    val summary = agentPresetDescription(option.id, option.description) ?: "No description."
    SegmentedRadioItem(
        title = agentPresetLabel(option.id, option.name),
        summary = when {
            option.broken -> "Failed to load. " + summary
            isDefault && !state.agentModePickerEnabled -> "Default. " + summary
            isDefault -> "New task default. " + summary
            else -> summary
        },
        selected = isDefault,
        enabled = !option.broken,
        onClick = { actions.onPreset(option.id) },
    )
}

/** Terminal: the Shell page's cell size. */
@Composable
internal fun TerminalSettingCards(state: MeSettingsState, actions: MeSettingsActions) {
    val cell = TerminalCellSize.of(state.terminalFontSp)
    SettingNote(
        "The Shell page's text size — the same value the shell's own A- / A+ buttons " +
            "and a pinch inside a shell change.",
    )
    val rows: List<@Composable () -> Unit> = listOf(
        {
            SegmentedListItem(
                headline = {
                    Text(
                        "Text size",
                        style = DshType.rowTitle,
                        color = DshTheme.colors.labelPrimary,
                    )
                },
                supporting = {
                    Text(
                        text = cell.fontSp.toInt().toString() + " sp · line " +
                            cell.lineHeightSp.toInt().toString() + " sp",
                        style = DshType.rowSummary,
                        color = DshTheme.colors.labelTertiary,
                    )
                },
                trailing = {
                    SettingStepper(
                        value = cell.fontSp.toInt().toString(),
                        unit = "sp",
                        decrease = if (cell.smallest) {
                            null
                        } else {
                            { actions.onTerminalFont(cell.smaller().fontSp) }
                        },
                        increase = if (cell.largest) {
                            null
                        } else {
                            { actions.onTerminalFont(cell.larger().fontSp) }
                        },
                    )
                },
            )
        },
    )
    SegmentedColumn(title = "Cell size", content = rows)
}

/** Plugins: the deployment's plugin surfaces, which this client cannot list yet. */
@Composable
internal fun PluginSettingCards() {
    SettingNote("Configure and inspect the plugins installed in this deployment.")
    // The configurable tab's read-only state. No cards render: the client never calls
    // pluginInventory/list, so there is no inventory to list here.
    SettingNote("This deployment stores settings read-only.")
}

/** Archived sessions: search, grouped by workspace, unarchive. */
@Composable
internal fun ArchivedSettingCards(state: MeSettingsState, actions: MeSettingsActions) {
    var query by rememberSaveable { mutableStateOf("") }
    // Archived sessions accumulate — a busy host has hundreds — so an uncapped list buries
    // everything above it. Paged the same way the drawer pages a Workspace, and reset by
    // each new query: an expanded page from the previous search is not a view the reader
    // chose for the next one.
    var expanded by rememberSaveable { mutableStateOf(false) }
    val colors = DshTheme.colors

    DshTextField(
        value = query,
        onValueChange = {
            query = it
            expanded = false
        },
        placeholder = "Search archived sessions",
        // Vertical only: the page already insets its content by 16dp, and the field is the
        // one thing on this page that is not inside a card.
        modifier = Modifier.padding(vertical = DshSpacing.md),
        // A blank query matches everything in the filter below, so clearing the text is the
        // whole of restoring the list.
        onClear = {
            query = ""
            expanded = false
        },
        clearContentDescription = SessionSearchCopy.CLEAR,
    )

    if (state.sessionsLoading) {
        SettingNote("Reading sessions…")
        return
    }
    if (state.archivedSessions.isEmpty()) {
        SettingNote("No archived sessions.")
        return
    }

    // The web filters on title and owning-workspace title only, and groups the survivors by
    // that workspace, with a synthetic Ungrouped bucket.
    val normalized = query.trim().lowercase()
    val rows = remember(state.archivedSessions, state.workspaces, normalized) {
        state.archivedSessions.map { session ->
            val owner = state.workspaces.firstOrNull { session.id in it.sessionIds }?.title
            session to (owner ?: "Ungrouped")
        }.filter { (session, workspace) ->
            normalized.isEmpty() ||
                session.title.lowercase().contains(normalized) ||
                workspace.lowercase().contains(normalized)
        }
    }

    if (rows.isEmpty()) {
        SettingNote("No matching sessions.")
        return
    }

    val shown = if (expanded) rows else rows.take(ARCHIVED_PAGE_SIZE)
    val hidden = rows.size - shown.size
    shown.groupBy { it.second }.forEach { (workspace, group) ->
        val groupRows: List<@Composable () -> Unit> = group.map { (session, owner) ->
            { ArchivedRow(session, owner, actions) }
        }
        SegmentedColumn(title = workspace, content = groupRows)
    }

    // The drawer's own wording and its own shape: one line, centred, that says how many are
    // left rather than just "more".
    if (hidden > 0 || expanded) {
        val label = if (expanded) {
            "Show less"
        } else {
            "Show " + hidden.toString() + " more session" + (if (hidden == 1) "" else "s")
        }
        Text(
            text = label,
            style = DshType.bodySmall,
            color = colors.link,
            modifier = Modifier
                .fillMaxWidth()
                .clickableNoRipple { expanded = !expanded }
                .padding(horizontal = DshSpacing.xl, vertical = DshSpacing.md),
        )
    }
}

@Composable
private fun ArchivedRow(
    session: SessionItem,
    workspace: String,
    actions: MeSettingsActions,
) {
    val colors = DshTheme.colors
    val unarchive = actions.onUnarchive
    SegmentedListItem(
        headline = {
            Text(
                // The web sets no title attribute; the full text is only in the row's
                // accessible name.
                text = session.title.ifBlank { "Untitled" },
                style = DshType.rowTitle,
                color = colors.labelPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supporting = {
            // [workspace, relativeTime].join(' · ').
            Text(
                text = workspace + " · " + relativeTime(session.updatedAt),
                style = DshType.rowSummary,
                color = colors.labelTertiary,
                maxLines = 1,
            )
        },
        trailing = if (unarchive != null) {
            {
                SettingTag("Unarchive", colors.labelPrimary) { unarchive(session.id) }
            }
        } else {
            null
        },
    )
}

/**
 * How many archived sessions a page shows before offering the rest.
 *
 * The drawer's page is 6 and this is the same idea at a different scale: the drawer pages
 * *within* a section that is one screenful of a list, while this is a scrolled document
 * whose archived list is its last section, so a slightly longer page keeps the common case
 * — unarchiving something recent — from needing the tap at all.
 */
private const val ARCHIVED_PAGE_SIZE = 8

// ------------------------------------------------------------------- building

/**
 * A section's own prose (the web's intro / read-only notices).
 *
 * The horizontal inset is 16dp rather than the sheet's old 24dp because a page already
 * insets itself by 16dp: 16 + 16 lands the prose on the cards' *text* edge, which is where
 * a card row's headline starts. At 24dp the note floated 8dp right of every row above it.
 */
@Composable
internal fun SettingNote(text: String) {
    Text(
        text = text,
        style = DshType.bodySmall,
        color = DshTheme.colors.labelTertiary,
        modifier = Modifier.padding(horizontal = DshSpacing.xl, vertical = DshSpacing.xs),
    )
}

/**
 * A failure note. The text is always somebody else's own wording — the host's refusal
 * message or the transport's failure — because a client paraphrase of a schema or CAS
 * error hides the one detail the reader needs.
 */
@Composable
internal fun SettingErrorNote(text: String) {
    Text(
        text = text,
        style = DshType.bodySmall,
        color = DshTheme.colors.error,
        modifier = Modifier.padding(horizontal = DshSpacing.xl, vertical = DshSpacing.xs),
    )
}

/** A small pill: the row's value, its state, or one action. */
@Composable
internal fun SettingTag(
    text: String,
    tint: Color,
    onClick: (() -> Unit)? = null,
) {
    val colors = DshTheme.colors
    val shape = RoundedCornerShape(DshRadius.pill)
    val base = Modifier
        .clip(shape)
        .border(0.5.dp, colors.borderL3, shape)
    Text(
        text = text,
        style = DshType.micro,
        color = tint,
        maxLines = 1,
        modifier = if (onClick != null) {
            base.clickableNoRipple(onClick = onClick)
                .padding(horizontal = DshSpacing.lg, vertical = DshSpacing.sm)
        } else {
            base.padding(horizontal = DshSpacing.sm, vertical = 1.dp)
        },
    )
}

/**
 * A two-arrow stepper for a row's trailing slot.
 *
 * Null on either end draws that arrow dead: a control that cannot move is better shown than
 * hidden, because the reader learns the range from it.
 */
@Composable
private fun SettingStepper(
    value: String,
    unit: String,
    decrease: (() -> Unit)?,
    increase: (() -> Unit)?,
) {
    val colors = DshTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(value, style = DshType.rowSummary, color = colors.labelPrimary, maxLines = 1)
        Spacer(Modifier.width(DshSpacing.sm))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            StepperArrow(Icons.Rounded.ExpandLess, "Increase text size", increase)
            StepperArrow(Icons.Rounded.ExpandMore, "Decrease text size", decrease)
        }
        Spacer(Modifier.width(DshSpacing.sm))
        Text(unit, style = DshType.micro, color = colors.labelCaption, maxLines = 1)
    }
}

@Composable
private fun StepperArrow(icon: ImageVector, description: String, onClick: (() -> Unit)?) {
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = if (onClick != null) DshTheme.colors.labelSecondary else DshTheme.colors.labelDimmed,
        modifier = Modifier
            .size(13.dp)
            .clickableNoRipple(enabled = onClick != null) { onClick?.invoke() },
    )
}

// -------------------------------------------------------------------- helpers

/**
 * What a pick in one of these rows *means*, as the host's own machine value.
 *
 * A row shows the host's labels — prose like "Full access" — while `settings/update` takes
 * the schema's enum member. Sending the label is the mistake this function exists to make
 * impossible: both the permission and the busy-Enter row go through it, so neither can
 * quietly pick up the other's convention.
 *
 * The host's option set wins when it has one. When it does not, the row is drawing the
 * client's own fallback roster (the permission presets the app already holds), and the pick
 * indexes *that* list instead.
 *
 * @param hostChoices the schema's choices, in the order the row drew them; empty when the
 *   host advertised none and the row fell back.
 * @param fallback the client's own option values, in the same order the row drew them.
 * @param picked the index the picker reported.
 */
internal fun pickedSettingValue(
    hostChoices: List<SettingsChoice>,
    fallback: List<String>,
    picked: Int,
): String? = if (hostChoices.isNotEmpty()) {
    hostChoices.getOrNull(picked)?.value
} else {
    fallback.getOrNull(picked)
}

/**
 * The change callback for one row, or null when the host would refuse the write.
 *
 * No field, no value, a restart-scoped namespace, a secret slot, or a provider that does
 * not accept writes: every one of those is a fact the describe reply carried, so the row
 * can be honest about which it is by simply not offering a control.
 */
private fun writerFor(
    field: SettingsField?,
    state: MeSettingsState,
    actions: MeSettingsActions,
): ((String) -> Unit)? {
    if (field == null || !field.editable || state.hostSettings?.writable != true) return null
    val write = actions.onSettingWrite ?: return null
    return { value -> write(field.ns, field.name, value) }
}

/**
 * Every appearance this client can draw, in the order the page lists them.
 *
 * Not the host's choices: three of the seven shapes describe *this device* — an OLED panel
 * and a wallpaper — which no host can know about. Taking the choices from the host would
 * have hidden them on every wired connection.
 */
internal val APPEARANCE_CHOICES = listOf(
    SettingsChoice(ThemeMode.LIGHT, "Light"),
    SettingsChoice(ThemeMode.DARK, "Dark"),
    SettingsChoice(ThemeMode.SYSTEM, "System"),
    SettingsChoice(ThemeMode.AMOLED, "AMOLED"),
    SettingsChoice(ThemeMode.MONET_LIGHT, "Monet light"),
    SettingsChoice(ThemeMode.MONET_DARK, "Monet dark"),
    SettingsChoice(ThemeMode.MONET_SYSTEM, "Monet"),
)

/** The appearances the host's ui-theme.preference enum can carry. */
internal val HOST_THEME_MODES = setOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM)

/** The web's own two busy-Enter options (EnterBehaviorRow.tsx), same fallback. */
private val BUSY_ENTER_CHOICES = listOf(
    SettingsChoice(BusyEnter.QUEUE, "Queue"),
    SettingsChoice(BusyEnter.STEER, "Steer"),
)

/**
 * The web's archivedSessions row time: relative, coarse, and never localized beyond the
 * unit (now, {n}min, {n}h, {n}d, {n}mo, {n}y).
 */
private fun relativeTime(updatedAt: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = ((now - updatedAt).coerceAtLeast(0)) / 60_000
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> minutes.toString() + "min"
        minutes < 60 * 24 -> (minutes / 60).toString() + "h"
        minutes < 60 * 24 * 30 -> (minutes / (60 * 24)).toString() + "d"
        minutes < 60 * 24 * 365 -> (minutes / (60 * 24 * 30)).toString() + "mo"
        else -> (minutes / (60 * 24 * 365)).toString() + "y"
    }
}