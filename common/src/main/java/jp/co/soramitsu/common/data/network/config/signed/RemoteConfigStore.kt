package jp.co.soramitsu.common.data.network.config.signed

import java.io.File
import java.io.IOException

/**
 * The last verified remote configuration, kept in app-private storage: the newest signed manifest and the last copy of
 * each file that matched a signed manifest. A file is stored under the hash of its path and named with the hash of its
 * content, so a damaged copy is never returned.
 */
class RemoteConfigStore(private val directory: File) {

    private val filesDirectory = File(directory, "files")
    private val manifestFile = File(directory, "manifest.json")

    /** The last manifest that verified, re-verified now; null when there is none or it no longer verifies. */
    fun lastManifest(verify: (ByteArray) -> SignedRemoteConfigManifest): SignedRemoteConfigManifest? =
        runCatching { if (manifestFile.isFile) verify(manifestFile.readBytes()) else null }.getOrNull()

    fun saveManifest(manifest: SignedRemoteConfigManifest) = writeAtomically(manifestFile, manifest.envelope)

    /** The last verified content of [path], or null. */
    fun lastVerified(path: String): ByteArray? {
        val prefix = SignedRemoteConfigManifest.sha256Hex(path.toByteArray(Charsets.UTF_8)) + "-"
        val candidates = filesDirectory.listFiles { file -> file.name.startsWith(prefix) }.orEmpty()
        return candidates.firstNotNullOfOrNull { file ->
            runCatching { file.readBytes() }.getOrNull()?.takeIf { SignedRemoteConfigManifest.sha256Hex(it) == file.name.removePrefix(prefix) }
        }
    }

    fun saveVerified(path: String, content: ByteArray) {
        val prefix = SignedRemoteConfigManifest.sha256Hex(path.toByteArray(Charsets.UTF_8)) + "-"
        val target = File(filesDirectory, prefix + SignedRemoteConfigManifest.sha256Hex(content))
        writeAtomically(target, content)
        filesDirectory.listFiles { file -> file.name.startsWith(prefix) && file.name != target.name }.orEmpty().forEach { it.delete() }
    }

    private fun writeAtomically(target: File, content: ByteArray) {
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, target.name + ".tmp")
        temporary.writeBytes(content)
        if (!temporary.renameTo(target)) {
            temporary.delete()
            throw IOException("Cannot store verified remote configuration")
        }
    }
}
