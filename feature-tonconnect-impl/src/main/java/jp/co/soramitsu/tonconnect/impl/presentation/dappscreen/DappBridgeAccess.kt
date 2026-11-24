package jp.co.soramitsu.tonconnect.impl.presentation.dappscreen

import jp.co.soramitsu.tonconnect.api.model.TonConnectUrlValidator
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Who may use the in-app browser's wallet bridge.
 *
 * Android exposes a JavaScript interface to every frame of every page, so the
 * interface alone proves nothing about the caller. Each main-frame page of the
 * trusted origin gets a fresh random token that only the script injected into
 * that page knows; a call is served only with the current token while the main
 * frame still shows the trusted origin.
 */
object DappBridgeAccess {

    private const val TOKEN_BYTES = 32
    private const val HEX_RADIX = 16
    private const val BYTE_MASK = 0xFF
    private val TOKEN_REGEX = Regex("^[0-9a-f]{64}$")
    private val secureRandom = SecureRandom()

    fun newPageToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString(separator = "") { byte ->
            (byte.toInt() and BYTE_MASK).toString(HEX_RADIX).padStart(2, '0')
        }
    }

    /** Whether [token] can be embedded in a script literal as is. */
    fun isWellFormedToken(token: String): Boolean = TOKEN_REGEX.matches(token)

    fun isTokenValid(presentedToken: String?, pageToken: String?): Boolean {
        if (presentedToken.isNullOrEmpty() || pageToken.isNullOrEmpty()) return false
        return MessageDigest.isEqual(presentedToken.toByteArray(), pageToken.toByteArray())
    }

    fun isTrustedPage(pageUrl: String?, trustedOrigin: String?): Boolean {
        if (pageUrl == null || trustedOrigin == null) return false
        return TonConnectUrlValidator.isSameOrigin(pageUrl, trustedOrigin)
    }

    fun isAuthorized(
        presentedToken: String?,
        pageToken: String?,
        pageUrl: String?,
        trustedOrigin: String?
    ): Boolean = isTokenValid(presentedToken, pageToken) && isTrustedPage(pageUrl, trustedOrigin)
}
