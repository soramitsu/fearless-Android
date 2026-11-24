package jp.co.soramitsu.common.shikiban

import java.io.File
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The ported shikiban maths against the bundled kit file (brand kit v4, `tokens/shikiban.json`). */
class ShikibanTest {
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "design/fw.tokens.json").isFile }
    private val bundled = File(root, "common/src/main/res/raw/fw_shikiban.json")
    private val tokens = Shikiban.parse(bundled.readText())
    private val samples = tokens.flight.samples
    private fun u(i: Int) = i / 24f

    @Test
    fun shouldBundleTheKitFileByteForByte() {
        val kitCopy = File(root, "brand/yatagarasu/tokens/shikiban.json")
        assertTrue(kitCopy.readBytes().contentEquals(bundled.readBytes()))
        val digest = MessageDigest.getInstance("SHA-256").digest(bundled.readBytes()).joinToString("") { "%02x".format(it) }
        assertEquals("6949daff2d3d4deef0a77712b3830ceaf0487560613ba2c5b623ca873f97a455", digest)
    }

    @Test
    fun shouldFollowTheDepthLawAndTheYamatoLadder() {
        assertEquals(900f, tokens.perspective)
        assertEquals(1f, tokens.depthScale(0f), 0f)
        assertEquals(0.5f, tokens.depthScale(900f), 1e-6f)
        // Gate i sits where its scale from the start is (1/√2)^i: the flat ad quadratum figure.
        tokens.gates.forEachIndexed { i, gate ->
            assertEquals(Math.pow(1 / sqrt(2.0), i.toDouble()).toFloat(), tokens.depthScale(gate.depth), 1e-5f)
        }
        assertEquals(listOf(90, 128, 180, 255, 360, 510, 720, 1020), tokens.durationsMs)
        assertEquals(tokens.gates.last().depth + tokens.flight.rest, tokens.flight.travel, 1e-2f)
        assertTrue(tokens.gates.last().mirror)
        assertEquals(5, tokens.gates.count { !it.mirror })
    }

    @Test
    fun shouldMatchTheKitsSampledCamera_andIris() {
        for (i in 0..24) {
            assertClose("camera $i", samples.camera[i], tokens.cameraAt(u(i)), 0.05f)
            assertClose("iris $i", samples.iris[i], tokens.irisScale(u(i)), 1e-3f)
        }
    }

    @Test
    fun shouldMatchTheKitsSampledGateScalesAndStrengths() {
        for (g in tokens.gates.indices) for (i in 0..24) {
            assertClose("gate $g at $i", samples.gates[g][i], tokens.gateScale(g, u(i)), 2e-3f)
            assertClose("gate alpha $g at $i", samples.gateAlpha[g][i], tokens.gateAlpha(g, u(i)), 2e-3f)
        }
        // At the start the gates are the flat figure: 1, 1/√2, 1/2 …
        assertEquals(1f, tokens.gateScale(0, 0f), 1e-6f)
        assertEquals(0.70711f, tokens.gateScale(1, 0f), 1e-5f)
        // A gate past the pass scale is gone.
        assertEquals(0f, tokens.gateScale(0, 0.5f))
        assertEquals(0f, tokens.gateAlpha(0, 0.5f))
        assertEquals(0f, tokens.gatesFade(0f))
        assertEquals(1f, tokens.gatesFade(tokens.flight.gateFadeIn))
    }

    @Test
    fun shouldMatchTheKitsSampledTiers_andEndEveryTierAtExactlyOne() {
        assertEquals(6, samples.tiers.size)
        for (k in samples.tiers.indices) {
            for (i in 0..23) assertClose("tier $k at $i", samples.tiers[k][i], tokens.tierScale(k, u(i)), 2e-4f)
            assertEquals("tier $k ends at 1", 1f, tokens.tierScale(k, 1f), 0f)
            // Farther tiers lag: the screen comes apart in depth on the way.
            if (k > 0) assertTrue(tokens.tierScale(k, 0.5f) < tokens.tierScale(k - 1, 0.5f))
        }
        // Tiers fade in from the mirror's opening, 45 ms apart, over 180 ms.
        assertEquals(0f, tokens.tierAlpha(0, 550f), 0f)
        assertEquals(0.5f, tokens.tierAlpha(0, 640f), 1e-5f)
        assertEquals(1f, tokens.tierAlpha(0, 730f), 0f)
        assertEquals(0f, tokens.tierAlpha(2, 640f), 0f)
        assertEquals(1f, tokens.tierAlpha(5, tokens.flight.durationMs.toFloat()), 0f)
    }

    @Test
    fun shouldOpenTheMirrorFromAPoint_andGrowWithTheGate() {
        val side = 1080f
        assertEquals(0f, tokens.irisRadius(549f, side), 0f)
        val open = tokens.irisRadius(550f + 180f, side)
        val expected = tokens.flight.baseRadius * side * tokens.irisScale(730f / 1270f) * tokens.mirror.cusp * tokens.flight.irisInset
        assertEquals(expected, open, 1e-2f)
        assertTrue(tokens.irisRadius(600f, side) < open)
        // By the end the opening has cleared any phone screen.
        assertTrue(tokens.irisRadius(1270f, side) > 3000f)
        assertEquals(0f, tokens.coronaStrength(550f))
        assertEquals(1f, tokens.coronaStrength(910f))
    }

    @Test
    fun shouldLandLaterArrivalsFromTheKitsScales() {
        for (k in 0 until 6) {
            assertClose("landing from $k", tokens.flight.landingFrom[k], tokens.landingScale(k, 0f), 1e-5f)
            assertEquals(1f, tokens.landingScale(k, 1f), 0f)
            val start = k * tokens.flight.tierStaggerMs.toFloat()
            assertEquals(tokens.flight.landingFrom[k], tokens.landingAt(k, start).first, 1e-5f)
            assertEquals(0f, tokens.landingAt(k, start).second, 0f)
            assertEquals(1f to 1f, tokens.landingAt(k, start + tokens.flight.landingMs))
        }
        // Fully opaque after 45 % of its time.
        assertEquals(1f, tokens.landingAt(0, 0.45f * 510f).second, 1e-4f)
        assertEquals(510 + 5 * 45, tokens.landingTotalMs(6))
        // easing.settle: arrives fast, rests.
        assertTrue(tokens.settle(0.3f) > 0.7f)
        assertEquals(0f, tokens.settle(0f))
        assertEquals(1f, tokens.settle(1f))
    }

    @Test
    fun shouldReadTheDialHonestly_includingFailuresAndIdle() {
        assertEquals(Shikiban.DialPhase.Idle, tokens.dialState(0, 0, 0).phase)
        val syncing = tokens.dialState(done = 9, failed = 0, total = 14)
        assertEquals(Shikiban.DialPhase.Syncing, syncing.phase)
        assertEquals(9f / 14f, syncing.lit, 1e-6f)
        assertEquals(1f, syncing.failedFrom, 0f)
        val partial = tokens.dialState(done = 12, failed = 2, total = 14)
        assertEquals(Shikiban.DialPhase.Partial, partial.phase)
        assertEquals(12f / 14f, partial.lit, 1e-6f)
        assertEquals(1f - 2f / 14f, partial.failedFrom, 1e-6f)
        assertTrue(partial.settled)
        assertEquals(Shikiban.DialPhase.Live, tokens.dialState(14, 0, 14).phase)
        // Out-of-range input never claims more than is known.
        val clamped = tokens.dialState(done = 20, failed = 5, total = 14)
        assertEquals(1f, clamped.lit, 0f)
        assertEquals(1f, clamped.failedFrom, 0f)
        // Segments keep their gaps dark.
        val gap = tokens.dial.gapTurn
        assertEquals(gap / 2f to 1f / 8f - gap / 2f, tokens.dialSegment(0, 0f, 1f))
        assertNull(tokens.dialSegment(1, 0f, 1f / 8f))
        assertEquals(8, tokens.dial.segments)
    }

    @Test
    fun shouldMapSealStagesToTheirStrokes() {
        assertEquals(listOf("ready", "signed", "sent", "accepted"), tokens.seal.stages)
        assertEquals(listOf("ring" to 0, "ring" to 1), tokens.seal.strokes["ready"])
        assertEquals(listOf("hexagram" to 0), tokens.seal.strokes["signed"])
        assertEquals(listOf("hexagram" to 1), tokens.seal.strokes["sent"])
        assertEquals(listOf("hexagram" to 2), tokens.seal.strokes["accepted"])
        assertEquals(mapOf("signed" to 2, "sent" to 4, "accepted" to 0), tokens.seal.stars)
        assertEquals(0.41f, tokens.sealProgress("accepted"), 0f)
        assertEquals(0f, tokens.sealProgress("unknown"), 0f)
        assertEquals(360, tokens.seal.stageMs)
        assertEquals(720, tokens.seal.igniteMs)
        assertEquals(510, tokens.seal.breakMs)
    }

    @Test
    fun shouldPlaceTheLoadersThreeCometsAThirdOfATurnApart() {
        val comets = tokens.loaderComets(0f)
        assertEquals(3, comets.size)
        assertEquals(0f, comets[0].first, 1e-6f)
        assertEquals(1f / 3f, comets[1].first, 1e-6f)
        assertEquals(-tokens.loader.tailTurn, comets[0].second, 1e-6f)
        val later = tokens.loaderComets(tokens.loader.secondsPerTurn / 4f)
        assertEquals(0.25f, later[0].first, 1e-5f)
        // One turn later they are back where they started.
        assertEquals(comets[2].first, tokens.loaderComets(tokens.loader.secondsPerTurn)[2].first, 1e-5f)
        // The rim's top is the top lobe's crown at y = -1; its cusps lie on the cusp circle.
        val (x0, y0) = tokens.rimPoint(0f)
        assertEquals(0f, x0, 1e-5f)
        assertEquals(-1f, y0, 1e-5f)
        val (xc, yc) = tokens.rimPoint(1f / 16f)
        assertEquals(tokens.mirror.cusp, sqrt(xc * xc + yc * yc), 1e-4f)
    }

    @Test
    fun shouldCarryTheAsanohaTileAndColours() {
        assertEquals(30, tokens.asanoha.lines.size)
        assertEquals(sqrt(3f), tokens.asanoha.height, 1e-4f)
        assertEquals(0xFF38E8E0.toInt(), tokens.colors.flight)
        assertEquals(0xFFFF5A4D.toInt(), tokens.colors.failure)
        assertEquals(5, tokens.flight.corona.size)
    }

    private fun assertClose(what: String, expected: Float, actual: Float, relative: Float) {
        val tolerance = maxOf(relative, abs(expected) * relative)
        assertTrue("$what: expected $expected, was $actual", abs(expected - actual) <= tolerance)
    }
}
