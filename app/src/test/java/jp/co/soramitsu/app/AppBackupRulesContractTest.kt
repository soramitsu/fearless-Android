package jp.co.soramitsu.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class AppBackupRulesContractTest {

    @Test
    fun `manifest replaces the library backup rules with the wallet rules`() {
        val application = parse("app/src/main/AndroidManifest.xml")
            .getElementsByTagName("application")
            .item(0) as Element

        assertEquals("false", application.getAttributeNS(ANDROID, "allowBackup"))
        assertEquals(
            "@xml/wallet_data_extraction_rules",
            application.getAttributeNS(ANDROID, "dataExtractionRules")
        )
        assertEquals(
            "@xml/wallet_full_backup_rules",
            application.getAttributeNS(ANDROID, "fullBackupContent")
        )
        // Without tools:replace the Reown rules, which exclude only their own
        // files, conflict with or win over the wallet rules in the merge.
        val replaced = application.getAttributeNS(TOOLS, "replace")
            .split(',')
            .map(String::trim)
        assertTrue(replaced.contains("android:dataExtractionRules"))
        assertTrue(replaced.contains("android:fullBackupContent"))
    }

    @Test
    fun `cloud backup and device transfer exclude every data domain`() {
        val rules = parse("app/src/main/res/xml/wallet_data_extraction_rules.xml")
            .documentElement

        assertEquals("data-extraction-rules", rules.tagName)
        assertEquals(
            listOf("cloud-backup", "device-transfer"),
            rules.childElements().map(Element::getTagName)
        )
        rules.childElements().forEach { section ->
            assertEquals(section.tagName, EVERY_DOMAIN_EXCLUDED, section.rules())
        }
    }

    @Test
    fun `full backup content excludes every data domain`() {
        val rules = parse("app/src/main/res/xml/wallet_full_backup_rules.xml")
            .documentElement

        assertEquals("full-backup-content", rules.tagName)
        assertEquals(EVERY_DOMAIN_EXCLUDED, rules.rules())
    }

    // Every rule as "tag domain path"; an include would re-add data.
    private fun Element.rules(): Set<String> = childElements()
        .map { rule -> "${rule.tagName} ${rule.getAttribute("domain")} ${rule.getAttribute("path")}" }
        .toSet()

    private fun Element.childElements(): List<Element> {
        val nodes = childNodes
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

    private fun parse(path: String): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        return factory.newDocumentBuilder().parse(File(repositoryRoot(), path))
    }

    private fun repositoryRoot(): File {
        val workingDirectory = System.getProperty("user.dir")
            ?: error("The test process has no working directory")
        return generateSequence(File(workingDirectory)) {
            it.parentFile
        }.firstOrNull {
            File(it, "settings.gradle").isFile &&
                File(it, "app").isDirectory
        } ?: error("Cannot locate the Fearless Android repository root")
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
        const val TOOLS = "http://schemas.android.com/tools"

        val EVERY_DOMAIN_EXCLUDED = listOf(
            "root",
            "file",
            "database",
            "sharedpref",
            "external",
            "device_root",
            "device_file",
            "device_database",
            "device_sharedpref"
        ).map { domain -> "exclude $domain ." }.toSet()
    }
}
