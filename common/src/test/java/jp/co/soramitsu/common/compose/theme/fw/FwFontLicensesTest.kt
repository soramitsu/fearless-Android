package jp.co.soramitsu.common.compose.theme.fw

import android.content.res.Resources
import com.google.gson.JsonParser
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import jp.co.soramitsu.common.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class FwFontLicensesTest {
    @Test fun offlineReaderPreservesEachOriginalNoticeAndClosesResources() {
        val resources = mock(Resources::class.java)
        val closed = mutableListOf<Int>()
        for ((index, id) in listOf(R.raw.fw_sora_ofl, R.raw.fw_inter_ofl, R.raw.fw_jetbrains_mono_ofl).withIndex()) {
            val stream = object : ByteArrayInputStream("Copyright © $index\nOFL 1.1\n".toByteArray(Charsets.UTF_8)) {
                override fun close() { closed += index; super.close() }
            }
            `when`(resources.openRawResource(id)).thenReturn(stream)
        }
        val licenses = loadFwFontLicenses(resources)
        assertEquals(listOf("Sora", "Inter", "JetBrains Mono"), licenses.map { it.name })
        assertEquals((0..2).map { "Copyright © $it\nOFL 1.1\n" }, licenses.map { it.text })
        assertEquals(listOf(0, 1, 2), closed)
    }

    @Test fun bundledFontAndNoticeBytesMatchTheReviewedCrossPlatformSources() {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "design/fw.fonts.json").isFile }
        val files = JsonParser.parseString(File(root, "design/fw.fonts.json").readText()).asJsonObject.getAsJsonArray("files")
        assertEquals(5, files.size())
        for (item in files) {
            val pin = item.asJsonObject
            val file = File(root, pin.get("path").asString)
            val hash = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
            assertEquals(file.name, pin.get("sha256").asString, hash)
            if (file.extension == "txt") {
                assertTrue(file.readText().contains("SIL OPEN FONT LICENSE Version 1.1"))
                assertTrue(file.readText().contains("Copyright", ignoreCase = true))
            }
        }
    }
}
