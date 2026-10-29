package jp.co.soramitsu.account.impl.presentation.common

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureScreenUsageContractTest {

    @Test
    fun `account screens leave the activity secure flag to SecureScreenGuard`() {
        // Recording a screen's own flag state and clearing it in onDestroy/onDestroyView drops
        // FLAG_SECURE under the next secure screen, because the incoming view is created before
        // the outgoing one is destroyed (for example a recovery screen opened from the PIN).
        val directUsers = mainSources()
            .filter { (_, text) -> "FLAG_SECURE" in text }
            .map { (path, _) -> path }
            .toSet()

        assertEquals(ALLOWED_DIRECT_USERS, directUsers)
    }

    @Test
    fun `screens that show or take secret material forward their lifecycle to the guard`() {
        val sources = mainSources().toMap()

        GUARDED_SCREENS.forEach { path ->
            val text = sources[path] ?: error("Missing $path")
            assertTrue(path, "SecureScreenGuard(this)" in text)
            listOf("onViewCreated()", "onStart()", "onStop()", "onDestroyView()").forEach { call ->
                assertTrue("$path does not forward $call", "secureScreen.$call" in text)
            }
        }
    }

    private fun mainSources(): List<Pair<String, String>> {
        val root = File(moduleRoot(), MAIN_SOURCES)
        return root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it.relativeTo(root).invariantSeparatorsPath to it.readText() }
            .toList()
    }

    private fun moduleRoot(): File {
        val workingDirectory = System.getProperty("user.dir")
            ?: error("The test process has no working directory")
        return generateSequence(File(workingDirectory)) { it.parentFile }
            .map { if (File(it, MAIN_SOURCES).isDirectory) it else File(it, "feature-account-impl") }
            .firstOrNull { File(it, MAIN_SOURCES).isDirectory }
            ?: error("Cannot locate the feature-account-impl module")
    }

    private companion object {
        const val MAIN_SOURCES = "src/main/java/jp/co/soramitsu/account/impl"
        const val PRESENTATION = "presentation"

        val ALLOWED_DIRECT_USERS = setOf(
            "$PRESENTATION/common/SecureScreenFlag.kt",
            "$PRESENTATION/common/SecureScreenGuard.kt",
            // A bottom sheet has its own window, which no fragment transition clears.
            "$PRESENTATION/mnemonic/backup/BackupMnemonicDialog.kt"
        )

        val GUARDED_SCREENS = listOf(
            "$PRESENTATION/exporting/ExportFragment.kt",
            "$PRESENTATION/importing/ImportAccountFragment.kt",
            "$PRESENTATION/mnemonic/backup/BackupMnemonicFragment.kt",
            "$PRESENTATION/mnemonic/confirm/ConfirmMnemonicFragment.kt",
            "$PRESENTATION/pincode/PincodeFragment.kt",
            "$PRESENTATION/recovery/ArchivedWalletRecoveryFragment.kt",
            "$PRESENTATION/recovery/SavedEndpointsFragment.kt"
        )
    }
}
