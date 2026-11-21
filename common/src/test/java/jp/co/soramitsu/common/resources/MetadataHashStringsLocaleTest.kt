package jp.co.soramitsu.common.resources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The refusal shown when a network's metadata hash cannot be computed exists, once, in every shipped locale. */
class MetadataHashStringsLocaleTest {

    private val keys = listOf("network_metadata_unverified_title", "network_metadata_unverified_message")

    @Test
    fun `every locale translates the metadata refusal`() {
        val root = File("src/main/res")
        val english = strings(File(root, "values/strings.xml"))
        listOf("in", "ja", "pt", "ru", "tr", "vi", "zh").forEach { locale ->
            val translated = strings(File(root, "values-$locale/strings.xml"))
            keys.forEach { key ->
                val text = translated[key]
                assertTrue("$locale $key", !text.isNullOrBlank())
                assertTrue("$locale $key must be translated", text != english[key])
            }
        }
        keys.forEach { assertTrue(it, !english[it].isNullOrBlank()) }
    }

    private fun strings(file: File): Map<String, String> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement.getElementsByTagName("string")
        val result = mutableMapOf<String, String>()
        for (index in 0 until nodes.length) {
            val node = nodes.item(index) as Element
            val name = node.getAttribute("name")
            if (name in keys) {
                assertEquals("duplicate $name in $file", null, result.put(name, node.textContent))
            }
        }
        return result
    }
}
