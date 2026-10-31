package uk.xa0.dsh.net

import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Locale
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * The chain a host offered, carried out of the handshake that refused it.
 *
 * The trust manager is the only place that sees the certificate, and it runs three
 * frames below the caller: a plain [CertificateException] would say "not trusted"
 * and nothing else, leaving the app unable to show *what* was presented — which is
 * the whole content of the prompt that follows. So the chain travels on the
 * exception, where JSSE preserves it as the cause of its own `SSLHandshakeException`.
 */
class CertTrustException(
    val chain: List<X509Certificate>,
    message: String,
) : CertificateException(message)

/**
 * What the app accepts, and why — the WeeChat-Android model, adapted.
 *
 * WeeChat-Android keeps a keystore of certificates the user has accepted and asks it
 * first, falling back to the system store unless pinning is switched on. That shape
 * is what makes one mechanism serve every host this app meets:
 *
 *  * a **system-trusted** certificate is accepted with no ceremony at all, so an
 *    ordinary public host needs no trusting, no dialog and no recorded state;
 *  * a **self-signed** certificate the system will not vouch for can still be used,
 *    because the user accepting it puts it in the anchor set and the platform's own
 *    path validation then has a trust anchor to terminate at (an accepted
 *    certificate is a *trust anchor*, so its validity dates are still enforced
 *    rather than waived);
 *  * a host that has an accepted certificate is additionally **pinned** for it — see
 *    [pins] — which is what refuses a system-valid impostor, the case an anchor set
 *    alone cannot catch.
 *
 * The store outlives any one client: [uk.xa0.dsh.net.DshClient] reads the pins from
 * it when it builds its clients, the app persists entries through
 * [uk.xa0.dsh.data.ConfigStore] using [uk.xa0.dsh.net.encodeAcceptedCerts], and
 * accepting or forgetting one swaps the trust manager in place while the socket
 * factory handed to OkHttp stays the same object.
 *
 * The entries and their codec live in `:core:config` (see [AcceptedCert]); this
 * class consumes them and owns the keystore they are loaded into.
 */
class CertTrust(
    entries: List<AcceptedCert> = emptyList(),
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /**
     * The accepted certificates as anchors.
     *
     * The platform's default keystore type, so this is the same code on the device
     * and in a JVM test — `TrustManagerFactory` understands whichever type the
     * platform names.
     */
    private val anchors: KeyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        .apply { load(null, null) }

    /** Accepted certificate per host, in acceptance order. */
    private val accepted = LinkedHashMap<String, AcceptedCert>()

    /**
     * The trust manager over [anchors] alone: "is this chain anchored by something
     * the user accepted?". Rebuilt whenever the anchor set changes; never mutated,
     * so a handshake already using it keeps a consistent view.
     */
    @Volatile
    private var userTrust: X509TrustManager = trustManagerOver(anchors)

    /**
     * The one object OkHttp is given. Its behaviour is read through [userTrust] at
     * every handshake, so replacing an anchor never requires rebuilding a client.
     */
    private val composed: X509TrustManager = ComposedTrustManager()

    init {
        for (entry in entries) accept(entry.host, entry.certificate, entry.acceptedAt)
    }

    /** Accepted certificates, in acceptance order. */
    @Synchronized
    fun entries(): List<AcceptedCert> = accepted.values.toList()

    @Synchronized
    fun entryFor(host: String): AcceptedCert? = accepted[host.lowercase(Locale.ROOT)]

    /**
     * The pins OkHttp enforces, one per host that has an accepted certificate.
     *
     * These are derived, never stored separately: the certificate the user accepted
     * *is* the pin. A host with no entry is not pinned, which is what leaves an
     * ordinary public host to the system store.
     */
    @Synchronized
    fun pins(): Map<String, String> = accepted.entries.associate { it.key to it.value.pin }

    /**
     * Accept a certificate for a host — first use, or a deliberate replacement.
     *
     * One entry per host: accepting the CA that signed a host's certificate and then
     * accepting a leaf for the same host replaces rather than accumulates. Both work
     * as pins for that host (OkHttp matches a pin against every certificate in the
     * cleaned chain), and the CA is the one that outlives a leaf renewal.
     */
    @Synchronized
    fun accept(host: String, certificate: X509Certificate, at: Long = clock()): AcceptedCert {
        val entry = AcceptedCert(host.lowercase(Locale.ROOT), at, certificate)
        accepted[entry.host] = entry
        anchors.setCertificateEntry(entry.host, certificate)
        userTrust = trustManagerOver(anchors)
        return entry
    }

    @Synchronized
    fun forget(host: String) {
        val key = host.lowercase(Locale.ROOT)
        if (accepted.remove(key) == null) return
        anchors.deleteEntry(key)
        userTrust = trustManagerOver(anchors)
    }

    /**
     * Replace the whole anchor set — the one call that loads stored state.
     *
     * Not [accept] in a loop: forgetting is as much a change as accepting, so a
     * reload has to be able to *lose* an entry the stored state no longer has.
     */
    @Synchronized
    fun replace(entries: List<AcceptedCert>) {
        accepted.clear()
        for (alias in anchors.aliases().toList()) anchors.deleteEntry(alias)
        for (entry in entries) {
            val host = entry.host.lowercase(Locale.ROOT)
            accepted[host] = entry.copy(host = host)
            anchors.setCertificateEntry(host, entry.certificate)
        }
        userTrust = trustManagerOver(anchors)
    }

    /**
     * The trust manager the app's clients are built with.
     *
     * Not an `SSLContext` per change: the object handed to OkHttp is stable, and it
     * consults whatever the anchor set is at the moment of the handshake.
     */
    fun trustManager(): X509TrustManager = composed

    fun socketFactory(): SSLSocketFactory = SSLContext.getInstance("TLS")
        .apply { init(null, arrayOf<TrustManager>(composed), null) }
        .socketFactory

    /**
     * The accepted anchors first, the system store second.
     *
     * The order matters for the message rather than the outcome: a failure has to
     * report *why the user's own store refused*, since that is the failure a pinned
     * host produces.
     */
    private inner class ComposedTrustManager : X509TrustManager {

        override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) {
            throw CertificateException("This client has no server certificate to validate")
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
            val userFailure = try {
                userTrust.checkServerTrusted(chain, authType)
                return
            } catch (e: CertificateException) {
                e
            } catch (e: RuntimeException) {
                // An empty anchor set is the normal state of an install that has not
                // accepted a certificate yet, and the platform's PKIX validator does
                // not report that as a refusal: it throws
                // `RuntimeException: ... InvalidAlgorithmParameterException: the
                // trustAnchors parameter must be non-empty`. That is not noise to
                // swallow — the chain is genuinely untrusted — but it is also not a
                // `CertificateException`, so without this branch it escapes the trust
                // manager entirely. OkHttp then reports a transport failure instead of
                // a certificate question, and the app never offers the prompt that
                // would let the user accept the host.
                //
                // The message is the app's own, not the JDK's: this string is shown in
                // the prompt that asks the user to trust the certificate, and "the
                // trustAnchors parameter must be non-empty" is not an answer to "why
                // is my host unreachable". The original stays as the cause.
                CertificateException(UNABLE_TO_VERIFY, e)
            }
            try {
                systemTrust.checkServerTrusted(chain, authType)
                return
            } catch (_: CertificateException) {
                // Fall through: neither the user's anchors nor the system's vouched
                // for this chain.
            } catch (_: RuntimeException) {
                // The system store can fail the same way — on this platform it throws
                // an NPE on a chain it cannot even begin to validate, rather than a
                // refusal. The meaning is unchanged: not trusted by this store either.
            }
            throw CertTrustException(
                chain = chain.toList(),
                message = userFailure.message ?: "The certificate was not accepted",
            )
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> =
            systemTrust.acceptedIssuers + userTrust.acceptedIssuers
    }

    companion object {

        /**
         * A trust manager over [store], or over the platform's own anchors when it is
         * null — which is the same call the network security config feeds, so the
         * user-installed roots a private CA needs are still honoured as the fallback.
         */
        private fun trustManagerOver(store: KeyStore?): X509TrustManager =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                .apply { init(store) }
                .trustManagers
                .filterIsInstance<X509TrustManager>()
                .first()

        /**
         * The system store, built once: it does not change while the app runs.
         *
         * Not private because [CertProbe] reports whether a chain the app *refused*
         * would have been accepted on its own — the difference between "this host is
         * self-signed" and "this host changed" is what the prompt has to say.
         */
        internal val systemTrust: X509TrustManager by lazy { trustManagerOver(null) }

        /**
         * What the prompt says when a store refused the chain without a usable reason.
         *
         * Deliberately not the platform's wording: an empty anchor set makes the PKIX
         * validator complain about a `trustAnchors` parameter, which explains the
         * mechanism to nobody and is shown to a person being asked to trust a
         * certificate.
         */
        private const val UNABLE_TO_VERIFY = "The certificate could not be verified."
    }
}
