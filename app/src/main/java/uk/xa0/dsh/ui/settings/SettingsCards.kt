package uk.xa0.dsh.ui.settings

import androidx.annotation.StringRes
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
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import uk.xa0.dsh.AgentPresetOption
import uk.xa0.dsh.ModelOption
import uk.xa0.dsh.R
import uk.xa0.dsh.data.BusyEnter
import uk.xa0.dsh.data.LiquidGlassLevel
import uk.xa0.dsh.data.ThemeMode
import uk.xa0.dsh.model.GeneralSettings
import uk.xa0.dsh.model.SessionItem
import uk.xa0.dsh.model.SettingsChoice
import uk.xa0.dsh.model.SettingsField
import uk.xa0.dsh.term.TerminalCellSize
import uk.xa0.dsh.ui.agentPresetDescription
import uk.xa0.dsh.ui.agentPresetLabel
import uk.xa0.dsh.ui.clickableNoRipple
import uk.xa0.dsh.ui.components.DshIconVectors
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
 * This device: the one switch the host has no schema for.
 *
 * AMOLED lives on the Appearance page instead, because it *modifies* the appearance rather
 * than being a separate fact — but it is the same kind of thing, and the note above the row
 * says so.
 */

/**
 * The languages this build can draw in.
 *
 * Only the two the app actually carries resources for. Listing eight and having six of
 * them fall back to English would be a menu that lies about what it does; the host's own
 * eight-language set is a *web* fact, and the Android resources are a separate
 * translation effort (`docs/I18N.md`).
 */
private val APP_LANGUAGES = listOf(
    "" to "Follow the device",
    "en" to "English",
    "zh-CN" to "简体中文",
)

/**
 * The language row.
 *
 * A client-local setting, beside the Trajectory/Shell switch, because it is a fact about
 * this phone rather than about the host: the host's locale drives the *web* client, and a
 * phone in one language driven from a console in another is a real setup.
 */
@Composable
internal fun LanguageSettingCard(state: MeSettingsState, actions: MeSettingsActions) {
    val index = APP_LANGUAGES.indexOfFirst { it.first == state.appLanguage }
        .coerceAtLeast(0)
    SegmentedColumn(
        title = stringResource(R.string.settings_language_title),
        content = listOf(
            {
                SegmentedDropdownItem(
                    title = stringResource(R.string.settings_language_row_title),
                    summary = stringResource(R.string.settings_language_row_summary),
                    icon = DshIconVectors.GlobeOutline,
                    items = APP_LANGUAGES.map { it.second },
                    selectedIndex = index,
                    onItemSelected = { picked ->
                        actions.onLanguage(APP_LANGUAGES[picked].first)
                    },
                )
            },
        ),
    )
}
@Composable
internal fun LocalSettingCard(state: MeSettingsState, actions: MeSettingsActions) {
    // Resolved here, not inside the row lambda: content is a List of composable lambdas,
    // so a stringResource called while building an *argument* to the row would be outside
    // any composable scope. The appearance card gets away with the same shape because it
    // calls appearanceChoices() before the list is built — this follows it.
    val levelChoices = liquidGlassLevelChoices()
    val levelIndex = LiquidGlassLevel.all.indexOf(state.liquidGlassLevel).coerceAtLeast(0)
    SegmentedColumn(
        title = stringResource(R.string.settings_local_title),
        content = listOf(
            {
                SegmentedSwitchItem(
                    title = stringResource(R.string.settings_local_chat_views_title),
                    checked = state.chatViewsEnabled,
                    onCheckedChange = actions.onChatViews,
                    icon = DshIconVectors.PlanOutline,
                    summary = stringResource(R.string.settings_local_chat_views_summary),
                )
            },
            {
                // The material's own row, and the reason it is a *setting* rather than a
                // build flag: on a phone whose GPU dislikes RenderEffect the choice is
                // between a beautiful app that closes and a plain one that works, and
                // that is the reader's call to make, not ours.
                //
                // Three levels and not a switch, because "the glass crashes" cannot be
                // acted on and "the runtime shader crashes" can. Blur keeps the look and
                // drops the two AGSL shaders — see LiquidGlassLevel.
                SegmentedDropdownItem(
                    title = stringResource(R.string.settings_local_liquid_glass_title),
                    summary = stringResource(R.string.settings_local_liquid_glass_summary),
                    icon = DshIconVectors.PluginPinwheelOutline,
                    items = levelChoices.map { it.label },
                    selectedIndex = levelIndex,
                    onItemSelected = { picked ->
                        actions.onLiquidGlassLevel(levelChoices[picked].value)
                    },
                )
            },
        ),
    )
}

/**
 * Appearance: the six shapes this device can draw.
 *
 * The list is the client's, not the host's appearance.choices: the schema's enum is
 * light/dark/system, while AMOLED (a separate switch) and the three Monet variants describe
 * *this device* — an OLED panel and a wallpaper — which no host can know about. A successful
 * local change is still mirrored to the host for the three modes it can express.
 */
@Composable
internal fun AppearanceSettingCard(state: MeSettingsState, actions: MeSettingsActions) {
    val appearance = state.hostSettings?.field(
        GeneralSettings.THEME_NS,
        GeneralSettings.THEME_PREFERENCE_FIELD,
    )
    val appearanceWrite = writerFor(appearance, state, actions)
    // Two readings of one list: the index is a pure value lookup (no resources needed),
    // and the drawn labels come from the composable accessor.
    val index = APPEARANCE_CHOICES.indexOfFirst { it.value == state.themeMode }
    val choices = appearanceChoices()
    val rows: List<@Composable () -> Unit> = listOf(
        {
            SegmentedDropdownItem(
                title = stringResource(R.string.settings_theme_title),
                summary = stringResource(R.string.settings_theme_summary),
                icon = Icons.Rounded.Palette,
                items = choices.map { it.label },
                selectedIndex = index,
                onItemSelected = { picked ->
                    val mode = choices[picked].value
                    actions.onTheme(mode)
                    // Mirror only what the host can represent: its schema enum is
                    // light/dark/system, and the reader already sees what they picked.
                    if (mode in HOST_THEME_MODES) appearanceWrite?.invoke(mode)
                },
            )
        },
        {
            // A switch, not a seventh appearance.
            //
            // As an entry in the list above it *replaced* the appearance rather than
            // modifying it, so "black at night, light at the desk" was two changes each
            // time, and the palette it drew was a frozen copy of Dark rather than
            // whatever dark appearance was selected. As a switch it applies to whichever
            // dark appearance wins — Dark, System-as-dark, or a Monet dark — which is
            // what "开启后是所有深色时使用 amoled 模式" asks for.
            SegmentedSwitchItem(
                title = stringResource(R.string.settings_amoled_title),
                checked = state.amoled,
                onCheckedChange = actions.onAmoled,
                icon = DshIconVectors.DarkOutline,
                // Two sentences rather than one with a clause cut out: they describe
                // different states, and a translation of the pair reads as one voice.
                summary = stringResource(
                    if (ThemeMode.isDark(state.themeMode, state.systemDark)) {
                        R.string.settings_amoled_summary_dark
                    } else {
                        R.string.settings_amoled_summary_light
                    },
                ),
            )
        },
    )
    SegmentedColumn(title = stringResource(R.string.settings_appearance_title), content = rows)
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
            state.hostSettings == null -> SettingNote(stringResource(R.string.settings_reading))
            // The web's own read-only copy (settings.models.readOnly); the fact is the
            // same one on this panel.
            !state.hostSettings.writable ->
                SettingNote(stringResource(R.string.settings_readonly_document))
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
                title = stringResource(R.string.settings_permission_default_title),
                summary = stringResource(R.string.settings_permission_default_summary),
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
    SegmentedColumn(title = stringResource(R.string.settings_permission_title), content = rows)
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
    // The client's own fallback labels, from resources: the host's labels win when it
    // advertised any, but this pair is the app speaking and is translated.
    val fallbackLabels = listOf(
        stringResource(R.string.settings_transcript_compact),
        stringResource(R.string.settings_transcript_normal),
    )
    val items = hostLabels.ifEmpty { fallbackLabels }
    val selected = field?.choices?.indexOfFirst { it.value == field.value }
        ?.takeIf { it >= 0 }
        ?: if (state.transcriptView == "normal") 1 else 0
    val rows: List<@Composable () -> Unit> = listOf(
        {
            SegmentedDropdownItem(
                title = stringResource(R.string.settings_transcript_title),
                summary = stringResource(R.string.settings_transcript_summary),
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
    SegmentedColumn(title = stringResource(R.string.settings_conversation_title), content = rows)
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
    // The host's options win; the client's own pair is the fallback, resolved here
    // because a picker handler below is not composable.
    val fallbackChoices = busyEnterChoices()
    val choices = field?.choices.orEmpty().ifEmpty { fallbackChoices }
    val current = field?.value ?: state.busyEnter
    val selected = choices.indexOfFirst { it.value == current }
    val rows: List<@Composable () -> Unit> = listOf(
        {
            SegmentedDropdownItem(
                title = stringResource(R.string.settings_busy_enter_row_title),
                summary = stringResource(R.string.settings_busy_enter_summary),
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
    SegmentedColumn(title = stringResource(R.string.settings_busy_enter_title), content = rows)
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

    SettingNote(stringResource(R.string.settings_models_note_keys))
    // A remote phone is handed the memory settings scope, which the web marks read-only;
    // the notice is the web's own settings.models.readOnly copy.
    SettingNote(stringResource(R.string.settings_readonly_document))

    if (providers.isEmpty()) {
        SettingNote(stringResource(R.string.settings_models_empty))
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
            { SettingTag(stringResource(R.string.settings_models_current), colors.accent) }
        } else {
            null
        },
    )
}

/** Agent: the preset roster, and the picker's own state. */
@Composable
internal fun AgentSettingCards(state: MeSettingsState, actions: MeSettingsActions) {
    SettingNote(stringResource(R.string.settings_agent_intro))

    // The picker switch is a settings/update, so a remote client cannot flip it; it
    // renders the app's real state disabled.
    val pickerRows: List<@Composable () -> Unit> = listOf(
        {
            SegmentedSwitchItem(
                title = stringResource(R.string.settings_agent_picker_title),
                summary = stringResource(R.string.settings_agent_picker_summary),
                checked = state.agentModePickerEnabled,
                onCheckedChange = {},
                enabled = false,
            )
        },
    )
    SegmentedColumn(content = pickerRows)

    if (state.agentPresetOptions.isEmpty()) {
        SettingNote(stringResource(R.string.settings_agent_presets_failed))
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
        SegmentedColumn(title = stringResource(R.string.settings_agent_built_in), content = rows)
    }
    if (custom.isNotEmpty()) {
        val rows: List<@Composable () -> Unit> = custom.map { option ->
            { PresetRow(option, option.id == defaultId, state, actions) }
        }
        SegmentedColumn(title = stringResource(R.string.settings_agent_custom), content = rows)
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
    // A preset's own description arrives from the host and is drawn as received; only
    // the *prefix* is the app speaking, so only the prefix is a resource. The three
    // prefixes are format strings rather than concatenations: "Failed to load. " + text
    // bakes the English full stop and word order into a place no translator can reach.
    val summary = agentPresetDescription(option.id, option.description)
        ?: stringResource(R.string.settings_agent_no_description)
    SegmentedRadioItem(
        title = agentPresetLabel(option.id, option.name),
        summary = when {
            option.broken -> stringResource(R.string.settings_agent_prefix_failed, summary)
            isDefault && !state.agentModePickerEnabled ->
                stringResource(R.string.settings_agent_prefix_default, summary)
            isDefault -> stringResource(R.string.settings_agent_prefix_new_task_default, summary)
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
    SettingNote(stringResource(R.string.settings_terminal_note))
    val rows: List<@Composable () -> Unit> = listOf(
        {
            SegmentedListItem(
                headline = {
                    Text(
                        stringResource(R.string.settings_terminal_text_size),
                        style = DshType.rowTitle,
                        color = DshTheme.colors.labelPrimary,
                    )
                },
                supporting = {
                    // Two readings in one sentence: the format string keeps the middle
                    // dot and the word "line" translatable, and the numbers are data.
                    Text(
                        text = stringResource(
                            R.string.settings_terminal_sp_pair,
                            cell.fontSp.toInt().toString(),
                            cell.lineHeightSp.toInt().toString(),
                        ),
                        style = DshType.rowSummary,
                        color = DshTheme.colors.labelTertiary,
                    )
                },
                trailing = {
                    // "sp" is a unit symbol, not prose: it is the same four letters in
                    // every locale this app ships and Android has no localised form.
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
    SegmentedColumn(title = stringResource(R.string.settings_terminal_cell_size), content = rows)
}

/** Plugins: the deployment's plugin surfaces, which this client cannot list yet. */
@Composable
internal fun PluginSettingCards() {
    SettingNote(stringResource(R.string.settings_plugins_note))
    // The configurable tab's read-only state. No cards render: the client never calls
    // pluginInventory/list, so there is no inventory to list here.
    SettingNote(stringResource(R.string.settings_plugins_note_readonly))
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
        placeholder = stringResource(R.string.settings_archived_search_placeholder),
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
        SettingNote(stringResource(R.string.settings_archived_reading))
        return
    }
    if (state.archivedSessions.isEmpty()) {
        SettingNote(stringResource(R.string.settings_archived_empty))
        return
    }

    // The web filters on title and owning-workspace title only, and groups the survivors by
    // that workspace, with a synthetic Ungrouped bucket.
    val normalized = query.trim().lowercase()
    // The synthetic bucket is the app's own word, so it is resolved here rather than
    // inside the remembered block, which is not a composable scope.
    val ungrouped = stringResource(R.string.settings_archived_ungrouped)
    val rows = remember(state.archivedSessions, state.workspaces, normalized, ungrouped) {
        state.archivedSessions.map { session ->
            val owner = state.workspaces.firstOrNull { session.id in it.sessionIds }?.title
            session to (owner ?: ungrouped)
        }.filter { (session, workspace) ->
            normalized.isEmpty() ||
                session.title.lowercase().contains(normalized) ||
                workspace.lowercase().contains(normalized)
        }
    }

    if (rows.isEmpty()) {
        SettingNote(stringResource(R.string.settings_archived_no_match))
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
        // A plural rather than "Show " + n + " more sessions": the count is the word
        // that changes, and a language with three plural forms cannot be served by a
        // ternary on English's own "s".
        val label = if (expanded) {
            stringResource(R.string.settings_archived_show_less)
        } else {
            pluralStringResource(R.plurals.settings_archived_show_more, hidden, hidden)
        }
        Text(
            text = label,
            style = DshType.bodySmall,
            color = colors.link,
            modifier = Modifier
                .fillMaxWidth()
                .clickableNoRipple { expanded = !expanded }
                .padding(horizontal = LocalSettingNoteInset.current, vertical = DshSpacing.md),
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
                text = session.title.ifBlank { stringResource(R.string.settings_archived_untitled) },
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
                SettingTag(
                    stringResource(R.string.settings_archived_unarchive),
                    colors.labelPrimary,
                ) { unarchive(session.id) }
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
 * How far a note is inset from the surface's own edge.
 *
 * A note has to land on the cards' *text* edge, which is 16dp inside a card. That distance
 * differs by surface: the Settings sheet is flush to its sheet, so a note carries the whole
 * 24dp (16 for the card plus the sheet's own 8); a Me tab is already inset 16dp by
 * [MeSettingsPage], so its note needs only the remaining 16.
 *
 * A composition local rather than an argument on every card: the inset is a fact about the
 * *surface*, and threading it through nine cards would put the same value in nine
 * signatures. The page provides it once; the sheet leaves the default.
 */
internal val LocalSettingNoteInset: ProvidableCompositionLocal<Dp> = compositionLocalOf { DshSpacing.xxl }

/**
 * A section's own prose (the web's intro / read-only notices).
 *
 * The inset comes from [LocalSettingNoteInset], so the same card draws correctly in the
 * sheet (flush) and on a tab (already inset) without either one knowing about the other.
 */
@Composable
internal fun SettingNote(text: String) {
    Text(
        text = text,
        style = DshType.bodySmall,
        color = DshTheme.colors.labelTertiary,
        modifier = Modifier.padding(
            horizontal = LocalSettingNoteInset.current,
            vertical = DshSpacing.xs,
        ),
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
        modifier = Modifier.padding(
            horizontal = LocalSettingNoteInset.current,
            vertical = DshSpacing.xs,
        ),
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
            StepperArrow(
                DshIconVectors.ChevronUpOutline,
                stringResource(R.string.settings_terminal_increase),
                increase,
            )
            StepperArrow(
                DshIconVectors.ChevronDownOutline,
                stringResource(R.string.settings_terminal_decrease),
                decrease,
            )
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
 * Not the host's choices: three of the six shapes describe *this device* — an OLED panel
 * and a wallpaper — which no host can know about. Taking the choices from the host would
 * have hidden them on every wired connection.
 *
 * The *value* is what this list is for: the picker's index and the mirror to the host both
 * read it. The drawn label is [appearanceChoices]' business — a label is a translated
 * resource now, and a resource cannot be read at class-initialisation time, so it cannot
 * live in a top-level list. The English in `label` below is **not drawn by the app**; it
 * is kept because `MeSettingsPageTest` reads the names off this list, and a name is the
 * one thing that list still has to carry for that test to mean anything.
 */
/** The material's three levels, in the order the row lists them. */
internal val LIQUID_GLASS_CHOICES: List<SettingsChoice> = listOf(
    SettingsChoice(LiquidGlassLevel.LIQUID, "Liquid glass"),
    SettingsChoice(LiquidGlassLevel.BLUR, "Backdrop and blur"),
    SettingsChoice(LiquidGlassLevel.BAR, "Bar only"),
    SettingsChoice(LiquidGlassLevel.OFF, "Off"),
)

/**
 * The level row's labels, resolved through resources.
 *
 * Same two-step as [appearanceChoices]: the value lookup is a pure list (so a test can
 * assert an index without a context) and only the drawn text needs resources.
 */
@Composable
internal fun liquidGlassLevelChoices(): List<SettingsChoice> = LIQUID_GLASS_CHOICES.map { choice ->
    SettingsChoice(choice.value, stringResource(liquidGlassLevelLabel(choice.value)))
}

@StringRes
private fun liquidGlassLevelLabel(level: String): Int = when (level) {
    LiquidGlassLevel.BLUR -> R.string.settings_liquid_glass_blur
    LiquidGlassLevel.BAR -> R.string.settings_liquid_glass_bar
    LiquidGlassLevel.OFF -> R.string.settings_liquid_glass_off
    else -> R.string.settings_liquid_glass_liquid
}

internal val APPEARANCE_CHOICES: List<SettingsChoice> = listOf(
    SettingsChoice(ThemeMode.LIGHT, "Light"),
    SettingsChoice(ThemeMode.DARK, "Dark"),
    SettingsChoice(ThemeMode.SYSTEM, "System"),
    SettingsChoice(ThemeMode.MONET_LIGHT, "Monet light"),
    SettingsChoice(ThemeMode.MONET_DARK, "Monet dark"),
    SettingsChoice(ThemeMode.MONET_SYSTEM, "Monet"),
)

/** [APPEARANCE_CHOICES] with the picker's own labels, in the same order. */
@Composable
internal fun appearanceChoices(): List<SettingsChoice> = APPEARANCE_CHOICES.map { choice ->
    SettingsChoice(choice.value, stringResource(appearanceLabel(choice.value)))
}

/** The theme picker's label for one stored appearance. */
@StringRes
private fun appearanceLabel(mode: String): Int = when (mode) {
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.MONET_LIGHT -> R.string.settings_theme_monet_light
    ThemeMode.MONET_DARK -> R.string.settings_theme_monet_dark
    ThemeMode.MONET_SYSTEM -> R.string.settings_theme_monet_system
    // Unreachable for a value that came out of APPEARANCE_MODES, but a stored appearance
    // from a build that had a shape this one no longer draws must not crash the page.
    else -> R.string.settings_theme_system
}

/** The appearances the host's ui-theme.preference enum can carry. */
internal val HOST_THEME_MODES = setOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM)

/**
 * The web's own two busy-Enter options (EnterBehaviorRow.tsx), same fallback.
 *
 * A composable property owner rather than a top-level `val`: a label is a translated
 * resource now, and a resource can only be read through a [android.content.Context].
 */
@Composable
private fun busyEnterChoices(): List<SettingsChoice> = listOf(
    SettingsChoice(BusyEnter.QUEUE, stringResource(R.string.settings_busy_enter_queue)),
    SettingsChoice(BusyEnter.STEER, stringResource(R.string.settings_busy_enter_steer)),
)

/**
 * The web's archivedSessions row time: relative, coarse, and — unlike the web — localized.
 *
 * The six units are the app speaking, so each is a resource. They are `%1$d` formats
 * rather than a number plus a literal suffix: Chinese writes 5 分钟 with a space and a
 * word, German writes 5 Min., and neither is derivable from a Kotlin `+`.
 *
 * Composable because a resource is read through a context; it is still a pure reading of
 * [updatedAt] and [now], which is why [now] stays injectable.
 */
@Composable
private fun relativeTime(updatedAt: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = ((now - updatedAt).coerceAtLeast(0)) / 60_000
    return when {
        minutes < 1 -> stringResource(R.string.settings_time_now)
        minutes < 60 ->
            stringResource(R.string.settings_time_minutes, minutes.toInt())
        minutes < 60 * 24 ->
            stringResource(R.string.settings_time_hours, (minutes / 60).toInt())
        minutes < 60 * 24 * 30 ->
            stringResource(R.string.settings_time_days, (minutes / (60 * 24)).toInt())
        minutes < 60 * 24 * 365 ->
            stringResource(R.string.settings_time_months, (minutes / (60 * 24 * 30)).toInt())
        else ->
            stringResource(R.string.settings_time_years, (minutes / (60 * 24 * 365)).toInt())
    }
}