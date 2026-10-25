package jp.co.soramitsu.account.impl.presentation.importing

import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FileReaderTest {
    @Test
    fun boundedReadPreservesJsonExactlyIncludingWhitespaceAndUnicode() {
        val json = "  {\"name\":\"復元 wallet\",\"encoded\":\"abc\"}\n"
        assertEquals(json, FileReader.readBoundedJson(StringReader(json)))
        val max = "a".repeat(FileReader.MAX_JSON_CHARS)
        assertEquals(max, FileReader.readBoundedJson(StringReader(max)))
    }

    @Test
    fun oversizedInputIsRejectedBeforeUnboundedAllocation() {
        assertThrows(IllegalArgumentException::class.java) {
            FileReader.readBoundedJson(StringReader("a".repeat(FileReader.MAX_JSON_CHARS + 1)))
        }
    }
}
