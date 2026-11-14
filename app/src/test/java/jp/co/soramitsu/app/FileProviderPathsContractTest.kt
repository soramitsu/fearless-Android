package jp.co.soramitsu.app

import jp.co.soramitsu.common.data.FileProviderImpl
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class FileProviderPathsContractTest {

    @Test
    fun `file provider serves only the export directory and the external cache`() {
        val paths = parse("app/src/main/res/xml/provider_paths.xml").documentElement
        val roots = paths.childElements().map { root ->
            "${root.tagName} ${root.getAttribute("path")}"
        }

        // Keystore exports come from the internal cache directory written by
        // FileProviderImpl; the receive QR image is in the external cache.
        // No root may expose shared storage, files or the whole cache.
        assertEquals(
            listOf(
                "cache-path ${FileProviderImpl.INTERNAL_SHARE_DIRECTORY}/",
                "external-cache-path ."
            ),
            roots
        )
    }

    @Test
    fun `manifest keeps the file provider private and on these paths`() {
        val provider = parse("app/src/main/AndroidManifest.xml")
            .getElementsByTagName("provider")
            .let { nodes -> (0 until nodes.length).map { nodes.item(it) as Element } }
            .single { it.getAttributeNS(ANDROID, "name") == "androidx.core.content.FileProvider" }
        val metaData = provider.childElements().single()

        assertEquals("false", provider.getAttributeNS(ANDROID, "exported"))
        assertEquals("true", provider.getAttributeNS(ANDROID, "grantUriPermissions"))
        assertEquals(
            "android.support.FILE_PROVIDER_PATHS",
            metaData.getAttributeNS(ANDROID, "name")
        )
        assertEquals("@xml/provider_paths", metaData.getAttributeNS(ANDROID, "resource"))
    }

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
    }
}
