package uk.xa0.dsh.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The capsule's ranking.
 *
 * What is being pinned is not "it renders" — that is the screenshot test's job — but
 * the *order* the four facts are ranked in, because that order is the whole design
 * decision. Each test states one step of it and why the step is where it is.
 */
class StatusCapsuleTest {

    private fun derive(
        pending: PendingKindWire? = null,
        connection: String? = null,
        running: Boolean = false,
        seatOpen: Boolean = true,
    ) = StatusCapsules.derive(pending, connection, running, seatOpen)

    @Test
    fun `an approval outranks a running turn`() {
        // The agent is stopped until this is answered. A spinner over it would hide
        // the one thing that is blocked on a human.
        val capsule = derive(pending = PendingKindWire.APPROVAL, running = true)

        assertEquals(CapsuleState.WAITING, capsule.state)
        assertEquals("Waiting for approval", capsule.label)
    }

    @Test
    fun `an approval outranks a dead socket`() {
        // Both are true; the one the reader can act on wins. The socket will redial
        // itself, the approval will not answer itself.
        val capsule = derive(pending = PendingKindWire.APPROVAL, connection = "Reconnecting")

        assertEquals(CapsuleState.WAITING, capsule.state)
    }

    @Test
    fun `a dead socket outranks a running turn`() {
        // A running turn on a closed socket is not running *for you*: every reading on
        // screen is stale, which is a more useful thing to say than "Working".
        val capsule = derive(connection = "Reconnecting", running = true)

        assertEquals(CapsuleState.OFFLINE, capsule.state)
        assertEquals("Reconnecting", capsule.label)
    }

    @Test
    fun `the connection label is the pill's own words`() {
        // Passed in rather than invented here, so the capsule and the pill that
        // already exists cannot word the same state differently.
        val capsule = derive(connection = "Reconnecting")

        assertEquals("Reconnecting", capsule.label)
    }

    @Test
    fun `a running session with nothing else to say is working`() {
        val capsule = derive(running = true)

        assertEquals(CapsuleState.WORKING, capsule.state)
        assertEquals("Working", capsule.label)
    }

    @Test
    fun `an idle session is not drawn`() {
        // The rule that keeps the seat meaningful: a chip reading "Ready" on every
        // idle screen trains the reader to ignore the seat, and then the approval it
        // does show is worth less.
        val capsule = derive()

        assertEquals(CapsuleState.IDLE, capsule.state)
        assertFalse(StatusCapsules.isDrawn(capsule.state))
    }

    @Test
    fun `no seat at all is unknown rather than idle`() {
        // "The client has no opinion" is a different fact from "the session is at
        // rest", and the capsule is drawn for the first because silence would be a
        // claim.
        val capsule = derive(seatOpen = false)

        assertEquals(CapsuleState.UNKNOWN, capsule.state)
        assertTrue(StatusCapsules.isDrawn(capsule.state))
    }

    @Test
    fun `a new-session hero is a seat, not a missing session`() {
        // The hero has no session id yet — `session/create` is deferred to its first
        // real use — but the reader is looking at the place their next turn will go.
        // Telling them "No session" over a hero would be the header contradicting the
        // screen: the pending target *is* a seat.
        val capsule = derive(seatOpen = true)

        assertEquals(CapsuleState.IDLE, capsule.state)
    }

    @Test
    fun `only idle is withheld`() {
        assertFalse(StatusCapsules.isDrawn(CapsuleState.IDLE))
        assertTrue(StatusCapsules.isDrawn(CapsuleState.WAITING))
        assertTrue(StatusCapsules.isDrawn(CapsuleState.OFFLINE))
        assertTrue(StatusCapsules.isDrawn(CapsuleState.WORKING))
        assertTrue(StatusCapsules.isDrawn(CapsuleState.UNKNOWN))
    }

    @Test
    fun `the pending kind travels with the capsule`() {
        // The tap target needs to know which card to open, and reading it from the
        // same derivation is what makes the capsule and the card a pair.
        val capsule = derive(pending = PendingKindWire.PLAN_REVIEW)

        assertEquals(PendingKindWire.PLAN_REVIEW, capsule.pending)
    }

    @Test
    fun `a non-pending capsule carries no pending kind`() {
        assertNull(derive(running = true).pending)
        assertNull(derive().pending)
    }

    @Test
    fun `the highest-precedence kind wins a set`() {
        // The web's own ranking: a plan review beats an ordinary question beats an
        // approval, applied where a session holds more than one.
        val highest = StatusCapsules.highest(
            listOf(PendingKindWire.APPROVAL, PendingKindWire.PLAN_REVIEW),
        )

        assertEquals(PendingKindWire.PLAN_REVIEW, highest)
    }

    @Test
    fun `an empty set has no highest`() {
        assertNull(StatusCapsules.highest(emptyList()))
    }

    @Test
    fun `each pending kind has its own label`() {
        assertEquals("Waiting for approval", PendingKindWire.APPROVAL.label)
        assertEquals("Waiting for answer", PendingKindWire.QUESTION.label)
        assertEquals("Plan awaiting review", PendingKindWire.PLAN_REVIEW.label)
    }
}
