package jp.co.soramitsu.common.data.network.config.signed

import com.google.gson.JsonParser
import java.io.InputStream

/**
 * The remote configuration bundled into the APK at build time from this repository's `remote-config` (see
 * `common/build.gradle`, `bundleRemoteConfigSnapshot`): the files the app fetches, each pinned to the SHA-256 recorded
 * in `config/remote-config-imports.json`, and the signed manifest of that commit.
 */
class RemoteConfigSnapshot(private val open: (String) -> InputStream?) {

    private val index: Map<String, String> by lazy {
        val text = runCatching { open(INDEX)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull() ?: return@lazy emptyMap()
        runCatching {
            JsonParser.parseString(text).asJsonObject.getAsJsonObject("files").entrySet()
                .associate { (path, digest) -> path to digest.asString }
        }.getOrDefault(emptyMap())
    }

    /** The bundled content of [path], or null when it is not bundled or does not match its pinned hash. */
    fun read(path: String): ByteArray? {
        val expected = index[path] ?: return null
        val content = runCatching { open("$FILES/$path")?.use { it.readBytes() } }.getOrNull() ?: return null
        return content.takeIf { SignedRemoteConfigManifest.sha256Hex(it) == expected }
    }

    /** The signed manifest bundled with the snapshot, as published at build time. */
    fun manifestEnvelope(): ByteArray? = runCatching { open(MANIFEST)?.use { it.readBytes() } }.getOrNull()

    companion object {
        const val ROOT = "remote-config-snapshot"
        private const val INDEX = "$ROOT/index.json"
        private const val FILES = "$ROOT/files"
        private const val MANIFEST = "$ROOT/${RemoteConfigTrust.MANIFEST_PATH}"
    }
}
