package uk.xa0.dsh.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uk.xa0.dsh.AppPhase
import uk.xa0.dsh.FULL_ACCESS_PRESET
import uk.xa0.dsh.PendingAttachment
import uk.xa0.dsh.PermissionOption
import uk.xa0.dsh.TerminalPhase
import uk.xa0.dsh.TerminalUiState
import uk.xa0.dsh.UiState
import uk.xa0.dsh.data.ThemeMode
import uk.xa0.dsh.model.ChatEntry
import uk.xa0.dsh.model.DrawerGrouping
import uk.xa0.dsh.model.FileTouch
import uk.xa0.dsh.model.SessionFile
import uk.xa0.dsh.model.SessionFiles
import uk.xa0.dsh.model.PendingSessionTarget
import uk.xa0.dsh.model.SessionHeader
import uk.xa0.dsh.model.SessionItem
import uk.xa0.dsh.model.SessionTarget
import uk.xa0.dsh.model.StartupStep
import uk.xa0.dsh.model.WorkspaceListPhase
import uk.xa0.dsh.scroll.HarnessApplication
import uk.xa0.dsh.term.SeatOption
import uk.xa0.dsh.term.TerminalCellSize
import uk.xa0.dsh.term.TerminalEmulator
import uk.xa0.dsh.term.TerminalInfo
import uk.xa0.dsh.term.TerminalSeat
import uk.xa0.dsh.term.TerminalState
import uk.xa0.dsh.ui.AboutContent
import uk.xa0.dsh.ui.ChatActions
import uk.xa0.dsh.ui.ChatPanel
import uk.xa0.dsh.ui.ChatUiState
import uk.xa0.dsh.ui.glass.LocalGlassEnabled
import uk.xa0.dsh.ui.ME_ENTRIES
import uk.xa0.dsh.ui.MeSubPage
import uk.xa0.dsh.ui.MeTab
import uk.xa0.dsh.ui.MiuixProbe
import uk.xa0.dsh.ui.PlaceholderTab
import uk.xa0.dsh.ui.ModelTriggerState
import uk.xa0.dsh.ui.PermissionSheet
import uk.xa0.dsh.ui.SessionsDrawer
import uk.xa0.dsh.ui.StartupScreen
import uk.xa0.dsh.ui.TerminalPanel
import uk.xa0.dsh.ui.nav.BottomBarItem
import uk.xa0.dsh.ui.components.Composer
import uk.xa0.dsh.ui.components.FilesPanel
import uk.xa0.dsh.ui.nav.FloatingBottomBar
import uk.xa0.dsh.ui.theme.DshSpacing
import uk.xa0.dsh.ui.theme.DshTheme

/**
 * The visual baseline for the screens this fork is about to re-skin.
 *
 * Phase 2 rewrites the shell — bottom bar, top bar, drawer, settings — and
 * `ChatScreen.kt` alone is 3 200 lines. Without a picture of what these screens
 * look like *now*, a re-skin regression is something a person notices on a phone
 * days later, or does not notice at all. These four captures are that picture:
 * a diff against them is the cheapest evidence that a change to a layout file
 * did not move something it should not have.
 *
 * ## Why so few, and why these
 *
 * Only screens that render from plain arguments, with no view model, no network
 * and no clock, are captured. A screen that needs a `DshViewModel` would drag the
 * whole host connection into a screenshot test and produce a baseline that
 * changes whenever the connection does — that is a flaky test wearing a
 * screenshot's clothes.
 *
 * The conversation screen used to be in that category, and 2.5a is what took it
 * out: `ChatPanel` takes data and lambdas instead of a view model, so the two
 * `chat-*` captures at the end of this file can drive it from a fixture. They are
 * the reason that refactor was worth doing at all — everything else here pins
 * chrome around the screen readers actually live in.
 *
 * ## What a passing diff does and does not mean
 *
 * It means the pixels are what they were on the machine that recorded them.
 * Robolectric lays text out with stub metrics, so every string here is one line
 * of one height regardless of its length — a capture can show that a label is
 * present, positioned and coloured, but not that it fits, wraps, or is legible.
 * The baselines are recorded by CI (`.github/workflows/screenshots.yml`) because
 * this repository's development box is aarch64 and cannot build Android at all;
 * the locale is pinned in the qualifiers below for the same reason it will matter
 * when i18n lands: an unpinned locale makes a baseline drift with the machine,
 * not with the code.
 */
@RunWith(RobolectricTestRunner::class)
// Native graphics: Roborazzi needs a real canvas, and `NATIVE` is the mode that
// draws with the platform's own Skia rather than Robolectric's legacy shadow.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A phone-sized surface with the locale fixed. The size is the Pixel-class
// 411x891 dp window the other UI tests use; the locale is pinned so a translated
// build cannot silently change what "the baseline" means.
@Config(
    sdk = [34],
    application = HarnessApplication::class,
    qualifiers = "en-rUS-w411dp-h891dp",
)
class ScreenBaselineTest {

    @get:Rule
    val rule = createComposeRule()

    /**
     * A fixed wall clock for the fixtures that draw one.
     *
     * The transcript prints a time under each user message, so a fixture left at
     * `time = 1_000L` renders "1971-1-1 00:00" — which is really "the epoch plus a
     * second", and reads as a bug in the capture rather than in the fixture.
     * 2026-10-31T09:00:00Z is arbitrary but *fixed*: a baseline may not depend on when
     * it was recorded, the same rule the locale pin in `@Config` follows.
     */
    private val FIXTURE_CLOCK_MS = 1_793_446_800_000L

    private fun capture(name: String, content: @Composable () -> Unit) {
        rule.setContent {
            // Light, explicitly. `DshTheme()` defaults to `ThemeMode.SYSTEM`, which
            // reads the device's night setting — so a baseline recorded on a light
            // machine and compared on a dark one would fail for a reason that has
            // nothing to do with the code. The dark palette gets its own baselines
            // when the re-skin starts touching colour.
            DshTheme(themeMode = ThemeMode.LIGHT) {
                // Glass off. Robolectric answers the capability test honestly — it is
                // an SDK compare and the shadow platform ships the classes — but it has
                // no GPU pipeline, and recording a backdrop there throws *during
                // composition*, taking the screen down with it rather than merely
                // drawing flat. So a capture pins the tier every device has, and the
                // liquid tier's verification is a device at API 33+ (see the PR that
                // wired it). The switch lives on the surface, mirroring
                // `LocalAnimatedDots` — which exists for the same class of problem.
                CompositionLocalProvider(LocalGlassEnabled provides false) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(DshTheme.colors.bgBase),
                ) {
                    content()
                }
                }
            }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    /**
     * About, which is the one page whose every row is a plain argument.
     *
     * It carries the connection facts, the actions and the certificate panel, so
     * it is also the screen most likely to be re-skinned by accident: phase 2
     * touches the settings sheet and the about page together.
     */
    @Test
    fun about() {
        capture("about") {
            AboutContent(
                baseUrl = "https://host.example/",
                username = "tony",
                socketConnected = true,
                onSignOut = {},
                onDismiss = {},
                sessionTitle = "Re-skin the shell",
                sessionCwd = "/home/user/var/work/dsh-android",
                sessionPermission = "default",
                activeUrl = "https://host.example/",
                activeRouteLan = false,
                onRestartConnection = {},
                onForceQuit = {},
            )
        }
    }

    /**
     * The start-up screen, at the step that is on screen longest.
     *
     * `CHECKING_SIGN_IN` is the one a cold start actually sits on when the stored
     * session has to be re-validated, so it is the frame a user sees most often.
     */
    @Test
    fun startup() {
        capture("startup") {
            StartupScreen(step = StartupStep.CHECKING_SIGN_IN)
        }
    }

    /**
     * The drawer, flat, with three sessions and one of them running.
     *
     * Flat rather than grouped because grouping interposes the Workspace section
     * headers, which are host state: a grouped baseline would change when the
     * host's registry changed rather than when the drawer's layout did.
     */
    @Test
    fun drawer() {
        val sessions = listOf(
            SessionItem(
                id = "s1",
                title = "Re-skin the shell",
                cwd = "/home/user/var/work/dsh-android",
                updatedAt = 1_000L,
                running = true,
                isSubagent = false,
            ),
            SessionItem(
                id = "s2",
                title = "Widget age cost",
                cwd = "/home/user/var/work/dsh-android",
                updatedAt = 2_000L,
                running = false,
                isSubagent = false,
            ),
            SessionItem(
                id = "s3",
                title = "Sidebar workspaces",
                cwd = "/home/user/var/work/other",
                updatedAt = 3_000L,
                running = false,
                isSubagent = false,
            ),
        )

        capture("drawer") {
            SessionsDrawer(
                sessions = sessions,
                workspaces = emptyList(),
                workspacesPhase = WorkspaceListPhase.Ready,
                archivedSessionIds = emptySet(),
                completedSessionIds = emptySet(),
                currentId = "s1",
                drawerOpen = true,
                loading = false,
                onSelect = {},
                onNew = {},
                onRefresh = {},
                onSettings = {},
                onAbout = {},
                grouping = DrawerGrouping.FLAT,
                orderByUpdated = true,
                showArchived = false,
                collapsedSections = emptySet(),
                onGrouping = {},
                onOrderByUpdated = {},
                onShowArchived = {},
                onCollapsedSections = {},
            )
        }
    }

    /**
     * The composer, which is the one surface every session ends at.
     *
     * Captured with text in it and an attachment attached, because those are the two
     * states that change the card's geometry — the send control swaps for a stop
     * button while running, and an attachment adds a chip row. An empty composer is
     * the state that can be inferred from the others; a populated one cannot.
     *
     * The horizontal inset is `DshSpacing.xl`, matching where the real screen puts
     * it. Without it the card measures the full 411dp and the baseline would be 32dp
     * wider than anything a reader ever sees.
     *
     * The capture is only as tall as the card: below it is the harness's own
     * background, not a screen. That is stated rather than padded away — a fake
     * conversation behind it would be a fixture pretending to be a transcript.
     */
    @Test
    fun composer() {
        capture("composer") {
            val state = remember { TextFieldState("Re-skin the shell, one screen at a time.") }
            Column(Modifier.padding(horizontal = DshSpacing.xl, vertical = DshSpacing.md)) {
                Composer(
                    state = state,
                    onSend = {},
                    onStop = {},
                    running = false,
                    modelTrigger = ModelTriggerState(
                        label = "DeepSeek V4 · High",
                        marker = null,
                        description = null,
                        alert = false,
                    ),
                    onModelClick = {},
                    permissionLabel = "Full access",
                    onPermissionClick = {},
                    planActive = false,
                    onExitPlan = {},
                    contextPercent = 12,
                    contextTokens = 12_000,
                    contextWindow = 100_000,
                    contextBreakdown = null,
                    // One attachment, because the chip row is the part of this card
                    // that only exists in a state this test would otherwise never
                    // reach — and it is the reason the card grows a row.
                    attachments = listOf(
                        PendingAttachment(
                            id = "att-1",
                            name = "screenshot.png",
                            bytes = 184_320,
                            mediaType = "image/png",
                        ),
                    ),
                    onToggleCommands = {},
                    onRemoveAttachment = {},
                )
            }
        }
    }

    /**
     * The Files panel, on the state a session actually reaches: a file it touched,
     * open in the preview.
     *
     * The list and the preview are exclusive on a phone (`maxWidth < 720.dp`), so one
     * capture can only show one of them. The preview is the half worth protecting —
     * the list is rows, the preview is where markdown, syntax colour and the
     * truncated badge meet. The 720dp two-pane reading gets no baseline here, and
     * this comment is the record of that rather than a silent gap.
     */
    @Test
    fun files() {
        val path = "/home/user/var/work/dsh-android/out/REPORT.md"
        capture("files") {
            FilesPanel(
                files = listOf(
                    SessionFile(
                        path = path,
                        touches = listOf(FileTouch.PRESENTED),
                        reads = 0,
                        writes = 0,
                        edits = 0,
                        presents = 1,
                        firstSeq = 1,
                        lastSeq = 1,
                    ),
                ),
                root = "/home/user/var/work/dsh-android",
                cached = mapOf(
                    path to SessionFiles.previewOfText(
                        path,
                        "# Findings\n\n- the drawer re-skins first\n- then the composer\n",
                    ),
                ),
                initialPath = path,
            )
        }
    }

    /**
     * The access-mode sheet — the one dialog that is both wholly argument-driven and
     * short enough to capture whole.
     *
     * Chosen over the other five because they are either lazy (`ModelSheet`,
     * `AgentPresetSheet`), so a capture shows only the viewport, or take a host
     * document. This one is a `Column` of three rows, and it carries the colour that
     * matters most: "Full access" is drawn in the warning tint and is the only option
     * that opens a second confirmation.
     *
     * A `ModalBottomSheet` is its own window, so this is the first capture here that
     * is not simply the activity's content root. It is committed with the fixture
     * rather than behind a separate mechanism: if `onRoot()` cannot see the sheet,
     * the record fails loudly on the first run rather than silently capturing an
     * empty window.
     */
    @Test
    fun permission() {
        capture("permission") {
            PermissionSheet(
                options = listOf(
                    PermissionOption("read-only", "Read-only", "Read files, run nothing."),
                    PermissionOption("default", "Default", "Ask before each step."),
                    PermissionOption(FULL_ACCESS_PRESET, "Full access", "Reduce confirmation steps."),
                ),
                current = "default",
                onSelect = {},
                onDismiss = {},
            )
        }
    }

    /**
     * The Shell panel, running, with a screen of real output in the grid.
     *
     * Captured against `TerminalPanel` rather than `TerminalScreen` because the screen
     * collects a `StateFlow` and opens/leaves the terminal over RPC — a baseline of
     * that would move with the connection instead of with the code. The panel is that
     * same tree with the view model taken out, so what these pixels pin is the
     * drawing: the bar's seat chips, the phase word, the cell-size caps, and every
     * glyph the emulator lays out.
     *
     * 24 rows, not a short grid: `visibleRows` is `minOf(rows, what fits)`, so an
     * emulator with fewer rows than the viewport leaves the rest blank and the
     * capture would mostly be a picture of the background.
     *
     * What this cannot say: Robolectric measures text with stub metrics, so grid
     * column alignment here is not proof that a cell is the right width on a device.
     * It can say the same cells land in the same places as last time.
     */
    @Test
    fun terminal() {
        capture("terminal") {
            val emulator = remember {
                TerminalEmulator(columns = 80, rows = 24).apply {
                    append(
                        "\$ ls -la\r\n" +
                            "total 128\r\n" +
                            "drwxr-xr-x  12 user user 4096 Oct 31 22:04 .\r\n" +
                            "drwxr-xr-x   3 user user 4096 Oct 30 09:12 ..\r\n" +
                            "-rw-r--r--   1 user user  512 Oct 31 21:58 README.md\r\n" +
                            "drwxr-xr-x   5 user user 4096 Oct 31 20:31 app\r\n" +
                            "drwxr-xr-x   5 user user 4096 Oct 31 20:31 core\r\n" +
                            "drwxr-xr-x   3 user user 4096 Oct 31 20:31 design\r\n" +
                            "drwxr-xr-x   2 user user 4096 Oct 31 20:31 docs\r\n" +
                            "\r\n\$ ",
                    )
                }
            }
            TerminalPanel(
                state = TerminalUiState(
                    sessionId = "s1",
                    seat = TerminalSeat.HOST_SHELL,
                    seats = listOf(
                        SeatOption(TerminalSeat.HOST_SHELL, "Host shell", "s1"),
                        SeatOption(TerminalSeat.THIS_SESSION, "This session", "s2"),
                    ),
                    phase = TerminalPhase.CONNECTED,
                    active = TerminalInfo(
                        id = "t1",
                        title = "bash",
                        shell = "/bin/bash",
                        cwd = "/home/user/var/work/dsh-android",
                        cols = 80,
                        rows = 24,
                        state = TerminalState.RUNNING,
                    ),
                    writable = true,
                ),
                emulator = emulator,
                screenTitle = "",
                cellSize = TerminalCellSize.of(TerminalCellSize.DEFAULT_FONT_SP),
                modifier = Modifier.fillMaxSize(),
                onCellSize = {},
                onNew = {},
                onClose = {},
                onRetry = {},
                onSelect = {},
                onSeat = {},
                onWrite = {},
                onResize = { _, _ -> },
            )
        }
    }

    /**
     * **The 2.3 gate.** One miuix preference row drawn inside this app's theme.
     *
     * This capture exists to answer one question — `docs/PHASE-2-PLAN.md` §1.4 — and
     * it is the only evidence that can. miuix has been on the classpath since 1.4
     * with zero call sites, so nothing had proved that `MiuixTheme` composes inside
     * `DshTheme` without throwing, that a real miuix component draws at all under the
     * JVM harness, or that its colours can be driven from the app's palette.
     *
     * A **crash** here is a clean "no" and the answer 2.4 needs; a **picture** is a
     * "yes". Either outcome is the deliverable, which is why this is a spike that can
     * be thrown away whole.
     *
     * If the answer is yes, this test and `MiuixProbe` are deleted by the PR that
     * starts actually using miuix: the probe is reachable from nowhere else.
     */
    @Test
    fun miuixProbe() {
        capture("miuix-probe") {
            MiuixProbe()
        }
    }

    /**
     * The floating bottom bar, now the vendored glass one (3.2).
     *
     * Captured on its own because the property it must hold is a *layout* property that
     * no unit test can see: **the item count is fixed**. The two captures below are
     * deliberately all of it — a bar whose item appeared or disappeared for one session
     * would show up here as two different widths for the same bar.
     *
     * The four destinations are Chat / Files / Plugins / Me (3.3). There is no
     * conditional one: the llama.cpp router panel left the destination list in 2.4,
     * because a conditional item is exactly what makes a bar's width change under the
     * reader's thumb. See `Destination` in `ui/ShellTabs.kt`.
     *
     * The second capture moves the highlight to a seat that is *not* the first, because
     * a single capture would pin the unselected seats and leave the active tint — the
     * whole affordance — unverified.
     */
    @Test
    fun floatingBottomBar() {
        capture("bottom-bar") {
            FloatingBottomBar(
                items = listOf(
                    BottomBarItem("CHAT", "Chat", Icons.Rounded.ChatBubble),
                    BottomBarItem("FILES", "Files", Icons.Rounded.Folder),
                    BottomBarItem("PLUGINS", "Plugins", Icons.Rounded.Extension),
                    BottomBarItem("ME", "Me", Icons.Rounded.Person),
                ),
                selectedKey = "CHAT",
                onSelect = {},
            )
        }
    }

    /**
     * The bar with a different destination selected, so the *selected* treatment is
     * captured too. A single capture would pin the unselected seats and leave the
     * active tint unverified — and the active tint is the whole affordance.
     */
    @Test
    fun floatingBottomBarFilesSelected() {
        capture("bottom-bar-files") {
            FloatingBottomBar(
                items = listOf(
                    BottomBarItem("CHAT", "Chat", Icons.Rounded.ChatBubble),
                    BottomBarItem("FILES", "Files", Icons.Rounded.Folder),
                    BottomBarItem("PLUGINS", "Plugins", Icons.Rounded.Extension),
                    BottomBarItem("ME", "Me", Icons.Rounded.Person),
                ),
                selectedKey = "FILES",
                onSelect = {},
            )
        }
    }

    /**
     * The drawer's empty state, which is a different screen from a drawer with no
     * rows: the copy that says the list has not arrived yet must not read like a
     * list that is genuinely empty.
     */
    @Test
    fun drawerEmpty() {
        capture("drawer-empty") {
            SessionsDrawer(
                sessions = emptyList(),
                workspaces = emptyList(),
                workspacesPhase = WorkspaceListPhase.Ready,
                archivedSessionIds = emptySet(),
                completedSessionIds = emptySet(),
                currentId = null,
                drawerOpen = true,
                loading = false,
                onSelect = {},
                onNew = {},
                onRefresh = {},
                onSettings = {},
                onAbout = {},
                grouping = DrawerGrouping.FLAT,
                orderByUpdated = true,
                showArchived = false,
                collapsedSections = emptySet(),
                onGrouping = {},
                onOrderByUpdated = {},
                onShowArchived = {},
                onCollapsedSections = {},
            )
        }
    }

    /**
     * The conversation screen's hero — the state a reader meets on a new session.
     *
     * This is the first baseline of the screen readers spend the most time on, and it
     * only exists because 2.5a took the view model out of it: [ChatPanel] takes data and
     * lambdas, so a fixture can hold it still. Before that, the only protection this
     * screen had was the functional suite — which asserts semantics, not layout.
     *
     * The bar is drawn only when the screen is *not* the hero (a session with nothing in
     * it has no Trajectory to show and no Shell to open), so its absence here is the
     * expected picture rather than a missing element. The bar has its own two captures.
     */
    @Test
    fun chatHero() {
        capture("chat-hero") {
            ChatPanel(
                state = ChatUiState(
                    ui = UiState(
                        phase = AppPhase.READY,
                        pendingSession = PendingSessionTarget(
                            target = SessionTarget.Directory("/home/user/var/work/dsh-android"),
                        ),
                    ),
                ),
                actions = ChatActions(),
                terminal = { Modifier.fillMaxSize() },
            )
        }
    }

    /**
     * The conversation screen with a transcript: two turns, a tool call, the composer
     * and the bar.
     *
     * The rows are the plainest ones available on purpose — a user message, an assistant
     * message, a tool call that answered, the next user message. Anything richer
     * (markdown, a diff, an image) would pin *that row's* rendering, which has its own
     * tests; what this capture is for is the **frame**: the header, the transcript's
     * insets and spacing, the composer's seat, the pills and the floating bar, and the
     * relationship between them.
     *
     * `elapsedMs` stays null so the running-turn status row is absent: a capture with a
     * live clock in it would change every time it ran.
     */
    @Test
    fun chatTranscript() {
        capture("chat-transcript") {
            ChatPanel(
                state = ChatUiState(
                    ui = UiState(
                        phase = AppPhase.READY,
                        currentSessionId = "s1",
                        sessions = listOf(
                            SessionItem(
                                id = "s1",
                                title = "Re-skin the shell",
                                cwd = "/home/user/var/work/dsh-android",
                                updatedAt = 1_000L,
                                running = false,
                                isSubagent = false,
                            ),
                        ),
                    ),
                    entries = listOf(
                        ChatEntry.UserMessage(
                            seq = 1,
                            id = "m1",
                            text = "Re-skin the shell, one screen at a time.",
                            time = FIXTURE_CLOCK_MS,
                            fromPlugin = false,
                            summary = null,
                        ),
                        ChatEntry.AssistantMessage(
                            seq = 2,
                            turn = 1,
                            step = 1,
                            reasoning = "",
                            text = "Starting with the frame: the bar, the header, the composer.",
                            time = FIXTURE_CLOCK_MS + 61_000L,
                        ),
                        ChatEntry.ToolCall(
                            seq = 3,
                            callId = "c1",
                            name = "read",
                            arguments = "{\"path\":\"/home/user/var/work/dsh-android/docs/PHASE-2-PLAN.md\"}",
                            result = "ok",
                            isError = false,
                            time = FIXTURE_CLOCK_MS + 74_000L,
                            turn = 1,
                        ),
                        ChatEntry.UserMessage(
                            seq = 4,
                            id = "m2",
                            text = "Good. Then the drawer.",
                            time = FIXTURE_CLOCK_MS + 132_000L,
                            fromPlugin = false,
                            summary = null,
                        ),
                    ),
                    header = SessionHeader(
                        id = "s1",
                        cwd = "/home/user/var/work/dsh-android",
                        createdAt = 0L,
                        agentPreset = null,
                        parentSession = null,
                    ),
                ),
                actions = ChatActions(),
                terminal = { Modifier.fillMaxSize() },
            )
        }
    }
    /**
     * The Me page: identity on top, the settings tabs under it (3.3).
     *
     * A page of its own rather than a row inside a screen capture, because the two
     * things worth pinning are its *shape* — a header that says whose account this is,
     * then a list of tabs — and the fact that the tabs are reached by opening a page
     * rather than by toggling a value.
     *
     * The balance is given as a string, not as a `BalanceState`: the page takes the
     * figure it should draw, so a capture cannot accidentally depend on which state the
     * read happened to be in when the baseline was recorded.
     */
    @Test
    fun meTab() {
        capture("me-tab") {
            MeTab(
                username = "tony",
                balanceText = "Balance · 12.34 CNY",
                entries = ME_ENTRIES,
                onOpen = {},
            )
        }
    }

    /**
     * A settings tab's own page: the back bar and, for now, a placeholder body (3.3).
     *
     * Captured with [PlaceholderTab] rather than with a real tab's contents because the
     * contents are the work of their own PRs. What this pins is the *frame* every tab
     * will be drawn in — and, more usefully, it makes the placeholder visible so nobody
     * ships believing the tabs are done.
     */
    @Test
    fun meSubPage() {
        capture("me-subpage") {
            MeSubPage(title = "Appearance", onBack = {}) {
                PlaceholderTab(
                    title = "Appearance",
                    summary = "Theme, and the seven appearances it resolves to.",
                    icon = Icons.Rounded.Palette,
                )
            }
        }
    }

    /**
     * The Plugins destination, which is a placeholder on purpose (3.3).
     *
     * Captured for the same reason the MiuiX probe was: an empty page is a claim about
     * the product, and a claim nobody can see is one that rots. When the plugin surfaces
     * land, this capture is what shows what they replaced.
     */
    @Test
    fun pluginsTab() {
        capture("plugins-tab") {
            PlaceholderTab(
                title = "Plugins",
                summary = "Plugin surfaces will live here. The panel and block protocol " +
                    "already exist; this page is where they land.",
                icon = Icons.Rounded.Extension,
            )
        }
    }
}
