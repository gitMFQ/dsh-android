package uk.xa0.dsh.data

import uk.xa0.dsh.model.DrawerGrouping
import uk.xa0.dsh.net.AcceptedCert
import uk.xa0.dsh.term.TerminalCellSize

/**
 * Everything the app needs to reach one DSH host.
 *
 * [manualCookie] exists because a DSH host can also be reached *directly* on the
 * carrier port, where the loopback-bound `dsh-auth-*` cookie is the only way in
 * (the LAN door normally mints its own session through /auth/login instead).
 */
data class DshConfig(
    val baseUrl: String = "",
    /**
     * An optional second door to the same host, on the local network.
     *
     * The phone is often on the LAN the host lives on, where the host answers
     * without a login and without a tunnel, and often on mobile data, where only
     * [baseUrl] is reachable. The app probes both and prefers this one while it
     * answers — so it is stored beside the public address rather than replacing
     * it, and blank simply means "this install has no LAN address to try". An
     * install whose [baseUrl] is *already* a private address needs nothing
     * entered here; see [uk.xa0.dsh.net.isPrivateLanHost].
     */
    val lanUrl: String = "",
    val username: String = "",
    val password: String = "",
    val manualCookie: String = "",
    val themeMode: String = ThemeMode.SYSTEM,
    /** Agent preset used for newly created sessions; blank means "host default". */
    val agentPreset: String = "",
    /**
     * What the send button does while the agent is running: `queue` (deliver
     * after the current turn) or `steer` (interrupt the turn with the message).
     *
     * The web client keeps this in the host user-settings document, but a
     * remote browser is handed a `memory` settings scope whose writes are
     * dropped, so there is nothing host-side to read or write. It is a
     * client-local preference here, matching the web's `busyEnter` field and
     * its `'queue'` default.
     */
    val busyEnter: String = BusyEnter.QUEUE,
    /**
     * Sidebar Group mode: sibling Workspaces, the Workspace Tree, or one flat list —
     * the web's own three-way `Group by`. A client-local preference like [busyEnter]:
     * the host has no settings scope a phone can write, and the sidebar's shape is a
     * property of this client anyway. See [DrawerGrouping].
     */
    val drawerGrouping: DrawerGrouping = DrawerGrouping.WORKSPACE,
    /** Sidebar Order mode: last updated (`true`) or the Workspace's manual order. */
    val drawerOrderByUpdated: Boolean = false,
    /**
     * The drawer's Archived filter. Saved for the same reason Group and Order are:
     * it is a view the user chose, and losing it on every restart is the bug the
     * user reported for Order ("on app restart the drawer's order should persist").
     */
    val drawerShowArchived: Boolean = false,
    /**
     * The Workspace sections the user has collapsed, by section key (a Workspace's
     * host id, or the `__flat__`/`__ungrouped__` sentinels). Persisted for the same
     * reason as the three above: collapsing a Workspace is a view the user chose,
     * and a plain `remember` in the drawer lost it on every cold start.
     *
     * Only the Workspace collapse lives here. The drawer's "Show N more" paging and
     * the per-session subagent disclosure stay ephemeral in the composable: they
     * are per-visit navigation, not a preference, and restoring them would reopen
     * the drawer in a state the user did not ask for.
     */
    val drawerCollapsedSections: Set<String> = emptySet(),
    /**
     * Root key → the dedicated "Host shell" Session that root's unconfined terminal
     * lives in. The key is a Workspace id, or — for a session in no Workspace — the
     * `cwd` the shell was rooted at (`uk.xa0.dsh.term.HostShellRoot.key`).
     *
     * Local, and not recoverable from the host, because that session is **archived**:
     * an archived session cannot be browsed to, so without this the app would build a
     * second one on every visit and leave the first behind. It is written only once the
     * whole bootstrap has succeeded (session created, mode set, archived), so a
     * remembered id is evidence that the mode step ran — see
     * [uk.xa0.dsh.term.HostShellProgress].
     *
     * The drawer's own "Archived" toggle is the recovery path: it is how the user finds
     * and restores the session if they ever want it back, or wants it gone.
     */
    val hostShellSessions: Map<String, String> = emptyMap(),
    /**
     * A DeepSeek API key, for reading the account's balance.
     *
     * Not the host's key and not a way to bill anything: DeepSeek's only account
     * endpoint is `GET /user/balance`, and the balance belongs to the *account* —
     * every key on it reads the same figure — so this key sees exactly the balance
     * the host is spending from. It is stored in the same encrypted preferences as
     * the host password, and sent to `api.deepseek.com` and nowhere else.
     *
     * Blank means the usage panel says so rather than showing a stale or invented
     * number.
     */
    val deepseekApiKey: String = "",
    /**
     * The Shell tab's terminal cell size, in sp.
     *
     * Client-local, for the same reason [busyEnter] and the drawer's modes are: the
     * web's own "Font size" setting is the *conversation's* and says so ("Only
     * affects conversation content"), and its terminal is xterm.js at a hard-coded
     * 13px with no control at all. There is no host field to write and none to
     * mirror, so this is the app's own preference — persisted because a size the
     * reader chose and lost on every restart is the bug the drawer's Order mode was
     * reported for.
     *
     * The value is a cell size rather than a scale factor: [TerminalCellSize] derives
     * the line box from it, and the font size *is* the cell's width, so a percentage
     * would only be a second spelling of the same number.
     */
    val terminalFontSp: Float = TerminalCellSize.DEFAULT_FONT_SP,
    /**
     * The certificates the app has been told to trust, one per host.
     *
     * The trust model is WeeChat-Android's: the accepted certificates are the trust
     * anchors the client's handshakes are validated against, and a host with one is
     * pinned to it. Stored here rather than in a keystore of their own because this
     * is already the one encrypted place client-local state lives — see
     * [uk.xa0.dsh.net.CertTrust] for the format and the reasoning.
     */
    val acceptedCerts: List<AcceptedCert> = emptyList(),
) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank()

    /** True when the user opted into pasting a raw cookie instead of logging in. */
    val usesManualCookie: Boolean get() = manualCookie.isNotBlank()
}

object ThemeMode {
    const val SYSTEM = "system"
    const val DARK = "dark"
    const val LIGHT = "light"
    val all = listOf(SYSTEM, DARK, LIGHT)
}

/** Accepted `busyEnter` values, mirroring the host's `BUSY_ENTER_BEHAVIORS`. */
object BusyEnter {
    const val QUEUE = "queue"
    const val STEER = "steer"

    /** The mode the other gesture uses; the web maps Cmd/Ctrl+Enter to this. */
    fun flip(mode: String): String = if (mode == STEER) QUEUE else STEER
}
