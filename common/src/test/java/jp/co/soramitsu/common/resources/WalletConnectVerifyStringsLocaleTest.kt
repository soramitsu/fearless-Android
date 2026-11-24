package jp.co.soramitsu.common.resources

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** The WalletConnect notices exist in every shipped locale with the same arguments. */
class WalletConnectVerifyStringsLocaleTest {
    @Test fun verifyNoticesExistInEveryShippedLocaleWithExactArguments() {
        val root = File("src/main/res")
        val original = entries(File(root, "values/walletconnect_verify.xml"))
        assertEquals(KEYS, original.keys)
        for (locale in listOf("in", "ja", "pt", "ru", "tr", "vi", "zh")) {
            val translated = entries(File(root, "values-$locale/walletconnect_verify.xml"))
            assertEquals("$locale keys", KEYS, translated.keys)
            for (key in KEYS) {
                val text = translated.getValue(key)
                assertTrue("$locale $key blank", text.isNotBlank())
                assertTrue("$locale $key too long", text.length <= MAX_LENGTH)
                assertEquals("$locale $key arguments", arguments(original.getValue(key)), arguments(text))
            }
        }
    }

    private fun entries(file: File): Map<String, String> {
        val children = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement.childNodes
        return (0 until children.length).mapNotNull { children.item(it) as? Element }
            .associate { it.getAttribute("name") to it.textContent }
    }

    private fun arguments(value: String): List<String> = Regex("%(?:[0-9]+\\$)?[sd]").findAll(value).map { it.value }.sorted().toList()

    private val KEYS = setOf(
        "walletconnect_origin_unverified",
        "walletconnect_origin_mismatch",
        "walletconnect_scam_blocked",
        "walletconnect_link_expired",
        "walletconnect_qr_invalid",
        "walletconnect_message_signed_title",
        "walletconnect_message_signed_text",
        "walletconnect_transaction_signed_title",
        "walletconnect_transaction_signed_text"
    )

    // Notices stay short: one sentence of warning and one of advice at most.
    private val MAX_LENGTH = 120
}
