package jp.co.soramitsu.runtime.multiNetwork.chain.remote

import com.google.gson.JsonParser
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The mutation route manifest binds the bundled route inventory by SHA-256, and the APK refuses every new XCM mutation
 * when a digest does not match. Editing either bundled file without refreshing the manifest silently turns execution
 * off (or, if the manifest is later regenerated from a different file, binds authority to unreviewed content), so the
 * committed pair is checked here.
 */
class BundledMutationRouteManifestTest {

    @Test
    fun `route manifest digests match the bundled route inventory`() {
        val manifest = JsonParser.parseString(file("common/src/main/assets/mutation_route_manifest.json").readText()).asJsonObject
        val files = manifest.getAsJsonObject("files")

        assertEquals("1", manifest.get("schema").asString)
        assertEquals(setOf("approved_xcm_routes.tsv", "local_chains.json"), files.keySet())
        files.keySet().forEach { name ->
            assertEquals(
                "$name changed without refreshing common/src/main/assets/mutation_route_manifest.json",
                files.get(name).asString,
                sha256(file("runtime/src/main/assets/$name").readBytes())
            )
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(separator = "") { "%02x".format(it) }

    /** Unit tests run from the module directory; the repository root is its parent. */
    private fun file(relativeToRepository: String): File = listOf(
        File("../$relativeToRepository"),
        File(relativeToRepository)
    ).firstOrNull(File::isFile) ?: error("$relativeToRepository not found from ${File(".").absolutePath}")
}
