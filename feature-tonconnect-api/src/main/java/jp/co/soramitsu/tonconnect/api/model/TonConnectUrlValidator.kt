package jp.co.soramitsu.tonconnect.api.model

import java.net.IDN
import java.net.InetAddress
import java.net.URI
import java.util.Locale

object TonConnectUrlValidator {

    private val ipv4Regex = Regex("^(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}$")
    private val decimalIpv4Regex = Regex("^\\d+$")
    private val shortIpv4Regex = Regex("^\\d+(\\.\\d+){1,3}$")
    private val hexIpv4Regex = Regex("^0x[0-9a-f]+$", RegexOption.IGNORE_CASE)
    private const val TON_API_HOST = "tonapi.io"

    fun normalizeManifestUrl(value: String): String {
        val parsed = parseAndValidateHttpsUri(value)
        val path = normalizePath(parsed)
        val query = parsed.rawQuery?.let { "?$it" }.orEmpty()

        return "${originFromParsed(parsed)}$path$query"
    }

    fun normalizeDappUrl(value: String): String {
        val parsed = parseAndValidateHttpsUri(value)
        val path = normalizePath(parsed)

        return "${originFromParsed(parsed)}$path"
    }

    fun origin(value: String): String {
        val parsed = parseAndValidateHttpsUri(value)
        return originFromParsed(parsed)
    }

    fun host(value: String): String? {
        return runCatching {
            parseAndValidateHttpsUri(value).canonicalHost()
        }.getOrNull()
    }

    fun isSameOrigin(first: String, second: String): Boolean {
        return runCatching {
            val firstUri = parseAndValidateHttpsUri(first)
            val secondUri = parseAndValidateHttpsUri(second)

            firstUri.canonicalHost() == secondUri.canonicalHost() && firstUri.canonicalPort() == secondUri.canonicalPort()
        }.getOrDefault(false)
    }

    fun validateTonApiFetchUrl(value: String): String {
        val parsed = parseAndValidateHttpsUri(value)
        val host = parsed.canonicalHost()
        val port = parsed.canonicalPort()

        require(host == TON_API_HOST || host.endsWith(".$TON_API_HOST")) { "Unsupported host" }
        require(port == 443) { "Unsupported port" }

        val path = normalizePath(parsed)
        val query = parsed.rawQuery?.let { "?$it" }.orEmpty()

        return "${originFromParsed(parsed)}$path$query"
    }

    private fun parseAndValidateHttpsUri(value: String): URI {
        val sanitized = value.trim()
        require(sanitized.isNotBlank()) { "URL is empty" }

        val parsed = URI(sanitized).normalize()
        val scheme = parsed.scheme?.lowercase(Locale.ROOT)
        require(scheme == "https") { "Only https URLs are allowed" }
        require(parsed.userInfo == null) { "URL with userinfo is not allowed" }

        parsed.canonicalHost()

        return parsed
    }

    private fun normalizePath(uri: URI): String {
        val rawPath = uri.rawPath?.takeIf { it.isNotBlank() } ?: "/"
        return if (rawPath.length > 1) rawPath.removeSuffix("/") else rawPath
    }

    private fun originFromParsed(uri: URI): String {
        val host = uri.canonicalHost()
        val portSuffix = if (uri.canonicalPort() == 443) "" else ":${uri.canonicalPort()}"

        return "https://$host$portSuffix"
    }

    private fun URI.canonicalHost(): String {
        val rawHost = requireNotNull(host?.trim()?.trimEnd('.')?.lowercase(Locale.ROOT)) { "Missing host" }
        require(rawHost.isNotBlank()) { "Missing host" }

        val host = if (rawHost.isIpLiteral()) {
            requireNotNull(InetAddress.getByName(rawHost).hostAddress) { "Missing host" }.lowercase(Locale.ROOT)
        } else {
            IDN.toASCII(rawHost, IDN.ALLOW_UNASSIGNED).lowercase(Locale.ROOT)
        }
        validateHost(host)

        return host
    }

    private fun URI.canonicalPort(): Int {
        return if (port == -1) 443 else port
    }

    private fun validateHost(host: String) {
        require(host != "localhost") { "localhost is not allowed" }
        require(!host.endsWith(".localhost")) { "Local hosts are not allowed" }
        require(!host.endsWith(".local")) { "Local hosts are not allowed" }

        if (host.isIpLiteral()) {
            val address = InetAddress.getByName(host)
            require(!address.isAnyLocalAddress) { "Wildcard address is not allowed" }
            require(!address.isLoopbackAddress) { "Loopback address is not allowed" }
            require(!address.isLinkLocalAddress) { "Link-local address is not allowed" }
            require(!address.isSiteLocalAddress) { "Private address is not allowed" }
            require(!address.isMulticastAddress) { "Multicast address is not allowed" }
        }
    }

    private fun String.isIpLiteral(): Boolean {
        return ipv4Regex.matches(this) ||
            contains(':') ||
            decimalIpv4Regex.matches(this) ||
            shortIpv4Regex.matches(this) ||
            hexIpv4Regex.matches(this)
    }
}
