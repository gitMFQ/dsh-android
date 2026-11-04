package uk.xa0.dsh.ui

import androidx.compose.runtime.Immutable
import uk.xa0.dsh.DirectoryLevel
import uk.xa0.dsh.DraftRestore
import uk.xa0.dsh.HostCommand
import uk.xa0.dsh.ModelOption
import uk.xa0.dsh.PluginInventoryState
import uk.xa0.dsh.ReferenceCandidate
import uk.xa0.dsh.TerminalUiState
import uk.xa0.dsh.UiState
import uk.xa0.dsh.model.ChatEntry
import uk.xa0.dsh.model.DrawerGrouping
import uk.xa0.dsh.model.ExplorerState
import uk.xa0.dsh.model.FileDownloader
import uk.xa0.dsh.model.GeneralSettings
import uk.xa0.dsh.model.LiveAttempt
import uk.xa0.dsh.model.ScheduleEntry
import uk.xa0.dsh.model.SessionHeader
import uk.xa0.dsh.model.SessionItem
import uk.xa0.dsh.model.TodoItem
import uk.xa0.dsh.model.WorkspaceFileLoader
import uk.xa0.dsh.model.WorkspaceFileSaver
import uk.xa0.dsh.model.WorkspaceTreeState
import uk.xa0.dsh.model.fileWriteCommand
import uk.xa0.dsh.model.translationTargetOf
import uk.xa0.dsh.model.translationTextOf

/**
 * Everything [ChatPanel] draws from, with the view model taken out.
 *
 * ## Why this exists (2.5a)
 *
 * `ChatScreen(vm: DshViewModel)` was the only entry point to the most-read screen in
 * the app, and that made it impossible to photograph: a baseline needs a fixture, and
 * a fixture cannot be a 9 000-line view model wired to a live host socket. The same
 * argument that produced `TerminalPanel` in 2.2b applies here, one size larger — so
 * this is the same move, made the same way: a pure move, no pixel change, verified by
 * the eleven baselines staying byte-identical.
 *
 * ## What is *not* in here
 *
 * No `DshViewModel`, no `collectAsState`, no RPC. What remains is:
 *
 *  - the state the tree reads, hoisted into [ChatUiState] (one field per flow);
 *  - the acts the tree can perform, hoisted into [ChatActions] (one lambda per method).
 *
 * That split is the whole contract. A field added to either one without a caller in
 * `ChatPanel` is dead weight; a `vm.` reachable from the tree would defeat the point.
 */

/**
 * The transcript and everything beside it, one field per flow the screen used to
 * collect inline.
 *
 * Defaulted on purpose: a screenshot fixture has to be able to say "the hero, with
 * nothing else" by naming nothing else, and a screen that needed forty arguments to
 * show an empty state would never get a baseline at all.
 */
@Immutable
data class ChatUiState(
    /** The whole [UiState]. Still read as one object: the screen reaches into it from
     * thirty-odd places and splitting it field-by-field would be a rename, not a
     * refactor. It is a plain data class, so it is stable by construction. */
    val ui: UiState = UiState(),
    val entries: List<ChatEntry> = emptyList(),
    val live: LiveAttempt? = null,
    val todos: List<TodoItem> = emptyList(),
    val header: SessionHeader? = null,
    val terminal: TerminalUiState = TerminalUiState(),
    val endedTurns: Set<Int> = emptySet(),
    val closingSeqs: Set<Int> = emptySet(),
    val branchSeqs: Map<Int, Int> = emptyMap(),
    val turnDurations: Map<Int, Long> = emptyMap(),
    val turnStartedAt: Long? = null,
    val references: List<ReferenceCandidate> = emptyList(),
    val commands: List<HostCommand> = emptyList(),
    val directoryLevel: DirectoryLevel? = null,
    val directoryLoading: Boolean = false,
    val directoryBusy: Boolean = false,
    val directoryError: String? = null,
    val workspaceExplorer: ExplorerState = ExplorerState.Idle,
    /** The Files tree: every level the host has answered, plus which are open. */
    val workspaceTree: WorkspaceTreeState = WorkspaceTreeState(),
    /**
     * The Plugins page's read of `pluginInventory/list`.
     *
     * A field of its own rather than part of [ui] for the same reason the explorer is:
     * only one page reads it, and a failed inventory is that page's business rather
     * than a session error that takes over the banner above the conversation.
     */
    val pluginInventory: PluginInventoryState = PluginInventoryState.Idle,
    /**
     * The running turn's elapsed milliseconds, or null when no turn of this session's
     * own is in flight.
     *
     * A number rather than a clock: the shell ticks it once a second, so the panel
     * draws the same frame for the same arguments and a baseline can hold it still.
     */
    val elapsedMs: Long? = null,
    /** What a failed send hands back, and the nonce that identifies that send. */
    val draftRestore: DraftRestore? = null,
    /** Tool-call id → the id of the call that dispatched it, for the nested tree. */
    val toolParents: Map<String, String> = emptyMap(),
    /**
     * The Shell page's cell size, in sp.
     *
     * A field of its own rather than a read of `ui`: it is a preference the *panel* writes
     * (the Me page's Terminal tab and the shell's own A−/A+ both go through
     * `DshViewModel.setTerminalFontSp`), and the panel is handed `UiState` as one object
     * it does not write into. Carrying it here is what lets a screenshot fixture state a
     * cell size without a view model.
     */
    val terminalFontSp: Float = uk.xa0.dsh.term.TerminalCellSize.DEFAULT_FONT_SP,
) {
    /** The roster row for the open session, if the roster has it. */
    val current: SessionItem?
        get() = ui.sessions.firstOrNull { it.id == ui.currentSessionId }

    /**
     * The host's "Conversation display" setting, honoured: `normal` folds nothing.
     * Absent or unreadable falls back to `compact`, which is what the app did before.
     */
    val transcriptView: String
        get() = ui.hostSettings
            ?.field(GeneralSettings.CHAT_NS, GeneralSettings.TRANSCRIPT_FIELD)
            ?.value
            ?: "compact"

    /** Whether a completed turn collapses its rows behind one summary line. */
    val foldTurns: Boolean get() = transcriptView != "normal"
}

/**
 * Every act [ChatPanel] can perform, one lambda per view-model method it used to call.
 *
 * Defaulted to no-ops for the same reason [ChatUiState] is defaulted: a fixture that
 * photographs the hero has nothing to wire, and `ChatScreen` overrides all of them
 * with `vm::method` references, so the wiring stays one line per act.
 *
 * A default is *not* a silent failure mode for the app: `ChatScreen` is the only
 * production caller, and it passes every field. The defaults exist for the tests.
 */
@Immutable
data class ChatActions(
    val openSession: (String) -> Unit = {},
    val startSession: (String?) -> Unit = {},
    val startSessionIn: (String) -> Unit = {},
    val startSessionInWorkspace: (String) -> Unit = {},
    /** [DshViewModel.send]'s two-argument form, with the mode defaulted at the call. */
    val send: (String, String) -> Unit = { _, _ -> },
    /** The hero's send: the deferred path, which never had a delivery mode. */
    val sendDeferred: (String) -> Unit = {},
    val submitMode: () -> String = { uk.xa0.dsh.data.BusyEnter.QUEUE },
    val stop: () -> Unit = {},
    val saveDraft: (String?, String) -> Unit = { _, _ -> },
    /** The draft stored on disk for a key, read synchronously to seed the box. */
    val draftFor: (String?) -> String = { "" },
    val consumeDraftRestore: (Boolean) -> Unit = {},
    val loadOlderHistory: () -> Unit = {},
    val loadHostSettings: () -> Unit = {},
    val toolParents: () -> Map<String, String> = { emptyMap() },
    val imageBitmap: suspend (String) -> androidx.compose.ui.graphics.ImageBitmap? = { null },
    val observeJob: (String?) -> Unit = {},
    val killJob: (String) -> Unit = {},
    val markJobsSeen: () -> Unit = {},
    val resolveApproval: (Boolean) -> Unit = {},
    val answerQuestions: (List<Triple<String, List<String>, String?>>) -> Unit = {},
    val skipQuestions: () -> Unit = {},
    val exitPlanMode: () -> Unit = {},
    val pauseGoal: () -> Unit = {},
    val resumeGoal: () -> Unit = {},
    val editGoal: (String) -> Unit = {},
    val clearGoal: () -> Unit = {},
    val editQueueItem: (String, String) -> Unit = { _, _ -> },
    val removeQueueItem: (String) -> Unit = {},
    val steerQueueItem: (String) -> Unit = {},
    val addAttachment: (android.net.Uri) -> Unit = {},
    val removeAttachment: (String) -> Unit = {},
    val executeCommand: (String) -> Unit = {},
    val searchReferences: (String) -> Unit = {},
    val clearReferences: () -> Unit = {},
    val refreshCommands: () -> Unit = {},
    val selectModel: (ModelOption, String?) -> Unit = { _, _ -> },
    val selectAgentPreset: (String) -> Unit = {},
    val refreshAgentPresets: () -> Unit = {},
    val setPermission: (String) -> Unit = {},
    val updateBusyEnter: (String) -> Unit = {},
    val updateTheme: (String) -> Unit = {},
    val updatePreset: (String) -> Unit = {},
    val branchFromConclusion: (Int) -> Unit = {},
    val dismissError: () -> Unit = {},
    val signInAgain: () -> Unit = {},
    val refreshSidebar: () -> Unit = {},
    val refreshBalance: () -> Unit = {},
    val checkRouter: () -> Unit = {},
    val setSubagentCatalogOpen: (String, Boolean) -> Unit = { _, _ -> },
    val resetDirectoryBrowser: () -> Unit = {},
    val listDirectory: (String?) -> Unit = {},
    val createDirectory: (String, String, (String) -> Unit) -> Unit = { _, _, _ -> },
    val addWorkspace: (String, (String) -> Unit) -> Unit = { _, _ -> },
    val listWorkspaceDirectory: (String) -> Unit = {},
    val reloadWorkspaceExplorer: () -> Unit = {},
    val resetWorkspaceExplorer: () -> Unit = {},
    /** Expands or collapses one directory in the Files tree. */
    val toggleWorkspaceDirectory: (String) -> Unit = {},
    val workspaceFileLoader: () -> WorkspaceFileLoader? = { null },
    /**
     * The write half of the Files preview, for hosts with no file-write RPC.
     *
     * Null on a caller with no session to write through, which is what keeps the
     * editor from being drawn at all rather than drawn and failing.
     */
    val workspaceFileSaver: () -> WorkspaceFileSaver? = { null },
    /**
     * Translates one fragment through the host, answering null when it could not.
     *
     * A callback rather than a flow: a translation is a one-shot answer to a gesture,
     * and holding it in app state would make it survive the screen it belongs to. The
     * caller owns where the result is shown.
     */
    val translateFragment: (String, (String?) -> Unit) -> Unit = { _, done -> done(null) },
    /**
     * Turns AMOLED black on or off. Independent of which appearance is selected.
     */
    val updateAmoled: (Boolean) -> Unit = {},

    /** Turns the Trajectory and Shell readings on or off. */
    val setChatViewsEnabled: (Boolean) -> Unit = {},

    /** Sets the in-app language, or clears it back to the device’s own. */
    val setAppLanguage: (String) -> Unit = {},
    val workspaceFileDownloader: () -> FileDownloader? = { null },
    /** Reads `pluginInventory/list`; the Plugins page's one invalidation. */
    val loadPluginInventory: () -> Unit = {},
    val renameSession: (String, String) -> Unit = { _, _ -> },
    val forkSession: (String) -> Unit = {},
    val archiveSession: (String) -> Unit = {},
    val unarchiveSession: (String) -> Unit = {},
    val setDrawerGrouping: (DrawerGrouping) -> Unit = {},
    val setDrawerOrderByUpdated: (Boolean) -> Unit = {},
    val setDrawerShowArchived: (Boolean) -> Unit = {},
    val setDrawerCollapsedSections: (Set<String>) -> Unit = {},
    val searchSessionContent: (String) -> Unit = {},
    val moveWorkspace: (String, String?) -> Unit = { _, _ -> },
    val openAutomationTasks: () -> Unit = {},
    val retryAutomationTasks: () -> Unit = {},
    val loadScheduleHistory: (ScheduleEntry) -> Unit = {},
    val loadMoreScheduleHistory: (ScheduleEntry) -> Unit = {},
    val deleteSchedule: (ScheduleEntry) -> Unit = {},
    val dismissScheduleAction: () -> Unit = {},
    val updateDeepSeekKey: (String) -> Unit = {},
    val signOut: () -> Unit = {},
    val connect: () -> Unit = {},
    val restartConnection: () -> Unit = {},
    val forceQuit: () -> Unit = {},
    val updateLanUrl: (String) -> Unit = {},
    val checkCertificate: (String) -> Unit = {},
    val trustCertificate: (String, java.security.cert.X509Certificate) -> Unit = { _, _ -> },
    val forgetCertificate: (String) -> Unit = {},
    val writeHostSetting: (String, String, String) -> Unit = { _, _, _ -> },
    /** `DshViewModel.setTerminalFontSp`, from the Me page's Terminal tab. */
    val setTerminalFont: (Float) -> Unit = {},
)
