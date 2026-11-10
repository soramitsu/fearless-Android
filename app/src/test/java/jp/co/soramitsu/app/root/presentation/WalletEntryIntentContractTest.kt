package jp.co.soramitsu.app.root.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Source contracts for the parts of the entry-Intent fix that need a real
 * Activity: the JVM cannot construct android.content.Intent.
 */
class WalletEntryIntentContractTest {

    @Test
    fun `wallet root reduces its Intent before the NavHost graph is created`() {
        val source = source("WalletRootActivity.kt")
        val onCreate = source.substringAfter(
            "override fun onCreate(savedInstanceState: Bundle?)"
        ).substringBefore("override fun onStart()")

        val sanitizeIndex = onCreate.indexOf("setIntent(")
        val handoffIndex = onCreate.indexOf("intent.toWalletInternalHandoff(")
        val targetIndex = onCreate.indexOf(
            "target = WalletRootActivity::class.java"
        )
        val superIndex = onCreate.indexOf("super.onCreate(savedInstanceState)")

        assertTrue(sanitizeIndex >= 0)
        assertTrue(handoffIndex > sanitizeIndex)
        assertTrue(targetIndex > handoffIndex)
        assertTrue(superIndex > targetIndex)
    }

    @Test
    fun `wallet root passes only the reduced Intent to new-intent listeners`() {
        val source = source("WalletRootActivity.kt")
        val onNewIntent = source.substringAfter(
            "override fun onNewIntent(intent: Intent)"
        ).substringBefore("@SuppressLint")

        val sanitizeIndex = onNewIntent.indexOf(
            "val entryIntent = intent.toWalletInternalHandoff("
        )
        val superIndex = onNewIntent.indexOf("super.onNewIntent(entryIntent)")

        assertTrue(sanitizeIndex >= 0)
        assertTrue(superIndex > sanitizeIndex)
        assertFalse(onNewIntent.contains("super.onNewIntent(intent)"))
    }

    @Test
    fun `internal handoff is built from scratch instead of copying the caller`() {
        val source = source("WalletStartupActivity.kt")
        val handoff = source.substringAfter(
            "internal fun Intent.toWalletInternalHandoff("
        ).substringBefore("abstract class WalletGateActivity")

        assertTrue(handoff.contains("WalletEntryIntentPolicy.admit("))
        assertTrue(handoff.contains("return Intent(context, target).apply {"))
        // Copying constructors and bulk setters would bring caller extras,
        // clip data or flags across the handoff again.
        listOf(
            "Intent(this)",
            "putExtras(",
            "replaceExtras(",
            "fillIn(",
            "setClipData(",
            "clipData =",
            "setSelector(",
            "selector =",
            "addCategory("
        ).forEach { forbidden ->
            assertFalse(forbidden, handoff.contains(forbidden))
        }
    }

    private fun source(fileName: String): String {
        return File(
            repositoryRoot(),
            "app/src/main/java/jp/co/soramitsu/app/root/presentation/$fileName"
        ).readText()
    }

    private fun repositoryRoot(): File {
        val workingDirectory = System.getProperty("user.dir")
            ?: error("The test process has no working directory")
        return generateSequence(File(workingDirectory)) {
            it.parentFile
        }.firstOrNull {
            File(it, "settings.gradle").isFile &&
                File(it, "app").isDirectory
        } ?: error("Cannot locate the Fearless Android repository root")
    }
}
