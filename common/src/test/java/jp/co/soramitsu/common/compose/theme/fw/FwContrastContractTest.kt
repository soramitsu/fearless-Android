package jp.co.soramitsu.common.compose.theme.fw

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Full shared pair inventory evaluated against the actual generated Compose colours. No pixel claim. */
class FwContrastContractTest {
    private val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
        .first { File(it, "design/fw.tokens.json").isFile }
    private val contractFile = File(root, "design/fw.contrast-pairs.json")
    private val contract = JsonParser.parseString(contractFile.readText()).asJsonObject
    private val tokens = JsonParser.parseString(File(root, "design/fw.tokens.json").readText()).asJsonObject

    @Test fun sharedContractBytesAndFullNativePairInventoryArePinned() {
        val sha = MessageDigest.getInstance("SHA-256").digest(contractFile.readBytes()).joinToString("") { "%02x".format(it) }
        assertEquals("c1cda5d513e1095459004426104ef8d2e80f4bb3dc96d6b2012904ccfd180737", sha)
        assertEquals("fearless.contrast-pairs", contract.get("format").asString)
        assertEquals(1, contract.get("version").asInt)
        assertEquals(33, contract.getAsJsonArray("pairs").size())
        assertEquals(1, contract.getAsJsonArray("cvd").size())
        var total = 0
        for (colors in listOf(FwStandard, FwHighContrast)) total += assertAllPairs(Recipes(colors))
        assertEquals("172 permitted combinations under each actual Compose palette", 344, total)
    }

    @Test fun nativeWorstBackdropsAndControlCompositesRetainReferenceContrast() {
        // Compose alpha is stored as an 8-bit channel; these differ from the unquantized reference.
        val recipes = Recipes(FwStandard)
        assertEquals(listOf(71.0, 50.0, 107.0), recipes.resolve("peakAllDither").single().rgb)
        assertEquals(7, recipes.resolve("chromeSurface").size)
        val glass = recipes.resolve("glassSurface").last()
        assertEquals(listOf(43.0, 40.0, 74.0), glass.rgb)
        val stroke = recipes.resolve("strokeControl").single()
        assertTrue(contrast(paint(stroke, glass), glass.rgb) >= 3.5)
        val high = Recipes(FwHighContrast).resolve("strokeControl").single()
        assertTrue(contrast(paint(high, glass), glass.rgb) >= 5.2)
        assertTrue(recipes.resolve("primaryFill").size > 50)
        assertEquals(21.0, contrast(listOf(0.0, 0.0, 0.0), listOf(255.0, 255.0, 255.0)), 0.0)
    }

    @Test fun nativePricePairPassesProtanAndDeutanFloorsWhileLegacyGreenRedFails() {
        val recipes = Recipes(FwStandard)
        val pair = contract.getAsJsonArray("cvd").single().asJsonObject
        assertEquals("price-up-down", pair.get("id").asString)
        assertEquals("priceUp", pair.get("foreground").asString)
        assertEquals("priceDown", pair.get("background").asString)
        assertEquals(40.0, pair.get("minimumDeltaE76").asDouble, 0.0)
        val up = recipes.resolve("priceUp").single().rgb
        val down = recipes.resolve("priceDown").single().rgb
        for ((simulation, expected) in listOf("protan" to 54, "deutan" to 67)) {
            val result = deltaE(up, down, simulation)
            assertTrue(result >= pair.get("minimumDeltaE76").asDouble)
            assertEquals(expected, result.roundToInt())
        }
        assertTrue(deltaE(listOf(14.0, 211.0, 59.0), listOf(240.0, 0.0, 4.0), "deutan") < 40.0)
        assertEquals(0.0, deltaE(up, up, "protan"), 0.0)
    }

    @Test fun actualControlOpacityRegressionCannotPassTheContract() {
        var rejected = false
        try {
            assertAllPairs(Recipes(FwStandard.copy(strokeControl = Color.White.copy(alpha = 0.28f))))
        } catch (failure: AssertionError) {
            assertTrue(failure.message.orEmpty().contains("control-boundary"))
            rejected = true
        }
        assertTrue("A decorative border cannot replace the interactive control boundary", rejected)
    }

    @Test fun gradientKeepsExactMidpointWhenChannelsMoveInOppositeDirections() {
        val stops = tokens.getAsJsonObject("gradient").getAsJsonObject("primaryFill").getAsJsonArray("\$value")
        stops[0].asJsonObject.getAsJsonObject("color").addProperty("hex", "#000100")
        stops[1].asJsonObject.getAsJsonObject("color").addProperty("hex", "#010000")
        assertTrue(Recipes(FwStandard).resolve("primaryFill").any { it.rgb == listOf(1.0, 1.0, 0.0) })
    }

    private fun assertAllPairs(recipes: Recipes): Int {
        var count = 0
        for (element in contract.getAsJsonArray("pairs")) {
            val group = element.asJsonObject
            val minimum = if (group.get("kind").asString == "text") 4.5 else 3.0
            assertEquals(minimum, group.get("minimum").asDouble, 0.0)
            for (fg in group.getAsJsonArray("foregrounds")) for (bg in group.getAsJsonArray("backgrounds")) {
                val foreground = recipes.resolve(fg.asString)
                val background = recipes.resolve(bg.asString)
                assertTrue(foreground.isNotEmpty() && background.isNotEmpty())
                for (back in background) for (front in foreground) {
                    assertEquals(1.0, back.alpha, 0.0)
                    val ratio = contrast(paint(front, back), back.rgb)
                    assertTrue("${group.get("id").asString}: ${fg.asString}/${bg.asString} ratio=$ratio < $minimum", ratio >= minimum)
                }
                count++
            }
        }
        return count
    }

    private data class Paint(val rgb: List<Double>, val alpha: Double = 1.0)

    private inner class Recipes(private val colors: FwColors) {
        private val definitions = contract.getAsJsonObject("recipes")
        private val colorTokens = tokens.getAsJsonObject("color")
        fun resolve(name: String, visiting: Set<String> = emptySet()): List<Paint> {
            require(name !in visiting) { "Cyclic recipe $name" }
            val next = visiting + name
            if (colorTokens.getAsJsonObject("semantic").has(name)) {
                val color = Color(FwColors::class.java.getDeclaredField(name).apply { isAccessible = true }.getLong(colors).toULong())
                return listOf(argb(color.toArgb()))
            }
            val recipe = requireNotNull(definitions.getAsJsonObject(name)) { "Missing recipe $name" }
            if (recipe.has("token")) return resolve(recipe.get("token").asString, next)
            if (recipe.has("aurora")) {
                val value = colorTokens.getAsJsonObject("aurora").getAsJsonObject(recipe.get("aurora").asString).getAsJsonObject("\$value")
                return listOf(fromJson(value))
            }
            if (recipe.has("variants")) {
                val values = recipe.getAsJsonArray("variants")
                require(values.size() > 0)
                return values.flatMap { resolve(it.asString, next) }
            }
            if (recipe.has("gradient")) return gradient(recipe.get("gradient").asString)
            val backgrounds = resolve(recipe.get("background").asString, next)
            val foregrounds = resolve(recipe.get("over").asString, next)
            val opacity = when {
                recipe.has("alpha") -> byteAlpha(recipe.get("alpha").asDouble)
                recipe.has("alphaSource") -> argb(if (recipe.get("alphaSource").asString == "info") colors.info.container(FW_INFO_CONTAINER_ALPHA).toArgb() else colors.success.container().toArgb()).alpha
                else -> null
            }
            return backgrounds.flatMap { bg ->
                require(bg.alpha == 1.0)
                foregrounds.map { fg -> Paint(paint(fg.copy(alpha = opacity ?: fg.alpha), bg)) }
            }
        }

        private fun gradient(name: String): List<Paint> {
            val stops = tokens.getAsJsonObject("gradient").getAsJsonObject(name).getAsJsonArray("\$value")
                .map { fromJson(it.asJsonObject.getAsJsonObject("color")) }
            val result = linkedSetOf<Paint>()
            for ((a, b) in stops.zipWithNext()) {
                require(a.alpha == 1.0 && b.alpha == 1.0)
                val positions = mutableSetOf(0.0, 1.0)
                for (c in 0..2) {
                    val delta = b.rgb[c] - a.rgb[c]
                    for (k in min(a.rgb[c], b.rgb[c]).toInt() until max(a.rgb[c], b.rgb[c]).toInt()) {
                        val t = (k + 0.5 - a.rgb[c]) / delta
                        positions += t
                        positions += max(0.0, t - 1e-10)
                        positions += min(1.0, t + 1e-10)
                    }
                }
                for (t in positions) result += Paint((0..2).map { (a.rgb[it] + (b.rgb[it] - a.rgb[it]) * t).roundToInt().toDouble() })
            }
            return result.toList()
        }
    }

    private fun argb(value: Int) = Paint(listOf(16, 8, 0).map { ((value ushr it) and 255).toDouble() }, ((value ushr 24) and 255) / 255.0)
    private fun byteAlpha(alpha: Double) = (alpha * 255).roundToInt() / 255.0
    private fun fromJson(value: JsonObject): Paint {
        val rgb = value.get("hex").asString.removePrefix("#").toInt(16)
        return Paint(listOf(16, 8, 0).map { ((rgb ushr it) and 255).toDouble() }, byteAlpha(value.get("alpha")?.asDouble ?: 1.0))
    }
    private fun paint(fg: Paint, bg: Paint) = (0..2).map { (fg.rgb[it] * fg.alpha + bg.rgb[it] * (1 - fg.alpha)).roundToInt().toDouble() }
    private fun linear(c: Double) = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    private fun contrast(a: List<Double>, b: List<Double>): Double {
        fun luminance(rgb: List<Double>) = rgb.map { linear(it / 255) }.zip(listOf(0.2126, 0.7152, 0.0722)).sumOf { it.first * it.second }
        val x = luminance(a)
        val y = luminance(b)
        return (max(x, y) + 0.05) / (min(x, y) + 0.05)
    }

    private fun deltaE(a: List<Double>, b: List<Double>, simulation: String): Double {
        val matrix = when (simulation) {
            "protan" -> listOf(listOf(0.152286, 1.052583, -0.204868), listOf(0.114503, 0.786281, 0.099216), listOf(-0.003882, -0.048116, 1.051998))
            "deutan" -> listOf(listOf(0.367322, 0.860646, -0.227968), listOf(0.280085, 0.672501, 0.047413), listOf(-0.011820, 0.042940, 0.968881))
            else -> error("Unknown simulation $simulation")
        }
        fun lab(rgb: List<Double>): List<Double> {
            val channels = rgb.map { linear(it / 255) }
            val transformed = matrix.map { row ->
                val v = row.zip(channels).sumOf { it.first * it.second }.coerceIn(0.0, 1.0)
                val gamma = if (v <= 0.0031308) 12.92 * v else 1.055 * v.pow(1 / 2.4) - 0.055
                linear(gamma)
            }
            val (r, g, blue) = transformed
            fun f(v: Double) = if (v > 0.008856) v.pow(1.0 / 3) else 7.787 * v + 16.0 / 116
            val x = f((0.4124 * r + 0.3576 * g + 0.1805 * blue) / 0.95047)
            val y = f(0.2126 * r + 0.7152 * g + 0.0722 * blue)
            val z = f((0.0193 * r + 0.1192 * g + 0.9505 * blue) / 1.08883)
            return listOf(116 * y - 16, 500 * (x - y), 200 * (y - z))
        }
        return sqrt(lab(a).zip(lab(b)).sumOf { (it.first - it.second).pow(2) })
    }
}
