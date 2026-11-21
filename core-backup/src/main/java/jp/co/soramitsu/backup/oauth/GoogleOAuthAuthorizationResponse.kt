package jp.co.soramitsu.backup.oauth

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** What the browser handed back to the redirect URI, checked against the request still pending. */
sealed interface GoogleOAuthAuthorizationResponse {

    /** The single-use authorization code; it is worthless without the request's PKCE verifier. */
    class Code internal constructor(val code: String) : GoogleOAuthAuthorizationResponse {
        override fun toString(): String = "Code(redacted)"
    }

    /** The user closed or declined the Google page (`error=access_denied`). */
    data object Denied : GoogleOAuthAuthorizationResponse

    /** Google refused the request itself, for example an unknown client or redirect URI. */
    data class Error(val error: String) : GoogleOAuthAuthorizationResponse

    /** Not a response to the pending request: wrong redirect, state, issuer or shape. Never acted on. */
    data class Rejected(val reason: Reason) : GoogleOAuthAuthorizationResponse

    enum class Reason { MALFORMED_URI, WRONG_REDIRECT, MALFORMED_QUERY, STATE_MISMATCH, WRONG_ISSUER, MALFORMED_RESPONSE }

    companion object {
        private const val ISSUER = "https://accounts.google.com"
        private const val ACCESS_DENIED = "access_denied"
        private const val MAX_URI_LENGTH = 8192
        private const val MAX_CODE_LENGTH = 2048
        private val CODE = Regex("^[\\x21-\\x7E]{1,$MAX_CODE_LENGTH}$")
        private val ERROR = Regex("^[\\x20\\x21\\x23-\\x5B\\x5D-\\x7E]{1,128}$")

        /**
         * Accepts only `<redirect scheme>:/oauth2redirect` with exactly one `state` equal to the pending
         * request's, an optional `iss` of Google, and either one `code` or one `error`.
         */
        @Suppress("ReturnCount")
        fun parse(request: GoogleOAuthAuthorizationRequest, redirect: String): GoogleOAuthAuthorizationResponse {
            if (redirect.length > MAX_URI_LENGTH) return Rejected(Reason.MALFORMED_URI)
            val uri = try {
                URI(redirect)
            } catch (_: URISyntaxException) {
                return Rejected(Reason.MALFORMED_URI)
            }
            if (!isRedirectOf(uri, request.redirectUri)) return Rejected(Reason.WRONG_REDIRECT)

            val parameters = parseQuery(uri.rawQuery) ?: return Rejected(Reason.MALFORMED_QUERY)
            val state = parameters["state"]
            if (state == null || !constantTimeEquals(state, request.state)) return Rejected(Reason.STATE_MISMATCH)
            parameters["iss"]?.let { if (it != ISSUER) return Rejected(Reason.WRONG_ISSUER) }

            return responseOf(code = parameters["code"], error = parameters["error"])
        }

        private fun responseOf(code: String?, error: String?): GoogleOAuthAuthorizationResponse = when {
            code != null && error == null && CODE.matches(code) -> Code(code)
            code == null && error == ACCESS_DENIED -> Denied
            code == null && error != null && ERROR.matches(error) -> Error(error)
            else -> Rejected(Reason.MALFORMED_RESPONSE)
        }

        private fun isRedirectOf(uri: URI, redirectUri: String): Boolean {
            val expected = URI(redirectUri)
            return uri.scheme.equals(expected.scheme, ignoreCase = true) &&
                uri.rawAuthority == null &&
                uri.rawPath == expected.rawPath
        }

        // Null when a parameter repeats or is not URL-encoded, so a second `state` or `code` is never smuggled in.
        private fun parseQuery(rawQuery: String?): Map<String, String>? {
            val pairs = rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }.map { pair ->
                decode(pair.substringBefore('=')) to decode(pair.substringAfter('=', missingDelimiterValue = ""))
            }
            val decoded = pairs.mapNotNull { (name, value) -> if (name != null && value != null) name to value else null }
            val parameters = decoded.toMap()
            return parameters.takeIf { decoded.size == pairs.size && parameters.size == decoded.size }
        }

        private fun decode(value: String): String? =
            runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }.getOrNull()

        private fun constantTimeEquals(actual: String, expected: String): Boolean =
            MessageDigest.isEqual(actual.toByteArray(Charsets.UTF_8), expected.toByteArray(Charsets.UTF_8))
    }
}
