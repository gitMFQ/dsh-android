package uk.xa0.dsh.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uk.xa0.dsh.model.ChatEntry
import uk.xa0.dsh.model.ExplorerState
import uk.xa0.dsh.model.SessionFiles
import uk.xa0.dsh.model.WorkspaceEntry
import uk.xa0.dsh.model.WorkspaceEntryKind
import uk.xa0.dsh.model.WorkspaceListing
import uk.xa0.dsh.model.WorkspaceTreeState
import uk.xa0.dsh.scroll.HarnessApplication
import uk.xa0.dsh.ui.components.ExplorerWiring
import uk.xa0.dsh.ui.components.FilesPanel
import uk.xa0.dsh.ui.theme.DshTheme

/**
 * The Files panel's explorer half: the workspace tree, with the session's own
 * touches drawn on it, and the derived list still one tap away.
 *
 * The user's complaint is the subject: a panel derived only from the session's
 * activity held a handful of rows on a real session and read as broken rather
 * than scoped. So these tests open the panel on the listing — a directory, a file
 * the session read, a file it never touched, and a hidden file — and check which
 * rows exist, which of them carry a mark, and that the session list still has its
 * own mode.
 *
 * ## What these can and cannot show
 *
 * They show which nodes the panel composed and what a tap asked for. They cannot
 * show that the host lists anything: `workspaceFiles/list` is an RPC and this
 * harness has no host, so the listing here is a literal. They cannot show that the
 * merge of two spellings onto one row *reads* well either — as everywhere in this
 * suite the text metrics are stubs, so nothing here says a row wraps or fits.
 * That a mark is absent from an untouched row is asserted as a node count, which
 * is the part that can be measured.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = HarnessApplication::class)
class FilesPanelExplorerTest {

    @get:Rule
    val rule = createComposeRule()

    private val cwd = "/w"

    private val listing = WorkspaceListing(
        path = "",
        entries = listOf(
            WorkspaceEntry("out", WorkspaceEntryKind.DIRECTORY),
            WorkspaceEntry("REPORT.md", WorkspaceEntryKind.FILE, size = 2048L),
            WorkspaceEntry("notes.txt", WorkspaceEntryKind.FILE, size = 10L),
            WorkspaceEntry(".secret", WorkspaceEntryKind.FILE),
        ),
        truncated = false,
    )

    /** One successful read of `/w/REPORT.md`, and nothing else. */
    private val entries = listOf(
        ChatEntry.ToolCall(
            seq = 1,
            callId = "call-1",
            name = "read",
            arguments = """{"file_path":"$cwd/REPORT.md"}""",
            result = "ok",
            isError = false,
            time = 0L,
            turn = 1,
        ),
    )

    /**
     * The panel under the app's own theme, in a box the size of a phone (400dp is
     * below the 720dp two-pane cut, so the list and the preview swap rather than
     * sitting side by side), with the listing already answered.
     *
     * A plain function around the rule's own content lambda, as
     * `FilesPanelTest.show` is. A `@Composable` member helper called inside
     * `DshTheme { }` is the shape that reported `receiver type mismatch` at
     * `DshTheme` and `@Composable invocations can only happen from the context of
     * a @Composable function` at the helper (`cfc78ae`, and the handoff's trap on
     * stale incremental state that imitates it), because the `DshTheme` object won
     * over the composable. Theme and real composables go directly in the content
     * lambda instead.
     */
    private fun show(onList: (String) -> Unit = {}) {
        rule.setContent {
            DshTheme {
                Box(Modifier.size(width = 400.dp, height = 800.dp)) {
                    FilesPanel(
                        files = SessionFiles.derive(entries),
                        root = cwd,
                        explorer = ExplorerWiring(
                            state = ExplorerState.Ready(listing),
                            onList = onList,
                            onReload = {},
                        ),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    /** The reported symptom: the panel is the workspace, not the session's dozen rows. */
    @Test
    fun `the panel opens on the listing and keeps the session list one tap away`() {
        show()

        rule.onNodeWithText("Browse").assertExists()
        rule.onNodeWithText("This session").assertExists()
        // A directory, and a file the session never touched: both are rows.
        rule.onNodeWithText("out").assertExists()
        rule.onNodeWithText("notes.txt").assertExists()
        // And the convention is stated rather than left to be inferred.
        rule.onNodeWithText("A file with no mark has not been touched by this session.")
            .assertExists()

        rule.onNodeWithText("This session").performClick()
        rule.onNodeWithText("REPORT.md").assertExists()
        // The session's own list is *not* the workspace's: the untouched file has
        // no row there, which is what makes the two lists different things.
        rule.onNodeWithText("notes.txt").assertDoesNotExist()
    }

    /**
     * A mark means the session read, wrote, edited or declared that exact path;
     * a row without one is a file it has not touched. The count is the assertion:
     * two files are on screen and exactly one carries the mark.
     */
    @Test
    fun `a mark is drawn only on the file the session touched`() {
        show()

        rule.onNodeWithText("REPORT.md").assertExists()
        rule.onNodeWithText("notes.txt").assertExists()
        rule.onAllNodesWithText("Read").assertCountEquals(1)
    }

    @Test
    fun `a hidden file is behind the toggle`() {
        show()

        rule.onNodeWithText(".secret").assertDoesNotExist()

        rule.onNodeWithText("Show hidden files").performClick()

        rule.onNodeWithText(".secret").assertExists()
    }

    /** A directory row descends by asking for the child's workspace path. */
    @Test
    fun `descending asks for the child's workspace path`() {
        val asked = mutableListOf<String>()
        show(onList = { asked += it })

        rule.onNodeWithText("out").performClick()

        assertEquals(listOf("out"), asked)
    }

    /**
     * The tree, with one directory open and its level arrived.
     *
     * `out` is open and its level has arrived. The tap assertion is the part that
     * matters: in tree mode a directory row must *expand*, not navigate, or the
     * reader loses the level they were reading to see a child of it.
     */
    private fun showTree(onToggle: (String) -> Unit = {}) {
        val tree = WorkspaceTreeState(
            levels = mapOf(
                "" to listing,
                "out" to WorkspaceListing(
                    path = "out",
                    entries = listOf(
                        // A name the root level does not carry: `REPORT.md` is already a
                        // root row in this fixture, and two rows with one text would make
                        // `onNodeWithText` ambiguous — the test would fail for a reason
                        // that has nothing to do with the tree.
                        WorkspaceEntry("inner.md", WorkspaceEntryKind.FILE, size = 512L),
                    ),
                    truncated = false,
                ),
            ),
            open = setOf("out"),
            root = cwd,
        )
        rule.setContent {
            DshTheme {
                Box(Modifier.size(width = 400.dp, height = 800.dp)) {
                    FilesPanel(
                        files = SessionFiles.derive(entries),
                        root = cwd,
                        explorer = ExplorerWiring(
                            state = ExplorerState.Ready(listing),
                            onList = {},
                            onReload = {},
                            tree = tree,
                            onToggle = onToggle,
                        ),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `an open directory draws its children under it`() {
        showTree()

        rule.onNodeWithText("out").assertExists()
        // inner.md is a child of out; it is on screen only because that level is open.
        rule.onNodeWithText("inner.md").assertExists()
        // And the root level's other rows are still there, below the subtree.
        rule.onNodeWithText("notes.txt").assertExists()
    }

    @Test
    fun `tapping a directory row asks to toggle it, not to descend`() {
        val toggled = mutableListOf<String>()
        showTree(onToggle = { toggled += it })

        rule.onNodeWithText("out").performClick()

        assertEquals(listOf("out"), toggled)
    }

    @Test
    fun `an open directory offers a collapse affordance`() {
        showTree()

        // Open draws "Collapse"; every closed directory draws "Expand". One of each
        // is on screen here only if the tree is drawing state rather than a level.
        rule.onAllNodesWithContentDescription("Collapse").assertCountEquals(1)
    }
}

