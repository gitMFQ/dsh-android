package uk.xa0.dsh.model

/**
 * What the header's status capsule says, derived from everything that could say
 * something.
 *
 * ## Why this is one value and not four chips
 *
 * Four separate indicators — connection, running, approval, question — is the
 * obvious reading of "show me the status", and it is wrong on a phone: the header
 * has one 40dp trailing seat per utility, and the cluster is already a set of
 * conditional seats. Four more would either overflow the title or push the
 * utilities off the row. Worse, three of the four are *usually* absent, so most of
 * the width would be spent drawing nothing.
 *
 * So the header wears **one** capsule, and this is the ranking that decides which
 * fact gets it. The rule is not "most recent" and not "most severe": it is **which
 * fact is the reader's to act on**, because a capsule that shows a spinner while an
 * approval waits is a capsule that hides the one thing that is blocked on a human.
 *
 * ## The order, and why each step is where it is
 *
 * 1. [Waiting] — an approval, a question, or a plan review. The agent is *stopped*
 *    until this is answered, and on a remote host the tool call fails if it is not.
 *    Nothing outranks work that cannot proceed.
 * 2. [Offline] — the socket is down. Everything else on screen is stale, so this
 *    outranks any claim about what the session is doing: a running turn on a dead
 *    socket is not running *for you*.
 * 3. [Working] — the session is running. This is the ordinary busy state; it is
 *    below the two above because neither is ordinary.
 * 4. [Idle] — nothing to say. The capsule is **not drawn** for this: a chip that
 *    reads "Ready" on every idle screen is chrome that trains the reader to ignore
 *    the seat, and then the approval it does show is worth less.
 *
 * [Unknown] is the fifth and is deliberately *not* idle: it means the app has no
 * opinion (no session, or a state it has not resolved yet), which is a different
 * fact from "the session is at rest". It is drawn, faintly, because "the client
 * does not know" is worth saying once rather than pretending to an answer.
 */
enum class CapsuleState {
    /** A human is blocking the agent: approval, question, or plan review. */
    WAITING,

    /** The transport is down; every other reading on screen is stale. */
    OFFLINE,

    /** The session is running. */
    WORKING,

    /** The session is at rest. **Not drawn** — see the class note. */
    IDLE,

    /** The client cannot say. Drawn, because silence would be a claim. */
    UNKNOWN,
}

/**
 * The capsule's content: one state, one short label, and the pending kind when the
 * label came from an attention card.
 *
 * [pending] is carried rather than re-derived at the call site so the capsule and
 * whatever it opens on tap cannot disagree about *which* interaction is waiting —
 * the tap target needs the kind, and reading it from the same derivation is what
 * makes the pair a pair.
 */
data class StatusCapsule(
    val state: CapsuleState,
    val label: String,
    val pending: PendingKindWire? = null,
)

/**
 * The three blocking kinds, as data.
 *
 * A mirror of the app's `PendingKind` enum rather than a reference to it: this file
 * lives in `:core:model`, and that enum lives in `:app` next to `AttentionCenter`,
 * which owns the `$events` stream. The precedence numbers are the web's own
 * (`ui-approval` registers 0, `ui-user-questions` 1 or 2) and are duplicated here
 * with the source named, because the *ranking* is what this file is about and a
 * ranking that could not be read without the app module would not be testable.
 */
enum class PendingKindWire(val precedence: Int, val label: String) {
    APPROVAL(0, "Waiting for approval"),
    QUESTION(1, "Waiting for answer"),
    PLAN_REVIEW(2, "Plan awaiting review"),
}

/**
 * The derivation.
 *
 * Every argument is a fact the app already holds; nothing here reads a host or a
 * clock. [pending] is the *current session's* kind, not the cross-session map: the
 * capsule sits in the conversation's header, so it speaks about the conversation on
 * screen. A session elsewhere holding an approval is the drawer's business, and it
 * already paints that per row.
 */
object StatusCapsules {
    /**
     * The one capsule the header should wear.
     *
     * [connectionLabel] is `UiState.connectionLabel` — non-null only while the mux is
     * reconnecting — and is passed as the words the pill would show, so the capsule
     * and the pill cannot word the same state differently.
     *
     * [seatOpen] is **not** "a session id exists". A new-session hero has no session id
     * yet and is still a real seat — the reader is looking at the place their next turn
     * will go — so it must not be told "No session". The caller passes whether a seat
     * is on screen at all: an open session, or the pending new-session target. The one
     * state that is genuinely seatless is a cold start with nothing to reopen, which is
     * a loading screen rather than a conversation.
     */
    fun derive(
        pending: PendingKindWire?,
        connectionLabel: String?,
        running: Boolean,
        seatOpen: Boolean,
    ): StatusCapsule = when {
        pending != null -> StatusCapsule(CapsuleState.WAITING, pending.label, pending)
        connectionLabel != null -> StatusCapsule(CapsuleState.OFFLINE, connectionLabel)
        !seatOpen -> StatusCapsule(CapsuleState.UNKNOWN, "No session")
        running -> StatusCapsule(CapsuleState.WORKING, "Working")
        else -> StatusCapsule(CapsuleState.IDLE, "Ready")
    }

    /**
     * Whether [state] is worth drawing at all.
     *
     * [CapsuleState.IDLE] is the only one that is not. Kept as a function rather than
     * left to each call site's `if`, so "an idle session shows nothing" is one
     * statement in one place — the rule the class note argues for, made mechanical.
     */
    fun isDrawn(state: CapsuleState): Boolean = state != CapsuleState.IDLE

    /**
     * The pending kind of highest precedence, from a per-session map.
     *
     * A separate entry point because the two shapes the app holds are different: the
     * header wants one session's kind, and the drawer holds `Map<String, PendingKind>`.
     * The ranking is the same rule the web applies at `ui-session`, and the larger
     * precedence wins — a plan review beats an ordinary question beats an approval.
     */
    fun highest(kinds: Collection<PendingKindWire>): PendingKindWire? =
        kinds.maxByOrNull { it.precedence }
}
