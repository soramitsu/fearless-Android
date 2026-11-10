package jp.co.soramitsu.common.data.network.config.signed

import okhttp3.HttpUrl

/**
 * Where the app's remote configuration is published and the key its manifest is signed with.
 *
 * Every file under `remote-config/` except images is listed with its SHA-256 in `remote-config-manifest.json`, which
 * is signed with the Ed25519 key below (`scripts/sign-remote-config.py`, `docs/security/remote-config-signing.md`).
 * The public key here must equal `config/remote-config-signing-key.json`; `scripts/verify-owned-remote-config.py`
 * checks that.
 */
object RemoteConfigTrust {
    const val REMOTE_CONFIG_PUBLIC_KEY_HEX = "4e2383c36737abeb3818c5a8a366e65ef83d3883891ecc30bcc832bb57702098"
    const val KEY_ID = "292d0fd4c8376d1f"

    const val HOST = "raw.githubusercontent.com"
    const val MANIFEST_PATH = "remote-config-manifest.json"
    const val MANIFEST_URL = "https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/remote-config-manifest.json"

    private val PREFIX_SEGMENTS = listOf("soramitsu", "fearless-Android", "master", "remote-config")

    // Images are shown, never interpreted; every other remote-config file is configuration and must be signed.
    private val UNSIGNED_SUFFIXES = listOf(".svg", ".png", ".jpg", ".jpeg", ".gif", ".webp")

    val publicKey: ByteArray
        get() = REMOTE_CONFIG_PUBLIC_KEY_HEX.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    /** The path below `remote-config/` of a signed configuration file at [url], or null for anything else. */
    fun signedPathOf(url: HttpUrl): String? {
        if (url.scheme != "https" || url.host != HOST || url.port != HttpUrl.defaultPort("https")) return null
        val segments = url.pathSegments
        if (segments.size <= PREFIX_SEGMENTS.size || segments.subList(0, PREFIX_SEGMENTS.size) != PREFIX_SEGMENTS) return null
        val path = segments.drop(PREFIX_SEGMENTS.size).joinToString("/")
        return path.takeIf { isSignedPath(it) }
    }

    /** Whether [path] (below `remote-config/`) names a configuration file the manifest must list. */
    fun isSignedPath(path: String): Boolean {
        val parts = path.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." }) return false
        if (path == MANIFEST_PATH) return false
        val lower = path.lowercase()
        return UNSIGNED_SUFFIXES.none { lower.endsWith(it) }
    }
}
