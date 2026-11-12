package jp.co.soramitsu.common.compose.theme.fw

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import java.io.File
import java.math.BigDecimal
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Brand kit SPEC "Launch, feel and sound", part 3: the sky card, the solar figure, the lit rim and the living balance. */
class FwIgniteDesignTest {
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "design/fw.tokens.json").isFile }

    // ---- Solar geometry: the kit file, copied byte for byte.

    @Test
    fun shouldReadTheWholeFigure_whenParsingTheKitFile() {
        val kit = File(root, "brand/yatagarasu/tokens/solar-geometry.json").readText()
        assertEquals(kit, File(root, "common/src/main/res/raw/fw_solar_geometry.json").readText())
        val geometry = FwSolarGeometry.parse(kit)
        // Two rings, the hexagram's six lines, 22 lattice paths, ten lattice circles and the long ray.
        assertEquals(41, geometry.strokes.size)
        assertEquals(setOf("ring", "hexagram", "lattice", "ray"), geometry.strokes.map { it.group }.toSet())
        assertEquals(16, geometry.stars.size)
        assertEquals(132, geometry.ticks.count)
        assertTrue(geometry.ticks.turnsPerSecond < 0f)
        assertEquals(FwSolarGeometry.parseHex("#FFD58A"), geometry.core)
        // Every stroke finishes drawing by the end of the ignite.
        assertTrue(geometry.strokes.all { fwRevealFraction(1f, it.revealStart, it.revealEnd) == 1f })
        assertTrue(geometry.strokes.all { fwRevealFraction(0f, it.revealStart, it.revealEnd) == 0f })
    }

    @Test
    fun shouldDrawEachStrokeOverItsOwnWindow() {
        assertEquals(0f, fwRevealFraction(0.25f, 0.26f, 0.34f), 0f)
        assertEquals(0.5f, fwRevealFraction(0.30f, 0.26f, 0.34f), 1e-5f)
        assertEquals(1f, fwRevealFraction(0.40f, 0.26f, 0.34f), 0f)
    }

    @Test
    fun shouldReadOnlyHexColours() {
        assertEquals(Color(red = 1f, green = 194 / 255f, blue = 74 / 255f), FwSolarGeometry.parseHex("#FFC24A"))
        assertTrue(runCatching { FwSolarGeometry.parseHex("gold") }.isFailure)
    }

    // ---- Sky card: placement and the scrim that keeps every text at 4.5:1.

    @Test
    fun shouldPutTheSunOnTheBadge_whileCoveringTheCard() {
        val place = fwSkyPlacement(360f, 330f, 1200f, 675f, anchorX = 180f, anchorY = 76f)
        val (left, top, width, height) = place.toList()
        assertTrue(left <= 0f && top <= 0f && left + width >= 360f && top + height >= 330f)
        assertEquals(180f, left + FwSkyScrim.SUN_X * width, 0.5f)
        assertEquals(76f, top + FwSkyScrim.SUN_Y * height, 0.5f)
    }

    @Test
    fun shouldStillCoverTheCard_whenTheSunCannotReachTheAnchor() {
        val (left, top, width, height) = fwSkyPlacement(360f, 330f, 1200f, 675f, anchorX = 0f, anchorY = 0f).toList()
        assertTrue(left <= 0f && top <= 0f && left + width >= 360f && top + height >= 330f)
        val cover = max(360f / 1200f, 330f / 675f)
        assertTrue(width / 1200f <= cover * FwSkyScrim.MAX_ZOOM + 1e-4f)
    }

    @Test
    fun shouldDarkenToTheTextLevel_whereTheTextStarts() {
        val stops = fwSkyScrimStops(height = 330f, badgeY = 72f, textTop = 124f)
        assertEquals(listOf(0f to FwSkyScrim.TOP, 72f / 330f to FwSkyScrim.AT_BADGE, 124f / 330f to FwSkyScrim.TEXT, 1f to FwSkyScrim.BOTTOM), stops)
        // Below the text's top the scrim never falls under the text level.
        assertTrue(stops.filter { it.first >= 124f / 330f }.all { it.second >= FwSkyScrim.TEXT })
        // Positions only increase, even when the text starts at the badge.
        val squeezed = fwSkyScrimStops(330f, 72f, 60f)
        assertTrue(squeezed.zipWithNext().all { (a, b) -> b.first > a.first })
        assertTrue(squeezed.last().second >= FwSkyScrim.TEXT)
    }

    /**
     * The brightest pixel of the kit's `png/sky/sky-card.webp` (sRGB 254, 234, 122 at 652, 253 of
     * 1200x675, measured with sharp on 2026-11-13). Under the scrim at the text level every text
     * colour on the hero must still read 4.5:1; ink is composited in sRGB as Android does.
     */
    @Test
    fun shouldKeepEveryHeroTextAtLeast45To1_overTheBrightestSky() {
        val sky = intArrayOf(254, 234, 122)
        val ink = FwStandard.brandInk
        val inkRgb = intArrayOf((ink.red * 255).roundToInt(), (ink.green * 255).roundToInt(), (ink.blue * 255).roundToInt())
        val a = FwSkyScrim.TEXT
        val blended = Color(
            red = (a * inkRgb[0] + (1 - a) * sky[0]).roundToInt() / 255f,
            green = (a * inkRgb[1] + (1 - a) * sky[1]).roundToInt() / 255f,
            blue = (a * inkRgb[2] + (1 - a) * sky[2]).roundToInt() / 255f
        )
        val texts = mapOf(
            "textPrimary" to FwStandard.textPrimary, "textSecondary" to FwStandard.textSecondary,
            "textTertiary" to FwStandard.textTertiary, "priceUp" to FwStandard.priceUp,
            "priceDown" to FwStandard.priceDown, "warning" to FwStandard.warning
        )
        for ((name, color) in texts) {
            val ratio = contrast(color, blended)
            assertTrue("$name reads $ratio:1", ratio >= 4.5)
        }
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    // ---- Lit rim.

    @Test
    fun shouldLightTheRimGoldThenPink_asTheSpecSays() {
        assertEquals(0.55f, FwLitRimTokens.GOLD_ALPHA, 0f)
        assertEquals(0.30f, FwLitRimTokens.PINK_ALPHA, 0f)
        assertEquals(Color(0xFFFFC24A), FwStandard.brandGold)
        assertEquals(Color(0xFFFF4FA3), FwStandard.sunRim)
        assertTrue(AURORA_PINK < 0.18f && AURORA_GOLD < 0.18f)
    }

    // ---- Living balance: the number counts, the formatting stays the app's.

    @Test
    fun shouldSplitAFormattedTotal_aroundItsNumber() {
        val dollars = FwAmountParts.parse("$1,234.56")!!
        assertEquals("$", dollars.prefix)
        assertEquals(BigDecimal("1234.56"), dollars.value)
        assertEquals(2, dollars.decimals)
        assertEquals('.', dollars.decimalSeparator)
        assertEquals(',', dollars.groupSeparator)
        assertEquals("$0.00", dollars.format(BigDecimal.ZERO))
        assertEquals("$617.28", dollars.format(BigDecimal("617.28")))
        assertEquals("$1,234.56", dollars.format(dollars.value))

        val euros = FwAmountParts.parse("1.234,5 €")!!
        assertEquals(BigDecimal("1234.5"), euros.value)
        assertEquals(" €", euros.suffix)
        assertEquals("1.234,5 €", euros.format(euros.value))

        val small = FwAmountParts.parse("$0.0906597")!!
        assertEquals(7, small.decimals)
        assertEquals("$0.0453298", small.format(BigDecimal("0.04532985")))

        assertEquals(BigDecimal("1234"), FwAmountParts.parse("$1,234")!!.value)
        assertEquals("¥1,234,567", FwAmountParts.parse("¥1,234,567")!!.format(BigDecimal("1234567")))
    }

    @Test
    fun shouldNotCount_whatIsNotOnePlainNumber() {
        assertNull(FwAmountParts.parse("—"))
        assertNull(FwAmountParts.parse("••••"))
        assertNull(FwAmountParts.parse("1 DOT + 2 KSM"))
    }

    @Test
    fun shouldCountForSixHundredMilliseconds() {
        assertEquals(600, FwCountUpTiming.COUNT_MS)
    }
}
