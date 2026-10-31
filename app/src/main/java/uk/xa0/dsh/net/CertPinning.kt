package uk.xa0.dsh.net

import okhttp3.CertificatePinner

/**
 * The pin set as an OkHttp [CertificatePinner].
 *
 * This function is the one member of [CertPins] that needs the protocol stack, and
 * that is why it lives here rather than beside the fingerprints it consumes: the
 * pins themselves — `pinOf`, `display`, `isWellFormed` — are JDK arithmetic about
 * certificates and belong to `:core:config`, but turning them into something OkHttp
 * enforces is transport. Splitting the object this way is what keeps okhttp out of
 * the configuration module; `pinner()` stays call-site compatible because it is an
 * extension, so `CertPins.pinner(pins)` still reads the same.
 *
 * OkHttp checks this while the connection is being established, before a single byte
 * of the request is written — which is why pinning belongs here and not in an
 * interceptor: an interceptor sees the response only after the request (with its
 * credentials) has already been sent to whoever answered the handshake.
 *
 * An entry whose pin is malformed is skipped rather than thrown over: the call then
 * behaves as if that host were unpinned, which is what it is.
 */
fun CertPins.pinner(pins: Map<String, String>): CertificatePinner {
    val builder = CertificatePinner.Builder()
    for ((host, pin) in pins) {
        if (host.isBlank() || !isWellFormed(pin)) continue
        builder.add(host, "sha256/" + pin.trim())
    }
    return builder.build()
}
