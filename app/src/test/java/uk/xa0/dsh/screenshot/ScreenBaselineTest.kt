package uk.xa0.dsh.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
import uk.xa0.dsh.data.ThemeMode
import uk.xa0.dsh.model.DrawerGrouping
import uk.xa0.dsh.model.SessionItem
import uk.xa0.dsh.model.StartupStep
import uk.xa0.dsh.model.WorkspaceListPhase
import uk.xa0.dsh.scroll.HarnessApplication
import uk.xa0.dsh.ui.AboutContent
import uk.xa0.dsh.ui.SessionsDrawer
import uk.xa0.dsh.ui.StartupScreen
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
 * screenshot's clothes. `ChatScreen` is therefore deliberately absent; it is the
 * next baseline to add, once it can be driven from a fixture.
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

    private fun capture(name: String, content: @Composable () -> Unit) {
        rule.setContent {
            // Light, explicitly. `DshTheme()` defaults to `ThemeMode.SYSTEM`, which
            // reads the device's night setting — so a baseline recorded on a light
            // machine and compared on a dark one would fail for a reason that has
            // nothing to do with the code. The dark palette gets its own baselines
            // when the re-skin starts touching colour.
            DshTheme(themeMode = ThemeMode.LIGHT) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(DshTheme.colors.bgBase),
                ) {
                    content()
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
}
