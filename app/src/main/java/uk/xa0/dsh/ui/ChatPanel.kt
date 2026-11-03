package uk.xa0.dsh.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.exclude
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import uk.xa0.dsh.BalanceState
import uk.xa0.dsh.PENDING_DRAFT_KEY
import uk.xa0.dsh.PendingKind
import uk.xa0.dsh.ReferenceCandidate
import uk.xa0.dsh.UiState
import uk.xa0.dsh.data.BusyEnter
import uk.xa0.dsh.diag.DiagFrames
import uk.xa0.dsh.diag.DiagLazyList
import uk.xa0.dsh.diag.ScrollDiag
import uk.xa0.dsh.diag.diagDrag
import uk.xa0.dsh.diag.diagOffset
import uk.xa0.dsh.diag.diagRowGrowth
import uk.xa0.dsh.model.CapsuleState
import uk.xa0.dsh.model.ChatEntry
import uk.xa0.dsh.model.DisplayRow
import uk.xa0.dsh.model.GeneralSettings
import uk.xa0.dsh.model.LiveAttempt
import uk.xa0.dsh.model.SessionFiles
import uk.xa0.dsh.model.StatusCapsule
import uk.xa0.dsh.model.StatusCapsules
import uk.xa0.dsh.model.PendingKindWire
import uk.xa0.dsh.model.SessionTarget
import uk.xa0.dsh.model.SubagentComposerState
import uk.xa0.dsh.model.SubagentReadOnlyReason
import uk.xa0.dsh.model.TurnDeliverables
import uk.xa0.dsh.model.buildDisplayRows
import uk.xa0.dsh.model.buildToolCallTree
import uk.xa0.dsh.model.buildTrajectory
import uk.xa0.dsh.model.familyUsage
import uk.xa0.dsh.model.nextFoldedTurns
import uk.xa0.dsh.model.subagentComposerState
import uk.xa0.dsh.model.subagentTargetOf
import uk.xa0.dsh.model.tokenRatesFor
import uk.xa0.dsh.model.turnOrNull
import uk.xa0.dsh.ui.agentPresetLabel
import uk.xa0.dsh.ui.components.ApprovalCard
import uk.xa0.dsh.ui.components.AssistantMessageRow
import uk.xa0.dsh.ui.glass.GlassBarBackdrop
import uk.xa0.dsh.ui.glass.glassBarLayer
import uk.xa0.dsh.ui.glass.glassLift
import uk.xa0.dsh.ui.glass.liquidPane
import uk.xa0.dsh.ui.glass.rememberGlassBarBackdrop
import uk.xa0.dsh.ui.nav.BarHeight
import uk.xa0.dsh.ui.nav.BottomBarItem
import uk.xa0.dsh.ui.components.CommandEntry
import uk.xa0.dsh.ui.components.CommandMenu
import uk.xa0.dsh.ui.components.ComposerAccessoryRail
import uk.xa0.dsh.ui.components.ComposerDock
import uk.xa0.dsh.ui.components.FullScreenComposer
import uk.xa0.dsh.ui.components.DeliverableRow
import uk.xa0.dsh.ui.components.DotState
import uk.xa0.dsh.ui.components.DshMark
import uk.xa0.dsh.ui.components.ExplorerWiring
import uk.xa0.dsh.ui.components.FilesPanel
import uk.xa0.dsh.ui.components.GoalBar
import uk.xa0.dsh.ui.components.JobsSeat
import uk.xa0.dsh.ui.components.JobsSheet
import uk.xa0.dsh.ui.components.LiveAttemptRow
import uk.xa0.dsh.ui.components.NoticeRow
import uk.xa0.dsh.ui.components.QueueDock
import uk.xa0.dsh.ui.components.QuestionCard
import uk.xa0.dsh.ui.components.ReferenceMenu
import uk.xa0.dsh.ui.components.StateDot
import uk.xa0.dsh.ui.components.SubagentReadOnlyComposer
import uk.xa0.dsh.ui.components.TodoCard
import uk.xa0.dsh.ui.components.ToolCallRow
import uk.xa0.dsh.ui.components.ToolCallTreeRow
import uk.xa0.dsh.ui.components.TurnProcessRow
import uk.xa0.dsh.ui.components.TurnStatusRow
import uk.xa0.dsh.ui.components.UserMessageRow
import uk.xa0.dsh.ui.components.WorkspaceBrowser
import uk.xa0.dsh.ui.components.composerCommandEntries
import uk.xa0.dsh.ui.composer.ComposerTriggers
import uk.xa0.dsh.ui.composer.applySendRestore
import uk.xa0.dsh.ui.composer.composerTriggers
import uk.xa0.dsh.ui.composer.consumeTrigger
import uk.xa0.dsh.ui.composer.replaceTrigger
import uk.xa0.dsh.ui.scroll.TailFollow
import uk.xa0.dsh.ui.settings.MeSettingsActions
import uk.xa0.dsh.ui.settings.MeSettingsPage
import uk.xa0.dsh.ui.settings.MeSettingsState
import uk.xa0.dsh.ui.overlay.ConfirmDialogHost
import uk.xa0.dsh.ui.overlay.ConfirmDialogVisuals
import uk.xa0.dsh.ui.overlay.DshSheet
import uk.xa0.dsh.ui.theme.DshRadius
import uk.xa0.dsh.ui.theme.DshSpacing
import uk.xa0.dsh.ui.theme.DshTheme
import uk.xa0.dsh.ui.theme.DshType

/**
 * Everything [ChatScreen] draws, with the view model taken out.
 *
 * ## Why (2.5a)
 *
 * The conversation screen was reachable only as `ChatScreen(vm: DshViewModel)`, and
 * that made it the one screen in the app no baseline could photograph: a screenshot
 * needs a fixture, and a fixture cannot be a nine-thousand-line view model wired to a
 * live host socket. The same argument produced [TerminalPanel] in 2.2b; this is that
 * move made once more, at the size the conversation screen actually is.
 *
 * ## What this is, and is not
 *
 * It is a **move**. The tree below is the tree `ChatScreen` drew, with two changes and
 * no others: every `vm.x` became `actions.x`, and every `vm::x` became `actions.x` —
 * so the pixels are what they were, and the evidence is the eleven baselines staying
 * byte-identical rather than anyone's reading of the diff.
 *
 * It is *not* a rewrite, and the state is deliberately coarse: [ChatUiState.ui] is the
 * whole `UiState` rather than thirty hoisted fields, because the screen reads it from
 * thirty places and splitting it would be a rename wearing a refactor's clothes. What
 * this buys is the thing 2.5 actually needs — a composable whose arguments are data, so
 * the chat screen can be photographed, and so the pager in 2.5c has a page to hold.
 *
 * ## What stays in the shell
 *
 * Anything that *is* the connection rather than a drawing of it: collecting the flows,
 * the live clock's ticker, the host-settings read on first frame, the terminal's
 * enter/leave RPCs, and the draft's persistence. [terminal] is a slot for exactly that
 * reason — `TerminalScreen` owns a `StateFlow` and an RPC, so the panel takes the
 * composed screen as a parameter instead of knowing how to build it.
 *
 * @param state everything the tree draws from.
 * @param actions every act the tree can perform.
 * @param terminal the Shell page's content. A slot rather than a view-model call, so
 *   the panel itself never reaches the host.
 */
@OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    // `TextFieldState.undoState` — the undo history the composer offers.
    ExperimentalFoundationApi::class,
)
@Composable
internal fun ChatPanel(
    state: ChatUiState,
    actions: ChatActions,
    terminal: @Composable (Modifier) -> Unit,
) {
    val ui = state.ui
    val entries = state.entries
    val live = state.live
    val todos = state.todos
    val header = state.header
    val endedTurns = state.endedTurns
    // The host's "Conversation display" setting, honoured at last: `normal` folds
    // nothing, so a completed turn keeps every row it had — interim messages, tool
    // calls, to-dos — instead of collapsing them behind one summary line. Absent
    // or unreadable falls back to `compact`, which is what the app did for
    // everything before.
    val transcriptView = ui.hostSettings
        ?.field(GeneralSettings.CHAT_NS, GeneralSettings.TRANSCRIPT_FIELD)
        ?.value
        ?: "compact"
    val foldTurns = transcriptView != "normal"

    // The Me page's tabs and the Settings sheet draw from this one holder (3.5b): eight
    // pages would otherwise thread thirty values through MeDestination, and the sheet
    // would keep a second copy of them. Everything here is a read of `ui` or of the
    // panel's own arguments; nothing new is stored.
    val meSettings = MeSettingsState(
        themeMode = ui.themeMode,
        busyEnter = ui.busyEnter,
        transcriptView = transcriptView,
        // No default-permission state exists on this client, so the row shows
        // "Unavailable" rather than claiming a value the host never sent.
        defaultPermission = "",
        permissionOptions = ui.permissionOptions,
        models = ui.models,
        providerOrder = ui.providerOrder,
        selectedModel = ui.selectedModel,
        agentPreset = ui.agentPreset,
        agentPresetOptions = ui.agentPresetOptions,
        agentModePickerEnabled = true,
        // Archived ids are a set on this client, so the host's own archive order is not
        // recoverable here.
        archivedSessions = ui.sessions.filter { it.id in ui.archivedSessionIds },
        workspaces = ui.workspaces,
        sessionsLoading = ui.sessionsLoading,
        hostSettings = ui.hostSettings,
        hostSettingsLoading = ui.hostSettingsLoading,
        hostSettingsError = ui.hostSettingsError,
        settingsSaving = ui.settingsSaving,
        settingsWriteError = ui.settingsWriteError,
        terminalFontSp = state.terminalFontSp,
    )
    val meSettingsActions = MeSettingsActions(
        onTheme = actions.updateTheme,
        onBusyEnter = actions.updateBusyEnter,
        onPreset = actions.updatePreset,
        onSelectModel = actions.selectModel,
        onUnarchive = actions.unarchiveSession,
        onSettingWrite = actions.writeHostSetting,
        onTerminalFont = actions.setTerminalFont,
    )
    val closingSeqs = state.closingSeqs
    val branchSeqs = state.branchSeqs
    val turnDurations = state.turnDurations
    val turnStartedAt = state.turnStartedAt
    val references = state.references
    val hostCommands = state.commands
    val directoryLevel = state.directoryLevel
    val directoryLoading = state.directoryLoading
    val directoryBusy = state.directoryBusy
    val directoryError = state.directoryError
    // The Files panel's explorer half. A flow of its own, like the browser's: a
    // listing failure belongs to the panel, not to the session error banner.
    val workspaceExplorer = state.workspaceExplorer
    // The Plugins page's inventory, a flow of its own for the same reason.
    val pluginInventory = state.pluginInventory

    // The running turn's elapsed time. Tick-tocked by the shell, not here: a clock is
    // an act against the host's timeline, and a screenshot fixture has to be able to
    // hand the panel a number instead of waiting a second for one.
    val elapsedMs = state.elapsedMs

    /** Turns whose process rows the user has opened; the rest stay folded. */
    var expandedTurns by remember { mutableStateOf(emptySet<Int>()) }

    /**
     * The `session/fork` boundary the reader has asked to branch at, while the
     * confirmation is up; null when none is.
     *
     * Held here rather than acted on at the tap, because a branch creates a
     * **new session**: the dialog is the one place the consequence is stated before
     * it is paid for, and a confirmation that a rotation can drop costs nothing —
     * the reader re-taps the row.
     */
    var branchAt by remember { mutableStateOf<Int?>(null) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    // Asked to start past the end and left to clamp: a session must come up at its
    // newest row, and how many rows that is only the host knows. Without this the
    // first frame drew the *oldest* row and the follow moved it half a second later
    // — measured on the phone as `surf items=179 i=0` followed by a jump to 170.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = Int.MAX_VALUE)

    // Whether a finger is on the transcript. See [rememberTouchGate]: everything
    // that moves the list on its own has to ask, because `isScrollInProgress` is off
    // for the whole of the gap between a finger landing and its first movement past
    // slop. Measured on the phone before that gate existed: 23 of the per-token
    // follows fired inside that gap, moving the list before the reader did.
    val touch = rememberTouchGate()

    // The conversation is an ordinary, top-anchored list followed at its tail. See
    // `ui/scroll/TailFollow.kt` for the measurements: the reversed list this
    // replaces anchored on the row at the *bottom* of the viewport, so a row that
    // grew grew upwards by exactly the height it gained — taking its own header off
    // the screen — and every content change moved whatever sat above the anchor.
    //
    // `followFrom` is a counter rather than a flag because two separate moments mean
    // "follow this again", and both of them count even when the reader has scrolled
    // away: opening a session, and sending a message.
    var followFrom by remember { mutableStateOf(0) }
    val following = TailFollow(
        state = listState,
        rearmKey = followFrom,
        holding = { touch.isDown },
        onMove = { ScrollDiag.prog("chat", it) },
    )

    // Freeze the streaming row while the reader is away from the tail.
    //
    // Not following stops the list being *scrolled*, but not the row being laid out
    // again: a thinking segment grows every few milliseconds, and a growing item
    // re-lays-out the list under the reader — "scrolling up into a text block while
    // it's being written to leads to involuntary scrolling". Pinning the content at
    // the moment the follow is released means nothing below can reflow while the
    // reader is in history; it catches up in one step when they return.
    var frozenLive by remember { mutableStateOf<LiveAttempt?>(null) }
    val currentLive by rememberUpdatedState(live)
    LaunchedEffect(Unit) {
        snapshotFlow { following.value }.distinctUntilChanged().collect { follow ->
            if (follow) {
                frozenLive = null
            } else if (frozenLive == null) {
                // Snapshot on the way out only: re-entering history must not
                // re-snapshot a row the reader is already part-way through, or the
                // content would jump forward under them on the second drag.
                frozenLive = currentLive
            }
        }
    }

    // ...and hold the *shape* of the ended-turn fold for the same reason.
    //
    // Folding is what a just-finished turn does to rows that a moment earlier were
    // the running turn's rows, and when the reader is inside them it rewrites the
    // whole viewport: measured on a parked reader, a fold replaced 79% of the rows
    // in view and took them from the middle of the turn to the beginning of the
    // session. Re-pinning cannot save that one — every row they can see is a fold
    // member, so there is no surviving key to pin and Compose falls back to the raw
    // index. The fix at this level is not to rewrite rows the reader is reading: the
    // set is held while they are away, and applied in one step when they return.
    var foldedTurns by remember { mutableStateOf(endedTurns) }

    // Which turns the reader can actually see, one frame behind. A fold replaces the
    // rows it stands in for, so folding a turn somebody is reading closes what they
    // are looking at — the complaint this answers, in the words it was made in.
    //
    // Being at the tail is not enough on its own: while following, the turn that just
    // ended is the one under the reader's eye, and the closing message is exactly the
    // moment the fold appears. A turn on screen is therefore not folded at all, however
    // the reader got there — following, dragging, or jumping with the rail — and folds
    // once they have scrolled past it.
    var visibleTurns by remember { mutableStateOf(emptySet<Int>()) }
    LaunchedEffect(following.value, endedTurns, visibleTurns) {
        val wanted = nextFoldedTurns(foldedTurns, endedTurns, visibleTurns, following.value)
        if (wanted != foldedTurns) foldedTurns = wanted
    }

    // A session switch is not a scroll. `listState` is remembered across sessions on
    // purpose — the list must not be rebuilt per session — so without this an opened
    // session inherits the previous one's position and freeze. Opening a session
    // means following it.
    LaunchedEffect(ui.currentSessionId) {
        frozenLive = null
        // A session switch replaces every row there is, which is indistinguishable
        // from the app moving a list on its own unless it says so.
        ScrollDiag.prog("chat", "session-switch")
        followFrom++
    }

    // Reaching the oldest loaded row asks the host for the page before it.
    // `session/follow` opens a bounded window, so without this the transcript just
    // stops when you scroll up. Requiring the list to be longer than the viewport
    // keeps a short session from paging on open, and the call is itself a no-op once
    // the host reports nothing older.
    //
    // The oldest row is at the *top* of a top-anchored list, so this watches the
    // first visible item rather than the last.
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val first = info.visibleItemsInfo.firstOrNull()?.index ?: -1
            info.totalItemsCount > info.visibleItemsInfo.size && first == 0
        }.distinctUntilChanged().collect { atOldest ->
            if (atOldest) actions.loadOlderHistory()
        }
    }

    // The draft carries its selection: the `/` and `@` menus follow the caret, so
    // a tap back into an earlier line must be visible to the trigger derivation.
    //
    // Seeded from disk, keyed by the session — and by [PENDING_DRAFT_KEY] on the
    // deferred new-session screen, which is a composer with no session to file under.
    // `rememberSaveable` alone lost the text on a switch (the key change recreates the
    // state) and on any cold start; a draft is what someone was in the middle of.
    //
    // `TextFieldState.Saver` is the platform's own saver for the stateful field: it
    // persists the text, the selection *and* the undo history, and it is the one
    // `rememberTextFieldState` uses internally. That function has no inputs, so it
    // would keep a single state across a session switch and file every draft under
    // the first key; passing `draftKey` here is what keeps the box per-session.
    val draftKey = ui.currentSessionId ?: PENDING_DRAFT_KEY
    val draft = rememberSaveable(draftKey, saver = TextFieldState.Saver) {
        TextFieldState(actions.draftFor(draftKey))
    }
    // Read once per composition: `TextFieldState.text` is a snapshot-backed
    // `CharSequence`, and this is the read that subscribes the screen to the field.
    // The trigger derivation below needs a `String` anyway.
    val draftText = draft.text.toString()
    // Written on every change rather than on a timer, and after every other way the text
    // moves — send clears it, a failed send hands it back — so the file always holds what
    // is on screen. An empty draft is a deletion on the store's side.
    LaunchedEffect(draftKey, draftText) { actions.saveDraft(draftKey, draftText) }

    // …and a draft typed on the deferred screen follows the session it turns into.
    //
    // Two ways that happens under the reader: a send materialises the session, and the
    // app's own auto-open picks one the moment the connection comes up — which can land
    // while they are still typing on the hero. The key changes, the box would reseed from
    // the new session's (empty) draft, and their words would be left behind under the
    // hero's key. The store already moves a draft on materialisation; this is the same
    // rule for the composed case, which is the one a reader can see.
    var trackedKey by remember { mutableStateOf(draftKey) }
    var trackedText by remember { mutableStateOf(draftText) }
    LaunchedEffect(draftKey, draftText) {
        if (draftKey == trackedKey) {
            trackedText = draftText
        } else {
            if (trackedKey == PENDING_DRAFT_KEY && draftText.isEmpty() && trackedText.isNotBlank()) {
                draft.edit { replace(0, length, trackedText) }
            }
            trackedKey = draftKey
            trackedText = draft.text.toString()
        }
    }

    // The box a send emptied, and whether it may still take that send's text back.
    //
    // A send clears the composer; if it never reached a session the host hands the text
    // back through `ui.draftRestore`. Asking only whether the box is empty stopped being
    // the right question once undo could empty it too, so the restore is keyed on the
    // send itself: `restoreArmed` is set by the clear, and an undo since then rules the
    // restore out. `canRedo` is true exactly when an undo has happened since the last
    // edit, and it is read at the decision — inside `applySendRestore`, called from the
    // effect below — rather than watched in a collector: an undo and a failure's
    // restore can land in the same frame, and a read at decision time cannot be
    // overtaken by the order two coroutines resume in. Reading it also
    // means an undo from any route counts: the keyboard's context-menu undo or a Ctrl
    // chord. Typing needs nothing extra: a non-empty box already fails
    // the rule. See [restoreSendFailure] and [applySendRestore] for the rule itself.
    //
    // Unscoped by `draftKey` on purpose. A failed first send off the pending seat
    // materialises a session first, so the key changes between the send and the failure
    // that hands the text back; scoping the arming to the old key would lose the reader's
    // only copy.
    var restoreArmed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.draftRestore) {
        val restore = state.draftRestore ?: return@LaunchedEffect
        // The rule, and the act of applying it, live together in
        // `applySendRestore`. Its answer is what the banner reports: the text is
        // only said to be back when it is.
        val restored = applySendRestore(draft, restore.text, restoreArmed)
        restoreArmed = false
        actions.consumeDraftRestore(restored)
    }

    // What a submission does to the box, in the one place all three send paths share.
    // The text is taken before the clear, so the send carries exactly what was on
    // screen; the clear goes through `state.edit`, which is also what wipes the undo
    // history — a message that has been sent must not come back on Ctrl+Z. Arming the
    // restore here is what lets a send that never reaches a session hand the text back
    // without a later undo being mistaken for the same empty box.
    fun takeDraftForSend(): String {
        val sent = draft.text.toString()
        draft.edit { replace(0, length, "") }
        // Only a send that carried text can need that text back; an attachment-only
        // send arms nothing, and a send in between disarms the one before it.
        restoreArmed = sent.isNotEmpty()
        return sent
    }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    // The About sheet is pure local state, so unlike Settings it needs no read on
    // open — see `AboutSheet`.
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showModels by rememberSaveable { mutableStateOf(false) }
    var showPermission by rememberSaveable { mutableStateOf(false) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var showWorkspace by rememberSaveable { mutableStateOf(false) }
    var showPresets by rememberSaveable { mutableStateOf(false) }
    var showJobs by rememberSaveable { mutableStateOf(false) }
    var showBrowser by rememberSaveable { mutableStateOf(false) }
    // Host-wide, and read on open rather than on a timer: the sheet issues its own
    // `schedule/catalog` call, so nothing here costs anything until it is asked for.
    var showAutomations by rememberSaveable { mutableStateOf(false) }

    // Two levels of navigation, and the split is the whole of 3.3.
    //
    // ## Destinations are the pager; views are a plain value
    //
    // The **destination** is where in the app you are — Chat, Files, Plugins, Me — and it
    // is a pager, so a horizontal swipe is how a phone says the same thing without a
    // control. Inside the Chat destination the **view** — Chat, Trajectory, Shell — is
    // three readings of the same session, so it is a pill, not a place.
    //
    // ## One writable state per level (2.5c, still true)
    //
    // `destinationState` is the only thing written for the outer level; `destination` is
    // *derived* from the page the pager is on, so the bar's highlighted seat, the back
    // handler and the Files seat all read the same fact the reader is looking at, and a
    // swipe updates all three for free. A parallel `var destination` would be two
    // writable sources for one answer — the symptom being a bar highlighting one place
    // while another is on screen.
    //
    // `chatView` is the second level, and having a second writable value here is not the
    // mistake 2.5c warned about: it answers a *different* question, and nothing derives
    // one from the other. What 2.5c forbade was two writables for the *same* answer.
    //
    // The session keys the view, as it did: the web remembers the choice per session, and
    // moving between sessions is not a reason to carry Shell open into the next one.
    val destinationState = rememberPagerState(
        initialPage = Destination.CHAT.ordinal,
        pageCount = { Destination.entries.size },
    )
    val destination = Destination.entries[destinationState.currentPage]
    // A tap on the bar is a *request* that takes a spring to arrive, so it goes through
    // the pager rather than writing the destination.
    val goToDestination: (Destination) -> Unit = { target ->
        scope.launch { destinationState.animateScrollToPage(target.ordinal) }
    }
    var chatView by rememberSaveable(ui.currentSessionId) { mutableStateOf(ChatView.CHAT) }
    val goToView: (ChatView) -> Unit = { chatView = it }

    // The bar's material needs something to sample (3.2b).
    //
    // The handle is opaque on purpose: this file must not name a miuix-blur type, or the
    // rule in NOTICE.md that keeps those names inside `:design` would be broken by the
    // one caller that is hardest to audit. Null means "this platform paints the flat
    // material", and both ends of the wiring below turn that into a no-op — so the same
    // two lines run on every device and the platform decides how much of them means
    // anything. See `ui/glass/GlassBarBackdrop.kt` for the capability test.
    val glassBackdrop = rememberGlassBarBackdrop()
    // A session switch returns to the conversation *and* to its transcript view, which is
    // what re-creating the old `rememberSaveable(ui.currentSessionId)` on the pager used
    // to do at both levels.
    LaunchedEffect(ui.currentSessionId) {
        if (destinationState.currentPage != Destination.CHAT.ordinal) {
            destinationState.scrollToPage(Destination.CHAT.ordinal)
        }
        if (chatView != ChatView.CHAT) chatView = ChatView.CHAT
    }

    // Closing the last shell returns to the conversation.
    //
    // The Shell tab is a place you go and then leave by closing what you opened; with
    // nothing left in it there is nothing to look at and no way back except the tab
    // strip. Fired on the *transition* to empty rather than on emptiness, so opening the
    // tab to start a first shell is not immediately undone.
    //
    // Keyed on the seat count rather than run once. It was `LaunchedEffect(Unit)`, which
    // captures `view`'s state object from the first composition — and
    // `rememberSaveable(ui.currentSessionId)` replaces that object on every session
    // switch, so after the first switch the rule read and wrote a dead copy. Measured on
    // the device: `seats=0 previous=1 view=CHAT` with the Shell tab on screen, and
    // nothing moved. A changed key re-runs the block, so it sees the live state.
    val seats = state.terminal.terminals.size
    var hadShell by remember { mutableStateOf(false) }
    LaunchedEffect(seats) {
        if (seats == 0 && hadShell && chatView == ChatView.TERMINAL) {
            android.util.Log.d("DshView", "last shell closed; returning to the conversation")
            // No animation: the shell the reader was looking at is gone, so there is
            // nothing to slide past — animating would show them two empty pages.
            chatView = ChatView.CHAT
        }
        hadShell = seats > 0
    }

    // The router panel's gate — a **panel**, not a destination.
    //
    // `ui.routerTarget` is derived from the host's own model selection — the same value
    // the composer's trigger draws — so it is non-null exactly while the route this
    // session will run is served by something on the host's loopback. A cloud model
    // selected in force, a switch to one that is only pending, and a selection the
    // host's document does not describe all make it null.
    //
    // ## Why it is no longer a tab (2.4)
    //
    // It used to be the fourth `ChatView`, which meant the tab strip had **three or four
    // items depending on the selected model** — and, because `localProviders` starts as an
    // empty map and is filled when `settings/describe` answers, it went from three to four
    // a moment after every cold start. On a top strip that only cost a row of chrome. On
    // the bottom bar it would move every other item sideways while the reader was looking
    // at it, which is the specific thing `docs/PHASE-2-PLAN.md` §2.4 says the bar must not
    // do. The bar's item list is therefore a constant (`ChatView.entries`), and this panel
    // opens from a header seat instead — the same affordance the Files and Jobs seats use.
    //
    // The alternative — a permanent fourth destination — was rejected on the code's own
    // terms rather than on taste: `RouterTab(target: RouterTarget)` takes a **non-null**
    // target and dereferences it 18 times, so a destination that can be reached with no
    // local route needs a nullable signature and a fourth kind of "nothing to report"
    // wording. It would also contradict `model/LlamaRouter.kt:138-147`, which states that
    // "the tab cannot appear for a cloud route" as the design intent: behind a cloud route
    // there is no llama.cpp router to describe, so there is nothing a reader loses.
    val routerTarget = ui.routerTarget
    // A panel, so it has its own open/closed state — the same shape as `showFiles` and
    // `showJobs`. Keyed by session because the answer belongs to one route in one session.
    var showRouter by rememberSaveable(ui.currentSessionId) { mutableStateOf(false) }
    // The gate can shut while the panel is open (the reader switches to a cloud model in
    // the composer without closing it first). Closing it is the honest response: the panel
    // describes a route that is no longer selected.
    LaunchedEffect(routerTarget) {
        if (routerTarget == null && showRouter) {
            android.util.Log.d("DshView", "local route left; closing the router panel")
            showRouter = false
        }
    }

    // Opening the panel is what asks the question, and re-opening it asks again: a
    // reachability answer is only worth reading with the time it was taken, so the check
    // is re-run rather than shown from a previous visit. The session is a key too: two
    // sessions can select the same local route, and moving between them is a new "is my
    // router up now", not a reason to show the other one's answer.
    LaunchedEffect(
        ui.currentSessionId,
        showRouter,
        routerTarget?.provider?.id,
        routerTarget?.modelId,
    ) {
        if (showRouter && routerTarget != null) actions.checkRouter()
    }

    // There is no `showFiles` any more (3.3): Files went from a panel that covered the
    // conversation to a destination of its own, so "is it open" is
    // `destination == Destination.FILES` — one fact, not two. The focus path survives
    // because a deliverable chip still has to say *which* file to look at.
    // The balance sheet, reached from the token panel. Its read is on demand rather
    // than on launch: it is the one call this app makes to DeepSeek itself.
    var showBalance by rememberSaveable { mutableStateOf(false) }
    // A deliverable chip opens that path in the panel, so the panel needs to be
    // told what to select when it was not opened from its own list.
    var filesFocus by remember(ui.currentSessionId) { mutableStateOf<String?>(null) }
    // A closed turn's deliverables cannot change, so each row's answer is computed
    // once and kept: recomputing per frame would be a full scan of the transcript
    // on every streaming token.
    val deliverableCache = remember(ui.currentSessionId) { HashMap<String, TurnDeliverables>() }

    // The subagent lineage dropdown behind the header chip.
    var showLineage by rememberSaveable(ui.currentSessionId) { mutableStateOf(false) }
    // Descendant counts per session — the web's `indexSubagentDescendants`. Derived
    // from the roster rather than from any subagent row, so a session's subagent
    // activity is visible without the user having to reveal subagent rows at all.
    // The catalogs only floor the totals here, where the rollup is printed; the
    // drawer passes none, because its caret expands the roster.
    val subagentRollups = remember(ui.sessions, ui.subagentCatalogs) {
        indexSubagentRollups(ui.sessions, ui.subagentCatalogs)
    }

    // The way *up*, when this session is a subagent the roster can place: the header
    // makes its title a tap that opens the parent, and because the parent is itself a
    // session, opening it offers the same affordance again — so a nested run is a walk
    // rather than a one-way trip back to the drawer.
    val subagentParent = remember(ui.sessions, ui.currentSessionId) {
        subagentParentOf(ui.sessions, ui.currentSessionId)
    }

    // The whole subagent family's tokens and cost, for the usage panel. Folded from
    // the same roster the drawer and the lineage chip read, so the three cannot
    // disagree about who is in the family; recomputed when the roster moves, when the
    // open session's own projections land, or when the panel's price table changes.
    // Priced routes only: the open session's share is priced at the same route the
    // panel's own cost row uses, so the two rows cannot price one session two ways.
    //
    // Read out of `ui` *before* the `remember`, keys included: a value read inside the
    // lambda would be captured on the first composition and never re-read, which is how
    // a family keeps a child the roster has since dropped.
    val usageRates = tokenRatesFor(ui.selectedModel?.provider, ui.selectedModel?.model)
    val usageSessions = ui.sessions
    val usageSessionId = ui.currentSessionId
    val usageStats = ui.sessionStats
    val usageCatalog = usageSessionId?.let { ui.subagentCatalogs[it] }
    val family = remember(usageSessions, usageSessionId, usageStats, usageRates, usageCatalog) {
        usageSessionId?.let { id ->
            familyUsage(
                sessions = usageSessions,
                rootId = id,
                own = usageStats,
                ownRates = usageRates,
                catalog = usageCatalog,
            )
        }
    }

    // The composer's open/closed shape. The dock never raises the keyboard by itself;
    // only a tap on the text field does (see `ComposerDock`).
    //
    // **Open by default** (round 2): the message bar is the dock's resting pane and the
    // four destination icons are folded into the current page's circle behind it. Typing
    // is the thing a conversation screen is for; navigation is a detour, so it is the
    // detour that takes the extra tap. The keyboard does not follow: neither arriving on a
    // session nor opening the bar with its circle raises it — only a tap on the text field
    // does, so a shape gesture never throws a keyboard over the transcript.
    var composerExpanded by rememberSaveable(ui.currentSessionId) { mutableStateOf(true) }
    // The full-screen writing surface offered by the selection toolbar. A dialog rather
    // than a route: the draft, the session and the send path stay exactly where they are.
    var fullScreenComposer by rememberSaveable(ui.currentSessionId) { mutableStateOf(false) }

    // Back walks *up* the screen before it is allowed anywhere near the close gate.
    //
    // One handler with an explicit order rather than several `BackHandler`s: which of
    // those wins is a question about registration order, and the answer here has to be
    // about what the reader means. The stack is the one the screen shows — an open
    // panel is on top of the view, the view is on top of the chat, and a subagent sits
    // under the session that spawned it — so: close the Files panel, then return to
    // Chat, then go up to the parent.
    //
    // `enabled` only while one of those applies, which matters: a handler always
    // consumes the press, and consuming one it cannot use would hold the close dialog
    // hostage. The sheets (jobs, lineage, settings, about) are modal and already
    // dismiss themselves. Composed here rather than at the root because this is where
    // the state lives, and the root gate is asked only after the screen declines.
    //
    // The panels come before the view: an open panel is on top of whatever view it
    // covers, so it has to close first or the press would change the view underneath
    // something still on screen.
    BackHandler(
        enabled = showRouter || destination != Destination.CHAT || chatView != ChatView.CHAT ||
            subagentParent != null || composerExpanded,
    ) {
        when {
            // An open composer is the topmost thing on the screen that Back can close.
            composerExpanded -> composerExpanded = false
            showRouter -> showRouter = false
            // The pill first, then the destination: inside Chat the pill is the shallower
            // level, so leaving Shell returns to the transcript rather than all the way
            // out to a different place.
            chatView != ChatView.CHAT -> chatView = ChatView.CHAT
            destination != Destination.CHAT -> goToDestination(Destination.CHAT)
            else -> subagentParent?.let { actions.openSession(it.sessionId) }
        }
    }

    // `GetMultipleContents`, not `GetContent`: the picker offered one file at a time
    // while the share sheet beside it had always taken several, and the view model
    // stages one uri at a time regardless — the loop is the whole difference.
    val attachLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        uris.forEach { actions.addAttachment(it) }
    }

    /**
     * Picks from the command menu. `file`, `model` and `permission` are the rows
     * this client owns — an attachment picker, the model sheet, and the permission
     * sheet (which stands in for the web's preset popupSelect) — so they are
     * handled by name. Every other row follows the host descriptor: a command that
     * takes input is claimed into the draft so its arguments can be typed, a bare
     * one runs on the pick.
     */
    // The `+` menu's roster, also the `/` menu's catalogue, built from the host.
    val commandEntries = remember(hostCommands) { composerCommandEntries(hostCommands) }
    // The caret, not the end of the draft, and the text the caret lives in: this is the
    // same derivation the old `TextFieldValue` produced, now read off the field's own
    // state. `selection.end` is the caret for a collapsed selection and the moving end
    // for a range, which is what the trigger grammar has always been given.
    val triggers = rememberComposerTriggers(
        text = draftText,
        caret = draft.selection.end,
        commandEntries = commandEntries,
        searchReferences = actions.searchReferences,
        clearReferences = actions.clearReferences,
        sessionId = ui.currentSessionId,
        refreshCommands = actions.refreshCommands,
    )

    /**
     * The live `/` token, or null — read by the picker so a pick from the typed
     * menu can consume the token it was typed as.
     */
    val onPickCommand: (CommandEntry) -> Unit = { entry ->
        menuOpen = false
        // A pick from the typed menu consumes the token it was typed as, wherever
        // the caret sits; the `+` launcher types no token, so it either seeds the
        // bare token for an input-taking command or changes nothing at all.
        //
        // Through `state.edit`: a pick is an edit the client makes, not typing, so it
        // is not recorded on the undo stack (and, like every `edit`, it clears the
        // history it found — see the note in `ui/components/Composer.kt`).
        val token = triggers.slash
        when (entry.name) {
            "file" -> {
                draft.edit { consumeTrigger(token) }
                attachLauncher.launch("*/*")
            }
            "model" -> {
                draft.edit { consumeTrigger(token) }
                showModels = true
            }
            "permission" -> {
                draft.edit { consumeTrigger(token) }
                showPermission = true
            }
            else -> if (entry.takesInput) {
                draft.edit { replaceTrigger(token, "/${entry.name} ") }
                // The pick wrote into a box the reader may not be looking at: the dock
                // can be collapsed while the command menu is open. Opening it here is
                // what makes the seeded token visible rather than a draft the circle
                // only marks with a dot.
                composerExpanded = true
            } else {
                draft.edit { consumeTrigger(token) }
                actions.executeCommand("/${entry.name}")
            }
        }
    }
    // The access chip is a dropdown, so its resting label is abbreviated to keep the
    // composer on one row; the picker sheet still shows the host's full names and
    // descriptions, which is where the exact wording matters.
    val permissionLabel = shortAccessLabel(ui.currentPermission, ui.permissionOptions)
    // The model trigger names the route the host's `modelSelection.next` holds — the
    // one the next turn will actually use. A pick the host has not answered, or a
    // switch no turn has run yet, is marked as such rather than drawn as if it were
    // in force; a route the catalog no longer lists still names itself, and says it
    // cannot run. See `ModelTriggerState`.
    val modelTrigger = modelTriggerState(ui.modelChoice, ui.selectedModel, ui.selectedEffort)

    val current = ui.sessions.firstOrNull { it.id == ui.currentSessionId }
    // The whole-file reader behind every Download control on this screen. Rebuilt with
    // the session for the same reason the loader is: it carries the session id the host
    // resolves the path against, so a stale one would read the wrong workspace.
    val fileDownloader = remember(current?.id) { actions.workspaceFileDownloader() }
    // The pending seat's directory, for the hero's workspace chip. The target's own
    // path is preferred; a Workspace the registry has not delivered a path for yet
    // still resolves through the registry, and a Directory target *is* its path.
    val pendingTarget = ui.pendingSession?.target
    val pendingPath = when (pendingTarget) {
        is SessionTarget.Workspace ->
            pendingTarget.path ?: ui.workspaces.firstOrNull { it.id == pendingTarget.id }?.path
        is SessionTarget.Directory -> pendingTarget.path
        null -> null
    }
    val heroCwd = current?.cwd ?: pendingPath
    // The pending seat is a session that has deliberately not been created, so it
    // wears the blank session's chips (workspace, preset) — a blank session is the
    // only other thing the host calls provisional.
    val blankSeat = current?.blank == true || (current == null && ui.pendingSession != null)
    val isHero = entries.isEmpty() && live == null
    // An empty transcript is not the same fact as a blank session. The follow
    // stream's opening snapshot is the one event that proves the host has
    // answered for *this* session, and `TranscriptReducer.applySnapshot` is what
    // sets the header (`publishTranscript` copies it out); `openSession`'s
    // `reducer.reset()` is what clears it. So a null header while a session is
    // open means nothing has loaded yet, not that there is nothing to load.
    // The roster's own `blank` is the other half: only the host may call a
    // session provisional, and that hero is where a new session is meant to
    // start, so it keeps the hero from the first frame rather than flashing a
    // spinner first.
    val transcriptArrived = header != null
    val awaitingTranscript =
        isHero && ui.currentSessionId != null && !transcriptArrived && current?.blank != true
    // Bug A: silence while connected, a named state otherwise.
    val connectionLabel = ui.connectionLabel
    // The host refuses to recompose an agent once a turn has run, so the chip is
    // only meaningful while the session is blank — the web hero seat's own rule.
    // The label prefers the session's recorded preset, then the host default.
    val presetLabel = ui.agentPresetOptions.firstOrNull {
        it.id == (ui.pendingSession?.preset ?: ui.currentAgentPreset)
    }
        ?.let { agentPresetLabel(it.id, it.name) }
        ?: (ui.pendingSession?.preset ?: ui.currentAgentPreset).takeIf { it.isNotBlank() }
        ?: ui.agentPresetOptions.firstOrNull { it.isDefault }?.name
        ?: ui.agentPresetOptions.firstOrNull()?.name
    val showPresetChip = isHero && blankSeat && presetLabel != null
    // A one-shot subagent's inbox is not its own to mutate — the web's
    // `queueMutable = subagent === null || mode === 'continuable'`.
    val queueMutable = current?.isSubagent != true || current.subagentMode == "continuable"
    // The web's `selectReadOnlySubagent`: an addressed child claims the composer's
    // seat with a status frame instead of taking input — a one-shot record always,
    // a continuable child only once its parent is known to be offline. Availability
    // is unknown until the parent's catalog lands, and unknown keeps the composer, so
    // the seat never flickers into a frame it would have to take back.
    val composerState = subagentComposerState(
        target = current?.let { subagentTargetOf(it.id, it.parentSessionId, it.subagentMode) },
        parentAvailable = ui.subagentParentAvailable,
        running = ui.running,
    )
    val readOnlyReason = (composerState as? SubagentComposerState.ReadOnly)?.reason

    // The web marks a catalog "open" while its menu is up: that is what turns a
    // frame-driven refresh into an immediate pull, and what drops a debounce the
    // opening owed on close (`setSubagentCatalogOpen`, manager.ts:433-446). The
    // phone's equivalent catalog is this sheet, rooted at the session on screen.
    DisposableEffect(showLineage, current?.id) {
        val root = current?.id
        if (showLineage && root != null) actions.setSubagentCatalogOpen(root, true)
        onDispose { if (root != null) actions.setSubagentCatalogOpen(root, false) }
    }

    // The loading body is the Column's *last* child, so the box it centres its
    // contents in is everything below the chrome — an area that already ends at
    // the viewport's bottom edge. Its centre therefore sits `chrome / 2` below
    // the viewport's centre, and the content is translated back up by exactly
    // that half rather than restructured into a viewport-filling overlay. The
    // chrome is measured, not assumed: the header grows a tab strip outside the
    // hero, and the banner is present only while `ui.error` is set.
    var headerPx by remember { mutableIntStateOf(0) }
    var bannerPx by remember { mutableIntStateOf(0) }
    // The floating card stack's measured height — approval, questions, todos, goal, queue —
    // including the dock area it is padded clear of. The transcript reserves it as bottom
    // content padding, which is what lets its last row scroll up clear of what is drawn
    // *over* it. It has to be measured rather than assumed: every card above is conditional.
    //
    // **Streaming must not change this number.** A token arrival is a change *inside* the
    // list; the dock's height moves only when the reader types another line, opens a menu,
    // or a card appears. That is why the session readings moved into the rail (round 2):
    // as a separate footer row they appeared the moment the first usage projection landed,
    // which pushed the whole transcript up once in the middle of a stream. The rail is one
    // fixed-height row, so numbers that arrive every turn change its width, never its
    // height. TailFollow then corrects growth against the padded edge, pixel for pixel, so
    // the newest line stays above the input instead of climbing out from behind it.
    var composerStackPx by remember { mutableIntStateOf(0) }
    // The height of the floating dock area: the accessory rail, the menus and the dock
    // row. Measured, not assumed — a long draft grows the bar, and the cards above it and
    // the transcript's bottom padding both have to follow.
    var dockAreaPx by remember { mutableIntStateOf(0) }
    val dockAreaDp = with(LocalDensity.current) { dockAreaPx.toDp() }

    // One send path for the bar and the full-screen surface: the text is taken from the
    // draft exactly once, the follow is re-armed, and the hero's first turn keeps its
    // deferred delivery.
    val sendFromComposer: () -> Unit = {
        val text = takeDraftForSend()
        followFrom++
        if (isHero) actions.sendDeferred(text) else actions.send(text, actions.submitMode())
    }
    val sendFromComposerWithMode: (String) -> Unit = { mode ->
        val text = takeDraftForSend()
        followFrom++
        actions.send(text, mode)
    }
    val toggleBusyEnter: () -> Unit = {
        actions.updateBusyEnter(
            if (ui.busyEnter == BusyEnter.STEER) BusyEnter.QUEUE else BusyEnter.STEER,
        )
    }
    // Leaving the conversation, or a subagent claiming the seat, puts the bar away; coming
    // back re-opens it, because "open" is this dock's resting shape rather than a mode the
    // reader has to remember. A deliberate collapse is not undone: the effect only runs when
    // the page or the read-only seat changes, not when the reader taps the dock's circle.
    LaunchedEffect(destination, readOnlyReason) {
        composerExpanded = destination == Destination.CHAT && readOnlyReason == null
        if (destination != Destination.CHAT || readOnlyReason != null) fullScreenComposer = false
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Enabled for its *scrim*, not for its drag.
        //
        // Material gates the scrim's tap on this same flag — `NavigationDrawer.kt`
        // only calls the scrim's `onClose` when `gesturesEnabled` is true, while
        // its full-size Canvas attaches the tap detector either way. So at `false`
        // the scrim swallowed every tap outside the drawer and closed nothing: the
        // tap went nowhere at all, which is why "tap outside to dismiss" could
        // never work.
        //
        // Its drag still never wins. `drawerDrag` sits on the sheet and on the
        // content, both descendants of the draggable container, and consumes every
        // move it owns before the ancestor sees it — the same child-consumes-first
        // rule the nested scrollables rely on. That is what keeps the commit
        // threshold at DRAWER_COMMIT (40dp, or a 200dp/s fling) instead of
        // Material's half-width 150dp.
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .width(DRAWER_WIDTH)
                    .drawerDrag(drawerState, DRAWER_WIDTH),
                windowInsets = WindowInsets(0, 0, 0, 0),
                drawerContainerColor = DshTheme.colors.sidebarFill,
            ) {
                SessionsDrawer(
                    sessions = ui.sessions,
                    workspaces = ui.workspaces,
                    workspacesPhase = ui.workspacesPhase,
                    archivedSessionIds = ui.archivedSessionIds,
                    completedSessionIds = ui.completedSessionIds,
                    pendingInteractions = ui.pendingInteractions,
                    currentId = ui.currentSessionId,
                    drawerOpen = drawerState.isOpen,
                    loading = ui.sessionsLoading,
                    // Why the list may be older than the host: a failed pull keeps
                    // the rows and says so, rather than leaving yesterday's
                    // sessions looking current.
                    sessionsError = ui.sessionsError,
                    // The host refused the last Workspace move: the order has already
                    // snapped back, so this is the reason the row returned.
                    workspaceOrderError = ui.workspaceOrderError,
                    onMoveWorkspace = actions.moveWorkspace,
                    onSelect = {
                        actions.openSession(it)
                        scope.launch { drawerState.close() }
                    },
                    onNew = {
                        // Inherits the current (or most recent) Workspace rather
                        // than creating at a bare cwd, which is what used to drop
                        // a new session into Ungrouped. The target is only
                        // *recorded* now — the session is created on first use, so
                        // leaving the hero leaves nothing behind.
                        actions.startSession(current?.cwd ?: header?.cwd)
                        scope.launch { drawerState.close() }
                    },
                    onNewInWorkspace = { workspaceId ->
                        // Same contract: reuse a blank if the host already has one
                        // in this Workspace, otherwise record the target.
                        actions.startSessionInWorkspace(workspaceId)
                        scope.launch { drawerState.close() }
                    },
                    onRefresh = { actions.refreshSidebar() },
                    grouping = ui.drawerGrouping,
                    orderByUpdated = ui.drawerOrderByUpdated,
                    showArchived = ui.drawerShowArchived,
                    collapsedSections = ui.drawerCollapsedSections,
                    onGrouping = actions.setDrawerGrouping,
                    onOrderByUpdated = actions.setDrawerOrderByUpdated,
                    onShowArchived = actions.setDrawerShowArchived,
                    onCollapsedSections = actions.setDrawerCollapsedSections,
                    // The box is one control with two answers: the roster filtered
                    // locally by title/path, and the host's own content search. The
                    // ViewModel owns the debounce and drops superseded pages.
                    searchHits = ui.sessionSearchHits,
                    searchLoading = ui.sessionSearchLoading,
                    searchError = ui.sessionSearchError,
                    searchHasMore = ui.sessionSearchHasMore,
                    onSearch = actions.searchSessionContent,
                    onRename = actions.renameSession,
                    onFork = actions.forkSession,
                    onArchive = actions.archiveSession,
                    onUnarchive = actions.unarchiveSession,
                    // The drawer's per-session caret is the other catalog
                    // disclosure: opening one is this app's `setSubagentCatalogOpen`
                    // (`manager.ts:433-446`), which reads the child catalog the
                    // caret is about to reveal.
                    onSubagentCatalogOpen = actions.setSubagentCatalogOpen,
                    // The browser is a modal of its own, so the drawer steps out of
                    // the way first: two stacked scrims would eat the first back
                    // gesture and hide which layer it belongs to.
                    onAddWorkspace = {
                        actions.resetDirectoryBrowser()
                        showBrowser = true
                        scope.launch { drawerState.close() }
                    },
                    onSettings = {
                        showSettings = true
                        // The sheet reads on open rather than on a timer, but it is
                        // no longer the *only* reader — the transcript needs
                        // `ui-chat.transcriptView` to know whether it may fold — so
                        // the connect-time read below is what usually supplies this.
                        actions.loadHostSettings()
                        scope.launch { drawerState.close() }
                    },
                    onAbout = {
                        showAbout = true
                        // One modal at a time: the drawer's sheet and the About
                        // sheet are separate scrims, and leaving both up eats the
                        // first back gesture.
                        scope.launch { drawerState.close() }
                    },
                    // Same reason as About: the drawer's own scrim goes first, or the
                    // first back gesture is spent on a sheet nobody can see.
                    onAutomations = {
                        showAutomations = true
                        scope.launch { drawerState.close() }
                    },
                    activeScheduleTotal = ui.activeScheduleTotal,
                )
            }
        },
    ) {
        // ## Wide screens get a rail instead of a bar (2.5d)
        //
        // The bar is a *thumb* affordance: it sits where a hand holds a phone. On a
        // tablet the same hand is nowhere near the bottom edge, and a full-width bar
        // stretches three 56dp seats across 1 200dp of nothing. A rail puts the same
        // three destinations in a column beside the content, which is where a wide
        // window's navigation belongs.
        //
        // **The breakpoint is measured here rather than asked of
        // `material3-window-size-class`.** That library is already on the classpath
        // twice — miuix pulls the JetBrains Compose build (org.jetbrains.compose) and
        // the BOM provides the androidx one — and `docs/HANDOFF-NEXT.md` §7.3.2 names
        // the duplicate-class risk as the one place this phase might need a new
        // dependency. `BoxWithConstraints` answers the only question this screen
        // actually asks ("is there room beside the content?") without adding a second
        // answer to "which window size class is this".
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                // The edge claim reads the *pager's own state object*, not the `destination`
                // value computed above: a lambda that closed over that val would answer with
                // whatever page was showing when it was created, and the whole point is to
                // answer at the moment the finger lands.
                .drawerDrag(
                    drawerState = drawerState,
                    width = DRAWER_WIDTH,
                    edgeClaim = { destinationState.currentPage == Destination.CHAT.ordinal },
                    // The dock's measured height: a swipe that starts on the bar (or the
                    // rail above it) belongs to the dock, not to the drawer.
                    blockedBottomPx = { dockAreaPx.toFloat() },
                )
                .background(DshTheme.colors.bgBase)
                .statusBarsPadding()
                // **No `navigationBarsPadding()` on the screen.**
                //
                // It used to be here, which ended the transcript exactly where the system
                // navigation bar starts: the conversation could not scroll into that strip
                // at all, whatever the bar's colour, so "the bar is transparent" bought
                // nothing. The pager now runs to the bottom of the window and the
                // navigation bar's inset is paid by the dock instead (see the overlay
                // below), so rows pass under the system bar while scrolling and are visible
                // through it. See `docs/DESIGN.md`'s edge-to-edge note.
                .imePadding(),
        ) {
        val wide = maxWidth >= WIDE_LAYOUT
        Row(Modifier.fillMaxSize()) {
        // The rail leads the row, so it sits on the **leading** edge — the side a wide
        // window's navigation belongs on, and the side the drawer opens from. Placing it
        // after the content would put it on the trailing edge, which reads as a toolbar
        // and puts it on the opposite side from every other navigation surface here.
        // Same gate as the bar, and no longer the hero's: the rail holds the
        // destinations, not the conversation's views, and two of those are as real on an
        // empty session as on a full one (3.3).
        if (wide) {
            NavigationRail(
                items = DESTINATION_ITEMS,
                selectedKey = destination.name,
                onSelect = { key ->
                    Destination.entries.firstOrNull { it.name == key }?.let(goToDestination)
                },
            )
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            ChatHeader(
                title = current?.title ?: "DSH",
                cwd = current?.cwd ?: header?.cwd,
                onMenu = { scope.launch { drawerState.open() } },
                jobs = ui.jobs,
                jobsFinishedUnseen = ui.jobsFinishedUnseen,
                onJobs = {
                    actions.markJobsSeen()
                    showJobs = true
                },
                // The panel's seat, offered only while there is a local route to
                // describe — the header's trailing cluster is already a set of
                // conditional seats, so this is the same shape as Files and Jobs
                // rather than a new idea. See the gate above for why this is not a tab.
                routerAvailable = routerTarget != null,
                routerOpen = showRouter,
                onRouter = { showRouter = !showRouter },
                filesOpen = destination == Destination.FILES,
                // The Files panel resolves every path against a session
                // (`agentId`), so on the pending seat there is nothing to list. The
                // seat states that fact by not being offered, rather than opening a
                // panel that can only be empty.
                filesAvailable = ui.currentSessionId != null,
                onFiles = {
                    filesFocus = null
                    val closing = destination == Destination.FILES
                    // Leaving the page drops the tree's cache with it, so returning
                    // reads the directory again rather than drawing a snapshot from
                    // whenever it was last looked at.
                    if (closing) actions.resetWorkspaceExplorer()
                    goToDestination(if (closing) Destination.CHAT else Destination.FILES)
                },
                lineage = current?.let { subagentRollups[it.id] },
                onLineage = { showLineage = true },
                parent = subagentParent,
                onParent = { subagentParent?.let { actions.openSession(it.sessionId) } },
                // One status fact, ranked. The derivation reads the whole `UiState`
                // because the four facts it ranks are held in four different places —
                // the attention mirrors, the mux poll, the running flag and the
                // session id — and doing that ranking at the call site is what keeps
                // `ChatHeader` a pure function of its arguments.
                //
                // `isDrawn` is the filter, not an extra `if` here: "an idle session
                // shows nothing" is one statement in `:core:model`, and a second copy
                // of it at the call site is how the two would drift.
                capsule = StatusCapsules
                    .derive(
                        pending = pendingKindOf(ui.pendingInteractions[ui.currentSessionId]),
                        connectionLabel = ui.connectionLabel,
                        running = ui.running,
                        // A seat, not a session id. The new-session hero has no id yet
                        // and is still a real seat; a cold start with nothing to reopen
                        // has neither and is the one genuinely seatless case. Passing
                        // `currentSessionId != null` here told the hero "No session",
                        // which is how this was caught: `chat-hero` moved.
                        seatOpen = ui.currentSessionId != null || ui.pendingSession != null,
                    )
                    .takeIf { StatusCapsules.isDrawn(it.state) },
                // Only one of the four states has somewhere to go, and the capsule is
                // a button for that one and a plain chip for the rest rather than a
                // button that does nothing:
                //
                //  * OFFLINE opens About, whose "Restart connection" is the action
                //    this state actually has. It is the same destination the drawer's
                //    footer already offers, reached from the place the symptom shows.
                //  * WAITING has no tap: the card that answers it is *already* floating
                //    over the conversation (the attention stack, #43), and opening a
                //    second surface that repeats its words would be a worse button.
                //  * WORKING and UNKNOWN have no action at all.
                onCapsule = if (capsuleStateIsOffline(ui)) {
                    { showAbout = true }
                } else {
                    null
                },
                // The same handle the bar samples: one backdrop for the shell, not one
                // per surface. Null on the flat tier, which is why every capture keeps
                // drawing the fill this header had before.
                backdrop = glassBackdrop,
                // Folded everywhere except the conversation. Chat is the one page whose
                // header has two facts worth showing — what this is and where it runs —
                // and it is also the only one the reader scrolls; the other four are one
                // line, so switching pages is a step rather than a jump. The rule is
                // §6's "随页变，不随滚动", and the reason it is not scroll-driven is that
                // Trajectory and Shell have no scroll position to drive it with.
                compact = destination != Destination.CHAT || chatView != ChatView.CHAT,
                // Feeds the loading offset above: the header is the top half of
                // the chrome, and its own height is not a constant.
                modifier = Modifier.onSizeChanged { headerPx = it.height },
            )

            if (ui.error != null) {
                ErrorBanner(
                    message = ui.error!!,
                    onDismiss = actions.dismissError,
                    // An expired session is not something to dismiss: the banner
                    // must carry the way out, or the screen is a dead end.
                    actionLabel = if (ui.errorNeedsSignIn) "Sign in again" else null,
                    onAction = if (ui.errorNeedsSignIn) actions.signInAgain else null,
                    // The banner sits between the header and the body, so it is
                    // part of the chrome the loading content has to be lifted by.
                    modifier = Modifier.onSizeChanged { bannerPx = it.height },
                )
            }

            // The four destinations, as pages (3.3).
            //
            // The bar says "you can go four places"; a horizontal swipe is how a phone
            // says the same thing without a control, and it is the one gesture the
            // transcript cannot use for anything else — it scrolls vertically. The
            // header, the banner and the bar are not pages: they are chrome around the
            // destinations, and only the body between them moves.
            //
            // ## The drawer's edge swipe, and the race that was measured (3.7a)
            //
            // The pager is a horizontal scrollable and a child of `drawerDrag`, and this is
            // the shape that caused a reported defect: **a rightward swipe from the left edge
            // opened the drawer sometimes and slid the first page right the rest of the time.**
            // A child gets the Main pass before its ancestors, so at page 0 the pager claimed a
            // rightward drag while clamping — "nowhere to go" is not the same as "do not
            // consume" — and `drawerDrag` then correctly stood down.
            //
            // The fix is in `drawerDrag` rather than here: it now decides this one case in the
            // **Initial** pass, where an ancestor still wins, and only for a gesture that began
            // inside `DRAWER_EDGE` on a page with nothing to its left. See its KDoc for the
            // trade-off that buys. On the three other destinations nothing changed: a rightward
            // swipe still goes back a page, which is what the reader asked for by being there,
            // and the drawer stays reachable from the header's menu seat.
            // The pager and the bar share one box, and the bar is *over* the pager rather than
            // below it. Two reasons, both of them the difference between a floating surface and
            // a row of chrome:
            //
            //  - **The glass needs something behind it.** In a flow column the bar sat on the
            //    page's flat background, so "liquid glass" refracted nothing. Overlaying puts
            //    whatever the page paints at its bottom edge behind the material.
            //  - **A capsule must be centred by its container.** The vendored bar sizes itself
            //    to its content (`width(IntrinsicSize.Min)`) and its own root aligns to the
            //    start, so in a Column it sat hard against the left edge — which is exactly what
            //    it looked like on the device.
            Box(Modifier.weight(1f)) {
            HorizontalPager(
                state = destinationState,
                // Keyed per destination so each page keeps its own vertical scroll
                // position: going to Files and back must not lose the reader's place in
                // the transcript. A shared saver would file all four under one key and
                // they would overwrite each other.
                key = { page -> Destination.entries[page].name },
                // `glassBarLayer` records this subtree into the backdrop the bar samples.
                // On the pager rather than on the Column because the *bar itself* must not
                // be part of what the bar blurs: the pager is the content behind it, and
                // nothing else in this column is.
                // The pages run **to the bottom of the screen**, behind the bar (3.2c).
                //
                // They used to stop above it, which is why the transcript could slide under
                // the floating composer but never under the bar: the bar's strip was reserved
                // layout, so nothing was ever painted there for the material to refract.
                // Reported from a device exactly that way — "it slides into the composer but
                // not into the bar".
                //
                // Nothing is lost by removing the reservation. Each page already keeps its own
                // last row clear: the conversation through its composer stack's measured bottom
                // padding, the others through their own content padding. What the strip cost
                // was the one thing the bar exists for.
                modifier = Modifier
                    .fillMaxSize()
                    .glassBarLayer(glassBackdrop),
                // Deliberately no `beyondViewportPageCount`: a page off screen is not
                // composed. Shell holds a live terminal canvas and Trajectory a full
                // derivation of the transcript; paying for both while the reader looks
                // at neither would cost more than the swipe saves.
            ) { page ->
                when (Destination.entries[page]) {
                    // Files stopped being a panel over the conversation and became a
                    // place (3.3). The surface is the one 2.4's panel already drew, so
                    // this step is a *move* — same tree, one level up — and the work
                    // left for its own PR is the VSCode-shaped explorer.
                    Destination.FILES -> Column(Modifier.fillMaxSize().navigationBarsPadding()) {
                // Derived on demand: the transcript can hold a thousand rows and
                // neither scan is worth paying for while the panel is closed.
                val files = remember(entries) { SessionFiles.derive(entries) }
                val previews = remember(entries) { SessionFiles.previews(entries) }
                // Rebuilt with the session: the loader is scoped by the session id
                // the host resolves the path against, so a stale one would read the
                // wrong workspace root.
                val fileLoader = remember(current?.id) { actions.workspaceFileLoader() }
                FilesSurface(
                    files = files,
                    previews = previews,
                    root = current?.cwd ?: header?.cwd,
                    focus = filesFocus,
                    loader = fileLoader,
                    // The panel decides whether Browse is offered: the wiring is
                    // always here, and a session with no cwd gets no mode tabs
                    // because `workspaceFiles/list` would be confined to a root
                    // this app cannot name.
                    explorer = ExplorerWiring(
                        state = workspaceExplorer,
                        onList = actions.listWorkspaceDirectory,
                        onReload = actions.reloadWorkspaceExplorer,
                    ),
                    // The downloader is session-scoped like the loader, and the panel
                    // decides whether to draw its control at all.
                    downloader = fileDownloader,
                    onClose = {
                        // The tree is a per-open reading; leaving the cache up
                        // would show a stale directory the next time it opens.
                        actions.resetWorkspaceExplorer()
                        goToDestination(Destination.CHAT)
                    },
                    modifier = Modifier.weight(1f),
                )
                    }

                    // The deployment's loaded plugins. A page rather than a sheet
                    // because it is a list you scan, and read-only on purpose: the
                    // write half (`pluginManager`) is on the permanent blocklist for a
                    // remote client — see `docs/BLOCK-SCHEMA.md` and the page's own
                    // note.
                    Destination.PLUGINS -> Box(Modifier.fillMaxSize().navigationBarsPadding()) {
                        PluginsPage(
                            state = pluginInventory,
                            onRefresh = actions.loadPluginInventory,
                        )
                    }

                    // The conversation page is the one that runs behind the system
                    // navigation bar (its transcript is the content worth showing through
                    // it); the other destinations keep their own bottom edge clear.
                    Destination.ME -> Box(Modifier.fillMaxSize().navigationBarsPadding()) {
                        MeDestination(
                            username = ui.username,
                            balance = ui.balance,
                            settings = meSettings,
                            settingsActions = meSettingsActions,
                            onOpenAbout = { showAbout = true },
                            onOpenSettings = {
                                showSettings = true
                                // The sheet reads on open rather than on a timer, but it
                                // is no longer the *only* reader — the transcript needs
                                // `ui-chat.transcriptView` to know whether it may fold —
                                // so the connect-time read is what usually supplies this.
                                actions.loadHostSettings()
                            },
                        )
                    }

                    // The conversation: three readings of one session, chosen by the
                    // pill under the header rather than by the bar below (3.3).
                    Destination.CHAT -> Column(Modifier.fillMaxSize()) {
                        ViewPill(
                            items = CHAT_VIEW_ITEMS,
                            selectedKey = chatView.name,
                            onSelect = { key ->
                                ChatView.entries.firstOrNull { it.name == key }?.let(goToView)
                            },
                            modifier = Modifier.padding(
                                start = DshSpacing.xl,
                                end = DshSpacing.xl,
                                top = DshSpacing.sm,
                                bottom = DshSpacing.xs,
                            ),
                        )
                        when (chatView) {
                        ChatView.TERMINAL -> Column(Modifier.fillMaxSize()) {
                    // A real PTY for this session, from the host's `terminal` Remote
                    // namespace. The screen creates or adopts the session's terminal, so
                    // it is only reachable for an open session — the strip that selects
                    // it is not drawn on the hero.
                    //
                    // The pending new-session seat therefore has no Shell tab, and the
                    // seat split is what keeps that honest: `HOST_SHELL` materialises its
                    // *own* dedicated archived session at the workspace root and never
                    // consults the pending target, while `THIS_SESSION` is work inside
                    // the open session and would have to materialise the seat first
                    // rather than open a second, unnamed terminal session.
                    terminal(Modifier.weight(1f))
                        }

                        ChatView.TRAJECTORY -> Column(Modifier.fillMaxSize()) {
                    val trajectory = remember(entries, endedTurns) {
                        buildTrajectory(entries, endedTurns, actions.toolParents())
                    }
                    TrajectoryScreen(
                        model = trajectory,
                        title = current?.title ?: "DSH",
                        onClose = { goToView(ChatView.CHAT) },
                        modifier = Modifier.weight(1f),
                        // The conversation header above already carries the title, the
                        // back-to-drawer seat and the view tabs, exactly as the web's
                        // `.viewArea` sits below `.tabs` — the screen's own title row
                        // would be a second copy of it.
                        showHeader = false,
                    )
                        }

                        ChatView.CHAT -> Column(Modifier.fillMaxSize()) {
    if (awaitingTranscript) {
                    // A non-blank session with nothing loaded yet: the host has not
                    // answered this session's follow stream. Naming the connection
                    // when it is the reason costs no new copy — the drawer's own
                    // "Loading…" plus the shell's connection word.
                    //
                    // The composer and the pills are not siblings of this branch —
                    // they live in the transcript `else` below — so the chrome is
                    // the whole offset: `weight(1f)`'s box runs from the header
                    // (plus banner) to the Column's bottom, putting its centre at
                    // `H/2 + (header + banner)/2`. Half of that, negated, is the
                    // viewport's centre. Reading the states inside `offset` keeps the
                    // first frame still (both are 0 until measured) and re-places
                    // instead of recomposing when the banner arrives.
                    LoadingSessionBody(
                        connection = connectionLabel,
                        // A Box wraps its content, so without `fillMaxWidth` the
                        // Column's Start alignment parks the dot+label on the left
                        // edge; filling the width is what makes the centring two-
                        // dimensional.
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .offset { IntOffset(0, -(headerPx + bannerPx) / 2) },
                    )
                } else if (isHero) {
                    // The composer is no longer inside the hero: it lives in the bottom
                    // dock, which is composed outside this page and therefore survives the
                    // hero → transcript switch without being re-seated. What the hero
                    // reserves is the dock area, measured, so its centred block stays in
                    // the middle of the room that is actually left above the dock.
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(bottom = dockAreaDp),
                    ) {
                        HeroBody(
                            modifier = Modifier.fillMaxSize(),
                            workspaceLabel = heroCwd?.let(::shortenLastSegment) ?: "Choose workspace",
                            onWorkspaceClick = { showWorkspace = true },
                            presetLabel = presetLabel,
                            showPresetChip = showPresetChip,
                            onPresetClick = {
                                actions.refreshAgentPresets()
                                showPresets = true
                            },
                        )
                        connectionLabel?.let {
                            ConnectionPill(
                                label = it,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = DshSpacing.md),
                            )
                        }
                    }
                } else {
                    // Ascending seq order from the reducer, oldest first, so the newest
                    // turn is the last item and therefore the visual bottom.
                    // `frozenLive` while the reader is in history, so the streaming row
                    // cannot grow and reflow the list under them.
                    val shownLive = frozenLive ?: live
                    val rows = remember(entries, foldedTurns, expandedTurns, shownLive, foldTurns) {
                        buildDisplayRows(
                            entries = entries,
                            endedTurns = foldedTurns,
                            expandedTurns = expandedTurns,
                            live = shownLive,
                            fold = foldTurns,
                        )
                    }

                    // The tool-call forest, keyed by call id so a row can find its own
                    // node. Keyed on `entries` because that is what changes when a
                    // dispatch event lands: the same event either adds the sub-call's
                    // row or fills its result in.
                    val toolNodes = remember(entries) {
                        buildToolCallTree(
                            entries.filterIsInstance<ChatEntry.ToolCall>(),
                            actions.toolParents(),
                        ).associateBy { it.entry.callId }
                    }

                    // Jump targets: the row index of every real (non-plugin) user
                    // message, oldest first so the rail reads top-to-bottom in time —
                    // which in a top-anchored list is also the order of the rows
                    // themselves, so no reversal is needed to match the transcript.
                    // Each carries its prompt and the assistant prose that answered it,
                    // which is what the rail's preview shows while you scrub. The rail
                    // shows the most recent `MAX_RAIL_TICKS` of them.
                    val jumpTargets = remember(rows) {
                        rows.withIndex()
                            .mapNotNull { (index, row) ->
                                val entry = (row as? DisplayRow.Single)?.entry
                                if (entry is ChatEntry.UserMessage && !entry.fromPlugin) {
                                    TurnJump(
                                        index = index,
                                        prompt = { entry.text },
                                        response = { responseAfter(rows, index) },
                                    )
                                } else {
                                    null
                                }
                            }
                    }
                    // The turns on screen, which is what the fold rule above asks about.
                    // One frame behind by construction, which is the safe direction: a fold
                    // can only ever be applied to a turn that was off screen when the turn
                    // ended.
                    val rowsNow by rememberUpdatedState(rows)
                    LaunchedEffect(listState) {
                        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index } }
                            .collect { indices ->
                                val latest = indices.mapNotNull { rowsNow.getOrNull(it)?.turnOrNull() }.toSet()
                                if (latest != visibleTurns) visibleTurns = latest
                            }
                    }

                    val firstVisible by remember { derivedStateOf { listState.firstVisibleItemIndex } }
                    // The turn you are reading is the newest target at or above the
                    // first visible row.
                    val activeTarget = remember(jumpTargets, firstVisible) {
                        jumpTargets.filter { it.index <= firstVisible }.maxByOrNull { it.index }?.index
                    }

                    // Bug A: the transcript is the flex child, so a row inserted
                    // above it takes its height out of the transcript's flexible
                    // space and leaves every seat below — the dock, the composer —
                    // exactly where it was. The newest turn stays pinned to the
                    // bottom and no row moves.
                    connectionLabel?.let {
                        ConnectionPill(
                            label = it,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = DshSpacing.sm),
                        )
                    }

                    // A top-anchored list: new rows appear below the fold rather than
                    // moving what is above them, and [TailFollow] decides — only for a
                    // reader already at the tail — when to bring them into view.
                    Box(Modifier.weight(1f)) {
                        // Latent: records the transcript's position and gestures. It
                        // consumes nothing and scrolls nothing, so what it captures is
                        // the behaviour that existed before any of this was added.
                        DiagLazyList("chat", listState)
                        DiagFrames()
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .touchGate(touch)
                                .diagDrag("chat") { listState.diagOffset() },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                start = DshSpacing.xl,
                                end = DshSpacing.xl,
                                top = DshSpacing.xl,
                                // The floating stack's own height, measured, plus the gap the
                                // last row keeps from the bottom edge. Without it the newest
                                // message would sit permanently under the composer: the list
                                // still ends at the stack, and the padding is what buys the
                                // room to scroll clear of it.
                                bottom = DshSpacing.md + with(LocalDensity.current) { composerStackPx.toDp() },
                            ),
                            verticalArrangement = Arrangement.spacedBy(DshSpacing.xl),
                        ) {
                        // Gated on this session's own turn, not the host's `running`: a
                        // parent whose turn the journal has closed reports a live
                        // subagent through the header's `+N running` chip, not by
                        // wearing this row.
                        items(items = rows, key = { it.key }) { row ->
                                Box(
                                    Modifier
                                        .widthIn(max = 920.dp)
                                        .diagRowGrowth(row.key),
                                ) {
                                  // Text in a Compose row is not selectable until it is
                                  // inside a SelectionContainer, and on the web every one
                                  // of these rows is. It is per row rather than around the
                                  // list: a selection that spans two lazily-composed
                                  // items loses its ends when either is recycled, and
                                  // "copy this answer" is the case that matters.
                                  SelectionContainer {
                                  Column {
                                    when (row) {
                                        is DisplayRow.Single -> when (val entry = row.entry) {
                                            is ChatEntry.UserMessage -> UserMessageRow(entry, actions.imageBitmap)
                                            is ChatEntry.AssistantMessage -> {
                                                // Offered only where the reducer could
                                                // name the turn's `turn/end`, which is
                                                // the boundary `session/fork` cuts at; a
                                                // row without one gets no button rather
                                                // than a dead one.
                                                val branch: (() -> Unit)? =
                                                    branchSeqs[entry.seq]
                                                        ?.let { atSeq -> { branchAt = atSeq } }
                                                AssistantMessageRow(
                                                    entry = entry,
                                                    showFooter = entry.seq in closingSeqs,
                                                    durationMs = turnDurations[entry.turn],
                                                    onBranch = branch,
                                                )
                                            }
                                            is ChatEntry.ToolCall -> {
                                                // A PTC sub-call renders under the call
                                                // that dispatched it; every other call
                                                // has no node children and falls through
                                                // to the same flat row as before.
                                                val node = toolNodes[entry.callId]
                                                if (node != null) {
                                                    ToolCallTreeRow(node, actions.imageBitmap)
                                                } else {
                                                    ToolCallRow(
                                                        entry,
                                                        actions.imageBitmap,
                                                        jobOutput = ui.jobOutput,
                                                        onObserveJob = actions.observeJob,
                                                    )
                                                }
                                            }
                                            is ChatEntry.Todos -> TodoCard(entry.items)
                                            is ChatEntry.Notice -> NoticeRow(
                                                entry,
                                                jobOutput = ui.jobOutput,
                                                onObserveJob = actions.observeJob,
                                            )
                                        }

                                        is DisplayRow.TurnProcess -> TurnProcessRow(row) {
                                            expandedTurns = if (row.turn in expandedTurns) {
                                                expandedTurns - row.turn
                                            } else {
                                                expandedTurns + row.turn
                                            }
                                        }

                                        is DisplayRow.Live -> LiveAttemptRow(
                                            row.attempt,
                                        )
                                    }

                                    // `turn-deliverables.ts`: the row belongs to the turn's
                                    // *closing* message, so it lands after the prose that
                                    // ended the turn — and, for a folded turn, after the
                                    // process summary that now stands in for that prose.
                                    // Through-seq is the closing message's own seq, which is
                                    // the web's `producedForClosing(data, owner.seq)`.
                                    // The closing entry is bound to a local first. This used
                                    // to test `row.entry is AssistantMessage` inline and
                                    // rely on the smart cast, which stopped working when
                                    // `ChatEntry` moved to :core:model: Kotlin will not
                                    // smart-cast a public API property across a module
                                    // boundary, because it cannot prove another module's
                                    // `val` never changes. One binding is the same test.
                                    val closing =
                                        (row as? DisplayRow.Single)?.entry as? ChatEntry.AssistantMessage
                                    val delivered = when {
                                        row is DisplayRow.TurnProcess ->
                                            deliverableCache.getOrPut(row.key) {
                                                SessionFiles.deliverablesFor(entries, row.turn)
                                            }

                                        closing != null && closing.seq in closingSeqs ->
                                            deliverableCache.getOrPut(row.key) {
                                                SessionFiles.deliverablesFor(entries, closing.turn, closing.seq)
                                            }

                                        else -> null
                                    }
                                    if (delivered != null && !delivered.isEmpty) {
                                        DeliverableRow(
                                            delivered = delivered,
                                            onOpen = { path ->
                                                filesFocus = path
                                                goToDestination(Destination.FILES)
                                            },
                                            downloader = fileDownloader,
                                        )
                                    }
                                  }
                                  }
                                }
                            }

                            // The running-turn status line is the newest thing in the
                            // transcript, so in a top-anchored list it is emitted last.
                            if (ui.ownTurnInFlight) {
                                item(key = "turn-status") { TurnStatusRow(elapsedMs) }
                            }
                        }

                        // Turn navigator rail: one tick per human message that opened a
                        // turn, so you can jump straight to any prompt. The web hides it
                        // under a 900px container; on a phone it is the fastest way back
                        // through a long session, so it stays.
                        if (jumpTargets.size > 1) {
                            TurnRail(
                                targets = jumpTargets,
                                activeIndex = activeTarget,
                                onJump = { index ->
                                    ScrollDiag.prog("chat", "rail")
                                    // Instant, not animated: a jump is a destination, and
                                    // an animation can be interrupted or cancelled by the
                                    // follow, which is exactly what "the ticks do nothing"
                                    // looked like. `scrollToItem` takes the mutex and lands.
                                    scope.launch { listState.scrollToItem(index) }
                                },
                                // Flush to the edge and only as wide as the ticks: the
                                // rail owns the whole column for pointer input, so any
                                // inset ate into the rows' trailing controls (the To-dos
                                // chevron sits in that strip and was unreachable).
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 1.dp),
                            )
                        }

                    }
                }
                        }
                    }
                }
            }
        }

            // Everything docked to the composer floats over the transcript instead of
            // sitting below it. Stacked below, the list's box ends at the stack's top
            // edge, so nothing the reader scrolls ever passes behind the composer —
            // which is the entire reason for giving it the glass material at all.
            //
            // ## Why this is *outside* the pager (the To-dos crash)
            //
            // These cards wear the liquid material, and the liquid material samples the
            // layer the pager records. Composed inside the pager they were sampling a
            // layer they were themselves painted into, and the To-dos card is the first
            // surface in this stack whose geometry changes while it is being recorded:
            // it is composed the moment the agent first writes a to-do list, so its
            // appearance *is* the frame that goes wrong — which is exactly what was
            // reported, the app closing as soon as the To-dos appeared.
            //
            // The back-to-bottom button had the same defect and was moved out for the
            // same reason (42bbd10). This is the rest of the stack.
            //
            // Nothing moves on screen: this box is the pager's sibling under the same
            // `Box(Modifier.weight(1f))`, fills the same width, and is bottom-aligned the
            // same way. The one thing that had to be reproduced by hand is the pager's
            // bottom inset — a page stops above the system navigation bar (each page
            // applies `navigationBarsPadding` itself), so a bottom-anchored overlay must
            // pay that same inset or the stack would sit behind the bar on a page that
            // does not.
            //
            // The cards are only meaningful over the conversation, which is what the gate
            // says: on the hero there is no transcript to float over, and no to-dos, goal
            // or queue either.
            if (destination == Destination.CHAT && chatView == ChatView.CHAT && !awaitingTranscript) {
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        // The measurement sits *outside* the padding, so the number is
                        // the whole footprint the transcript must clear — cards plus
                        // the dock area they are lifted above. With it inside the
                        // padding (as it was) the list reserved the cards only and the
                        // last row sat behind the dock, which is the reported defect.
                        .onSizeChanged { composerStackPx = it.height }
                        .padding(bottom = dockAreaDp),
                ) {
                    if (ui.approval != null) {
                        ApprovalCard(
                            toolName = ui.approval!!.toolName,
                            reason = ui.approval!!.reason,
                            answer = ui.approval!!.answer,
                            onAllow = { actions.resolveApproval(true) },
                            onReject = { actions.resolveApproval(false) },
                            modifier = Modifier.padding(horizontal = DshSpacing.xl, vertical = DshSpacing.sm),
                        )
                    }

                    // A host question blocks the agent's tool call, so it takes the
                    // same seat as an approval — above the composer dock.
                    ui.questions?.let { questions ->
                        QuestionCard(
                            set = questions,
                            onSubmit = actions.answerQuestions,
                            onSkip = actions.skipQuestions,
                            modifier = Modifier.padding(horizontal = DshSpacing.xl, vertical = DshSpacing.sm),
                        )
                    }

                    // Dock order mirrors the web UI: todo (0) → goal (10) → queue (20).
                    if (todos.isNotEmpty()) {
                        TodoCard(
                            items = todos,
                            backdrop = glassBackdrop,
                            modifier = Modifier.padding(horizontal = DshSpacing.xl, vertical = DshSpacing.sm),
                        )
                    }

                    ui.goal?.takeIf { it.visible }?.let { goal ->
                        GoalBar(
                            goal = goal,
                            onPause = actions.pauseGoal,
                            onResume = actions.resumeGoal,
                            onEdit = { actions.editGoal(it) },
                            onClear = actions.clearGoal,
                            modifier = Modifier.padding(horizontal = DshSpacing.xl, vertical = DshSpacing.sm),
                        )
                    }

                    // Queue (dock order 20). It used to tuck under the composer card so the
                    // two read as one surface; the card now lives in the dock row below, and
                    // the queue rests above the accessory rail like the other cards. It stays
                    // composed when empty so it can animate itself away.
                    QueueDock(
                        items = ui.queue,
                        running = ui.running,
                        mutable = queueMutable,
                        busy = ui.queueBusy,
                        onEdit = actions.editQueueItem,
                        onRemove = actions.removeQueueItem,
                        onSteer = actions.steerQueueItem,
                        backdrop = glassBackdrop,
                        modifier = Modifier
                            .padding(horizontal = DshSpacing.xl + DshSpacing.md)
                            .fillMaxWidth(),
                    )
                }
            }

            // The bottom dock: the destination bar and the composer share one row, in the
            // shape BiliPai's liquid-glass search dock uses. **The open bar is the resting
            // shape** (round 2): `[current-page circle] [gap] [message bar]`. Tapping the
            // circle trades the bar for its own circle and puts the four destinations
            // back — so the row never changes width, and the two panes cannot drift apart.
            //
            // The controls that used to live inside the input are the rail above it, one
            // glass pill each, plus the session's two readings. The rail is always visible:
            // a reader who only wants to check the model, the cache share or the speed
            // should not have to open a keyboard first, and keeping it one fixed-height
            // row is also what keeps token arrivals from moving the transcript.
            //
            // On a wide window the rail owns navigation, so the dock is the input alone and
            // always open. A 56dp circle in the middle of a tablet would be a phone
            // affordance with no thumb to meet it.
            //
            // **Exactly one of the two navigation surfaces is composed.** The dock on a
            // phone, the rail on a wide window; both read the same `destination` and call
            // the same `goToDestination`.
            if (destination == Destination.CHAT || !wide) {
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        // The dock is the one surface that still has to sit above the
                        // system navigation bar: it is a set of controls, not content.
                        // The measurement sits **outside** both bottom insets, so
                        // `dockAreaPx` is the whole distance from the window's bottom edge
                        // to the top of the dock; everything that must clear the dock adds
                        // it once, with no second gap to drift.
                        .onSizeChanged { dockAreaPx = it.height }
                        // The navigation inset **minus what the IME already covers**:
                        // the screen applies `imePadding` at its root, so adding the
                        // navigation bar on top of an open keyboard would lift the dock by
                        // both at once. With the keyboard shut, the IME inset is zero and
                        // this is the ordinary bar inset.
                        .windowInsetsPadding(
                            WindowInsets.navigationBars.exclude(WindowInsets.ime),
                        )
                        .padding(bottom = BAR_BOTTOM_GAP),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (destination == Destination.CHAT) {
                        if (readOnlyReason == null) {
                            // Everything the composer used to keep inside its card, plus
                            // the session's two readings, in one row of glass pills. The
                            // readings joined it in round 2: they are glanceable values,
                            // and a separate footer row was one more height that could
                            // change under a streaming transcript.
                            ComposerAccessoryRail(
                                backdrop = glassBackdrop,
                                permissionLabel = permissionLabel,
                                onPermissionClick = { showPermission = true },
                                planActive = ui.planActive,
                                onExitPlan = actions.exitPlanMode,
                                modelTrigger = modelTrigger,
                                onModelClick = { showModels = true },
                                contextPercent = ui.contextPercent,
                                contextTokens = ui.contextTokens,
                                contextWindow = ui.contextWindow,
                                contextBreakdown = ui.contextBreakdown,
                                stats = ui.sessionStats,
                                // Priced routes only. The rates follow the session's
                                // *current* model, which is what the host bills the next
                                // turn at.
                                rates = usageRates,
                                family = family,
                                onBalance = {
                                    showBalance = true
                                    // Ask when the door opens, not when the app starts: a
                                    // figure read at launch is stale by the time anyone
                                    // looks at it.
                                    actions.refreshBalance()
                                },
                            )
                            Spacer(Modifier.height(DshSpacing.sm))

                            // The `@` and `/` menus hang directly above the input, where
                            // the caret is; `@` and `/` are mutually exclusive by their
                            // first character, so only one can be open at a time.
                            ComposerPopups(
                                triggers = triggers,
                                references = references,
                                state = draft,
                                menuOpen = menuOpen,
                                onPickCommand = onPickCommand,
                                clearReferences = actions.clearReferences,
                                modifier = Modifier.padding(
                                    start = DshSpacing.xl,
                                    end = DshSpacing.xl,
                                    bottom = DshSpacing.sm,
                                ),
                            )
                        } else {
                            // The frame claims the composer's seat without taking input;
                            // the dock keeps the navigation it has always drawn.
                            SubagentReadOnlyComposer(
                                reason = readOnlyReason,
                                modifier = Modifier.padding(
                                    start = DshSpacing.xl,
                                    end = DshSpacing.xl,
                                    top = DshSpacing.sm,
                                    bottom = DshSpacing.sm,
                                ),
                            )
                        }
                    }

                    ComposerDock(
                        state = draft,
                        expanded = destination == Destination.CHAT && (composerExpanded || wide),
                        onExpand = { composerExpanded = true },
                        onCollapse = { composerExpanded = false },
                        onSend = sendFromComposer,
                        onStop = actions.stop,
                        running = ui.running,
                        attachments = ui.attachments,
                        onRemoveAttachment = actions.removeAttachment,
                        busyEnter = ui.busyEnter,
                        onSendMode = sendFromComposerWithMode,
                        onToggleBusyEnter = toggleBusyEnter,
                        onToggleCommands = { menuOpen = !menuOpen },
                        onFullscreen = { fullScreenComposer = true },
                        items = DESTINATION_ITEMS,
                        selectedKey = destination.name,
                        onSelect = { key ->
                            Destination.entries.firstOrNull { it.name == key }?.let(goToDestination)
                        },
                        backdrop = glassBackdrop,
                        showNavigation = !wide,
                        showInput = destination == Destination.CHAT && readOnlyReason == null,
                    )
                }
            }

            // Back-to-bottom, composed *outside* the pager on purpose: its glass samples
            // the layer the transcript records, and a pane inside that layer would blur
            // its own previous frame instead of the conversation. It sits above the dock
            // area, so the bar can no longer hide it.
            if (destination == Destination.CHAT && chatView == ChatView.CHAT && !following.value) {
                ScrollToBottomButton(
                    onClick = {
                        ScrollDiag.prog("chat", "to-bottom-button")
                        // Instant, and to the last index at the moment it runs: an
                        // animation to an index captured before it started lands short
                        // whenever a row arrives during it, which during a turn is always.
                        scope.launch {
                            val last = listState.layoutInfo.totalItemsCount - 1
                            if (last >= 0) listState.scrollToItem(last)
                        }
                    },
                    backdrop = glassBackdrop,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = DshSpacing.xl,
                            bottom = dockAreaDp + DshSpacing.md,
                        ),
                )
            }
            }
        }
        }
        }
    }

    // Full-screen writing, offered from the selection toolbar. A dialog window: it
    // covers the chrome, keeps the draft's own state, and Back closes it before anything
    // underneath hears the press.
    if (fullScreenComposer) {
        Dialog(
            onDismissRequest = { fullScreenComposer = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
            ),
        ) {
            FullScreenComposer(
                state = draft,
                running = ui.running,
                attachments = ui.attachments,
                onRemoveAttachment = actions.removeAttachment,
                onToggleCommands = { menuOpen = !menuOpen },
                onSend = {
                    sendFromComposer()
                    fullScreenComposer = false
                },
                onStop = actions.stop,
                onClose = { fullScreenComposer = false },
                busyEnter = ui.busyEnter,
                onSendMode = { mode ->
                    sendFromComposerWithMode(mode)
                    fullScreenComposer = false
                },
                onToggleBusyEnter = toggleBusyEnter,
            )
        }
    }

    branchAt?.let { atSeq ->
        BranchFromMessageDialog(
            onDismiss = { branchAt = null },
            onConfirm = {
                branchAt = null
                actions.branchFromConclusion(atSeq)
            },
        )
    }

    if (showSettings) {
        // The same state holder and acts the Me page's tabs are handed, so the sheet and
        // the pages are one rendering of one document rather than two (3.5b).
        SettingsSheet(
            state = meSettings,
            actions = meSettingsActions,
            onDismiss = { showSettings = false },
        )
    }

    // The router panel, opened from its header seat. Composed here rather than in the
    // body's view chain because it is a *panel* over the current view, not a view: the
    // conversation stays behind it, which is what lets the reader compare the router's
    // answer with the model they selected without losing their place.
    //
    // `routerTarget != null` is the gate twice over — the seat that opens it is only drawn
    // while the gate is open, and the effect above closes the panel if the gate shuts while
    // it is. Checking again here keeps the non-null contract of `RouterTab` true by
    // construction rather than by argument.
    if (showRouter) {
        routerTarget?.let { target ->
            RouterSheet(
                target = target,
                probe = ui.routerProbe,
                // The model trigger's own label, so the panel cannot name the effort
                // differently from the chip beside the composer.
                effort = effortLabel(ui.selectedModel, ui.selectedEffort),
                onCheck = actions.checkRouter,
                onDismiss = { showRouter = false },
            )
        }
    }

    // Host-wide, so it is a sheet over whatever is open rather than a session view.
    // The read happens on open (inside the sheet), never here.
    if (showAutomations) {
        AutomationSheet(
            page = ui.schedulePage,
            historyTaskId = ui.scheduleHistoryTaskId,
            history = ui.scheduleHistory,
            deletingId = ui.deletingScheduleId,
            actionError = ui.scheduleActionError,
            notice = ui.scheduleNotice,
            // The catalogue carries only the session id; a title is this client's own
            // roster fact, and a session it does not hold is shown by id.
            sessionTitle = { id -> ui.sessions.firstOrNull { it.id == id }?.title },
            onOpen = actions.openAutomationTasks,
            onRetry = actions.retryAutomationTasks,
            onHistory = actions.loadScheduleHistory,
            onLoadMoreHistory = actions.loadMoreScheduleHistory,
            onDelete = actions.deleteSchedule,
            onDismissAction = actions.dismissScheduleAction,
            onOpenSession = { id ->
                showAutomations = false
                actions.openSession(id)
            },
            onDismiss = { showAutomations = false },
        )
    }

    if (showAbout) {
        AboutSheet(
            baseUrl = ui.baseUrl,
            username = ui.username,
            socketConnected = ui.socketConnected,
            onSignOut = {
                showAbout = false
                actions.signOut()
            },
            onDismiss = { showAbout = false },
            // The same session facts the Settings sheet used to print, from the
            // same two sources: the roster row and the session header.
            sessionTitle = current?.title.orEmpty(),
            sessionCwd = current?.cwd ?: header?.cwd,
            sessionPermission = ui.currentPermission,
            sessionAgentPreset = ui.currentAgentPreset,
            permissionOptions = ui.permissionOptions,
            selectedModel = ui.selectedModel,
            selectedEffort = ui.selectedEffort,
            // Which door traffic is really using — not always `baseUrl`.
            activeUrl = ui.activeUrl,
            activeRouteLan = ui.activeRouteLan,
            // Why the LAN door is not the one in use, when it is not: the fault the
            // app recorded against it. Nothing else on this page can tell "the LAN
            // is not here" from "the LAN answered and the app is avoiding it".
            lanDoorFault = ui.lanDoorFault,
            // And the other reason a public route needs a sentence: no LAN address
            // is configured, so no LAN door was ever in the running.
            lanRouteConfigured = ui.lanRouteConfigured,
            // A wrong answer is the one verdict no automatic path may lift, so the
            // reader is offered the one action that can: their own "try again".
            onRetryLanRoute = actions.connect,
            // The host's own DSH version, from its `$events` opening frame. Never
            // this app's version; see `model/HostVersion.kt`.
            hostVersion = ui.hostVersion,
            // The transport-level repair, beside the route facts that explain why a
            // reader might need it. It rebuilds the socket in this process, so the
            // connection service and every waiting approval survive; that is the
            // action the Close control was mistaken for.
            onRestartConnection = actions.restartConnection,
            connectionRestart = ui.connectionRestart,
            // The one action that ends the process. It is here because a restart
            // cannot reach state below the app, and that is exactly what the reader
            // has tried before reaching for this.
            onForceQuit = actions.forceQuit,
            // The other door, and what this app accepts as the host's identity. Here
            // rather than in Settings: a signed-in session has no way back to setup,
            // and a certificate that can only be pinned before signing in is one
            // nobody pins.
            lanUrl = ui.lanUrl,
            onLanUrl = actions.updateLanUrl,
            acceptedCerts = ui.acceptedCerts,
            certReadout = ui.certReadout,
            certChecking = ui.certChecking,
            certError = ui.certError,
            onCheckCertificate = actions.checkCertificate,
            onTrustCertificate = actions.trustCertificate,
            onForgetCertificate = actions.forgetCertificate,
        )
    }

    if (showLineage && current != null) {
        DshSheet(
            onDismissRequest = { showLineage = false },
        ) {
            LineageSheet(
                rootId = current.id,
                sessions = ui.sessions,
                rollups = subagentRollups,
                onOpen = { id ->
                    showLineage = false
                    actions.openSession(id)
                },
            )
        }
    }

    if (showBalance) {
        BalanceSheet(
            state = ui.balance,
            onKey = actions.updateDeepSeekKey,
            onRefresh = actions.refreshBalance,
            onDismiss = { showBalance = false },
        )
    }

    if (showJobs) {
        DshSheet(
            // Closing the sheet stops the observation: the web closes its job
            // popover's stream on unmount for the same reason, and a stream left open
            // here would keep pulling output nobody is looking at.
            onDismissRequest = {
                showJobs = false
                actions.observeJob(null)
            },
        ) {
            JobsSheet(
                jobs = ui.jobs,
                output = ui.jobOutput,
                onObserve = actions.observeJob,
                onKill = actions.killJob,
                onDismiss = {
                    showJobs = false
                    actions.observeJob(null)
                },
                modifier = Modifier.padding(horizontal = DshSpacing.lg, vertical = DshSpacing.md),
            )
        }
    }

    if (showWorkspace) {
        WorkspaceSheet(
            workspaces = ui.workspaces,
            // The pending seat's directory counts as "where this session will run":
            // the picker's check mark is what tells the reader which target the
            // hero already records.
            currentCwd = heroCwd,
            onPickWorkspace = { workspaceId ->
                showWorkspace = false
                actions.startSessionInWorkspace(workspaceId)
            },
            onPickPath = { cwd ->
                showWorkspace = false
                actions.startSessionIn(cwd)
            },
            onDismiss = { showWorkspace = false },
        )
    }

    // Registering a directory is a different act from picking one for a new
    // session: Open here adds the Workspace, then adopts it the same way the
    // workspace chip does, so the session you land in is the one you just added.
    if (showBrowser) {
        WorkspaceBrowser(
            level = directoryLevel,
            loading = directoryLoading,
            busy = directoryBusy,
            error = directoryError,
            onList = actions.listDirectory,
            onCreateDirectory = actions.createDirectory,
            onOpen = { path ->
                actions.addWorkspace(path) { workspaceId ->
                    showBrowser = false
                    // The id, not the path: only it attaches the new session to the
                    // workspace, so the session lands in that group.
                    if (workspaceId.isNotEmpty()) {
                        actions.startSessionInWorkspace(workspaceId)
                    } else {
                        actions.startSessionIn(path)
                    }
                }
            },
            onDismiss = {
                showBrowser = false
                actions.resetDirectoryBrowser()
            },
        )
    }

    if (showPermission) {
        PermissionSheet(
            options = ui.permissionOptions,
            current = ui.currentPermission,
            onSelect = {
                actions.setPermission(it)
                showPermission = false
            },
            onDismiss = { showPermission = false },
        )
    }

    if (showPresets) {
        AgentPresetSheet(
            options = ui.agentPresetOptions,
            current = ui.currentAgentPreset,
            onSelect = {
                actions.selectAgentPreset(it)
                showPresets = false
            },
            onDismiss = { showPresets = false },
        )
    }

    if (showModels) {
        ModelSheet(
            models = ui.models,
            providerOrder = ui.providerOrder,
            selected = ui.selectedModel,
            selectedEffort = ui.selectedEffort,
            choice = ui.modelChoice,
            // A hero pick is deferred, not applied; a session's is the host's. The
            // sheet says which, so the check mark can never be read as "this is
            // running" when it means "this is what you asked for".
            sessionOpen = ui.currentSessionId != null,
            onSelect = { option, effort ->
                actions.selectModel(option, effort)
                showModels = false
            },
            onDismiss = { showModels = false },
        )
    }
}


/**
 * The bar's wide-window counterpart: the same three destinations, as a column beside
 * the content.
 *
 * ## Why a rail at all (2.5d)
 *
 * The floating bar is a thumb affordance. On a phone the bottom edge is where the hand
 * already is; on a tablet it is the far edge of a device being held by its side, and a
 * bar spanning 1 200dp stretches three 56dp seats across a lot of nothing. A rail puts
 * the same choice in a column where a wide window's navigation belongs.
 *
 * ## Why it is not `material3`'s `NavigationRail`
 *
 * Material's is a fixed 80dp column with its own item and label typography and its own
 * selection pill, and adopting it would mean this screen obeying two type scales — the
 * same problem `MiuixPalette` exists to prevent, one layer up. The three seats here are
 * the *same composable* the bar uses ([BottomBarSeat] is private to the bar's file, so
 * the shape is repeated rather than shared), reading the same `DshTheme` roles, which is
 * what keeps a rail and a bar from looking like two different apps.
 *
 * ## The item count
 *
 * Unchanged, and it must stay unchanged: [items] is rendered exactly as given, exactly
 * as the bar renders it. `docs/PHASE-2-PLAN.md` §2.4's rule is about a count that
 * cannot move under the reader's thumb, and a rail is under the same rule.
 */
@Composable
private fun NavigationRail(
    items: List<BottomBarItem>,
    selectedKey: String,
    onSelect: (String) -> Unit,
) {
    val colors = DshTheme.colors
    // A Row so the seam hairline can sit *beside* the rail rather than under it: the
    // rail's own Column is fixed-width, and the hairline has to be a sibling of it for
    // `fillMaxHeight` to mean the full window rather than the rail's padded box.
    Row(Modifier.fillMaxHeight()) {
    Column(
        Modifier
            .width(RAIL_SIDEBAR_WIDTH)
            .fillMaxHeight()
            // `sidebarFill` rather than `menu`: the rail is the sidebar's counterpart on
            // a wide window, and the two should read as the same surface — `menu` is a
            // floating layer's fill and at rail width it disappears into the transcript.
            .background(colors.sidebarFill)
            .padding(vertical = DshSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        items.forEach { item ->
            val active = item.key == selectedKey
            val tint = if (active) colors.link else colors.labelTertiary
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(DshRadius.md))
                    .clickableNoRipple { onSelect(item.key) }
                    .padding(vertical = DshSpacing.md)
                    .semantics {
                        contentDescription = item.label
                        selected = active
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.height(DshSpacing.xs))
                Text(
                    text = item.label,
                    style = DshType.micro,
                    color = tint,
                    maxLines = 1,
                )
            }
        }
    }
    // A hairline on the seam, because two surfaces meeting at a colour step reads as a
    // seam only where the step is visible; on the AMOLED palette both are near-black.
    Box(
        Modifier
            .fillMaxHeight()
            .width(0.5.dp)
            .background(colors.borderL3),
    )
    }
}

/**
 * The width at or above which the rail replaces the bar.
 *
 * 600dp is Material's `Medium` window boundary, and it is the point where a phone's
 * bottom-edge reach stops being the right assumption. Named rather than inlined so the
 * baseline test for the wide layout and the screen itself cannot disagree about it.
 */
internal val WIDE_LAYOUT = 600.dp

/** The rail's own width. Narrower than Material's 80dp because the labels are short. */
private val RAIL_SIDEBAR_WIDTH = 72.dp

/**
 * The header's lineage seat: the descendant count plus the chevron that opens the
 * tree, mirroring `SubagentHeaderLineage`.
 *
 * The label is deliberately terse (`+1 subagent`) — see [SubagentRollup.label] — so
 * a live session is signalled by the accent tint and an ongoing dot rather than by
 * the word "running", and the chip keeps the same width as subagents start and
 * finish. The full web sentence is the accessibility label.
 */
@Composable
private fun LineageChip(rollup: SubagentRollup, onClick: () -> Unit) {
    val colors = DshTheme.colors
    val running = rollup.running > 0
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(colors.hover)
            .clickableNoRipple(onClick = onClick)
            .padding(horizontal = DshSpacing.sm, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (running) {
            StateDot(state = DotState.ONGOING, size = 8.dp)
            Spacer(Modifier.width(DshSpacing.sm))
        }
        Text(
            text = rollup.label,
            style = DshType.micro,
            color = if (running) colors.link else colors.labelTertiary,
            maxLines = 1,
            modifier = Modifier.semantics { contentDescription = rollup.description },
        )
        Icon(
            Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = if (running) colors.link else colors.labelCaption,
            modifier = Modifier
                .size(14.dp)
                .padding(start = 1.dp),
        )
    }
}

/** One row of the lineage tree: the subagent, indented by its depth. */
private data class LineageRow(val session: uk.xa0.dsh.model.SessionItem, val depth: Int)

/**
 * Flattens [parentId]'s subagent descendants depth-first, so the sheet can render
 * the web's `role="tree"` as an indented list.
 *
 * Depth is capped: the host caps a real lineage far below this, and a cycle would
 * otherwise be unbounded.
 */
private fun flattenLineage(
    sessions: List<uk.xa0.dsh.model.SessionItem>,
    parentId: String,
    maxDepth: Int = 6,
): List<LineageRow> {
    val out = ArrayList<LineageRow>()
    fun walk(id: String, depth: Int) {
        if (depth > maxDepth) return
        for (child in subagentChildrenOf(sessions, id)) {
            out += LineageRow(child, depth)
            walk(child.id, depth + 1)
        }
    }
    walk(parentId, 0)
    return out
}

/**
 * The lineage dropdown: the subagents under the current session that are working
 * right now, newest first, each row opening that subagent's own session.
 *
 * Running only, to match the chip that opens it. The web portals a full `role="tree"`
 * of every descendant here; on this client the complete tree - archived records
 * included - is the drawer's per-session caret, and duplicating it in a sheet made
 * the chip's number and the sheet's contents disagree.
 */
@Composable
private fun LineageSheet(
    rootId: String,
    sessions: List<uk.xa0.dsh.model.SessionItem>,
    rollups: Map<String, SubagentRollup>,
    onOpen: (String) -> Unit,
) {
    val colors = DshTheme.colors
    val all = remember(sessions, rootId) { flattenLineage(sessions, rootId) }
    val rows = remember(all) { all.filter { it.session.running } }
    // The chip, the sheet and the roster each answer a different question, and this says
    // which one is empty. In the diag file rather than logcat: the test phone's ROM hides
    // app logs.
    uk.xa0.dsh.diag.ScrollDiag.note(
        "lineage",
        "root" to rootId,
        "sessions" to sessions.size,
        "descendants" to all.size,
        "running" to rows.size,
    )
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = DshSpacing.xl, vertical = DshSpacing.md),
    ) {
        Text("Running subagents", style = DshType.heading3, color = colors.labelPrimary)
        Spacer(Modifier.height(DshSpacing.sm))
        if (rows.isEmpty()) {
            Text("Nothing is running.", style = DshType.bodyMedium, color = colors.labelTertiary)
            Spacer(Modifier.height(DshSpacing.xl))
            return@Column
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
            items(rows, key = { it.session.id }) { row ->
                LineageSheetRow(
                    session = row.session,
                    depth = row.depth,
                    rollup = rollups[row.session.id],
                    onClick = { onOpen(row.session.id) },
                )
            }
        }
        Spacer(Modifier.height(DshSpacing.md))
    }
}

@Composable
private fun LineageSheetRow(
    session: uk.xa0.dsh.model.SessionItem,
    depth: Int,
    rollup: SubagentRollup?,
    onClick: () -> Unit,
) {
    val colors = DshTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DshRadius.md))
            .clickableNoRipple(onClick = onClick)
            .padding(
                start = DshSpacing.md + (DshSpacing.xl * depth),
                end = DshSpacing.md,
                top = DshSpacing.lg,
                bottom = DshSpacing.lg,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StateDot(
            state = when {
                session.running -> DotState.ONGOING
                session.blank -> DotState.IDLE
                else -> DotState.DONE
            },
            size = 8.dp,
        )
        Spacer(Modifier.width(DshSpacing.md))
        Text(
            text = session.subagentLabel ?: session.title.ifBlank { session.id },
            style = DshType.bodyMedium,
            color = colors.labelPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // A child that has children of its own says so, which is what makes the
        // flattened tree readable.
        if (rollup != null && !rollup.isEmpty) {
            Spacer(Modifier.width(DshSpacing.md))
            Text(
                text = rollup.total.toString(),
                style = DshType.micro,
                color = if (rollup.running > 0) colors.link else colors.labelCaption,
            )
        }
        Spacer(Modifier.width(DshSpacing.md))
        Text(
            text = when (session.subagentMode) {
                "continuable" -> "Continuable"
                "one-shot" -> "One-shot"
                else -> ""
            },
            style = DshType.micro,
            color = colors.labelCaption,
            maxLines = 1,
        )
    }
}

@Composable
private fun ChatHeader(
    title: String,
    cwd: String?,
    onMenu: () -> Unit,
    jobs: List<uk.xa0.dsh.model.JobItem> = emptyList(),
    jobsFinishedUnseen: Boolean = false,
    onJobs: () -> Unit = {},
    /**
     * Whether the router panel's seat is offered at all.
     *
     * Conditional for the same reason `filesAvailable` is: a session whose selected
     * route is not local has no router behind it, so there is nothing for the panel to
     * describe. This is a *seat* rather than a bar destination precisely because it can
     * be absent — see the gate in `ChatScreen` for the whole argument.
     */
    routerAvailable: Boolean = false,
    routerOpen: Boolean = false,
    onRouter: () -> Unit = {},
    filesOpen: Boolean = false,
    /**
     * Whether the Files seat is offered at all. A session is what the panel
     * resolves every path against, so the pending new-session hero — which has
     * deliberately no session — does not draw the seat.
     */
    filesAvailable: Boolean = true,
    onFiles: () -> Unit = {},
    lineage: SubagentRollup? = null,
    onLineage: () -> Unit = {},
    /**
     * The session above this one, when the open session is a subagent whose parent the
     * roster can name. The title becomes the way up; null draws no affordance at all.
     */
    parent: SubagentParent? = null,
    onParent: () -> Unit = {},
    /**
     * The one status fact this header wears, or null when there is nothing to say.
     *
     * One, not four: see [uk.xa0.dsh.model.StatusCapsules] for the ranking and the
     * argument. The derivation happens at the call site because it needs the whole
     * `UiState`; this composable only draws what it is handed, which is also what
     * keeps the header fixture-able in a screenshot test.
     */
    capsule: StatusCapsule? = null,
    /**
     * What the capsule opens, or **null** when it opens nothing.
     *
     * Null rather than an empty lambda on purpose: a chip that looks tappable and
     * does nothing is worse than a chip that plainly is not a button, and the three
     * states that have no action are the majority. The composable drops the
     * `clickable` when this is null, so the two are structurally the same decision.
     */
    onCapsule: (() -> Unit)? = null,
    /**
     * The backdrop the chrome samples, or null on a device that paints the flat tier.
     *
     * The header is chrome, not content: it sits *outside* the pager, so it is not part
     * of what the bar records. Giving it the material is therefore not a second use of
     * the same backdrop — it is the other half of "the shell floats over the
     * conversation", which is the visual direction this step is about. Null is the
     * honest value on API < 33 and in a capture, and [liquidPane] turns it into the flat
     * fill the header had before, so the layout is identical either way.
     */
    backdrop: GlassBarBackdrop? = null,
    /**
     * Whether the header is folded to its one-line form.
     *
     * **Per page, not per scroll.** `docs/PHASE-3-PLAN.md` §6 settles the semantics and
     * the reason: the three readings share one header, and only Chat has a scroll
     * position, so a fold that followed scrolling would jump every time the reader
     * switched to a view that has no transcript to scroll. The fold follows the *page*
     * instead — Chat is the one that has something worth two lines (a title and a
     * directory), and Trajectory, Shell, Files, Plugins and Me are one line.
     *
     * The second line is the session's `cwd`, and dropping it is the whole of the fold:
     * it is the only variable-height thing in this bar, and the seats on either side are
     * fixed 40dp circles.
     */
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = DshTheme.colors
    // The header wears the same material as the bar below it.
    //
    // `flatFill = Color.Transparent` is the whole reason this can be added without
    // redrawing every flat-tier screen. `glassSurface` — the material's flat answer —
    // is fill-plus-hairline, and a hairline is right for a pane that floats: it is the
    // only boundary between that pane and the page. The header is *flush* with the page
    // and spans the full width, so an all-round stroke puts a 0.5dp line along the
    // screen's top edge and both side edges — a frame, not a surface. Passing the fill
    // the header actually had (none) keeps the flat tier byte-identical and leaves the
    // material to the tier that can show it.
    // Animated rather than swapped: the header is the top of a very tall scroll view,
    // and a 8dp jump between two states reads as the whole screen twitching. Measured
    // through `onSizeChanged` at the call site, so the loading offset the transcript uses
    // follows the animation frame by frame rather than moving once at the end.
    val rowHeight by animateDpAsState(
        targetValue = if (compact) 48.dp else 56.dp,
        label = "HeaderHeight",
    )
    Column(
        modifier.liquidPane(
            backdrop = backdrop,
            shape = RoundedCornerShape(0.dp),
            flatFill = Color.Transparent,
        ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(rowHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Menu,
                contentDescription = "Sessions",
                tint = colors.labelPrimary,
                modifier = Modifier
                    .size(HEADER_SEAT)
                    .clip(CircleShape)
                    .clickableNoRipple(onClick = onMenu)
                    .padding(DshSpacing.md),
            )
            Spacer(Modifier.width(HEADER_SEAT_GAP))
            // The way *up* is the whole seat, not just the words in it.
            //
            // The tap used to be on the title row, so it was only as wide as the arrow
            // and the name and only as tall as one line — a tap a few pixels to the
            // right of the title, in space that belongs to nothing else, did nothing at
            // all. A bar's empty space belongs to the thing the bar is about.
            val seat = Modifier.weight(1f).fillMaxHeight()
            Column(
                modifier = if (parent != null) {
                    seat
                        .clip(RoundedCornerShape(DshRadius.sm))
                        .clickableNoRipple(onClick = onParent)
                        .semantics {
                            contentDescription = "Open parent session: ${parent.title}"
                        }
                } else {
                    seat
                },
                verticalArrangement = Arrangement.Center,
            ) {
                // The arrow is the whole hint — it is drawn only when the tap actually
                // leads somewhere (`SubagentParent` is null otherwise, so a top-level
                // session's title is not secretly clickable).
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (parent != null) {
                        Icon(
                            Icons.Rounded.ArrowUpward,
                            contentDescription = null,
                            tint = colors.labelTertiary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = title,
                        style = DshType.messageBody.copy(fontWeight = FontWeight.Medium),
                        color = colors.labelPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // The folded half. Animated out rather than dropped, so the title does
                // not slide from a two-line block to a one-line block in one frame.
                AnimatedVisibility(visible = !compact && !cwd.isNullOrBlank()) {
                    Text(
                        text = shortenPath(cwd.orEmpty()),
                        style = DshType.bodySmall,
                        color = colors.labelTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // The `SubagentHeaderLineage` seat, worn as one of the header's trailing
            // seats rather than inline after the title.
            //
            // Inline, it centred on the title *line* while the block it sits in is
            // two lines tall (title + directory), which measured 22px — about 8dp on
            // this 420dpi screen — above the block's centre, so it read as floating
            // high. Here it centres on the header row like the files and jobs seats,
            // and the title gets its full width back instead of truncating to make
            // room for it.
            //
            // Running only, and absent when nothing is running: this is a live
            // indicator, and the drawer's per-session caret is the complete tree.
            if (lineage != null && lineage.running > 0) {
                LineageChip(lineage, onLineage)
                Spacer(Modifier.width(HEADER_SEAT_GAP))
            }
            // The right panel's seat, which on the web is a header utility. It
            // stays available on a blank-session hero too: a blank session still has
            // a workspace worth listing. On the *pending* hero there is no session
            // for the panel to address at all, so the seat is withheld.
            if (filesAvailable) {
                Icon(
                    Icons.Rounded.Folder,
                    contentDescription = if (filesOpen) "Close files" else "Files",
                    tint = if (filesOpen) colors.link else colors.labelSecondary,
                    modifier = Modifier
                        .size(HEADER_SEAT)
                        .clip(CircleShape)
                        .clickableNoRipple(onClick = onFiles)
                        .padding(DshSpacing.md),
                )
                Spacer(Modifier.width(HEADER_SEAT_GAP))
            }
            // The router panel's seat. Drawn before the jobs seat so the two cannot
            // swap places as either one appears; both are conditional, and a fixed
            // order is what keeps the cluster from reshuffling.
            if (routerAvailable) {
                Icon(
                    Icons.Rounded.Hub,
                    contentDescription = if (routerOpen) "Close router" else "Router",
                    tint = if (routerOpen) colors.link else colors.labelSecondary,
                    modifier = Modifier
                        .size(HEADER_SEAT)
                        .clip(CircleShape)
                        .clickableNoRipple(onClick = onRouter)
                        .padding(DshSpacing.md),
                )
                Spacer(Modifier.width(HEADER_SEAT_GAP))
            }
            JobsSeat(jobs = jobs, finishedUnseen = jobsFinishedUnseen, onClick = onJobs)
            // The capsule is the *last* seat, after the utilities, because it is the
            // one that changes width with its label: putting it before them would
            // shift every utility sideways as states change.
            //
            // It is capped rather than free to grow. The header row has no trailing
            // inset — every other seat is a fixed 40dp circle, so none of them ever
            // needed one — and a label as long as "Waiting for approval" ran off the
            // right edge the first time it was recorded. A maximum width plus the
            // row's own end padding is what makes the longest of the four labels fit;
            // the ellipsis then covers a label a future state adds that does not.
            if (capsule != null) {
                Spacer(Modifier.width(HEADER_SEAT_GAP))
                StatusCapsuleChip(
                    capsule,
                    onClick = onCapsule,
                    modifier = Modifier.widthIn(max = CAPSULE_MAX_WIDTH),
                )
                Spacer(Modifier.width(DshSpacing.sm))
            }
            // No separate running-turn dot here on purpose. `JobsSeat` already
            // draws a `StateDot(ONGOING)` while a job runs, and this glyph is the
            // same one, so a running turn plus a running job painted two identical
            // spinners side by side, animating on two independent transitions -
            // visibly out of step. The web has no running indicator in the
            // conversation header at all (`conversation.session.header.utilities`
            // has exactly one contributor, the desktop-only open-in-app button);
            // running state is the sidebar row's status and the composer's own
            // stop affordance, both of which this app already has.
            Spacer(Modifier.width(HEADER_SEAT_GAP))
        }
        // The rule that separates the header from the content beneath it. The bar
        // already floats; the header floating too is what makes the two read as one
        // shell rather than a chrome strip with a bar under it.
        //
        // The hairline stays on every tier, unlike the pane's own edge stroke. The
        // difference is what it is separating: a pane's rim is a boundary between two
        // surfaces, which the lens already draws, while this line marks where chrome
        // ends and scrolling content begins — a distinction the material does not
        // express by itself. `borderL3` is the same rule the header had before it had
        // a material.
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(colors.borderL3),
        )
    }
}

/**
 * The three readings of one session: the transcript, its trajectory, and the shell.
 *
 * ## This used to be the bottom bar (3.3)
 *
 * `ui-conversation/src/client/contract/views.ts` — chat (order 0, the default in
 * `view-selection.ts`) and trajectory (order 10). Terminal is not a host-registered
 * view: it is a client view over the host's `terminal` Remote namespace, which is what
 * the web client's own terminal tab is assembled from too.
 *
 * 2.4 put these three in the bar and 3.3 took them out again, because they were never
 * destinations: all three are the session already on screen, read differently. The bar
 * now holds `Destination` and this holds the pill under the header. What survives from
 * 2.4 is the invariant — **every value is unconditional**, so a control built from
 * `ChatView.entries` has a constant item count and cannot move under the reader's thumb.
 */
private enum class ChatView(val label: String) {
    CHAT("Chat"),
    TRAJECTORY("Trajectory"),
    // "Shell", not "Terminal": the view hosts two seats — an unconfined host shell and
    // this session's own confined terminal — so the accurate word is the general one,
    // and the seat chips inside say which is which.
    TERMINAL("Shell"),
}

/**
 * What the pill draws — one entry per `ChatView`, in the enum's order.
 *
 * All three, unconditionally. The llama.cpp router panel used to be a fourth value in
 * this list and is now a sheet behind a header seat; see the gate in `ChatPanel` for why.
 * The icons are Material's, the same set the bar below uses.
 */
private val CHAT_VIEW_ITEMS: List<BottomBarItem> = listOf(
    BottomBarItem(ChatView.CHAT.name, ChatView.CHAT.label, Icons.Rounded.ChatBubble),
    BottomBarItem(ChatView.TRAJECTORY.name, ChatView.TRAJECTORY.label, Icons.Rounded.Timeline),
    BottomBarItem(ChatView.TERMINAL.name, ChatView.TERMINAL.label, Icons.Rounded.Terminal),
)

/**
 * The `files` surface of the web's right panel, full-body on a phone.
 *
 * The web opens it as a preview tab beside the transcript and keeps the header in
 * place; a phone has no room for both, so this carries its own back bar and the
 * path it was asked to select.
 */
@Composable
private fun FilesSurface(
    files: List<uk.xa0.dsh.model.SessionFile>,
    previews: Map<String, uk.xa0.dsh.model.FilePreview>,
    root: String?,
    focus: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Reads a file the *transcript* never touched. Without it the pane can only
     * ever show what the session itself read or wrote, which is most of the reason
     * a file the agent merely mentioned looked like it had no contents.
     */
    loader: uk.xa0.dsh.model.WorkspaceFileLoader? = null,
    /**
     * The workspace listing, for the panel's Browse mode. Null — no host, or no
     * cwd to be relative to — leaves the panel as the session's own list, which
     * is what it was before there was a tree to browse.
     */
    explorer: ExplorerWiring? = null,
    /**
     * Reads a whole file, for the preview header's Download control. Wired from the
     * session scope like [loader]; without it the panel offers no download at all
     * rather than a control that cannot work.
     */
    downloader: uk.xa0.dsh.model.FileDownloader? = null,
) {
    val colors = DshTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.bgBase),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = DshSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.ArrowBack,
                contentDescription = "Back to chat",
                tint = colors.labelSecondary,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickableNoRipple(onClick = onClose)
                    .padding(DshSpacing.md),
            )
            Spacer(Modifier.width(DshSpacing.sm))
            Text(
                text = "Files",
                style = DshType.messageBody.copy(fontWeight = FontWeight.Medium),
                color = colors.labelPrimary,
            )
            if (root != null) {
                Spacer(Modifier.width(DshSpacing.md))
                Text(
                    text = shortenPath(root),
                    style = DshType.bodySmall,
                    color = colors.labelTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(colors.borderL3),
        )
        FilesPanel(
            files = files,
            root = root,
            cached = previews,
            loader = loader,
            downloader = downloader,
            initialPath = focus,
            explorer = explorer,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RunningDot() {
    val transition = rememberInfiniteTransition(label = "running")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "alpha",
    )
    Box(
        Modifier
            .size(8.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(DshTheme.colors.accent),
    )
}

/**
 * The web UI's back-to-bottom affordance: a 34dp floating circle pinned above the
 * composer, shown only once you have scrolled away from the newest turn.
 */
@Composable
private fun ScrollToBottomButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: GlassBarBackdrop? = null,
) {
    val colors = DshTheme.colors
    // The same material as the dock it floats above. This used to be an opaque
    // `bgLayer2` circle, which read as a hole punched in the glass vocabulary right
    // beside the composer; it is now one more sheet of it, lifting the way the bar does.
    Box(
        modifier
            .size(34.dp)
            .glassLift(CircleShape)
            .liquidPane(backdrop, CircleShape)
            .clickableNoRipple(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.ArrowDownward,
            contentDescription = "Scroll to latest",
            tint = colors.labelPrimary,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** One rail mark: where in the transcript it jumps to, and what it says. */
/**
 * One stop on the turn rail.
 *
 * The prompt and the answer are *lambdas*, not strings, and that is a scrolling
 * cost rather than a tidiness one: the rail's targets are rebuilt whenever the row
 * list changes, which is every streamed token, and building them eagerly meant
 * joining the assistant prose of every turn in the session — hundreds of kilobytes
 * of it in a long conversation — on every frame, to fill a card that is only on
 * screen while a finger is on the rail.
 */
private class TurnJump(
    val index: Int,
    val prompt: () -> String,
    val response: () -> String,
)

/**
 * The assistant prose that answered the prompt at [index].
 *
 * [rows] is newest-first, so the answer lives at *smaller* indices: walk back
 * until the previous human message, collecting assistant text in turn order.
 */
private fun responseAfter(rows: List<DisplayRow>, index: Int): String =
    rows.subList(0, index)
        .takeWhile { row ->
            val entry = (row as? DisplayRow.Single)?.entry
            !(entry is ChatEntry.UserMessage && !entry.fromPlugin)
        }
        .asReversed()
        .mapNotNull { ((it as? DisplayRow.Single)?.entry as? ChatEntry.AssistantMessage)?.text }
        .filter { it.isNotBlank() }
        .joinToString("\n")

/**
 * The turn rail.
 *
 * At rest it is the web's rail: short ticks — 12dp idle, 20dp for the turn you
 * are reading — inside a taller tap target, since the web's 12x2px marks are far
 * below a comfortable touch size.
 *
 * Pressing it grows the whole rail into a scrubber, and it stays grown until the
 * finger lifts: the ticks widen, a panel appears behind them, and the mark under
 * your finger highlights with a preview of that turn's prompt and answer. Lifting
 * jumps there. This is the web's hover affordance (preview mark + tooltip card)
 * translated to touch, which is the only way its rail can work at all on a
 * phone — there is no hover to reveal anything.
 */
@Composable
private fun TurnRail(
    targets: List<TurnJump>,
    activeIndex: Int?,
    onJump: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DshTheme.colors
    val shown = targets.takeLast(MAX_RAIL_TICKS)
    var pressed by remember { mutableStateOf(false) }
    var hovered by remember { mutableStateOf<Int?>(null) }
    // The tick ladder's own bounds, in this Box's coordinates: the pointer only
    // knows a local y, and the surface padding shifts the ladder inside the Box.
    var ladder by remember { mutableStateOf<Rect?>(null) }

    val pad by animateDpAsState(if (pressed) 8.dp else 0.dp, label = "railPad")
    val railShape = RoundedCornerShape(DshRadius.card)
    val previewIndex = hovered?.takeIf { pressed && it in shown.indices }
    val preview = previewIndex?.let { shown[it] }

    Box(
        modifier
            .clip(railShape)
            .then(if (pressed) Modifier.background(colors.tip).border(0.5.dp, colors.borderL1, railShape) else Modifier)
            .padding(horizontal = pad, vertical = pad)
            .pointerInput(shown.size, ladder) {
                // A tap, expressed as a tap.
                //
                // This was a hand-rolled awaitEachGesture loop that observed without
                // consuming and committed on release. On the device the ticks did nothing:
                // the loop's "claimed" test (a down/up carrying `isConsumed`) is not a
                // reliable description of a tap once a scrollable and a row's controls
                // share the same hit path, and the commit path was guarded by it. The
                // canonical detector answers exactly the question being asked — a press
                // that stays put and lifts is a tap, a press the list claims is a scroll —
                // and the rail keeps its own tap target.
                detectTapGestures(
                    onPress = { offset ->
                        pressed = true
                        hovered = shown.indexOfAt(offset.y, ladder)
                        // `tryAwaitRelease` is the whole state machine: true when the
                        // finger lifts inside the rail without the list taking the gesture.
                        if (tryAwaitRelease()) {
                            hovered?.let { if (it in shown.indices) onJump(shown[it].index) }
                        }
                        pressed = false
                        hovered = null
                    },
                )
            },
    ) {
        Column(
            Modifier
                .width(RAIL_WIDTH)
                .heightIn(max = 280.dp)
                .onGloballyPositioned { ladder = it.boundsInParent() },
            verticalArrangement = Arrangement.spacedBy(DshSpacing.sm),
            horizontalAlignment = Alignment.End,
        ) {
            shown.forEachIndexed { position, target ->
                val active = target.index == activeIndex
                val isHovered = position == previewIndex
                val width by animateDpAsState(
                    when {
                        active -> 20.dp
                        isHovered -> 18.dp
                        pressed -> 14.dp
                        else -> 12.dp
                    },
                    label = "railTick",
                )
                val tint = when {
                    active -> colors.labelPrimary
                    isHovered -> colors.labelTertiary
                    else -> colors.borderL4
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(16.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        Modifier
                            .width(width)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(tint),
                    )
                }
            }
        }

        // Drawn fully to the left of the rail — the web's `right: calc(100% + 10px)`
        // measures from the frame's *left* edge, not from the marks — so it never
        // covers the ticks it is describing. The Box is unclipped, so the card
        // overhangs the transcript gutter.
        if (preview != null) {
            TurnPreviewCard(
                preview = preview,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = -(RAIL_WIDTH + pad * 2 + 10.dp)),
            )
        }
    }
}

/** The rail's hover card: the turn's prompt, then its answer. */
@Composable
private fun TurnPreviewCard(preview: TurnJump, modifier: Modifier = Modifier) {
    val colors = DshTheme.colors
    val shape = RoundedCornerShape(DshRadius.lg)
    // Once per turn shown, not once per frame: the rail is rebuilt on every streamed
    // token and the answer is the whole of that turn's prose.
    val prompt = remember(preview.index) { preview.prompt() }
    val response = remember(preview.index) { preview.response() }
    Column(
        modifier
            .widthIn(max = 260.dp)
            .shadow(6.dp, shape)
            .clip(shape)
            .background(colors.menu)
            .border(0.5.dp, colors.borderL1, shape)
            .padding(horizontal = DshSpacing.lg, vertical = 10.dp),
    ) {
        Text(
            text = prompt,
            style = DshType.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = colors.labelPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (response.isNotBlank()) {
            Spacer(Modifier.height(DshSpacing.xs))
            Text(
                text = response,
                style = DshType.bodySmall,
                color = colors.labelCaption,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Which tick a pointer at [y] is over.
 *
 * Rows are a uniform pitch, so the ladder's own height divides cleanly — no need
 * to know the gap. [ladder] is the measured ladder rect (in the gesture owner's
 * coordinates), which the rail's own padding would otherwise skew.
 */
private fun List<TurnJump>.indexOfAt(y: Float, ladder: Rect?): Int? {
    val rect = ladder ?: return null
    if (isEmpty() || rect.height <= 0f) return null
    val relative = (y - rect.top).coerceIn(0f, rect.height - 0.01f)
    return ((relative / rect.height) * size).toInt().coerceIn(0, size - 1)
}

private const val MAX_RAIL_TICKS = 22

/** The web's rail frame is 28px wide; the ticks are right-aligned inside it. */
private val RAIL_WIDTH = 22.dp

/**
 * The gap between the floating bar and the bottom edge of the screen.
 *
 * Only the gap: the bar's own height is the bar's business, and nothing reserves either any
 * more. The pages run to the bottom so the material has something behind it — see the pager's
 * modifier — and each page keeps its own last row clear through its content padding.
 */
private val BAR_BOTTOM_GAP = DshSpacing.md

/** Sidebar width; `drawerDrag` needs it to know what a full travel is. */
private val DRAWER_WIDTH = 300.dp

/** How far from the left edge a closed drawer's swipe may start. */
private val DRAWER_EDGE = 96.dp

/** Horizontal travel that commits a drawer swipe; short on purpose. */
private val DRAWER_COMMIT = 40.dp

/**
 * The header's seat rhythm.
 *
 * Every trailing seat — the lineage chip, the files button, the jobs pill — occupies
 * the same square touch box and is separated by the same gap. Before this each seat
 * carried its own inset and its own spacer (6dp after the hamburger, 6dp after the
 * chip, none at all between files and jobs, then a 12dp tail), so the spacing was
 * whatever the paddings happened to add up to and the row read as unevenly bunched.
 */
private val HEADER_SEAT = 40.dp
private val HEADER_SEAT_GAP = DshSpacing.xs

/**
 * The widest the status capsule may be.
 *
 * The header row has no trailing inset — every other seat is a fixed 40dp circle, so
 * none of them ever needed one — and the first recorded capture of the capsule showed
 * "Waiting for approval" running off the right edge. This is the width that fits the
 * longest of the four labels alongside the utilities on a 411dp phone, with the row's
 * own end padding after it. A label a future state adds that does not fit ellipsizes
 * rather than clipping.
 */
private val CAPSULE_MAX_WIDTH = 148.dp

/**
 * The drawer's edge drag, on the screen **and** on the sheet.
 *
 * ## It follows the finger
 *
 * The public [DrawerState] surface only offers "settle to an anchor" (`open`/`close`), so
 * an earlier version could only decide the drawer's fate on release: the sheet sat still
 * under the thumb and then slid. Material's own draggable is what normally moves it, but
 * this detector consumes the gesture in the **Initial** pass (which is what keeps the
 * pager from claiming a rightward drag at page 0), so Material never sees it.
 *
 * The fix is to drive the drawer's offset ourselves through the anchored-draggable state
 * the state object already wraps: every move dispatches its horizontal delta, so the sheet
 * and the scrim track the finger exactly; release settles to the nearer anchor with the
 * app's own commit rule (40dp of travel or a 200dp/s fling — Material's modal drawer would
 * want half the panel width). The property is `internal` to Material3; the suppression is
 * deliberate and local, and the compile-time check is what keeps it honest across a
 * library bump.
 *
 * ## The dock keeps its own gestures (round 3)
 *
 * The destination bar has horizontal gestures of its own (dragging the selection between
 * tabs), so a rightward swipe on it must not open the drawer. Material's drawer draggable
 * is a single root gesture — it cannot be told to ignore a region — so this detector
 * consumes the gesture **in the Main pass, after the dock's children have seen it**, for
 * downs that start inside the dock's measured area. Consuming an ancestor's Main pass is
 * exactly enough to make the root draggable stand down without taking the swipe from the
 * bar itself.
 *
 * ## Soft case retained
 *
 * A horizontally scrollable child inside the edge band still wins: if the child consumed
 * the move, the drawer stands down, which is why a code block's long line can be scrolled
 * sideways.
 */
@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
private fun DrawerState.dragState(): AnchoredDraggableState<DrawerValue> = anchoredDraggableState

@Composable
private fun Modifier.drawerDrag(
    drawerState: DrawerState,
    width: Dp,
    /**
     * Whether a rightward edge drag has somewhere else to go *right now* — the caller's
     * "is there anything to the left of this page". Read at gesture time, so a page change
     * between two swipes is seen immediately.
     */
    edgeClaim: () -> Boolean = { false },
    /**
     * Height, in pixels, of the bottom region whose gestures belong to the dock. A down
     * inside it never opens the drawer; the dock's own controls still receive the gesture
     * because the suppression happens after them, in the Main pass.
     */
    blockedBottomPx: () -> Float = { 0f },
): Modifier {
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current
    val layoutDirection = LocalLayoutDirection.current
    val scope = rememberCoroutineScope()
    val edgePx = with(density) { DRAWER_EDGE.toPx() }
    val commitPx = with(density) { DRAWER_COMMIT.toPx() }
    val flingPx = with(density) { 200.dp.toPx() }
    // The panel's own width only matters to keep the parameter honest about what
    // "travel" means; the commit threshold above is absolute.
    val widthPx = with(density) { width.toPx() }
    // The state's offsets are in pixels with the closed anchor at `-width`; a rightward
    // drag adds to it in LTR and subtracts in RTL, matching `reverseDirection` in Material's
    // own draggable.
    val direction = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f

    // Keyed on the drawer only. A new `edgeClaim` (or a dock height that changed because
    // the composer grew) must NOT restart the gesture detector: both are read at use, so a
    // running gesture keeps seeing the current answer.
    return pointerInput(drawerState, widthPx) {
        awaitEachGesture {
            // Read in the **Initial** pass: an ancestor sees the down before its children,
            // which is what makes the hard claim below possible at all.
            val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)

            // Dock region: stand down for the drawer only. The loop reads Main, which bubbles
            // up after the dock's own rows/controls; consuming there stops Material's root
            // draggable without stealing the tap or drag from the bar.
            if (down.position.y >= size.height - blockedBottomPx()) {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    change.consume()
                    if (!change.pressed) break
                }
                return@awaitEachGesture
            }

            val fromEdge = down.position.x <= edgePx
            val wasOpen = drawerState.isOpen
            if (!wasOpen && !fromEdge) return@awaitEachGesture

            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var totalX = 0f
            var totalY = 0f
            var last = down.position
            var dragging = false
            // Set once this gesture has been taken ahead of the children. From then on the
            // loop stays in the Initial pass, consumes every move, and feeds the drawer's
            // offset — so the pager never gets an unconsumed drag to claim, and the sheet
            // moves under the finger.
            var claimed = false

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val dx = change.position.x - last.x
                totalX += dx
                totalY += change.position.y - last.y
                last = change.position
                tracker.addPosition(change.uptimeMillis, change.position)

                if (!dragging) {
                    if (abs(totalX) > viewConfiguration.touchSlop && abs(totalX) > abs(totalY)) {
                        dragging = true
                    } else if (abs(totalY) > viewConfiguration.touchSlop) {
                        return@awaitEachGesture
                    }
                }
                if (!dragging) continue

                if (!claimed) {
                    // An open drawer can be pushed either way; a closed one only opens, and
                    // only from the edge with nowhere left for the pager to go.
                    claimed = wasOpen || (fromEdge && totalX > 0f && edgeClaim())
                }
                if (claimed) {
                    change.consume()
                    drawerState.dragState().dispatchRawDelta(dx * direction)
                    continue
                }

                // The soft case: give the child its turn and stand down if it took the
                // gesture. The obvious child is a horizontally scrollable bash output block.
                val afterChild = awaitPointerEvent(PointerEventPass.Main)
                val childChange = afterChild.changes.firstOrNull { it.id == down.id }
                if (childChange == null || childChange.isConsumed) return@awaitEachGesture
                // The child did not want the edge swipe after all: take the gesture live from
                // here, replaying the travel that began before the claim so the sheet does
                // not lag the thumb.
                if (!wasOpen && fromEdge && totalX > 0f && edgeClaim()) {
                    claimed = true
                    childChange.consume()
                    drawerState.dragState().dispatchRawDelta(totalX * direction)
                    continue
                }
                childChange.consume()
            }

            if (!dragging) return@awaitEachGesture
            val velocity = tracker.calculateVelocity().x
            // The offset has already moved with the finger; this only picks the anchor it
            // settles to. An open drawer needs the opposite question from a closed one:
            // "did the reader pull it far enough shut", not "did they pull it open".
            val open = if (wasOpen) {
                !(velocity < -flingPx || totalX < -commitPx)
            } else {
                when {
                    velocity > flingPx -> true
                    velocity < -flingPx -> false
                    else -> totalX > commitPx
                }
            }
            scope.launch { if (open) drawerState.open() else drawerState.close() }
        }
    }
}

/**
 * Derives [ComposerTriggers] and keeps the host's rosters in step with them.
 *
 * This is shared rather than duplicated because the hero and a session are the
 * same input surface: the blank-session seat used to get none of it, so `/` and
 * `@` did nothing there while the identical typing worked one screen later.
 *
 * The derivation itself lives in `ui/composer`, out of this 2100-line file; this
 * only drives the host callbacks the derived query feeds. [text] and [caret] are
 * passed in rather than a value object because the field's state is the source of
 * both — the caller reads `state.text` and `state.selection.end` and hands them
 * over, so the same derivation runs against any caret position, mid-text included.
 */
@Composable
private fun rememberComposerTriggers(
    text: String,
    caret: Int,
    commandEntries: List<CommandEntry>,
    searchReferences: (String) -> Unit,
    clearReferences: () -> Unit,
    sessionId: String?,
    refreshCommands: () -> Unit,
): ComposerTriggers {
    val triggers = remember(text, caret, commandEntries) {
        composerTriggers(text, caret, commandEntries)
    }
    LaunchedEffect(triggers.referenceQuery) {
        val query = triggers.referenceQuery
        if (query != null) searchReferences(query) else clearReferences()
    }
    // The roster is the host's and is session-scoped, so it is re-read whenever
    // the open session changes rather than cached once at connect.
    LaunchedEffect(sessionId) { refreshCommands() }
    return triggers
}

/**
 * The two popups that hang off the composer: the `@` reference roster and the
 * `/` (or `+`) command roster. `@` and `/` are mutually exclusive by their first
 * character, so only one can be open at a time.
 */
@Composable
private fun ComposerPopups(
    triggers: ComposerTriggers,
    references: List<ReferenceCandidate>,
    state: TextFieldState,
    menuOpen: Boolean,
    onPickCommand: (CommandEntry) -> Unit,
    clearReferences: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val query = triggers.referenceQuery
    if (query != null) {
        ReferenceMenu(
            entries = references,
            onPick = { candidate ->
                // Replace the token under the caret, keeping whatever surrounds
                // it in the draft; a pick must never rewrite the draft's end.
                state.edit { replaceTrigger(triggers.reference, candidate.mention + " ") }
                clearReferences()
            },
            modifier = modifier,
        )
    }
    if (menuOpen || triggers.slashQuery != null) {
        CommandMenu(entries = triggers.shownEntries, onPick = onPickCommand, modifier = modifier)
    }
}

/** Empty-session state: the mark, "New Session", and the workspace/preset chips. */
@Composable
private fun HeroBody(
    modifier: Modifier,
    workspaceLabel: String,
    onWorkspaceClick: () -> Unit,
    presetLabel: String?,
    showPresetChip: Boolean,
    onPresetClick: () -> Unit,
) {
    val colors = DshTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = DshSpacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DshMark(size = 26.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                text = "New Session",
                style = DshType.heading1.copy(fontWeight = FontWeight.Medium),
                color = colors.labelPrimary,
            )
        }
        Spacer(Modifier.height(DshSpacing.xxl))

        // The hero's workspace chip: which directory this session will run in. The
        // web places it 8px above the card, and it is a *different* control from
        // the access-mode chip inside the composer.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
        Row(
            Modifier
                .height(28.dp)
                .clip(RoundedCornerShape(DshRadius.thumb))
                .clickableNoRipple(onClick = onWorkspaceClick)
                .padding(horizontal = DshSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Folder,
                contentDescription = null,
                tint = colors.labelSecondary,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(DshSpacing.xs))
            Text(
                text = workspaceLabel,
                style = DshType.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = colors.labelSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(DshSpacing.xxs))
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = colors.labelCaption,
                modifier = Modifier.size(12.dp),
            )
        }
        // The agent-preset chip, the web hero's second seat: the preset this
        // session's agent will run. Offered only while the session is blank.
        if (showPresetChip && presetLabel != null) {
            Spacer(Modifier.width(DshSpacing.md))
            AgentPresetChip(label = presetLabel, onClick = onPresetClick)
        }
        }
        Spacer(Modifier.height(DshSpacing.md))

        // The composer itself is not here: it lives in the bottom dock, which is
        // composed outside the page. The hero only decides what sits above it.
        Spacer(Modifier.height(DshSpacing.xl))
    }
}

/**
 * The agent-preset chip: a 28dp pill in the same family as the workspace chip,
 * with a hub glyph, the preset's display name, and a disclosure chevron.
 */
@Composable
private fun AgentPresetChip(label: String, onClick: () -> Unit) {
    val colors = DshTheme.colors
    Row(
        Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(DshRadius.thumb))
            .clickableNoRipple(onClick = onClick)
            .padding(horizontal = DshSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Hub,
            contentDescription = null,
            tint = colors.labelSecondary,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(DshSpacing.xs))
        Text(
            text = label,
            style = DshType.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = colors.labelSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(DshSpacing.xxs))
        Icon(
            Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = colors.labelCaption,
            modifier = Modifier.size(12.dp),
        )
    }
}

/**
 * The one-line error strip over the transcript.
 *
 * Tapping anywhere dismisses it (the long-standing behaviour); [actionLabel]
 * adds a second, separately tappable verb for failures the user can actually do
 * something about — an expired session's "Sign in again", which is the
 * difference between a dead end and a way out.
 */
@Composable
private fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = DshTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .background(colors.warnTertiary)
            .padding(horizontal = DshSpacing.xl, vertical = DshSpacing.lg)
            .clickableNoRipple(onClick = onDismiss),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = DshType.bodySmall,
            color = colors.error,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = DshType.bodySmall,
                color = colors.link,
                // Its own tap target: the inner clickable consumes the event, so
                // the action never also dismisses the banner it lives on.
                modifier = Modifier
                    .clickableNoRipple(onClick = onAction)
                    .padding(horizontal = DshSpacing.sm),
            )
        }
        Text("Dismiss", style = DshType.bodySmall, color = colors.link)
    }
}

/**
 * The app's `PendingKind` as the model layer's mirror of it.
 *
 * Two enums rather than one because the ranking lives in `:core:model` (where it can
 * be tested without Android) and the kind is minted in `AttentionCenter`, next to the
 * event stream that produces it. This is the one place the two meet; an unmapped value
 * becomes null rather than throwing, because a kind this build does not know is a
 * reason to draw no capsule, not a reason to crash the header.
 */
private fun pendingKindOf(kind: PendingKind?): PendingKindWire? = when (kind) {
    PendingKind.APPROVAL -> PendingKindWire.APPROVAL
    PendingKind.QUESTION -> PendingKindWire.QUESTION
    PendingKind.PLAN_REVIEW -> PendingKindWire.PLAN_REVIEW
    null -> null
}

/**
 * Whether the capsule would be showing the offline state, computed from the same
 * inputs `StatusCapsules.derive` reads.
 *
 * Recomputed rather than read off the derived value because the derived value is
 * passed inline as an argument and the tap handler is chosen in the same expression
 * list — naming it would need a local outside the call, and a `remember`ed capsule
 * would go stale against `UiState`. The duplication is two boolean reads against one
 * source of truth (`connectionLabel`), which is the smaller of the two evils.
 */
private fun capsuleStateIsOffline(ui: UiState): Boolean =
    ui.connectionLabel != null && ui.pendingInteractions[ui.currentSessionId] == null

/**
 * The header's status capsule: one fact, one word, one tint.
 *
 * ## Why a chip and not a dot
 *
 * A coloured dot is the cheapest status indicator and the worst one here: the four
 * states this can show are not a severity ramp (an approval is not "redder" than a
 * dropped socket), and a dot cannot say *which* interaction is waiting. The word is
 * the payload; the tint only groups the state.
 *
 * ## Why the tint is not the palette's semantic colour alone
 *
 * [CapsuleState.WAITING] wears the warning tint because the agent is stopped until a
 * human answers — the same colour the Full-access option and the truncated-entry note
 * wear, which is the app's existing vocabulary for "this is on you". [OFFLINE] wears
 * the error tint, [WORKING] the accent, and [UNKNOWN] tertiary ink: a state the client
 * cannot name should not be loud.
 *
 * The dot is the same `StateDot` the rest of the app uses, so a waiting approval
 * chases exactly like a running job does rather than inventing a second animation.
 */
@Composable
private fun StatusCapsuleChip(
    capsule: StatusCapsule,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = DshTheme.colors
    val tint = when (capsule.state) {
        CapsuleState.WAITING -> colors.warn
        CapsuleState.OFFLINE -> colors.error
        CapsuleState.WORKING -> colors.accent
        CapsuleState.UNKNOWN -> colors.labelTertiary
        // Not drawn — `StatusCapsules.isDrawn` says so and the caller filters it — but
        // the branch has to exist, and tertiary ink is the honest choice for a state
        // that is deliberately not shown.
        CapsuleState.IDLE -> colors.labelTertiary
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(DshRadius.pill))
            .background(colors.bgLayer1)
            .then(if (onClick != null) Modifier.clickableNoRipple(onClick = onClick) else Modifier)
            .semantics { contentDescription = capsule.label }
            .padding(horizontal = DshSpacing.sm, vertical = DshSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A chase for the two states that are *moving* — a blocked agent that the host
        // is still holding, and a socket that is redialling. A still dot for the two
        // that are not: a running turn is motion but its indicator does not need to
        // animate twice (the composer's stop affordance already does), and "no session"
        // is a standing fact.
        when (capsule.state) {
            CapsuleState.WAITING, CapsuleState.OFFLINE ->
                StateDot(state = DotState.ONGOING, size = 6.dp)

            else -> Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(tint),
            )
        }
        Spacer(Modifier.width(DshSpacing.xs))
        Text(
            text = capsule.label,
            style = DshType.micro,
            color = tint,
            maxLines = 1,
            // `weight(1f)` — with `fill` left at its default `true` — is what makes the
            // label take the space that is left and then ellipsize inside it. A bare
            // `Text` measures at its intrinsic width, so a `widthIn` on the Row only
            // shrinks the *container* and the label keeps drawing past its edge.
            //
            // `fill = false` was tried first and is wrong here: it asks for the
            // intrinsic width, which is the very thing that overflows. The cost of
            // `fill = true` is that a short label ("Working") reserves the cap's width
            // and the pill reads slightly wide — accepted, because a pill that is a few
            // dp too wide is a cosmetic imprecision and a label running off the screen
            // is a bug.
            modifier = Modifier.weight(1f),
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The connection pill (Bug A).
 *
 * The socket being down was invisible in the conversation: sends looked like
 * they went nowhere and nothing said why. This is the smallest honest thing to
 * put up — the shell's own two words, a chase dot while the supervisor is
 * working, and the browser surface's pill idiom. It is only ever composed for a
 * non-null [uk.xa0.dsh.UiState.connectionLabel], so an ordinary OPEN socket
 * draws nothing.
 */
@Composable
private fun ConnectionPill(label: String, modifier: Modifier = Modifier) {
    val colors = DshTheme.colors
    Row(
        // The side inset lives here rather than at the three call sites: the pill is
        // centred, so without it a longer label ("Reconnecting to the host…") runs
        // edge to edge and reads as a clipped bar rather than a chip. Padding before
        // the clip insets the whole pill; after it, it would only shrink the fill.
        modifier
            .padding(horizontal = DshSpacing.xl)
            .clip(RoundedCornerShape(DshRadius.pill))
            .background(colors.tip)
            .padding(horizontal = DshSpacing.md, vertical = DshSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The chase is the honest glyph for both non-OPEN states: the supervisor
        // re-dials whether the mux is mid-handshake or waiting out a backoff.
        StateDot(state = DotState.ONGOING, size = 8.dp)
        Spacer(Modifier.width(DshSpacing.sm))
        Text(
            text = label,
            style = DshType.bodySmall,
            color = colors.labelSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Bug B: a non-blank session whose transcript has not arrived yet.
 *
 * The hero used to stand in for this, which is what made a real session with a
 * slow or unreachable host look like a brand-new one. The wording is the
 * drawer's existing "Loading…" (`SessionsDrawer`), and when the connection is
 * the reason it is named underneath rather than guessed at.
 */
@Composable
private fun LoadingSessionBody(connection: String?, modifier: Modifier = Modifier) {
    val colors = DshTheme.colors
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StateDot(state = DotState.ONGOING, size = 8.dp)
                Spacer(Modifier.width(DshSpacing.sm))
                Text(
                    text = "Loading…",
                    style = DshType.bodyMedium,
                    color = colors.labelTertiary,
                )
            }
            if (connection != null) {
                ConnectionPill(connection, Modifier.padding(top = DshSpacing.md))
            }
        }
    }
}

/** Abbreviate the home prefix the way the web client's path crumbs do. */
internal fun shortenPath(path: String): String =
    when {
        path == "/home/user" -> "~"
        path.startsWith("/home/user/") -> "~/" + path.removePrefix("/home/user/")
        else -> path
    }

/**
 * A tool argument path, shortened for a one-line row.
 *
 * Tool rows carry absolute paths like
 * `/home/user/var/work/dsh-android/app/src/main/java/uk/xa0/dsh/ui/ChatScreen.kt`,
 * which is most of a phone's line width. The home prefix collapses to `~`, and a
 * still-long path keeps its head and tail with `…` between, so what identifies
 * the file (its name, and the project it lives in) survives the elision.
 */
internal fun shortenToolPath(path: String, max: Int = 56): String {
    val home = shortenPath(path)
    if (home.length <= max) return home
    val parts = home.split('/').filter { it.isNotEmpty() }
    if (parts.size <= 3) return home
    val head = parts.take(2).joinToString("/", prefix = if (home.startsWith("/")) "/" else "")
    val tail = parts.takeLast(2).joinToString("/")
    val elided = "$head/…/$tail"
    return if (elided.length < home.length) elided else home
}

/**
 * Short resting label for the access-mode chip; the sheet shows the host's names.
 *
 * `workspace-write` is deliberately rendered as "Write" rather than "Workspace":
 * the latter reads as a *directory* selector, which is a different control.
 */
private fun shortAccessLabel(current: String, options: List<uk.xa0.dsh.PermissionOption>): String? {
    if (current.isEmpty()) return null
    return when (current) {
        "read-only" -> "Read-only"
        "workspace-write" -> "Write"
        "danger-full-access" -> "Full access"
        else -> options.firstOrNull { it.value == current }?.name ?: current
    }
}

/** Last path segment, for the hero's workspace chip. */
private fun shortenLastSegment(path: String): String =
    path.trimEnd('/').substringAfterLast('/').ifEmpty { path }

/**
 * The pre-flight notice for a branch, which the web client does not have.
 *
 * The web's branch button forks on the tap and opens the child as another session
 * in its own chrome; a phone shows one session at a time, so the new session
 * replaces the conversation on screen. Without this dialog the reader's own turn
 * appears to have been undone, or the app appears to have duplicated the
 * conversation by accident — and there is nothing to undo it with. So the
 * consequence is stated here, before the tap: `session/fork` creates a **new
 * session** holding the source's events up to and including the chosen turn, opens
 * it, and leaves this session exactly as it was.
 */
@Composable
private fun BranchFromMessageDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ConfirmDialogHost(
        show = true,
        visuals = ConfirmDialogVisuals(
            title = "Branch into a new conversation?",
            content = "This starts a new session holding this conversation up to the " +
                "end of this turn, and opens it straight away. This session is left " +
                "alone: it keeps every message and stays in your list.",
            confirm = "Branch",
            dismiss = "Cancel",
        ),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/**
 * The Me destination, and the only place a settings tab's sub-page state lives.
 *
 * ## Why the sub-page is state here and not a second pager
 *
 * A settings tab is a *stack*, not a sibling: you go in and come back out. A pager would
 * make the tabs swipeable as if they were places beside each other, and it would put the
 * reader's position in a structure that is not showing them where they are.
 *
 * ## What the tabs are (3.5b)
 *
 * Real pages. Each one composes the card the Settings sheet composes for that subject
 * (`ui/settings/SettingsCards.kt`), so the two surfaces cannot disagree about a row or
 * about the host's write rules. The tab is the section with room to breathe; the sheet is
 * the whole document.
 *
 * **About** is the one exception, and it is not a page: it opens the About *sheet*, which
 * is a document about the host — connection, certificate, session, sign-out — rather than
 * a setting. Drawing it inside this page's column would put a second Close button on a
 * screen that already has a back arrow, and would hide the sign-out action behind a tab
 * nobody associates with it.
 *
 * ## Back
 *
 * A sub-page is on top of the page, so Back has to leave it before it leaves Me. That
 * handler is registered here rather than in the ordered one above because this is where
 * the state is — and because it must win over "return to Chat", which is the press the
 * reader would otherwise get.
 */
@Composable
private fun MeDestination(
    username: String,
    balance: BalanceState,
    settings: MeSettingsState,
    settingsActions: MeSettingsActions,
    onOpenAbout: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var openTab by rememberSaveable { mutableStateOf<String?>(null) }
    val tab = openTab?.let { id -> ME_ENTRIES.firstOrNull { it.id == id } }
    if (tab != null) {
        BackHandler { openTab = null }
    }
    if (tab == null) {
        MeTab(
            username = username,
            // The figure, not the state machine: the page has no room for a retry, and
            // the balance sheet behind the stat pills is where a failed read is answered.
            balanceText = (balance as? BalanceState.Ready)?.let { state ->
                // The figure the stat pills already lead with: the whole total for the
                // currency the account is billed in, not a derived number. Null when the
                // reply carried no currency, and the header then draws only the name.
                state.account.primary?.let { info ->
                    "Balance · " + info.totalBalance + " " + info.currency
                }
            },
            entries = ME_ENTRIES,
            onOpen = { entry ->
                // About is the sheet, not a page: see this function's note.
                if (entry.id == "about") onOpenAbout() else openTab = entry.id
            },
        )
    } else {
        MeSubPage(
            title = tab.title,
            onBack = { openTab = null },
            // The sheet's own reach: Settings remains a complete document, and the tab is
            // one section of it. Nothing on the page needs the full sheet, but a reader
            // who wants the rest of the document should not have to leave Me to find it.
            trailing = {
                Text(
                    text = "All settings",
                    style = DshType.bodySmall,
                    color = DshTheme.colors.link,
                    modifier = Modifier
                        .clip(RoundedCornerShape(DshRadius.sm))
                        .clickableNoRipple(onClick = onOpenSettings)
                        .padding(DshSpacing.sm),
                )
            },
        ) {
            // The page takes the height the back bar leaves; the page owns its own scroll,
            // so the bar never scrolls away with the rows.
            MeSettingsPage(
                id = tab.id,
                state = settings,
                actions = settingsActions,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * The settings tabs, as rows on the Me page.
 *
 * Order is the reader's: the everyday settings first (how the app behaves and what it
 * looks like), then what powers it (model, agent), then what is rare or dangerous
 * (archived, plugins), then the facts (about). Each one's page is a PR of its own; the
 * list is here so the level is fixed before the contents are argued about.
 */
internal val ME_ENTRIES: List<MeEntry> = listOf(
    MeEntry("appearance", "Appearance", "Theme, and the seven appearances it resolves to.", Icons.Rounded.Palette),
    MeEntry("general", "General", "Enter behaviour, transcript folding, and the host's own settings.", Icons.Rounded.Tune),
    MeEntry("model", "Model", "Which route answers, and the provider order behind it.", Icons.Rounded.SmartToy),
    MeEntry("agent", "Agent", "Presets, and the permission level a session starts at.", Icons.Rounded.Badge),
    MeEntry("terminal", "Terminal", "The shell's font size and cells.", Icons.Rounded.Terminal),
    MeEntry("plugins", "Plugins", "The surfaces this client is allowed to draw.", Icons.Rounded.Extension),
    MeEntry("crashes", "Crash log", "What closed the app, kept so it can be shared.", Icons.Rounded.BugReport),
    MeEntry("archived", "Archived sessions", "Sessions put away, and how to bring one back.", Icons.Rounded.Archive),
    MeEntry("about", "About", "Connection, account, version and the way out.", Icons.Rounded.Info),
)
