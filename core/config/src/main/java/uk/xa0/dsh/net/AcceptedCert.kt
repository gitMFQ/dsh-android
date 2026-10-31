package uk.xa0.dsh.net

import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.Locale

/**
 * One certificate the app has been told to trust, for one host.
 *
 * The certificate itself is kept, not a hash of it, because it is used as a *trust
 * anchor*: a self-signed host is only usable at all if the platform's own path
 * validation has something to terminate at, and the certificate the user accepted
 * is exactly that something. The fingerprint is derived for display and for pinning.
 */
data class AcceptedCert(
    val host: String,
    val acceptedAt: Long,
    val certificate: X509Certificate,
) {
    /** SHA-256 over the certificate's public key — see [CertPins]. */
    val pin: String get() = CertPins.pinOf(certificate)
    val subject: String get() = certificate.subjectX500Principal.name
    val issuer: String get() = certificate.issuerX500Principal.name
    val notBefore: Long get() = certificate.notBefore.time
    val notAfter: Long get() = certificate.notAfter.time
}

/**
 * The stored form of the accepted certificates, and its reader.
 *
 * `host<TAB>acceptedAt<TAB>base64(DER)` per line, in the same spirit as
 * [uk.xa0.dsh.term.encodeHostShellSessions]: tab-separated text in the one
 * place client-local state lives, with a malformed line dropped rather than
 * thrown — a preference the app cannot read must not be able to stop it from
 * starting.
 *
 * These used to be `CertTrust.encode` / `CertTrust.decode`. The format is a property
 * of [AcceptedCert] — the type this module owns — while `CertTrust` is the trust
 * manager that consumes the result, and that class keeps its KeyStore and
 * TrustManager and so belongs in `:core:protocol` when `net/` moves at M3. Leaving
 * the codec behind in `CertTrust` would have made this module depend on the protocol
 * module to read its own persisted field.
 */
fun encodeAcceptedCerts(entries: List<AcceptedCert>): String = entries.joinToString("\n") { entry ->
    val der = Base64.getEncoder().encodeToString(entry.certificate.encoded)
    "${entry.host}\t${entry.acceptedAt}\t$der"
}

/** Reads what [encodeAcceptedCerts] wrote; a malformed line is dropped, never thrown. */
fun decodeAcceptedCerts(raw: String?): List<AcceptedCert> {
    if (raw.isNullOrEmpty()) return emptyList()
    val factory = CertificateFactory.getInstance("X.509")
    val out = ArrayList<AcceptedCert>()
    for (line in raw.split('\n')) {
        val parts = line.split('\t')
        if (parts.size != 3) continue
        val host = parts[0].trim()
        val at = parts[1].trim().toLongOrNull() ?: continue
        val der = runCatching { Base64.getDecoder().decode(parts[2].trim()) }.getOrNull()
            ?: continue
        val certificate = runCatching {
            factory.generateCertificate(ByteArrayInputStream(der)) as? X509Certificate
        }.getOrNull() ?: continue
        if (host.isNotEmpty()) out += AcceptedCert(host.lowercase(Locale.ROOT), at, certificate)
    }
    return out
}
