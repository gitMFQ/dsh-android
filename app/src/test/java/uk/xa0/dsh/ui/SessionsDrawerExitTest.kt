package uk.xa0.dsh.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uk.xa0.dsh.model.DrawerGrouping
import uk.xa0.dsh.model.SessionItem
import uk.xa0.dsh.model.WorkspaceListPhase
import uk.xa0.dsh.scroll.HarnessApplication
import uk.xa0.dsh.ui.theme.DshTheme

/**
 * The drawer's last row: the only control in the app that closes the screen.
 *
 * Back no longer asks and no longer closes — it leaves the task in the background —
 * so closing is a decision the reader has to make somewhere visible. This pins that
 * there is exactly one place for it, that a tap reaches the caller, and that a
 * surface without an Activity to close (a fixture, a test) gets no dead row.
 *
 * It cannot show the confirmation itself: the root owns that dialog, and this test
 * composes the drawer alone. What it proves is the request being handed up, which is
 * the whole of the drawer's side of the contract.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = HarnessApplication::class, qualifiers = "w411dp-h891dp")
class SessionsDrawerExitTest {

    @get:Rule
    val rule = createComposeRule()

    private fun session(id: String, title: String) = SessionItem(
        id = id,
        title = title,
        cwd = "/home/user/var/work/dsh-android",
        updatedAt = 1_000L,
        running = false,
        isSubagent = false,
    )

    @Composable
    private fun drawer(
        onExit: (() -> Unit)?,
        onAutomations: (() -> Unit)? = null,
    ) {
        SessionsDrawer(
            sessions = listOf(session("s1", "Widget age cost")),
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
            onAutomations = onAutomations,
            onExit = onExit,
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

    @Test
    fun `the exit row is labelled Close DSH and a tap asks to close`() {
        var exits = 0
        rule.setContent {
            DshTheme { drawer(onExit = { exits++ }) }
        }
        rule.waitForIdle()

        rule.onNodeWithText("Close DSH").assertExists()
        rule.onNodeWithTag("drawer-exit").performClick()
        rule.waitForIdle()
        assertEquals(1, exits)
    }

    @Test
    fun `no exit row is drawn when the caller has no Activity to close`() {
        rule.setContent {
            DshTheme { drawer(onExit = null) }
        }
        rule.waitForIdle()

        rule.onNodeWithTag("drawer-exit").assertDoesNotExist()
        rule.onNodeWithText("Close DSH").assertDoesNotExist()
    }

    @Test
    fun `the exit row sits beside Automation tasks in the fixed footer`() {
        rule.setContent {
            DshTheme {
                drawer(
                    onExit = {},
                    onAutomations = {},
                )
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag("drawer-automation-tasks").assertExists()
        rule.onNodeWithTag("drawer-exit").assertExists()
    }
}
