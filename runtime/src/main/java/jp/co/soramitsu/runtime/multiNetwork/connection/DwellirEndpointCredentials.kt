@file:Suppress("PackageNaming") // Preserve the existing runtime multiNetwork package.

package jp.co.soramitsu.runtime.multiNetwork.connection

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/** Restricts provider credential decoration to unambiguous Dwellir WSS origins. */
internal object DwellirEndpointCredentials {
    private const val MAX_URL_LENGTH = 8_192
    private const val MAX_KEY_LENGTH = 512
    private const val MAX_HOST_LENGTH = 253
    private const val MAX_LABEL_LENGTH = 63
    private const val SECURE_PORT = 443

    /** Append one opaque path segment only to an admitted origin with an empty path. */
    fun fill(url: String, key: String): String {
        if (!validKey(key)) return url
        val parsed = providerUri(url) ?: return url
        if (parsed.rawPath != "") return url
        return "$url/$key"
    }

    /** Remove only the exact configured segment from an admitted origin; preserve all other bytes. */
    fun clear(url: String, key: String): String {
        if (!validKey(key)) return url
        val parsed = providerUri(url) ?: return url
        if (parsed.rawPath != "/$key") return url
        return url.dropLast(key.length + 1)
    }

    private fun validKey(key: String): Boolean = key.length in 1..MAX_KEY_LENGTH &&
        key != "." && key != ".." && key.all { asciiAlphanumeric(it) || it in "-._~" }

    private fun providerUri(url: String): URI? {
        if (url.length !in 1..MAX_URL_LENGTH) return null
        val parsed = try {
            URI(url)
        } catch (_: URISyntaxException) {
            return null
        }
        return parsed.takeIf { supportedUri(it) && providerAuthority(it) }
    }

    private fun supportedUri(uri: URI): Boolean {
        val secureWebsocket = uri.isAbsolute && !uri.isOpaque && uri.scheme.equals("wss", ignoreCase = true)
        val noCredentialsOrRouting = uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null
        return secureWebsocket && noCredentialsOrRouting
    }

    private fun providerAuthority(uri: URI): Boolean {
        val host = uri.host ?: return false
        if (!providerHost(host)) return false
        val authority = when (uri.port) {
            -1 -> host
            SECURE_PORT -> "$host:$SECURE_PORT"
            else -> return false
        }
        return uri.rawAuthority == authority
    }

    private fun providerHost(host: String): Boolean {
        val providerDomain = host.length <= MAX_HOST_LENGTH && host.lowercase(Locale.ROOT).endsWith(".dwellir.com")
        return providerDomain && host.split('.').all(::validLabel)
    }

    private fun validLabel(label: String): Boolean {
        if (label.length !in 1..MAX_LABEL_LENGTH) return false
        return asciiAlphanumeric(label.first()) && asciiAlphanumeric(label.last()) &&
            label.all { asciiAlphanumeric(it) || it == '-' }
    }

    private fun asciiAlphanumeric(value: Char): Boolean = value in 'a'..'z' || value in 'A'..'Z' || value in '0'..'9'
}
