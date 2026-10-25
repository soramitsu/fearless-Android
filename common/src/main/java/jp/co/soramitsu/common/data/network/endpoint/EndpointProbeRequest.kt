package jp.co.soramitsu.common.data.network.endpoint

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URI

/** Exact explicit candidate and trusted expected identity; constructing this value performs no I/O. */
class EndpointProbeRequest(
    val family: EndpointProbeFamily,
    internal val candidateUrl: String,
    expectedGenesis: String
) {
    internal val expectedBytes = GenesisProbeProtocol.decodeIdentity(family, expectedGenesis)

    internal val host: String
    internal val isCleartext: Boolean

    init {
        require(candidateUrl.length <= 8192 && candidateUrl.all { it.code in 33..126 }) {
            "Invalid endpoint candidate"
        }
        val uri = try { URI(candidateUrl) } catch (_: Exception) { null }
        require(uri != null && uri.rawUserInfo == null && uri.rawFragment == null) { "Invalid endpoint candidate" }
        val schemes = if (family == EndpointProbeFamily.SUBSTRATE) setOf("ws", "wss") else setOf("http", "https")
        require(uri.scheme in schemes) { "Unsupported endpoint scheme" }
        val httpForm = when (uri.scheme) {
            "ws" -> "http" + candidateUrl.substring(2)
            "wss" -> "https" + candidateUrl.substring(3)
            else -> candidateUrl
        }
        val parsed = httpForm.toHttpUrlOrNull()
        require(parsed != null && parsed.toString() == httpForm && parsed.encodedUsername.isEmpty() && parsed.encodedPassword.isEmpty()) {
            "Endpoint candidate requires explicit normalization"
        }
        host = parsed.host
        isCleartext = !parsed.isHttps
    }

    override fun toString(): String = "EndpointProbeRequest(family=$family)"
}
