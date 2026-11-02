package uk.xa0.dsh.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import uk.xa0.dsh.DshViewModel

/**
 * The conversation screen's **shell**: everything that is the connection rather than a
 * drawing of it.
 *
 * ## The split (2.5a)
 *
 * This file used to hold the whole screen — 3 284 lines, with the tree, the drawer, the
 * sheets and thirty-odd `vm.` reads in one function. [ChatPanel] now holds the tree and
 * takes data and lambdas; this holds the flows, the effects that talk to the host, and
 * the one line that wires the two together.
 *
 * The split is the same one 2.2b made for the Shell tab, for the same reason: a screen
 * whose entry point is a view model cannot be photographed, and the conversation is the
 * screen readers spend the most time on. `docs/PHASE-2-PLAN.md` §2.5a.
 *
 * ## What belongs here, and only here
 *
 *  - **Collecting the flows.** [ChatPanel] takes plain values, so the subscription to
 *    each `StateFlow` lives here. That is also what keeps a fixture from needing a host.
 *  - **Effects with side effects on the host.** The host-settings read on the first
 *    frame, the one-second clock for a running turn's status line, and the draft's
 *    persistence. Each of those is an *act*, not a drawing.
 *  - **The Shell page's content.** `TerminalScreen` collects its own flow and enters the
 *    terminal over RPC, so it is passed to the panel as a slot rather than built there.
 *
 * ## What does not
 *
 * The draft's `TextFieldState` is still created here, because its saver is keyed by the
 * session and the shell is what knows the key. It is passed *into* the panel through a
 * callback rather than as state: the box is the panel's to draw and to edit, and the
 * shell only ever reads what the panel has already saved.
 */

/** The conversation screen. The only production caller of [ChatPanel]. */
@Composable
fun ChatScreen(vm: DshViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val todos by vm.todos.collectAsStateWithLifecycle()
    val header by vm.header.collectAsStateWithLifecycle()
    // The Shell tab's own state. The panel reads one thing from it — the seat count,
    // which is what decides that closing the last shell returns to the conversation —
    // so it is collected here and passed in rather than left to the slot below.
    val terminal by vm.terminal.collectAsStateWithLifecycle()
    val endedTurns by vm.endedTurns.collectAsStateWithLifecycle()
    // The host's settings document is read once the connection is up, not only when
    // the Settings sheet is opened.
    //
    // It was read on that tap alone, and the transcript needs one field from it: until
    // the sheet had been opened once, `ui-chat.transcriptView` was unknown and the
    // fallback folded every completed turn. The host on this device is set to `normal`,
    // so the reader's rows were being closed under them by a setting that says the
    // opposite of what was happening.
    var settingsAsked by remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!settingsAsked) {
            settingsAsked = true
            vm.loadHostSettings()
        }
    }

    // A live clock for the running turn's status line, ticked once a second. Gated on
    // the session's *own* turn (`ownTurnInFlight`) like the row it feeds, so a session
    // the host still reports running while only a subagent works neither draws the row
    // nor keeps a ticker alive for it.
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val turnStartedAt by vm.turnStartedAt.collectAsStateWithLifecycle()
    LaunchedEffect(ui.ownTurnInFlight, turnStartedAt) {
        if (!ui.ownTurnInFlight) return@LaunchedEffect
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
    }
    val elapsedMs = turnStartedAt?.let { (nowMs - it).coerceAtLeast(0L) }

    val closingSeqs by vm.closingSeqs.collectAsStateWithLifecycle()
    val branchSeqs by vm.branchSeqs.collectAsStateWithLifecycle()
    val turnDurations by vm.turnDurations.collectAsStateWithLifecycle()
    val references by vm.references.collectAsStateWithLifecycle()
    val hostCommands by vm.commands.collectAsStateWithLifecycle()
    val directoryLevel by vm.directoryLevel.collectAsStateWithLifecycle()
    val directoryLoading by vm.directoryLoading.collectAsStateWithLifecycle()
    val directoryBusy by vm.directoryBusy.collectAsStateWithLifecycle()
    val directoryError by vm.directoryError.collectAsStateWithLifecycle()
    // The Files panel's explorer half. A flow of its own, like the browser's: a listing
    // failure belongs to the panel, not to the session error banner.
    val workspaceExplorer by vm.workspaceExplorer.collectAsStateWithLifecycle()

    val state = ChatUiState(
        ui = ui,
        entries = entries,
        live = live,
        todos = todos,
        header = header,
        terminal = terminal,
        endedTurns = endedTurns,
        closingSeqs = closingSeqs,
        branchSeqs = branchSeqs,
        turnDurations = turnDurations,
        turnStartedAt = turnStartedAt,
        elapsedMs = elapsedMs,
        references = references,
        commands = hostCommands,
        directoryLevel = directoryLevel,
        directoryLoading = directoryLoading,
        directoryBusy = directoryBusy,
        directoryError = directoryError,
        workspaceExplorer = workspaceExplorer,
        // A field of `ui`, not a flow of its own: the restore belongs to the session's
        // state, and a second subscription to it would be a second source of truth.
        draftRestore = ui.draftRestore,
        toolParents = vm.toolParents(),
        // The Shell page's cell size: a preference the panel writes (Me page's Terminal
        // tab and the shell's own A-/A+), so it travels with the state the panel draws.
        terminalFontSp = ui.terminalFontSp,
    )

    ChatPanel(
        state = state,
        actions = ChatActions(
            openSession = vm::openSession,
            startSession = vm::startSession,
            startSessionIn = vm::startSessionIn,
            startSessionInWorkspace = vm::startSessionInWorkspace,
            send = vm::send,
            sendDeferred = { text -> vm.send(text) },
            submitMode = vm::submitMode,
            stop = vm::stop,
            saveDraft = vm::saveDraft,
            draftFor = vm::draftFor,
            consumeDraftRestore = vm::consumeDraftRestore,
            loadOlderHistory = vm::loadOlderHistory,
            loadHostSettings = vm::loadHostSettings,
            toolParents = vm::toolParents,
            imageBitmap = vm::imageBitmap,
            observeJob = vm::observeJob,
            markJobsSeen = vm::markJobsSeen,
            resolveApproval = vm::resolveApproval,
            answerQuestions = vm::answerQuestions,
            skipQuestions = vm::skipQuestions,
            exitPlanMode = vm::exitPlanMode,
            pauseGoal = vm::pauseGoal,
            resumeGoal = vm::resumeGoal,
            editGoal = vm::editGoal,
            clearGoal = vm::clearGoal,
            editQueueItem = vm::editQueueItem,
            removeQueueItem = vm::removeQueueItem,
            steerQueueItem = vm::steerQueueItem,
            addAttachment = vm::addAttachment,
            removeAttachment = vm::removeAttachment,
            executeCommand = vm::executeCommand,
            searchReferences = vm::searchReferences,
            clearReferences = vm::clearReferences,
            refreshCommands = vm::refreshCommands,
            selectModel = vm::selectModel,
            selectAgentPreset = vm::selectAgentPreset,
            refreshAgentPresets = vm::refreshAgentPresets,
            setPermission = vm::setPermission,
            updateBusyEnter = vm::updateBusyEnter,
            updateTheme = vm::updateTheme,
            updatePreset = vm::updatePreset,
            branchFromConclusion = vm::branchFromConclusion,
            dismissError = vm::dismissError,
            signInAgain = vm::signInAgain,
            refreshSidebar = vm::refreshSidebar,
            refreshBalance = vm::refreshBalance,
            checkRouter = vm::checkRouter,
            setSubagentCatalogOpen = vm::setSubagentCatalogOpen,
            resetDirectoryBrowser = vm::resetDirectoryBrowser,
            listDirectory = vm::listDirectory,
            createDirectory = vm::createDirectory,
            addWorkspace = vm::addWorkspace,
            listWorkspaceDirectory = vm::listWorkspaceDirectory,
            reloadWorkspaceExplorer = vm::reloadWorkspaceExplorer,
            resetWorkspaceExplorer = vm::resetWorkspaceExplorer,
            workspaceFileLoader = vm::workspaceFileLoader,
            workspaceFileDownloader = vm::workspaceFileDownloader,
            renameSession = vm::renameSession,
            forkSession = vm::forkSession,
            archiveSession = vm::archiveSession,
            unarchiveSession = vm::unarchiveSession,
            setDrawerGrouping = vm::setDrawerGrouping,
            setDrawerOrderByUpdated = vm::setDrawerOrderByUpdated,
            setDrawerShowArchived = vm::setDrawerShowArchived,
            setDrawerCollapsedSections = vm::setDrawerCollapsedSections,
            searchSessionContent = vm::searchSessionContent,
            moveWorkspace = vm::moveWorkspace,
            openAutomationTasks = vm::openAutomationTasks,
            retryAutomationTasks = vm::retryAutomationTasks,
            loadScheduleHistory = vm::loadScheduleHistory,
            loadMoreScheduleHistory = vm::loadMoreScheduleHistory,
            deleteSchedule = vm::deleteSchedule,
            dismissScheduleAction = vm::dismissScheduleAction,
            updateDeepSeekKey = vm::updateDeepSeekKey,
            signOut = vm::signOut,
            connect = vm::connect,
            restartConnection = vm::restartConnection,
            forceQuit = vm::forceQuit,
            updateLanUrl = vm::updateLanUrl,
            checkCertificate = vm::checkCertificate,
            trustCertificate = vm::trustCertificate,
            forgetCertificate = vm::forgetCertificate,
            writeHostSetting = vm::writeHostSetting,
            setTerminalFont = vm::setTerminalFontSp,
        ),
        // The Shell page. Composed here rather than inside the panel because it enters
        // and leaves the terminal over RPC — the panel is handed the finished screen.
        terminal = { modifier ->
            TerminalScreen(
                vm = vm,
                sessionId = ui.currentSessionId,
                // The cell size is a UI preference like the theme and the drawer's
                // modes, and this is the composable that already holds that state.
                fontSp = ui.terminalFontSp,
                modifier = modifier,
            )
        },
    )
}

