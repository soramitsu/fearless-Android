package jp.co.soramitsu.walletconnect.impl.data.legacy

import jp.co.soramitsu.walletconnect.impl.data.legacy.LegacyFixtures.assertRowsMatch
import jp.co.soramitsu.walletconnect.impl.data.legacy.LegacyFixtures.bytes
import jp.co.soramitsu.walletconnect.impl.data.legacy.LegacyFixtures.passphrase
import jp.co.soramitsu.walletconnect.impl.data.legacy.LegacyFixtures.section
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SqlCipherDatabaseDecoderTest {

    private val decoder = SqlCipherDatabaseDecoder()

    @Test
    fun `reads the SDK's sign database as SQLCipher 4 wrote it with its default settings`() {
        val image = SqliteDatabaseImage(decoder.decode(LegacyDatabaseFiles(bytes("reown-sign.db")), passphrase("reown")))
        val expected = section("reown").getAsJsonObject("sign")

        listOf("SessionDao", "NamespaceDao", "ProposalNamespaceDao", "OptionalNamespaceDao").forEach { table ->
            assertRowsMatch(expected.getAsJsonObject(table), image.readTable(table)!!, table)
        }
    }

    @Test
    fun `reads the core database, whose history spans interior pages and overflow chains`() {
        val image = SqliteDatabaseImage(decoder.decode(LegacyDatabaseFiles(bytes("reown-core.db")), passphrase("reown")))
        val expected = section("reown").getAsJsonObject("core")

        listOf("Pairing", "MetaData", "JsonRpcHistoryDao").forEach { table ->
            assertRowsMatch(expected.getAsJsonObject(table), image.readTable(table)!!, table)
        }
    }

    @Test
    fun `a wrong passphrase is refused, never read as garbage`() {
        val wrong = passphrase("reown").copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }

        assertThrows(LegacyDatabaseException::class.java) {
            decoder.decode(LegacyDatabaseFiles(bytes("reown-sign.db")), wrong)
        }
        assertThrows(LegacyDatabaseException::class.java) {
            decoder.decode(LegacyDatabaseFiles(bytes("reown-sign.db")), ByteArray(0))
        }
    }

    @Test
    fun `a page changed on disk fails its HMAC check`() {
        val tampered = bytes("reown-sign.db").also { it[2 * 4096 + 100] = (it[2 * 4096 + 100].toInt() xor 0x40).toByte() }

        val error = assertThrows(LegacyDatabaseException::class.java) {
            decoder.decode(LegacyDatabaseFiles(tampered), passphrase("reown"))
        }
        assertTrue(error.message, error.message!!.contains("Page 3"))
    }

    @Test
    fun `a hot rollback journal is applied, so a cut-off transaction leaves no trace`() {
        val journal = section("journal")
        val journalDecoder = SqlCipherDatabaseDecoder(kdfIterations = journal["kdfIter"].asInt)
        val key = passphrase("journal")

        val rolledBack = journalDecoder.decode(LegacyDatabaseFiles(bytes("journal.db"), journal = bytes("journal.db-journal")), key)
        assertRowsMatch(journal.getAsJsonObject("rows"), SqliteDatabaseImage(rolledBack).readTable("t")!!, "t")

        val withoutJournal = runCatching { journalDecoder.decode(LegacyDatabaseFiles(bytes("journal.db")), key) }.getOrNull()
        assertFalse("the journal changed nothing", withoutJournal?.contentEquals(rolledBack) == true)
    }

    @Test
    fun `a journal from the transaction that created the file rolls it back to nothing`() {
        val journal = section("journal")
        val journalDecoder = SqlCipherDatabaseDecoder(kdfIterations = journal["kdfIter"].asInt)
        val fromEmpty = bytes("journal.db-journal").also { it.fill(0, 16, 20) } // initial size: 0 pages

        val image = journalDecoder.decode(LegacyDatabaseFiles(bytes("journal.db"), journal = fromEmpty), passphrase("journal"))

        assertEquals(0, image.size)
    }

    @Test
    fun `a journal whose header was zeroed is not hot and changes nothing`() {
        val journal = section("journal")
        val journalDecoder = SqlCipherDatabaseDecoder(kdfIterations = journal["kdfIter"].asInt)
        val zeroed = bytes("journal.db-journal").also { it.fill(0, 0, 28) }
        val database = bytes("journal.db")

        assertArrayEquals(
            journalDecoder.decode(LegacyDatabaseFiles(database), passphrase("journal")),
            journalDecoder.decode(LegacyDatabaseFiles(database, journal = zeroed), passphrase("journal"))
        )
    }

    @Test
    fun `committed write-ahead log frames count and frames of an unfinished transaction do not`() {
        val wal = section("wal")
        val walDecoder = SqlCipherDatabaseDecoder(kdfIterations = wal["kdfIter"].asInt)

        val image = SqliteDatabaseImage(walDecoder.decode(LegacyDatabaseFiles(bytes("wal.db"), wal = bytes("wal.db-wal")), passphrase("wal")))

        val rows = image.readTable("t")!!
        assertRowsMatch(wal.getAsJsonObject("rows"), rows, "t")
        assertEquals("updated in the log", rows.single { it.long("id") == 7L }.string("value"))
    }

    @Test
    fun `a log whose header checksum fails is ignored`() {
        val wal = section("wal")
        val walDecoder = SqlCipherDatabaseDecoder(kdfIterations = wal["kdfIter"].asInt)
        val broken = bytes("wal.db-wal").also { it[30] = (it[30].toInt() xor 1).toByte() }

        val image = SqliteDatabaseImage(walDecoder.decode(LegacyDatabaseFiles(bytes("wal.db"), wal = broken), passphrase("wal")))

        assertEquals(0, image.readTable("t")!!.size)
    }

    @Test
    fun `an unencrypted database is passed through`() {
        val plain = bytes("plain.db")

        assertArrayEquals(plain, decoder.decode(LegacyDatabaseFiles(plain), ByteArray(0)))
    }

    @Test
    fun `empty and short files are refused`() {
        val key = passphrase("reown")
        listOf(ByteArray(0), ByteArray(600)).forEach { file ->
            assertThrows(LegacyDatabaseException::class.java) { decoder.decode(LegacyDatabaseFiles(file), key) }
        }
    }

    @Test
    fun `the page and HMAC keys are distinct 32-byte keys`() {
        val database = bytes("reown-sign.db")
        val keys = decoder.deriveKeys(passphrase("reown"), database.copyOfRange(0, SqlCipherDatabaseDecoder.SALT_SIZE))

        assertEquals(32, keys.cipherKey.size)
        assertEquals(32, keys.hmacKey.size)
        assertFalse(keys.cipherKey.contentEquals(keys.hmacKey))
    }

    @Test
    fun `PBKDF2-HMAC-SHA512 matches the published vectors and a two-block output`() {
        val vectors = mapOf(
            1 to "867f70cf1ade02cff3752599a3a53dc4af34c7a669815ae5d513554e1c8cf252c02d470a285a0501bad999bfe943c08f050235d7d68b1da55e63f73b60a57fce",
            2 to "e1d9c16aa681708a45f5c7c4e215ceb66e011a2e9f0040713f18aefdb866d53cf76cab2868a39b9f7840edce4fef5a82be67335c77a6068e04112754f27ccf4e",
            4096 to "d197b1b33db0143e018b12f3d1d1479e6cdebdcc97c5c0f87f6902e072f457b5143f30602641b3d55cd335988cb36b84376060ecd532e039b742a239434af2d5"
        )
        vectors.forEach { (iterations, expected) ->
            assertEquals(expected, pbkdf2HmacSha512("password".toByteArray(), "salt".toByteArray(), iterations, 64).toHex())
        }
        assertEquals(
            "6a7be24f5b46c5c3769d73a6689b3ad79f9f245a1cdb20e41ee9d5a31bea14445f54a362850dd4dc7d6a22b714d3d05635425c3f127951dfb8f1d9bf88f1435" +
                "38798e0acae2ca6a88b3f109a4e8c2cc1e64f77a4131b3b721cdaafd197d350736d920b41",
            pbkdf2HmacSha512("pw".toByteArray(), "salt".toByteArray(), 3, 100).toHex()
        )
    }

    @Test
    fun `the page size of a plain header is read, with 1 meaning 65536`() {
        val header = sqliteHeader.copyOf(100)
        header[16] = 0x10
        header[17] = 0
        assertEquals(4096, plainPageSize(header))
        header[16] = 0
        header[17] = 1
        assertEquals(65_536, plainPageSize(header))
        header[17] = 3
        assertThrows(LegacyDatabaseException::class.java) { plainPageSize(header) }
    }

    @Test
    fun `byte helpers read big-endian values and prefixes`() {
        val data = byteArrayOf(0x12, 0x34, 0x56, 0x78, 0xff.toByte())

        assertEquals(0x12345678, data.int32(0))
        assertEquals(0x1234, data.uint16(0))
        assertEquals(255, data.uint8(4))
        assertTrue(data.startsWith(byteArrayOf(0x34, 0x56), offset = 1))
        assertFalse(data.startsWith(byteArrayOf(0x34), offset = 0))
        assertEquals(4096, sqliteHeader.copyOf(100).also { it[16] = 0x10 }.sqlitePageSize())
    }

    @Test
    fun `byte helpers keep the sign of 32-bit values, not of bytes, and stop at the end of the data`() {
        val data = byteArrayOf(0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xfe.toByte())

        assertEquals(-2, data.int32(0))
        assertEquals(65_535, data.uint16(0))
        assertEquals(254, data.uint8(3))
        assertThrows(IndexOutOfBoundsException::class.java) { data.int32(1) }
        assertThrows(IndexOutOfBoundsException::class.java) { data.uint16(3) }
    }

    @Test
    fun `journal and log helpers leave the image alone without a sidecar`() {
        val image = ByteArray(4096)

        assertTrue(applyHotJournal(image, null, 4096) === image)
        assertTrue(applyHotJournal(image, ByteArray(10), 4096) === image)
        assertTrue(applyWal(image, null, 4096) === image)
        assertTrue(applyWal(image, ByteArray(10), 4096) === image)
    }
}
