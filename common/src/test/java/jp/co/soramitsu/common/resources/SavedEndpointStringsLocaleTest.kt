package jp.co.soramitsu.common.resources

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class SavedEndpointStringsLocaleTest {
    @Test fun everyShippedLocaleContainsAllRecoveryMessagesAndExactFormatArguments() {
        val res = File("src/main/res")
        val directories = res.listFiles { file -> file.isDirectory &&
            file.name.matches(Regex("values(-[a-zA-Z+-]+)?")) && File(file, "strings.xml").isFile
        }.orEmpty().toList()
        assertEquals(setOf("values", "values-in", "values-ja", "values-pt", "values-ru", "values-tr", "values-vi", "values-zh"),
            directories.map { it.name }.toSet())
        val english = strings(File(res, "values/strings.xml"))
        assertEquals(82, english.size)
        assertEquals(setOf("intro", "add", "restore", "working", "added", "restored", "present_active",
            "present_inactive", "mismatch", "invalid", "unreachable", "large", "timeout", "busy", "failed", "source_changed"),
            english.keys.filter { it.startsWith("saved_endpoint_attachment_") }
                .map { it.removePrefix("saved_endpoint_attachment_") }.toSet())
        directories.forEach { directory ->
            val localized = strings(File(directory, "strings.xml"))
            assertEquals(directory.name, english.keys, localized.keys)
            localized.forEach { (key, value) ->
                assertTrue("${directory.name}: $key", value.isNotBlank())
                val arguments = Regex("%[0-9]+\\$[sd]")
                assertEquals("${directory.name}: $key", arguments.findAll(english.getValue(key)).map { it.value }.toList(),
                    arguments.findAll(value).map { it.value }.toList())
                if (directory.name != "values") assertFalse("${directory.name}: $key", value == english.getValue(key))
            }
        }
    }

    private fun strings(file: File): Map<String, String> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
        val result = linkedMapOf<String, String>()
        (0 until nodes.length).forEach { index ->
            val element = nodes.item(index) as Element
            val key = element.getAttribute("name")
            if ((key.startsWith("saved_endpoints_") || key.startsWith("saved_endpoint_"))) {
                assertFalse("duplicate $key in $file", result.containsKey(key))
                result[key] = element.textContent
            }
        }
        return result
    }
}
