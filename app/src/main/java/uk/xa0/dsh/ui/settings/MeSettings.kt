package uk.xa0.dsh.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import uk.xa0.dsh.AgentPresetOption
import uk.xa0.dsh.ModelOption
import uk.xa0.dsh.PermissionOption
import uk.xa0.dsh.data.BusyEnter
import uk.xa0.dsh.data.LiquidGlassLevel
import uk.xa0.dsh.data.ThemeMode
import uk.xa0.dsh.model.HostSettings
import uk.xa0.dsh.model.SessionItem
import uk.xa0.dsh.model.WorkspaceItem
import uk.xa0.dsh.term.TerminalCellSize
import uk.xa0.dsh.ui.theme.DshSpacing

/**
 * The Me page's settings tabs, as data and as acts.
 *
 * ## Why a holder rather than thirty arguments
 *
 * The eight tabs are eight *pages*, and every one of them needs a different slice of the
 * same state: appearance reads the theme, General reads the host document, Models reads the
 * catalog, Terminal reads the cell size, Archived reads the roster. Passing those one by
 * one through MeDestination would make its signature a second copy of SettingsSheet's, and
 * a page added later would have to be threaded through both. One immutable holder and one
 * bundle of actions keep the seam at two values — the same seam ChatPanel already uses for
 * its state and its acts (ui/ChatPanelState.kt).
 *
 * Defaulted on purpose, like [uk.xa0.dsh.ui.ChatUiState]: a screenshot fixture has to be
 * able to photograph the Appearance page by naming only the appearance, and a page that
 * needed twenty arguments to draw one card would never get a baseline at all.
 *
 * ## What this is *not*
 *
 * Not a second source of truth. Every field here is a copy of one already held by UiState
 * (or by the view model, for the cell size); nothing writes back except through
 * [MeSettingsActions], which are the view model's own methods. The holder exists so the
 * page composables can be drawn without a view model, which is what makes them testable
 * and photographable at all.
 */
@Immutable
data class MeSettingsState(
    val themeMode: String = ThemeMode.SYSTEM,
    /** Whether a dark appearance is drawn in AMOLED black; see DshConfig.amoled. */
    val amoled: Boolean = false,
    /** The system night setting, so the AMOLED summary can say whether it applies now. */
    val systemDark: Boolean = false,
    /** Whether the Trajectory and Shell readings are offered. */
    val chatViewsEnabled: Boolean = true,
    /** Whether the floating chrome may paint liquid glass; see DshConfig. */
    val liquidGlassEnabled: Boolean = true,
    /** How much of it may run; see DshConfig.liquidGlassLevel. Off is the shipped default. */
    val liquidGlassLevel: String = LiquidGlassLevel.OFF,
    /** The in-app language override (BCP-47), or blank to follow the device. */
    val appLanguage: String = "",
    val busyEnter: String = BusyEnter.QUEUE,
    /** ui-chat.transcriptView as the host last reported it; compact before a read. */
    val transcriptView: String = "compact",
    /**
     * The pre-read fallback for the default permission row. The host's own value, once
     * settings/describe answers, comes off [hostSettings] instead — this is what the sheet
     * drew before that read existed.
     */
    val defaultPermission: String = "",
    val permissionOptions: List<PermissionOption> = emptyList(),
    val models: List<ModelOption> = emptyList(),
    val providerOrder: List<String> = emptyList(),
    val selectedModel: ModelOption? = null,
    val agentPreset: String = "",
    val agentPresetOptions: List<AgentPresetOption> = emptyList(),
    val agentModePickerEnabled: Boolean = true,
    val archivedSessions: List<SessionItem> = emptyList(),
    val workspaces: List<WorkspaceItem> = emptyList(),
    val sessionsLoading: Boolean = false,
    val hostSettings: HostSettings? = null,
    val hostSettingsLoading: Boolean = false,
    val hostSettingsError: String? = null,
    /** [namespace].[field] of the row with a settings/update in flight. */
    val settingsSaving: String? = null,
    /** The host's own wording for the last refused write. */
    val settingsWriteError: String? = null,
    /** The Shell page's cell size, in sp; see DshViewModel.setTerminalFontSp. */
    val terminalFontSp: Float = TerminalCellSize.DEFAULT_FONT_SP,
)

/**
 * Every act a settings tab can perform.
 *
 * Defaulted to no-ops for the same reason the state is defaulted: a fixture that draws the
 * Appearance page has nothing to wire. ChatScreen is the only production caller and it
 * passes every field, so a default is never a silent failure in the app.
 */
@Immutable
data class MeSettingsActions(
    val onTheme: (String) -> Unit = {},
    /** The AMOLED switch, independent of which appearance is selected. */
    val onAmoled: (Boolean) -> Unit = {},
    /** The Trajectory/Shell tabs switch. */
    val onChatViews: (Boolean) -> Unit = {},
    /** The liquid-glass material switch. */
    val onLiquidGlass: (Boolean) -> Unit = {},
    /** The material's level; see LiquidGlassLevel. */
    val onLiquidGlassLevel: (String) -> Unit = {},
    /** The language row; blank clears the override. */
    val onLanguage: (String) -> Unit = {},
    val onBusyEnter: (String) -> Unit = {},
    val onPreset: (String) -> Unit = {},
    val onSelectModel: (ModelOption, String?) -> Unit = { _, _ -> },
    /** Null leaves the Unarchive control off the row, for a caller that cannot write. */
    val onUnarchive: ((String) -> Unit)? = null,
    /** One changed host field, as (namespace, field, value). */
    val onSettingWrite: ((String, String, String) -> Unit)? = null,
    val onTerminalFont: (Float) -> Unit = {},
)

/**
 * One Me tab's page, by [id] — the same string MeEntry.id carries.
 *
 * A when rather than a map of composable lambdas: the eight pages take different
 * arguments, and a Map<String, @Composable () -> Unit> would have to close over all of
 * them anyway. An unknown id draws nothing rather than throwing, because the ids come from
 * a list this module also owns and a stale rememberSaveable after an upgrade is a real (if
 * rare) way to arrive here with a name that no longer exists.
 *
 * The page scrolls itself: MeSubPage owns the back bar and hands the rest of the column
 * over, so a page taller than the screen has to be the thing that scrolls.
 */
@Composable
internal fun MeSettingsPage(
    id: String,
    state: MeSettingsState,
    actions: MeSettingsActions,
    modifier: Modifier = Modifier,
) {
    // The page insets its cards by the same 16dp the Me page does, so tapping a row does
    // not shift the content sideways. Notes then need only the remaining 16dp to reach a
    // card's text edge — see LocalSettingNoteInset, which is why this is a local rather
    // than a second argument on every card.
    CompositionLocalProvider(LocalSettingNoteInset provides DshSpacing.xl) {
        Column(
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DshSpacing.xl)
                .padding(bottom = DshSpacing.xxxl),
        ) {
            when (id) {
                // "local" is the section the objective asked for: the settings that are
                // facts about this phone rather than about the host. The host has no
                // schema for either of them, which is exactly why they need a page here.
                "local" -> {
                    LanguageSettingCard(state, actions)
                    LocalSettingCard(state, actions)
                }
                "appearance" -> AppearanceSettingCard(state, actions)
                "general" -> {
                    HostSettingsNotes(state)
                    PermissionSettingCard(state, actions)
                    ConversationSettingCard(state, actions)
                    BusyEnterSettingCard(state, actions)
                }
                "model" -> ModelSettingCards(state)
                "agent" -> AgentSettingCards(state, actions)
                "terminal" -> TerminalSettingCards(state, actions)
                "plugins" -> PluginSettingCards()
                "crashes" -> CrashLogSettingCards()
                "archived" -> ArchivedSettingCards(state, actions)
                // "about" is not a page: it opens the About sheet, which is a document
                // about the host rather than a setting. See MeDestination in ChatPanel.kt.
            }
        }
    }
}