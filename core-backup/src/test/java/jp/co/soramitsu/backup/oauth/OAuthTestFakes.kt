package jp.co.soramitsu.backup.oauth

import jp.co.soramitsu.backup.passkey.GoogleDriveHttpRequest
import jp.co.soramitsu.backup.passkey.GoogleDriveHttpResponse
import jp.co.soramitsu.backup.passkey.GoogleDriveHttpTransport
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom

/** Records every request and answers with [responder]; no network. Synthetic tokens only. */
internal class FakeGoogleHttpTransport(
    var responder: (GoogleDriveHttpRequest) -> GoogleDriveHttpResponse = { error("no responder") }
) : GoogleDriveHttpTransport {
    val requests = mutableListOf<GoogleDriveHttpRequest>()

    override suspend fun execute(request: GoogleDriveHttpRequest): GoogleDriveHttpResponse {
        requests += request
        return responder(request)
    }
}

internal class InMemoryTokenStore(var value: String? = null) : GoogleOAuthTokenStore {
    var writes = 0
    var clears = 0

    override fun read(): String? = value

    override fun write(value: String) {
        writes++
        this.value = value
    }

    override fun clear() {
        clears++
        value = null
    }
}

/** A deterministic "random" source so requests are reproducible in assertions. */
internal class FixedRandom(private val byte: Byte = 7) : SecureRandom() {
    override fun nextBytes(bytes: ByteArray) {
        bytes.fill(byte)
    }
}

internal fun jsonResponse(code: Int, body: String) =
    GoogleDriveHttpResponse(code, body.toByteArray(StandardCharsets.UTF_8))

internal fun GoogleDriveHttpRequest.form(): Map<String, String> =
    requireNotNull(body).toString(StandardCharsets.UTF_8).split('&').associate { pair ->
        URLDecoder.decode(pair.substringBefore('='), "UTF-8") to URLDecoder.decode(pair.substringAfter('='), "UTF-8")
    }

internal fun tokenResponse(
    accessToken: String = "ya29.synthetic-access",
    refreshToken: String? = "1//synthetic-refresh",
    scope: String? = GoogleOAuthConfig.DRIVE_APPDATA_SCOPE,
    expiresIn: Long = 3599
): GoogleDriveHttpResponse = jsonResponse(
    200,
    buildString {
        append("{\"access_token\":\"").append(accessToken).append("\",\"token_type\":\"Bearer\",\"expires_in\":").append(expiresIn)
        refreshToken?.let { append(",\"refresh_token\":\"").append(it).append('"') }
        scope?.let { append(",\"scope\":\"").append(it).append('"') }
        append('}')
    }
)

internal fun oauthError(code: Int, error: String) =
    jsonResponse(code, "{\"error\":\"$error\",\"error_description\":\"synthetic\"}")
