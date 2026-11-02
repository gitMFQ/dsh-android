package uk.xa0.dsh.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uk.xa0.dsh.AgentPresetOption
import uk.xa0.dsh.ModelOption
import uk.xa0.dsh.PermissionOption
import uk.xa0.dsh.data.BusyEnter
import uk.xa0.dsh.data.ThemeMode
import uk.xa0.dsh.model.GeneralSettings
import uk.xa0.dsh.model.HostSettings
import uk.xa0.dsh.model.SessionItem
import uk.xa0.dsh.model.SettingsChoice
import uk.xa0.dsh.model.SettingsField
import uk.xa0.dsh.model.WorkspaceItem
import uk.xa0.dsh.scroll.HarnessApplication
import uk.xa0.dsh.ui.theme.DshTheme

/**
 * The Me page's settings tabs, as pages.
 *
 * Phase 3.5b replaced eight placeholders with eight real pages, and the risk in that move
 * is not visual — the screenshots pin the shape. The risk is *behavioural*, and it is the
 * part this repository has been bitten by before: a row that looks like a control but
 * writes nothing, a host document that is read-only while the rows stay live, a preset the
 * host called broken that can still be chosen. Each test below pins one of those.
 *
 * The pages are composed without a view model on purpose — that is what [MeSettingsState]
 * and [MeSettingsActions] exist for — so a test can state a host's answer exactly and then
 * assert what the page did with it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = HarnessApplication::class, qualifiers = "w411dp-h891dp")
class MeSettingsPageTest {

    @get:Rule
    val rule = createComposeRule()

    private fun page(
        id: String,
        state: MeSettingsState = MeSettingsState(),
        actions: MeSettingsActions = MeSettingsActions(),
    ) {
        rule.setContent {
            DshTheme(themeMode = ThemeMode.LIGHT) {
                Box(Modifier.fillMaxSize()) {
                    MeSettingsPage(id = id, state = state, actions = actions)
                }
            }
        }
    }

    // ------------------------------------------------------------- appearance

    /**
     * The appearance page offers all seven shapes, including the three the host's schema has
     * no word for.
     *
     * This is the row that would silently lose four options if someone "simplified" it to
     * read the host's `appearance.choices` — which is exactly the bug the sheet's own note
     * records having avoided. The assertion is on the count the picker would offer, taken
     * from the shared list the page and the sheet both draw.
     */
    @Test
    fun `appearance offers every shape this device can draw`() {
        assertEquals(7, APPEARANCE_CHOICES.size)
        assertEquals(
            listOf("Light", "Dark", "System", "AMOLED", "Monet light", "Monet dark", "Monet"),
            APPEARANCE_CHOICES.map { it.label },
        )
        // And only the three the host can carry are mirrored to it.
        assertEquals(3, HOST_THEME_MODES.size)
        assertEquals(setOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM), HOST_THEME_MODES)
    }

    /** The page draws the row, and the current appearance is the one the state named. */
    @Test
    fun `appearance draws the theme row`() {
        page("appearance", MeSettingsState(themeMode = ThemeMode.DARK))
        rule.onNodeWithText("Theme").assertIsDisplayed()
        rule.onNodeWithText("Dark").assertIsDisplayed()
    }

    // ---------------------------------------------------------------- general

    /**
     * A row shows the host's *labels* and writes the host's *values*.
     *
     * The labels are prose ("Full access") and the schema's enum member is a machine id.
     * Sending the label is the mistake this pins, and it is invisible on screen — which is
     * why it is asserted on the pure mapping rather than by driving a window popup. The
     * popup itself is miuix's, and a Robolectric click through a `Dialog` would be testing
     * the popup library rather than this page's contract with the host.
     */
    @Test
    fun `a pick maps to the host's machine value, not its label`() {
        val choices = listOf(
            SettingsChoice("default", "Default"),
            SettingsChoice(uk.xa0.dsh.FULL_ACCESS_PRESET, "Full access"),
        )
        assertEquals("default", pickedSettingValue(choices, emptyList(), 0))
        assertEquals(uk.xa0.dsh.FULL_ACCESS_PRESET, pickedSettingValue(choices, emptyList(), 1))
        // Out of range is null rather than an exception: a host that shrank its enum
        // between frames must not take the screen down.
        assertNull(pickedSettingValue(choices, emptyList(), 7))
    }

    /**
     * With no host option set the row draws the client's roster, and a pick indexes that.
     *
     * The permission row is the one place this fallback exists: before `settings/describe`
     * answers, the app still knows its own permission presets. Indexing the *host's* list
     * there would return null for every pick and the row would look alive and write nothing.
     */
    @Test
    fun `a pick falls back to the client roster when the host advertised none`() {
        assertEquals("default", pickedSettingValue(emptyList(), listOf("default", "full"), 0))
        assertEquals("full", pickedSettingValue(emptyList(), listOf("default", "full"), 1))
        assertNull(pickedSettingValue(emptyList(), listOf("default"), 3))
    }

    /**
     * A writable host's General page draws the host's own current value.
     *
     * The row's label comes from the host's reply, not from the client's stored preference:
     * a host that answers "Full access" must not be drawn as "Default" because that is what
     * this device last chose. This is the half of the write path that is visible.
     */
    @Test
    fun `the general page draws the host's current value`() {
        page(
            id = "general",
            state = MeSettingsState(hostSettings = hostSettingsFixture()),
        )
        // The fixture's permission value is "default", which the host labels "Default".
        rule.onNodeWithText("Default").assertIsDisplayed()
        // And the conversation row draws the host's "Compact" label.
        rule.onNodeWithText("Compact").assertIsDisplayed()
    }

    /**
     * A read-only host draws the same rows with no write path at all.
     *
     * The row is still *there* — the reader has to be able to read the value — but tapping
     * it must not open a picker, and nothing may be written. That is the honest rendering of
     * a deployment whose provider refuses writes, and it is the difference between "this
     * setting is off" and "this setting cannot be changed here".
     */
    @Test
    fun `a read-only host offers no write control`() {
        var written: Triple<String, String, String>? = null
        val readOnly = hostSettingsFixture().copy(writable = false)
        page(
            id = "general",
            state = MeSettingsState(
                hostSettings = readOnly,
                permissionOptions = listOf(PermissionOption("default", "Default", null)),
            ),
            actions = MeSettingsActions(onSettingWrite = { ns, field, value -> written = Triple(ns, field, value) }),
        )
        // The read-only notice is the web's own copy, and it is what tells the reader why.
        rule.onNodeWithText("The settings document is read-only in this deployment.")
            .assertIsDisplayed()
        rule.onNodeWithText("Default").performClick()
        // No picker opened, so there is no member to choose; nothing was written either.
        assertNull(written)
    }

    /**
     * A secret field is not editable, however writable the document is.
     *
     * The host strips a `role('secret')` field's value out of the reply, so the row has
     * nothing to show and nothing to send. `SettingsField.editable` is the single place
     * that decides this, and `writerFor` obeys it rather than re-deriving the rule.
     */
    @Test
    fun `a secret field is never a control`() {
        val secret = SettingsField(
            ns = GeneralSettings.PERMISSION_NS,
            name = GeneralSettings.PERMISSION_FIELD,
            revision = 1,
            value = "default",
            choices = listOf(SettingsChoice("default", "Default")),
            live = true,
            secret = true,
        )
        assertEquals(false, secret.editable)
        // The document is writable; the field is not, so no control is offered.
        val settings = HostSettings(writable = true, hasDocument = true, fields = mapOf(secret.key to secret))
        var written: Triple<String, String, String>? = null
        page(
            id = "general",
            state = MeSettingsState(hostSettings = settings),
            actions = MeSettingsActions(onSettingWrite = { ns, field, value -> written = Triple(ns, field, value) }),
        )
        rule.onNodeWithText("Default").performClick()
        assertNull(written)
    }

    /** The host's refusal text is shown verbatim, not paraphrased. */
    @Test
    fun `a refused write keeps the host's own wording`() {
        page(
            id = "general",
            state = MeSettingsState(
                hostSettings = hostSettingsFixture(),
                settingsWriteError = "settings/conflict: revision 3 is stale",
            ),
        )
        rule.onNodeWithText("settings/conflict: revision 3 is stale").assertIsDisplayed()
    }

    // ---------------------------------------------------------------- terminal

    /**
     * The terminal page's stepper moves by one step and stops at the range's ends.
     *
     * The value is the same cell model the shell itself uses (`TerminalCellSize`), so this
     * pins that the page cannot ask for a size the shell would refuse: the arrows are dead
     * at the model's own bounds rather than at a second, hand-written pair of numbers.
     */
    @Test
    fun `the terminal stepper moves one step and stops at the ends`() {
        val sizes = mutableListOf<Float>()
        page(
            id = "terminal",
            state = MeSettingsState(terminalFontSp = 10f),
            actions = MeSettingsActions(onTerminalFont = { sizes += it }),
        )
        rule.onNodeWithText("Text size").assertIsDisplayed()
        rule.onNodeWithContentDescription("Increase text size").performClick()
        assertEquals(listOf(11f), sizes)

        // At the largest size the increase arrow is dead.
        sizes.clear()
        page(
            id = "terminal",
            state = MeSettingsState(terminalFontSp = uk.xa0.dsh.term.TerminalCellSize.MAX_FONT_SP),
            actions = MeSettingsActions(onTerminalFont = { sizes += it }),
        )
        rule.onNodeWithContentDescription("Increase text size").performClick()
        assertEquals(emptyList<Float>(), sizes)
    }

    // ------------------------------------------------------------------ models

    /**
     * The catalog page groups by provider, marks the answering route, and offers no control.
     *
     * Choosing a model is the composer's chip, where the reasoning effort that goes with it
     * is chosen in the same sheet. A second picker here would be a second way to change the
     * same thing, and it could not carry the effort — so this page is a reading.
     */
    @Test
    fun `the model page is a catalog, not a picker`() {
        val model = ModelOption(
            provider = "deepseek",
            providerName = "DeepSeek",
            model = "deepseek-chat",
            name = "DeepSeek Chat",
        )
        var selected: Pair<ModelOption, String?>? = null
        page(
            id = "model",
            state = MeSettingsState(models = listOf(model), selectedModel = model),
            actions = MeSettingsActions(onSelectModel = { m, e -> selected = m to e }),
        )
        rule.onNodeWithText("DeepSeek Chat").assertIsDisplayed()
        rule.onNodeWithText("DeepSeek").assertIsDisplayed()
        // The answering route is marked...
        rule.onNodeWithText("Current").assertIsDisplayed()
        // ...and tapping the row writes nothing, because the row is not a control.
        rule.onNodeWithText("DeepSeek Chat").performClick()
        assertNull(selected)
    }

    // ------------------------------------------------------------------- agent

    /**
     * A broken preset cannot be chosen; the working one can.
     *
     * The host reports a preset it cannot compose (`broken`), and selecting it would be
     * refused. The page therefore disables that row rather than letting the reader make a
     * choice the host will reject — and it still draws the preset, because "this exists and
     * is broken" is a fact the reader needs.
     */
    @Test
    fun `a broken preset cannot be chosen`() {
        val chosen = mutableListOf<String>()
        val broken = AgentPresetOption(
            id = "custom-broken",
            name = "Broken preset",
            description = "Its plugin is gone.",
            isDefault = false,
            broken = true,
        )
        val working = AgentPresetOption(
            id = "custom-working",
            name = "Working preset",
            description = "Fine.",
            isDefault = true,
            broken = false,
        )
        page(
            id = "agent",
            state = MeSettingsState(agentPresetOptions = listOf(broken, working)),
            actions = MeSettingsActions(onPreset = { chosen += it }),
        )
        rule.onNodeWithText("Broken preset").performClick()
        assertEquals(emptyList<String>(), chosen)
        rule.onNodeWithText("Working preset").performClick()
        assertEquals(listOf("custom-working"), chosen)
    }

    // ---------------------------------------------------------------- archived

    /** An empty archive says so rather than drawing an empty card. */
    @Test
    fun `an empty archive says so`() {
        page("archived", MeSettingsState(archivedSessions = emptyList()))
        rule.onNodeWithText("No archived sessions.").assertIsDisplayed()
    }

    /**
     * An archived session is grouped under its workspace, and Unarchive carries its id.
     *
     * The id is the whole contract: the row's label is a title a host may have changed, so
     * the action has to travel with the session it belongs to rather than with the text.
     */
    @Test
    fun `an archived session unarchives by id`() {
        val unarchived = mutableListOf<String>()
        val session = SessionItem(
            id = "s-archived",
            title = "Put away",
            cwd = "/home/user/var/work/dsh-android",
            updatedAt = System.currentTimeMillis() - 5 * 60_000,
            running = false,
            isSubagent = false,
        )
        val workspace = WorkspaceItem(
            id = "w1",
            path = "/home/user/var/work/dsh-android",
            title = "dsh-android",
            sessionIds = listOf("s-archived"),
        )
        page(
            id = "archived",
            state = MeSettingsState(
                archivedSessions = listOf(session),
                workspaces = listOf(workspace),
            ),
            actions = MeSettingsActions(onUnarchive = { unarchived += it }),
        )
        rule.onNodeWithText("dsh-android").assertIsDisplayed()
        rule.onNodeWithText("Put away").assertIsDisplayed()
        rule.onNodeWithText("Unarchive").performClick()
        assertEquals(listOf("s-archived"), unarchived)
    }

    /**
     * A caller that cannot unarchive gets no Unarchive control.
     *
     * The action is nullable for exactly this reason: a surface handed no writer must not
     * draw a button that does nothing. The row still shows, because reading the archive is
     * not the same permission as changing it.
     */
    @Test
    fun `an archive with no writer draws no Unarchive control`() {
        val session = SessionItem(
            id = "s-archived",
            title = "Put away",
            cwd = null,
            updatedAt = 0L,
            running = false,
            isSubagent = false,
        )
        page(
            id = "archived",
            state = MeSettingsState(archivedSessions = listOf(session)),
            actions = MeSettingsActions(onUnarchive = null),
        )
        rule.onNodeWithText("Put away").assertIsDisplayed()
        rule.onNodeWithText("Unarchive").assertDoesNotExist()
    }

    // ----------------------------------------------------------------- plugins

    /** The plugins page states what it cannot list instead of drawing an empty card. */
    @Test
    fun `the plugins page states its own limit`() {
        page("plugins")
        rule.onNodeWithText("Configure and inspect the plugins installed in this deployment.")
            .assertIsDisplayed()
        rule.onNodeWithText("This deployment stores settings read-only.").assertIsDisplayed()
    }

    // ----------------------------------------------------------------- helpers

    /** The same writable document the screenshot fixture states, built here for the tests. */
    private fun hostSettingsFixture(): HostSettings = HostSettings(
        writable = true,
        hasDocument = true,
        fields = listOf(
            SettingsField(
                ns = GeneralSettings.PERMISSION_NS,
                name = GeneralSettings.PERMISSION_FIELD,
                revision = 3,
                value = "default",
                choices = listOf(
                    SettingsChoice("default", "Default"),
                    SettingsChoice(uk.xa0.dsh.FULL_ACCESS_PRESET, "Full access"),
                ),
                live = true,
            ),
            SettingsField(
                ns = GeneralSettings.CHAT_NS,
                name = GeneralSettings.TRANSCRIPT_FIELD,
                revision = 3,
                value = "compact",
                choices = listOf(
                    SettingsChoice("compact", "Compact"),
                    SettingsChoice("normal", "Normal"),
                ),
                live = true,
            ),
            SettingsField(
                ns = GeneralSettings.CONVERSATION_NS,
                name = GeneralSettings.BUSY_ENTER_FIELD,
                revision = 3,
                value = BusyEnter.QUEUE,
                choices = listOf(
                    SettingsChoice(BusyEnter.QUEUE, "Queue"),
                    SettingsChoice(BusyEnter.STEER, "Steer"),
                ),
                live = true,
            ),
        ).associateBy { it.key },
    )
}