package jp.co.soramitsu.common.resources

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The recovery and emergency key access copy ships in every locale with the
 * same placeholders, and never sends the user to support as the only way
 * forward (addendum U0.4).
 */
class WalletRecoveryStringsLocaleTest {

    @Test
    fun `recovery copy is translated in every shipped locale`() {
        val english = strings(File(RES, "values/strings.xml"))
        val keys = english.keys.filter { key ->
            key.startsWith("wallet_emergency_") ||
                key == "wallet_recovery_scoped_title" ||
                key == "wallet_recovery_required_message" ||
                key == "wallet_startup_recovery_required_message"
        }
        assertTrue(keys.size >= EXPECTED_MINIMUM_KEYS)

        localeDirectories().forEach { directory ->
            val localized = strings(File(directory, "strings.xml"))
            keys.forEach { key ->
                val value = localized[key]
                assertTrue("${directory.name} misses $key", !value.isNullOrBlank())
                assertEquals(
                    "${directory.name} $key placeholders",
                    placeholders(english.getValue(key)),
                    placeholders(checkNotNull(value))
                )
                if (directory.name != "values") {
                    assertFalse(
                        "${directory.name} $key is still English",
                        value == english.getValue(key) && key !in SAME_IN_SOME_LANGUAGES
                    )
                }
            }
        }
    }

    @Test
    fun `the earlier extension address choice is translated in every shipped locale`() {
        val english = strings(File(RES, "values/strings.xml"))
        val keys = english.keys.filter { it.startsWith("substrate_password_choice_") }
        assertEquals(5, keys.size)

        localeDirectories().forEach { directory ->
            val localized = strings(File(directory, "strings.xml"))
            keys.forEach { key ->
                val value = localized[key]
                assertTrue("${directory.name} misses $key", !value.isNullOrBlank())
                if (directory.name != "values") {
                    assertFalse("${directory.name} $key is still English", value == english.getValue(key))
                }
            }
        }
    }

    @Test
    fun `recovery copy no longer makes support the only option`() {
        val english = strings(File(RES, "values/strings.xml"))
        listOf("wallet_startup_recovery_required_message", "wallet_recovery_required_message").forEach {
            assertFalse(it, english.getValue(it).contains("support", ignoreCase = true))
        }
    }

    private fun localeDirectories(): List<File> =
        RES.listFiles { file ->
            file.isDirectory && file.name.matches(VALUES_DIRECTORY) && File(file, "strings.xml").exists()
        }.orEmpty().sortedBy { it.name }

    private fun strings(file: File): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length).associate { index ->
            val element = nodes.item(index) as Element
            element.getAttribute("name") to element.textContent
        }
    }

    private fun placeholders(value: String): List<String> =
        PLACEHOLDER.findAll(value).map { it.value }.sorted().toList()

    private companion object {
        val RES = File("src/main/res")
        val VALUES_DIRECTORY = Regex("^values(-[a-zA-Z+-]+)?$")
        val PLACEHOLDER = Regex("%\\d\\$[sd]")
        const val EXPECTED_MINIMUM_KEYS = 37

        // Technical terms some locales deliberately keep in English.
        val SAME_IN_SOME_LANGUAGES = setOf(
            "wallet_emergency_field_nonce",
            "wallet_emergency_field_seed",
            "wallet_emergency_field_entropy"
        )
    }
}
