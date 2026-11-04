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
    /**
     * The appearance this *device* draws in — the one value the theme reads.
     *
     * Separate from [hostThemeMode] because the two answer different questions. This
     * one is a property of the phone: whether its panel is OLED (so black is worth
     * having), and what its wallpaper looks like (so Monet has something to follow).
     * The host cannot know either, and its settings schema has no word for them.
     */
    val themeMode: String = ThemeMode.SYSTEM,
    /**
     * Whether a dark appearance should be drawn in AMOLED black.
     *
     * **An overlay, not an appearance.** It used to be the fourth of seven entries in
     * the appearance list, which made AMOLED something you switched *to* rather than
     * something you turned on: choosing it replaced Light or Dark instead of modifying
     * it, so a reader who wanted black in the evening and light at their desk had to
     * make two changes each time, and the palette it drew was a frozen copy of Dark
     * rather than whatever dark appearance was selected.
     *
     * Now it is a switch that only *applies* to a dark appearance. Turning it on while
     * a light appearance is selected changes nothing on screen and keeps the intent for
     * when the reader switches to a dark one, which is what a switch that outlives its
     * present effect is for.
     */
    val amoled: Boolean = false,
    /**
     * The host's own `ui-theme.preference`, mirrored so the next session and the web
     * UI see the same choice.
     *
     * Kept apart from [themeMode] since the appearance list grew: the host's schema
     * offers light/dark/system, and a device-only choice such as AMOLED has no
     * representation to send it. Writing the host value back into [themeMode] would
     * therefore silently reset an appearance the host cannot express — which is
     * exactly what happened when the two shared one field.
     *
     * Blank until a host read supplies it; the settings row then shows it, and the
     * appearance row shows [themeMode].
     */
    val hostThemeMode: String = "",
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
     * Whether the conversation offers its Trajectory and Shell readings.
     *
     * A setting rather than a constant because the two views are the parts of this app a
     * reader either relies on or never touches: the trajectory is a derived view of the
     * whole session and the shell is a PTY, and both are big surfaces to keep a pill for
     * on the strip of someone who only reads answers. Off means the pill strip shows Chat
     * alone, which is also what a narrow screen wants.
     *
     * Client-local, like every other drawer flag: the host has no schema for what a tablet
     * or a phone chooses to display.
     */
    val chatViewsEnabled: Boolean = true,
    /**
     * Whether the floating chrome may paint its liquid glass — the backdrop blur, the
     * lens refraction, the AGSL specular rim and the two-layer shadow.
     *
     * ## Why this is a setting and not a constant
     *
     * Because the alternative is what happened: a build that died on launch with
     * `Fatal signal 11 (SIGSEGV)` on the RenderThread, no tombstone in dropbox, and no way
     * to tell whether the fault was the driver or the material this app feeds it. One
     * switch turns the whole GPU-heavy path into the flat tier — the same design, the same
     * layout, the same motion — so a reader can answer "does it still crash with the glass
     * off" in ten seconds instead of a debugger session.
     *
     * It is also a real preference for a real person: a phone whose GPU dislikes
     * `RenderEffect` should be able to keep a working app rather than a beautiful one that
     * closes. That is why it is on by default and stored, rather than a debug-only flag.
     *
     * Default `true`: turning a material off by default would be a silent redesign.
     */
    val liquidGlassEnabled: Boolean = true,
    /**
     * How much of the liquid material may run, when [liquidGlassEnabled] allows any.
     *
     * ## Why a level and not a switch
     *
     * Because "the glass crashes" was as far as one bit could get. The material is four
     * separate GPU mechanisms stacked on one pane — a recorded backdrop layer, a Gaussian
     * blur, an AGSL runtime shader that refracts, and an AGSL specular rim — and a driver
     * can be fine with the first two and die on the third. Turning the whole thing off
     * proves the material is at fault; it does not say which half, and the half is what a
     * fix has to change.
     *
     * So the levels are a *bisect*, not a taste menu. Each one is a strictly smaller set of
     * GPU features than the one before:
     *
     *  - [LIQUID]: everything. The intended look.
     *  - [BLUR]: backdrop + blur, **no runtime shader** — no lens, no specular rim. The
     *    lens is the single most exotic thing this app asks a driver to do, so a device
     *    that survives BLUR and dies on LIQUID has named its culprit.
     *  - [OFF]: no backdrop recording, no blur, no shader. The flat tier.
     *
     * A device that dies on [BLUR] too is not a shader problem, and one that dies with the
     * material [OFF] is not this material's problem at all.
     */
    val liquidGlassLevel: String = LiquidGlassLevel.LIQUID,
    /**
     * The language this app draws in, as a BCP-47 tag, or blank for "follow the device".
     *
     * Client-local, and deliberately so: the host has a locale of its own for the web
     * client, but what language a *phone* shows is the phone's business, and the two can
     * legitimately differ (a Chinese device driven by an English-speaking operator).
     *
     * Blank is the default and means "no override" — the app then uses whatever the
     * system already resolved, which is also what every screenshot baseline records.
     */
    val appLanguage: String = "",
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

/**
 * The appearance the reader chose.
 *
 * Three of these predate this app's own look and match the web client's cube
 * (Light / Dark / System). The rest are the phase-2 appearances from
 * `docs/DESIGN-TOKENS.md` §4: AMOLED, and the three Monet entries that follow the
 * wallpaper's colours on API 31+.
 *
 * They are stored as **strings in the same preference**, not as an enum, for the
 * reason the drawer's Group mode is: the value has to survive a downgrade and a
 * re-read of a document written by an older build. [parse] is therefore total — an
 * unknown id falls back to [SYSTEM] rather than throwing, because a stored value
 * this build cannot read must not be able to stop the app from starting.
 */
/**
 * How much of the liquid material may run — see [DshConfig.liquidGlassLevel].
 *
 * Stored as strings for the same reason [ThemeMode] is: a value written by a build this
 * one does not know must degrade to something that draws, not throw. [parse] is total.
 *
 * The three levels are a bisect of the GPU work, ordered by how much of it they allow:
 * backdrop recording, Gaussian blur, then the AGSL runtime shaders (lens and specular
 * rim). Each level is a strict subset of the one above it, which is what makes a report
 * of "it works on BLUR" mean something specific.
 */
object LiquidGlassLevel {
    /** Everything: backdrop, blur, lens refraction, specular rim. */
    const val LIQUID = "liquid"

    /**
     * Backdrop and blur, no runtime shader.
     *
     * The lens is the most exotic request this app makes of a driver — a fragment shader
     * that resamples the recorded layer with a refraction offset — and the specular rim is
     * the second. Both are AGSL. If a device survives this level and dies on [LIQUID], the
     * runtime shader is the answer and the blur was never the problem.
     */
    const val BLUR = "blur"

    /** No material at all: the flat tier. Same layout, same motion, no GPU pipeline. */
    const val OFF = "off"

    /** Every level, most capable first — the order the settings row lists them in. */
    val all = listOf(LIQUID, BLUR, OFF)

    /** Total, like [ThemeMode.parse]: an unreadable value is the full material. */
    fun parse(value: String?): String = if (value in all) value!! else LIQUID

    /** Whether the backdrop and the blur may be drawn at all. */
    fun paintsMaterial(level: String): Boolean = level != OFF

    /**
     * Whether the AGSL runtime shaders may be drawn.
     *
     * False for [BLUR] and [OFF]: the lens and the specular rim are the two call sites that
     * build a `RuntimeShader`, and they are gated on this rather than on the material.
     */
    fun paintsShader(level: String): Boolean = level == LIQUID
}

object ThemeMode {
    const val SYSTEM = "system"
    const val DARK = "dark"
    const val LIGHT = "light"

    /** Pure black surfaces. The point is an OLED panel drawing no light at all. */
    const val AMOLED = "amoled"

    /** Wallpaper-derived colour, following the system's light/dark setting. */
    const val MONET_SYSTEM = "monet-system"

    /** Wallpaper-derived colour, forced light. */
    const val MONET_LIGHT = "monet-light"

    /** Wallpaper-derived colour, forced dark. */
    const val MONET_DARK = "monet-dark"

    /**
     * Every appearance, in the order the settings sheet lists them.
     *
     * Ordered light-to-dark then the derived ones, because that is how the reader
     * scans a list of appearances: the three fixed choices first, then the two that
     * follow something else (the system, the wallpaper).
     */
    val all = listOf(LIGHT, DARK, SYSTEM, AMOLED, MONET_LIGHT, MONET_DARK, MONET_SYSTEM)

    /** True when this mode asks for wallpaper colours, which only API 31+ can give. */
    fun isMonet(mode: String): Boolean =
        mode == MONET_SYSTEM || mode == MONET_LIGHT || mode == MONET_DARK

    /** True when the mode wants a dark palette, whatever decides that. */
    fun isDark(mode: String, systemDark: Boolean): Boolean = when (mode) {
        DARK, AMOLED, MONET_DARK -> true
        LIGHT, MONET_LIGHT -> false
        // SYSTEM and MONET_SYSTEM — and anything unreadable, which [parse] has
        // already folded into SYSTEM by the time this is called.
        else -> systemDark
    }

    /**
     * A stored id as a mode this build understands.
     *
     * Total on purpose: the settings screen hands this value straight to the theme,
     * so an unrecognised id has to mean *something*. System-following is the least
     * surprising answer — it is also what a fresh install gets.
     */
    fun parse(stored: String?): String =
        // `stored in all` would be a `List.contains` call with a nullable argument.
        // On the JDK that resolves through the erased `contains(Object)` and is safe,
        // but one of the collection implementations behind `all` could legitimately
        // throw on null, and this is reached from a cold start with whatever a
        // preference file holds. Spelling the null test out removes the question.
        if (stored != null && all.contains(stored)) stored else SYSTEM
}

/** Accepted `busyEnter` values, mirroring the host's `BUSY_ENTER_BEHAVIORS`. */
object BusyEnter {
    const val QUEUE = "queue"
    const val STEER = "steer"

    /** The mode the other gesture uses; the web maps Cmd/Ctrl+Enter to this. */
    fun flip(mode: String): String = if (mode == STEER) QUEUE else STEER
}
