package jp.co.soramitsu.fearless_utils.scale.polkaj

import jp.co.soramitsu.fearless_utils.encrypt.CryptoTestVectors
import jp.co.soramitsu.fearless_utils.encrypt.string
import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.MessageDigest

/**
 * The SCALE codec built from source (package io.emeraldpay.polkaj.scale) reproduces, line for line, the transcript
 * the retired polkaj-scale-0.2.3.jar produced for every reader, writer and helper.
 * tools/upgrade-fixtures/android/LegacyCryptoFixtures.java recorded the jar's transcript digests.
 */
class PolkajScaleGoldenTest {

    @Test
    fun `source codec reproduces the polkaj-scale-0_2_3 jar transcript`() {
        val fixture = CryptoTestVectors.json("legacy-crypto/polkaj-scale-0.2.3-jar-transcript.json")
        val lines = PolkajScaleExercise.run(fixture.get("seed").asLong, fixture.get("rounds").asInt)
        val chunk = fixture.get("chunkLines").asInt
        val chunks = fixture.getAsJsonArray("chunks").map { it.asString }

        assertEquals(fixture.get("lines").asInt, lines.size)
        chunks.forEachIndexed { index, expected ->
            val from = index * chunk
            val to = minOf(lines.size, from + chunk)
            assertEquals("transcript lines $from until $to, starting: ${lines[from]}", expected, digest(lines.subList(from, to)))
        }
        assertEquals(fixture.string("sha256"), digest(lines))
    }

    private fun digest(lines: List<String>): String {
        val sha256 = MessageDigest.getInstance("SHA-256")
        lines.forEach { line ->
            sha256.update(line.encodeToByteArray())
            sha256.update('\n'.code.toByte())
        }
        return sha256.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
