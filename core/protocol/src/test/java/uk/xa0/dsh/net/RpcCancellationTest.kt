package uk.xa0.dsh.net

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Cancellation wins over a verdict.
 *
 * From a device crash report: a bare `DshRpcException` — "the host did not answer;
 * its web server replied instead" — thrown inside a `StandaloneCoroutine{Cancelling}`
 * with the cancellation attached only as a suppressed
 * `DiagnosticCoroutineContextException`. The verdict is a correct statement about
 * the answer, but the coroutine that asked for it had already been cancelled, and
 * kotlinx.coroutines makes the first non-cancellation exception in a cancelling
 * job's exception list that job's completion cause — so the verdict was reported
 * through the unhandled-exception path and the process died.
 *
 * The rule [rpcFailureForCaller] encodes is the one [DshClient.rpcRaw] applies
 * after its wire work has produced a verdict: if the caller is still there, the
 * verdict; if not, a cancellation with the verdict as its cause. These tests pin
 * that decision, including that an existing cancellation is never re-wrapped and
 * that the activity it reads is the coroutine's own.
 *
 * What no test here reaches is [DshClient.rpcRaw]'s socket: the decision is a pure
 * function precisely so it does not need a host.
 */
class RpcCancellationTest {

    /** A 502 from a web server in front of the host: markup where an envelope belongs. */
    private fun webServerVerdict() = DshRpcException(
        code = "http/502",
        message = gatewayAnswerText("<!DOCTYPE html><html><body>Bad Gateway</body></html>"),
        details = null,
    )

    @Test
    fun `a verdict on a caller that is still running is raised unchanged`() {
        val verdict = webServerVerdict()

        assertSame(verdict, rpcFailureForCaller(verdict, coroutineActive = true))
    }

    @Test
    fun `a verdict formed after the caller was cancelled becomes a cancellation`() {
        val verdict = webServerVerdict()

        val raised = rpcFailureForCaller(verdict, coroutineActive = false)

        assertTrue("expected CancellationException, got $raised", raised is CancellationException)
        // Not dropped: the reason the call failed is still reachable, one level down.
        assertSame(verdict, raised.cause)
    }

    @Test
    fun `an auth refusal formed after cancellation becomes a cancellation too`() {
        val refusal = DshAuthException("Session expired (HTTP 401)")

        val raised = rpcFailureForCaller(refusal, coroutineActive = false)

        assertTrue("expected CancellationException, got $raised", raised is CancellationException)
        assertSame(refusal, raised.cause)
    }

    @Test
    fun `a transport failure formed after cancellation becomes a cancellation`() {
        val failure = IOException("Expected 1 byte but was 0")

        val raised = rpcFailureForCaller(failure, coroutineActive = false)

        assertTrue("expected CancellationException, got $raised", raised is CancellationException)
        assertSame(failure, raised.cause)
    }

    @Test
    fun `an existing cancellation is never wrapped again`() {
        val cancelled = CancellationException("the screen went away")

        assertSame(cancelled, rpcFailureForCaller(cancelled, coroutineActive = false))
        assertSame(cancelled, rpcFailureForCaller(cancelled, coroutineActive = true))
    }

    /**
     * The flag comes from the coroutine's own job, not from an estimate: once a
     * coroutine cancels itself, its job reports inactive and a verdict raised from
     * there on is a cancellation, exactly as `rpcRaw`'s `catch` would find it.
     */
    @Test
    fun `a cancelled coroutine reports inactive and its verdict becomes a cancellation`() = runBlocking {
        val verdict = webServerVerdict()
        var raised: Throwable? = null
        val job = launch(Dispatchers.Unconfined) {
            coroutineContext[Job]?.cancel()
            try {
                throw rpcFailureForCaller(verdict, coroutineContext[Job]?.isActive == true)
            } catch (failure: Throwable) {
                raised = failure
            }
        }
        job.join()

        assertTrue("expected CancellationException, got $raised", raised is CancellationException)
        assertSame(verdict, raised?.cause)
    }
}