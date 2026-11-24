package jp.co.soramitsu.backup.oauth

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import jp.co.soramitsu.backup.passkey.GoogleDriveAccountAccess
import jp.co.soramitsu.backup.passkey.GoogleDriveHttpRequest
import jp.co.soramitsu.backup.passkey.GoogleDriveHttpResponse
import jp.co.soramitsu.backup.passkey.GoogleDriveHttpTransport
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** A successful token endpoint response. The refresh token is absent on refreshes. */
class GoogleOAuthTokenResponse internal constructor(
    val accessToken: String,
    val expiresInSeconds: Long,
    val refreshToken: String?,
    /** Null when Google omitted `scope`, which per RFC 6749 means the requested scopes were granted. */
    val grantedScopes: Set<String>?
) {
    override fun toString(): String =
        "GoogleOAuthTokenResponse(redacted, expiresIn=$expiresInSeconds, scopes=$grantedScopes)"
}

/** A failed token or revocation call. Never carries a token, a code or the response body. */
class GoogleOAuthException(
    val kind: Kind,
    val httpCode: Int,
    val error: String?
) : IOException("Google OAuth request failed: ${kind.name} (HTTP $httpCode${error?.let { ", $it" }.orEmpty()})") {

    enum class Kind {
        /** The code or refresh token is expired, used or revoked: the user must sign in again. */
        INVALID_GRANT,

        /** Google does not accept this client, redirect or request: a build configuration fault. */
        CLIENT_CONFIGURATION,

        /** Google failed or answered with something that is not a token response; retrying can help. */
        SERVER
    }

    val requiresSignIn: Boolean
        get() = kind != Kind.SERVER
}

/**
 * Talks to Google's token and revocation endpoints for an installed-app client: no client secret,
 * PKCE on the code exchange, form-encoded bodies, bounded JSON responses and no redirects.
 */
class GoogleOAuthTokenClient(
    private val config: GoogleOAuthConfig,
    private val transport: GoogleDriveHttpTransport
) {

    suspend fun exchangeCode(code: String, request: GoogleOAuthAuthorizationRequest): GoogleOAuthTokenResponse {
        require(request.clientId == config.clientId && request.redirectUri == config.redirectUri) {
            "Authorization request was made for another OAuth client"
        }
        val response = post(
            GoogleOAuthConfig.TOKEN_ENDPOINT,
            mapOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "client_id" to config.clientId,
                "redirect_uri" to config.redirectUri,
                "code_verifier" to request.codeVerifier
            )
        )
        val tokens = parseTokenResponse(response)
        if (tokens.refreshToken == null) throw GoogleOAuthException(GoogleOAuthException.Kind.SERVER, response.code, null)
        return tokens
    }

    suspend fun refresh(refreshToken: String): GoogleOAuthTokenResponse {
        val response = post(
            GoogleOAuthConfig.TOKEN_ENDPOINT,
            mapOf(
                "grant_type" to "refresh_token",
                "refresh_token" to refreshToken,
                "client_id" to config.clientId
            )
        )
        return parseTokenResponse(response)
    }

    /** Revokes the grant behind [token]. A token Google no longer knows counts as revoked. */
    suspend fun revoke(token: String) {
        val response = post(GoogleOAuthConfig.REVOCATION_ENDPOINT, mapOf("token" to token))
        if (response.code in HTTP_OK..HTTP_LAST_SUCCESS) return
        val error = errorCode(response)
        if (response.code == HTTP_BAD_REQUEST && error == "invalid_token") return
        throw GoogleOAuthException(GoogleOAuthException.Kind.SERVER, response.code, error)
    }

    private suspend fun post(url: String, form: Map<String, String>): GoogleDriveHttpResponse {
        val body = form.entries.joinToString("&") { (name, value) -> "${formEncode(name)}=${formEncode(value)}" }
        return transport.execute(
            GoogleDriveHttpRequest(
                method = "POST",
                url = url,
                headers = mapOf(
                    "Content-Type" to FORM_CONTENT_TYPE,
                    "Accept" to "application/json",
                    "Cache-Control" to "no-store"
                ),
                body = body.toByteArray(StandardCharsets.UTF_8),
                isOneShot = true,
                maxResponseBytes = MAX_RESPONSE_BYTES
            )
        )
    }

    private fun parseTokenResponse(response: GoogleDriveHttpResponse): GoogleOAuthTokenResponse {
        if (response.code != HTTP_OK) throw failure(response)
        return parseObject(response)?.let(::parseTokens) ?: throw malformed(response)
    }

    // Null unless the JSON is a Bearer token response with a sane lifetime and well-formed tokens.
    private fun parseTokens(json: JsonObject): GoogleOAuthTokenResponse? {
        val accessToken = json.string("access_token")
            ?.let { runCatching { GoogleDriveAccountAccess.requireAccessToken(it) }.getOrNull() }
        val expiresIn = json.long("expires_in")?.takeIf { it in 1..MAX_EXPIRES_IN_SECONDS }
        val refreshToken = json.string("refresh_token")
        val isWellFormed = json.string("token_type").equals("Bearer", ignoreCase = true) &&
            (refreshToken == null || REFRESH_TOKEN.matches(refreshToken))
        if (accessToken == null || expiresIn == null || !isWellFormed) return null
        return GoogleOAuthTokenResponse(
            accessToken = accessToken,
            expiresInSeconds = expiresIn,
            refreshToken = refreshToken,
            grantedScopes = json.string("scope")?.split(' ')?.filter { it.isNotEmpty() }?.toSet()
        )
    }

    private fun failure(response: GoogleDriveHttpResponse): GoogleOAuthException {
        val error = errorCode(response)
        val kind = when {
            error == "invalid_grant" -> GoogleOAuthException.Kind.INVALID_GRANT
            error in CLIENT_CONFIGURATION_ERRORS -> GoogleOAuthException.Kind.CLIENT_CONFIGURATION
            else -> GoogleOAuthException.Kind.SERVER
        }
        return GoogleOAuthException(kind, response.code, error)
    }

    private fun malformed(response: GoogleDriveHttpResponse) =
        GoogleOAuthException(GoogleOAuthException.Kind.SERVER, response.code, null)

    private fun errorCode(response: GoogleDriveHttpResponse): String? =
        parseObject(response)?.string("error")?.takeIf { ERROR_CODE.matches(it) }

    private fun parseObject(response: GoogleDriveHttpResponse): JsonObject? = runCatching {
        JsonParser.parseString(response.body.toString(StandardCharsets.UTF_8)).asJsonObject
    }.getOrNull()

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun JsonObject.long(name: String): Long? =
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.let { runCatching { it.asLong }.getOrNull() }

    private fun formEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private companion object {
        const val FORM_CONTENT_TYPE = "application/x-www-form-urlencoded"
        const val HTTP_OK = 200
        const val HTTP_LAST_SUCCESS = 299
        const val HTTP_BAD_REQUEST = 400
        const val MAX_RESPONSE_BYTES = 64 * 1024
        const val MAX_EXPIRES_IN_SECONDS = 7L * 24 * 60 * 60
        val REFRESH_TOKEN = Regex("^[\\x21-\\x7E]{1,2048}$")
        val ERROR_CODE = Regex("^[a-z_]{1,64}$")
        val CLIENT_CONFIGURATION_ERRORS = setOf(
            "invalid_client",
            "unauthorized_client",
            "invalid_request",
            "invalid_scope",
            "unsupported_grant_type",
            "redirect_uri_mismatch"
        )
    }
}
