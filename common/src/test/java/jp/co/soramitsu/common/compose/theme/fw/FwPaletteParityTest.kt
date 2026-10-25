package jp.co.soramitsu.common.compose.theme.fw

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Native parity against the committed cross-platform source and the frozen original palette. */
class FwPaletteParityTest {
    private val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
        .first { File(it, "design/fw.tokens.json").isFile }

    @Test fun generatedHeadersAndEverySemanticColorMatchTheSharedSource() {
        val source = File(root, "design/fw.tokens.json")
        val sha = MessageDigest.getInstance("SHA-256").digest(source.readBytes()).joinToString("") { "%02x".format(it) }
        val kotlin = File(root, "common/src/main/java/jp/co/soramitsu/common/compose/theme/fw/FwColors.kt").readText()
        val xml = File(root, "common/src/main/res/values/fw_colors.xml")
        assertTrue(kotlin.contains("sha256 $sha"))
        assertTrue(xml.readText().contains("sha256 $sha"))
        val tokens = JsonParser.parseString(source.readText()).asJsonObject.getAsJsonObject("color")
        val resources = xmlColors(xml)
        for ((name, item) in tokens.getAsJsonObject("semantic").entrySet()) {
            if (name.startsWith("$")) continue
            val expected = tokenColor(item.asJsonObject)
            assertEquals(name, expected, memberColor(FwStandard, name))
            val resourceName = "fw_" + name.replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").lowercase()
            assertEquals(resourceName, expected.toArgb(), resources.getValue(resourceName).removePrefix("#").toLong(16).toInt())
        }
        for ((name, item) in tokens.getAsJsonObject("highContrast").entrySet()) {
            if (!name.startsWith("$")) assertEquals(name, tokenColor(item.asJsonObject), memberColor(FwHighContrast, name))
        }
    }

    @Test fun legacyComposeNightAndXmlAliasesPreserveTheFrozenPalette() {
        val source = File(root, "design/fw.legacy.android.json")
        val sha = MessageDigest.getInstance("SHA-256").digest(source.readBytes()).joinToString("") { "%02x".format(it) }
        assertTrue(File(root, "common/src/main/java/jp/co/soramitsu/common/compose/theme/fw/FwLegacyPalette.kt").readText().contains("sha256 $sha"))
        val palette = JsonParser.parseString(source.readText()).asJsonObject
        val expected = mutableMapOf<String, Color>()
        for ((name, item) in palette.getAsJsonObject("compose").entrySet()) {
            val entry = item.asJsonObject
            val color = if (entry.has("argb")) Color(entry.get("argb").asString.toLong(16)) else expected.getValue(entry.get("alias").asString)
            expected[name] = color
            val actual = Class.forName("jp.co.soramitsu.common.compose.theme.ColorKt").getDeclaredField(name)
                .apply { isAccessible = true }.getLong(null)
            assertEquals(name, color.value.toLong(), actual)
        }
        val night = Class.forName("jp.co.soramitsu.common.compose.theme.tokens.NightThemeColors")
        val original = Class.forName("jp.co.soramitsu.common.compose.theme.tokens.Colors")
        for ((name, item) in palette.getAsJsonObject("night").entrySet()) {
            val old = original.getDeclaredField(item.asString).apply { isAccessible = true }.getLong(null)
            assertEquals(name, old, night.getDeclaredField(name).apply { isAccessible = true }.getLong(null))
        }
        val originalXml = palette.getAsJsonObject("xml").entrySet().associate { it.key to it.value.asString }
        val all = xmlColors(File(root, "common/src/main/res/values/fw_legacy_colors.xml")) +
            xmlColors(File(root, "common/src/main/res/values/colors.xml"))
        fun resolve(values: Map<String, String>, name: String): String {
            val value = values.getValue(name)
            return if (value.startsWith("@color/")) resolve(values, value.removePrefix("@color/")) else value
        }
        for (name in originalXml.keys) assertEquals(name, resolve(originalXml, name), resolve(all, name))
    }

    @Test fun approvedTextFillAndControlPairsMeetContrastAndStatusContainersKeepTheirAlpha() {
        val c = FwStandard
        for (fill in listOf(c.fillTop, c.fillBottom, c.dangerFill)) assertTrue(contrast(c.textOnAccent, fill) >= 4.5)
        for (background in listOf(c.bgBase, c.surfaceRaised, c.surfaceRaised2, c.surfaceSunken)) {
            assertTrue(contrast(c.textPrimary, background) >= 4.5)
            assertTrue(contrast(c.textSecondary, background) >= 4.5)
            assertTrue(contrast(c.textTertiary, background) >= 4.5)
        }
        val glassWorst = Color(0xFF2B284B)
        assertTrue(contrast(c.strokeControl.compositeOver(glassWorst), glassWorst) >= 3.0)
        assertTrue(contrast(FwHighContrast.strokeControl.compositeOver(glassWorst), glassWorst) >= 5.0)
        assertEquals(c.warning.copy(alpha = 0.14f), c.warning.container())
        assertEquals(c.info.copy(alpha = 0.12f), c.info.container(FW_INFO_CONTAINER_ALPHA))
        assertTrue(contrast(c.qrField, c.qrModule) >= 19.0)
    }

    private fun tokenColor(token: JsonObject): Color {
        val value = token.getAsJsonObject("\$value")
        val rgb = value.get("hex").asString.removePrefix("#").toLong(16)
        val alpha = ((value.get("alpha")?.asDouble ?: 1.0) * 255).roundToInt()
        return Color((alpha.toLong() shl 24) or rgb)
    }

    private fun memberColor(colors: FwColors, name: String): Color = Color(
        FwColors::class.java.getDeclaredField(name).apply { isAccessible = true }.getLong(colors).toULong()
    )

    private fun xmlColors(file: File): Map<String, String> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("color")
        return (0 until nodes.length).associate { index ->
            val node = nodes.item(index)
            node.attributes.getNamedItem("name").nodeValue to node.textContent
        }
    }

    private fun contrast(a: Color, b: Color): Double {
        fun luminance(color: Color): Double {
            fun channel(value: Float): Double = if (value <= 0.04045f) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
            return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
        }
        val x = luminance(a)
        val y = luminance(b)
        return (max(x, y) + 0.05) / (min(x, y) + 0.05)
    }
}
