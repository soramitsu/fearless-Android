package jp.co.soramitsu.walletconnect.impl.data.legacy

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** A legacy database could not be read: wrong key, damaged file or an unknown format. */
internal class LegacyDatabaseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** A database file as found on disk, with the rollback journal or write-ahead log SQLite may have left beside it. */
internal class LegacyDatabaseFiles(
    val database: ByteArray,
    val journal: ByteArray? = null,
    val wal: ByteArray? = null
)

/**
 * Reads the databases that the removed Reown SDK encrypted with SQLCipher 4 under its default settings,
 * without SQLCipher's native library:
 * - the file starts with a 16-byte salt;
 * - the page key is PBKDF2-HMAC-SHA512 of the passphrase and the salt, 256,000 iterations, 32 bytes;
 * - the HMAC key is PBKDF2-HMAC-SHA512 of the page key and the salt XOR 0x3a, 2 iterations, 32 bytes;
 * - each 4,096-byte page is AES-256-CBC encrypted and ends with 80 reserved bytes: the IV, then an
 *   HMAC-SHA512 over the ciphertext, the IV and the little-endian page number.
 *
 * Everything happens in memory and the result is a plain SQLite image; no plaintext reaches the disk.
 * A hot rollback journal or a write-ahead log left beside the database is applied first, as SQLite would
 * on open, so a transaction cut off by the app being killed is rolled back and committed WAL frames count.
 */
internal class SqlCipherDatabaseDecoder(private val kdfIterations: Int = DEFAULT_KDF_ITERATIONS) {

    /**
     * Returns the plain SQLite image of [files]; throws [LegacyDatabaseException] when it cannot. The image
     * is empty when a rolled-back journal shows the file held nothing before the transaction cut off.
     */
    fun decode(files: LegacyDatabaseFiles, passphrase: ByteArray): ByteArray {
        val database = files.database
        if (database.startsWith(sqliteHeader)) {
            val pageSize = plainPageSize(database)
            return applyWal(applyHotJournal(database, files.journal, pageSize), files.wal, pageSize)
        }
        val readable = passphrase.isNotEmpty() && database.size >= SALT_SIZE + MIN_PAGE_SIZE
        val keys = if (readable) deriveKeys(passphrase, database.copyOfRange(0, SALT_SIZE)) else null
        val pageSize = ENCRYPTED_PAGE_SIZES.firstOrNull { size ->
            keys != null && database.size % size == 0 && pageHmacMatches(database, PageRef(0, size), keys)
        }
        if (keys == null || pageSize == null) {
            throw LegacyDatabaseException("No passphrase, a short file, a wrong key, or not an SQLCipher 4 database")
        }
        val encrypted = applyWal(applyHotJournal(database, files.journal, pageSize), files.wal, pageSize)
        return decryptPages(encrypted, pageSize, keys)
    }

    internal class Keys(val cipherKey: ByteArray, val hmacKey: ByteArray)

    /** The page and HMAC keys SQLCipher 4 derives from [passphrase] and the file's [salt]. */
    internal fun deriveKeys(passphrase: ByteArray, salt: ByteArray): Keys {
        val cipherKey = pbkdf2HmacSha512(passphrase, salt, kdfIterations, KEY_SIZE)
        val hmacSalt = ByteArray(salt.size) { index -> (salt[index].toInt() xor HMAC_SALT_MASK).toByte() }
        return Keys(cipherKey, pbkdf2HmacSha512(cipherKey, hmacSalt, FAST_KDF_ITERATIONS, KEY_SIZE))
    }

    private class PageRef(val index: Int, val size: Int) {
        val offset get() = index * size
        val dataStart get() = offset + if (index == 0) SALT_SIZE else 0
        val ivStart get() = offset + size - RESERVE_SIZE
        val hmacStart get() = ivStart + IV_SIZE
    }

    private fun decryptPages(
        encrypted: ByteArray,
        pageSize: Int,
        keys: Keys
    ): ByteArray {
        val plain = ByteArray(encrypted.size)
        val cipher = Cipher.getInstance(AES_CBC)
        val key = SecretKeySpec(keys.cipherKey, AES)
        for (index in 0 until encrypted.size / pageSize) {
            val page = PageRef(index, pageSize)
            // A page that was never written reads as zeros, as SQLCipher treats it.
            if (encrypted.isZeroed(page.offset, pageSize)) continue
            if (!pageHmacMatches(encrypted, page, keys)) throw LegacyDatabaseException("Page ${index + 1} failed its integrity check")
            cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(encrypted, page.ivStart, IV_SIZE))
            cipher.doFinal(encrypted, page.dataStart, page.ivStart - page.dataStart, plain, page.dataStart)
        }
        if (plain.isNotEmpty()) sqliteHeader.copyInto(plain, 0)
        return plain
    }

    private fun pageHmacMatches(
        image: ByteArray,
        page: PageRef,
        keys: Keys
    ): Boolean {
        val mac = Mac.getInstance(HMAC_SHA512)
        mac.init(SecretKeySpec(keys.hmacKey, HMAC_SHA512))
        mac.update(image, page.dataStart, page.hmacStart - page.dataStart)
        mac.update((page.index + 1).toLittleEndianBytes())
        return MessageDigest.isEqual(mac.doFinal(), image.copyOfRange(page.hmacStart, page.hmacStart + HMAC_SIZE))
    }

    companion object {
        const val DEFAULT_KDF_ITERATIONS = 256_000
        const val SALT_SIZE = 16
        const val RESERVE_SIZE = 80
        private const val MIN_PAGE_SIZE = 512
        private const val FAST_KDF_ITERATIONS = 2
        private const val HMAC_SALT_MASK = 0x3a
        private const val KEY_SIZE = 32
        private const val IV_SIZE = 16
        private const val HMAC_SIZE = 64
        private const val AES = "AES"
        private const val AES_CBC = "AES/CBC/NoPadding"
        private const val HMAC_SHA512 = "HmacSHA512"

        // SQLCipher 4's default first; the others only in case the SDK ever set cipher_page_size.
        private val ENCRYPTED_PAGE_SIZES = listOf(4096, 1024, 2048, 8192, 16_384, 32_768, 65_536)
    }
}

/** RFC 8018 PBKDF2 with HMAC-SHA512 over raw [password] bytes (the JCA's PBEKeySpec takes only chars). */
internal fun pbkdf2HmacSha512(
    password: ByteArray,
    salt: ByteArray,
    iterations: Int,
    length: Int
): ByteArray {
    require(password.isNotEmpty()) { "PBKDF2 needs a password" }
    require(iterations > 0 && length > 0) { "PBKDF2 needs at least one iteration and one byte" }
    val mac = Mac.getInstance("HmacSHA512")
    mac.init(SecretKeySpec(password, "HmacSHA512"))
    val blockSize = mac.macLength
    val blocks = (length + blockSize - 1) / blockSize
    val output = ByteArray(blocks * blockSize)
    for (block in 1..blocks) {
        mac.update(salt)
        mac.update(block.toBigEndianBytes())
        var u = mac.doFinal()
        val t = u.copyOf()
        repeat(iterations - 1) {
            u = mac.doFinal(u)
            t.xorInPlace(u)
        }
        t.copyInto(output, (block - 1) * blockSize)
    }
    return output.copyOf(length)
}

internal val sqliteHeader = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

private val JOURNAL_MAGIC = byteArrayOf(
    0xd9.toByte(),
    0xd5.toByte(),
    0x05,
    0xf9.toByte(),
    0x20,
    0xa1.toByte(),
    0x63,
    0xd7.toByte()
)
private const val SQLITE_HEADER_SIZE = 100
private const val JOURNAL_HEADER_FIELDS = 28
private const val JOURNAL_ALL_RECORDS = -1
private const val JOURNAL_CHECKSUM_STRIDE = 200
private const val MIN_SECTOR_SIZE = 512
private const val MAX_PAGE_SIZE = 65_536
private const val SQLITE_PAGE_SIZE_OFFSET = 16
private const val WAL_MAGIC_LITTLE_ENDIAN = 0x377f0682
private const val WAL_MAGIC_BIG_ENDIAN = 0x377f0683
private const val WAL_HEADER_SIZE = 32
private const val WAL_HEADER_CHECKSUMMED = 24
private const val WAL_FRAME_HEADER_SIZE = 24
private const val WAL_FRAME_HEADER_CHECKSUMMED = 8
private const val WAL_CHECKSUM_STEP = 2 * Int.SIZE_BYTES

/** The page size a plain SQLite header declares. */
internal fun plainPageSize(image: ByteArray): Int {
    if (image.size < SQLITE_HEADER_SIZE) throw LegacyDatabaseException("The database header is cut short")
    val size = image.sqlitePageSize()
    if (size < MIN_SECTOR_SIZE || size > MAX_PAGE_SIZE || size.countOneBits() != 1) {
        throw LegacyDatabaseException("Bad page size $size")
    }
    return size
}

private class JournalSegment(
    val headerOffset: Int,
    val records: Int,
    val nonce: Int,
    val initialPages: Int,
    val sectorSize: Int
) {
    val recordsStart get() = headerOffset + sectorSize

    companion object {
        fun read(
            journal: ByteArray,
            offset: Int,
            pageSize: Int
        ): JournalSegment? {
            val present = offset >= 0 && offset + JOURNAL_HEADER_FIELDS <= journal.size && journal.startsWith(JOURNAL_MAGIC, offset)
            if (!present) return null
            val sectorSize = journal.int32(offset + JournalField.SECTOR_SIZE)
            val validSector = sectorSize in MIN_SECTOR_SIZE..MAX_PAGE_SIZE && sectorSize.countOneBits() == 1
            if (!validSector || journal.int32(offset + JournalField.PAGE_SIZE) != pageSize) return null
            val recordSize = journalRecordSize(pageSize)
            val declared = journal.int32(offset + JournalField.RECORDS)
            val records = if (declared == JOURNAL_ALL_RECORDS) (journal.size - offset - sectorSize) / recordSize else declared
            return JournalSegment(
                headerOffset = offset,
                records = records.coerceAtLeast(0),
                nonce = journal.int32(offset + JournalField.NONCE),
                initialPages = journal.int32(offset + JournalField.INITIAL_PAGES).coerceAtLeast(0),
                sectorSize = sectorSize
            )
        }
    }
}

private object JournalField {
    const val RECORDS = 8
    const val NONCE = 12
    const val INITIAL_PAGES = 16
    const val SECTOR_SIZE = 20
    const val PAGE_SIZE = 24
}

private fun journalRecordSize(pageSize: Int) = Int.SIZE_BYTES + pageSize + Int.SIZE_BYTES

/**
 * Rolls [image] back with a hot rollback journal, as SQLite does on open: the journal holds the pages
 * as they were before an unfinished transaction, and the file is cut back to its size from then. A
 * journal with a zeroed or foreign header is not hot and changes nothing. Page images are copied as
 * stored, so this works on encrypted and plain files alike.
 */
internal fun applyHotJournal(
    image: ByteArray,
    journal: ByteArray?,
    pageSize: Int
): ByteArray {
    if (journal == null) return image
    val first = JournalSegment.read(journal, 0, pageSize) ?: return image
    val result = image.copyOf(first.initialPages * pageSize)
    var segment: JournalSegment? = first
    while (segment != null) {
        val end = playJournalSegment(journal, segment, result, pageSize) ?: break
        val nextHeader = (end + segment.sectorSize - 1) / segment.sectorSize * segment.sectorSize
        segment = JournalSegment.read(journal, nextHeader, pageSize)
    }
    return result
}

// Copies the segment's valid records into [result], which is already cut to the size the first header gave
// (SQLite skips pages past it in every segment); returns where the segment ends, or null where playback stops.
private fun playJournalSegment(
    journal: ByteArray,
    segment: JournalSegment,
    result: ByteArray,
    pageSize: Int
): Int? {
    val recordSize = journalRecordSize(pageSize)
    val pages = result.size / pageSize
    var offset = segment.recordsStart
    repeat(segment.records) {
        val pageNumber = if (offset + recordSize <= journal.size) journal.int32(offset) else 0
        val dataStart = offset + Int.SIZE_BYTES
        val valid = pageNumber > 0 && journal.int32(dataStart + pageSize) == journal.journalChecksum(segment.nonce, dataStart, pageSize)
        if (!valid) return null
        if (pageNumber <= pages) {
            journal.copyInto(result, (pageNumber - 1) * pageSize, dataStart, dataStart + pageSize)
        }
        offset += recordSize
    }
    return offset
}

/**
 * Applies the committed frames of a write-ahead log to [image]: frames count while their salts and
 * running checksums hold, and only up to the last commit frame, whose database size is then used.
 */
internal fun applyWal(
    image: ByteArray,
    wal: ByteArray?,
    pageSize: Int
): ByteArray {
    val header = wal?.let { WalHeader.read(it, pageSize) } ?: return image
    val committed = LinkedHashMap<Int, Int>()
    val pending = LinkedHashMap<Int, Int>()
    var databasePages = -1
    var checksum = header.checksum
    var offset = WAL_HEADER_SIZE
    while (offset + WAL_FRAME_HEADER_SIZE + pageSize <= wal.size) {
        checksum = header.frameChecksum(wal, offset, checksum) ?: break
        pending[wal.int32(offset)] = offset + WAL_FRAME_HEADER_SIZE
        val commitSize = wal.int32(offset + Int.SIZE_BYTES)
        if (commitSize != 0) {
            committed.putAll(pending)
            pending.clear()
            databasePages = commitSize
        }
        offset += WAL_FRAME_HEADER_SIZE + pageSize
    }
    if (databasePages < 0) return image
    val result = image.copyOf(databasePages * pageSize)
    committed.filterKeys { it <= databasePages }.forEach { (pageNumber, dataOffset) ->
        wal.copyInto(result, (pageNumber - 1) * pageSize, dataOffset, dataOffset + pageSize)
    }
    return result
}

private class WalHeader(
    val bigEndianWords: Boolean,
    val pageSize: Int,
    val salt1: Int,
    val salt2: Int,
    val checksum: Pair<Int, Int>
) {
    // The running checksum after the frame at [offset], or null when the frame is not a valid frame of this log.
    fun frameChecksum(
        wal: ByteArray,
        offset: Int,
        previous: Pair<Int, Int>
    ): Pair<Int, Int>? {
        val saltsMatch = wal.int32(offset + FRAME_SALT_1) == salt1 && wal.int32(offset + FRAME_SALT_2) == salt2
        if (!saltsMatch || wal.int32(offset) <= 0) return null
        val afterHeader = wal.walChecksum(offset, WAL_FRAME_HEADER_CHECKSUMMED, bigEndianWords, previous)
        val sum = wal.walChecksum(offset + WAL_FRAME_HEADER_SIZE, pageSize, bigEndianWords, afterHeader)
        val stored = wal.int32(offset + FRAME_CHECKSUM_1) to wal.int32(offset + FRAME_CHECKSUM_2)
        return sum.takeIf { it == stored }
    }

    companion object {
        private const val PAGE_SIZE = 8
        private const val SALT_1 = 16
        private const val SALT_2 = 20
        private const val CHECKSUM_1 = 24
        private const val CHECKSUM_2 = 28
        private const val FRAME_SALT_1 = 8
        private const val FRAME_SALT_2 = 12
        private const val FRAME_CHECKSUM_1 = 16
        private const val FRAME_CHECKSUM_2 = 20

        fun read(wal: ByteArray, pageSize: Int): WalHeader? {
            if (wal.size < WAL_HEADER_SIZE) return null
            val magic = wal.int32(0)
            val known = magic == WAL_MAGIC_LITTLE_ENDIAN || magic == WAL_MAGIC_BIG_ENDIAN
            if (!known || wal.int32(PAGE_SIZE) != pageSize) return null
            val bigEndianWords = magic == WAL_MAGIC_BIG_ENDIAN
            val checksum = wal.walChecksum(0, WAL_HEADER_CHECKSUMMED, bigEndianWords, 0 to 0)
            if (checksum.first != wal.int32(CHECKSUM_1) || checksum.second != wal.int32(CHECKSUM_2)) return null
            return WalHeader(
                bigEndianWords = bigEndianWords,
                pageSize = pageSize,
                salt1 = wal.int32(SALT_1),
                salt2 = wal.int32(SALT_2),
                checksum = checksum
            )
        }
    }
}

private fun ByteArray.journalChecksum(
    nonce: Int,
    offset: Int,
    pageSize: Int
): Int {
    var sum = nonce
    var index = pageSize - JOURNAL_CHECKSUM_STRIDE
    while (index > 0) {
        sum += uint8(offset + index)
        index -= JOURNAL_CHECKSUM_STRIDE
    }
    return sum
}

// SQLite's WAL checksum: two running sums over pairs of 32-bit words, in the byte order the log's magic gives.
private fun ByteArray.walChecksum(
    offset: Int,
    length: Int,
    bigEndianWords: Boolean,
    start: Pair<Int, Int>
): Pair<Int, Int> {
    val order = if (bigEndianWords) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN
    val words = ByteBuffer.wrap(this, offset, length).order(order)
    var (s0, s1) = start
    while (words.remaining() >= WAL_CHECKSUM_STEP) {
        s0 += words.int + s1
        s1 += words.int + s0
    }
    return s0 to s1
}

private fun ByteArray.isZeroed(offset: Int, length: Int): Boolean =
    (offset until offset + length).all { this[it].toInt() == 0 }

private fun ByteArray.xorInPlace(other: ByteArray) {
    for (index in indices) this[index] = (this[index].toInt() xor other[index].toInt()).toByte()
}

internal fun ByteArray.startsWith(prefix: ByteArray, offset: Int = 0): Boolean =
    size >= offset + prefix.size && prefix.indices.all { this[offset + it] == prefix[it] }

/** The big-endian 32-bit integer at [offset], as SQLite stores them. */
internal fun ByteArray.int32(offset: Int): Int = ByteBuffer.wrap(this, offset, Int.SIZE_BYTES).int

internal fun ByteArray.uint16(offset: Int): Int = uint8(offset) shl Byte.SIZE_BITS or uint8(offset + 1)

internal fun ByteArray.uint8(offset: Int): Int = this[offset].toUByte().toInt()

// The header stores 65536 as 1 because it does not fit in two bytes.
internal fun ByteArray.sqlitePageSize(): Int =
    uint16(SQLITE_PAGE_SIZE_OFFSET).let { if (it == 1) MAX_PAGE_SIZE else it }

private fun Int.toBigEndianBytes(): ByteArray = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(this).array()

private fun Int.toLittleEndianBytes(): ByteArray =
    ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(this).array()
