package jp.co.soramitsu.wallet.impl

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AnonymousFeeEstimateGuardTest {

    @Test
    fun `wallet production code does not use anonymous extrinsic fee estimates`() {
        val sourceRoot = File("src/main/java")
        val anonymousEstimate = Regex("""extrinsicService\s*\.\s*estimateFee\s*\(\s*chain\s*(?:,\s*false\s*)?\)\s*\{""")
        val offenders = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { anonymousEstimate.containsMatchIn(it.readText()) }
            .map { it.relativeTo(sourceRoot).path }
            .toList()

        assertTrue("Anonymous fee estimates must use a real signer account: $offenders", offenders.isEmpty())
    }
}
