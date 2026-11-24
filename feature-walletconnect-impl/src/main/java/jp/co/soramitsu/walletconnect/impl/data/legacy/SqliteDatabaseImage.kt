package jp.co.soramitsu.walletconnect.impl.data.legacy

/** One table row: column name to value (Long, Double, String, ByteArray or null). */
internal class SqliteRow(private val values: Map<String, Any?>) {
    fun string(column: String): String? = values[column] as? String

    fun long(column: String): Long? = when (val value = values[column]) {
        is Long -> value
        is Double -> value.toLong()
        is String -> value.toLongOrNull()
        else -> null
    }

    fun boolean(column: String): Boolean? = long(column)?.let { it != 0L }

    operator fun get(column: String): Any? = values[column]
}

/**
 * A column of a table as its CREATE TABLE statement declares it. [hasRealAffinity] follows SQLite's
 * affinity rules: such a column keeps whole numbers as integers on disk and reads them back as floats.
 */
internal data class SqliteColumn(
    val name: String,
    val isRowidAlias: Boolean,
    val defaultValue: Any?,
    val hasRealAffinity: Boolean = false
)

/**
 * Reads table rows straight from a plain SQLite database image held in memory, following the file
 * format (https://www.sqlite.org/fileformat2.html): the schema table on page 1, table b-trees, overflow
 * chains and records. It is read-only and deliberately small; indexes, WITHOUT ROWID tables and UTF-16
 * text are refused rather than guessed at. Every page and offset is bounds-checked, so a damaged image
 * raises [LegacyDatabaseException] instead of crashing.
 */
internal class SqliteDatabaseImage(private val bytes: ByteArray) {
    private val pageSize: Int = plainPageSize(bytes)
    private val usableSize: Int = pageSize - bytes.uint8(RESERVED_BYTES_OFFSET)
    private val pageCount: Int = bytes.size / pageSize

    init {
        if (!bytes.startsWith(sqliteHeader)) throw LegacyDatabaseException("Not an SQLite database")
        if (usableSize < MIN_USABLE_SIZE) throw LegacyDatabaseException("Too little usable space per page")
        val encoding = bytes.int32(TEXT_ENCODING_OFFSET)
        if (encoding != 0 && encoding != UTF8_ENCODING) throw LegacyDatabaseException("Text encoding $encoding is not UTF-8")
    }

    private val schema: List<SchemaEntry> by lazy {
        val entries = mutableListOf<SchemaEntry>()
        forEachRecord(SCHEMA_ROOT_PAGE) { _, values ->
            val type = values.getOrNull(0) as? String
            val name = values.getOrNull(1) as? String
            val rootPage = (values.getOrNull(SCHEMA_ROOT_PAGE_COLUMN) as? Long)?.toInt()
            val sql = values.getOrNull(SCHEMA_SQL_COLUMN) as? String
            if (type != null && name != null && rootPage != null) entries += SchemaEntry(type, name, rootPage, sql)
        }
        entries
    }

    /** The names of the tables the schema lists. */
    fun tableNames(): List<String> = schema.filter { it.type == "table" }.map { it.name }

    /** The rows of [table] in rowid order, or null when there is no such table. */
    fun readTable(table: String): List<SqliteRow>? {
        val entry = schema.firstOrNull { it.type == "table" && it.name.equals(table, ignoreCase = true) } ?: return null
        val sql = entry.sql ?: throw LegacyDatabaseException("Table $table has no CREATE statement")
        if (WITHOUT_ROWID.containsMatchIn(sql)) throw LegacyDatabaseException("Table $table is a WITHOUT ROWID table")
        val columns = parseCreateTableColumns(sql)
        val rows = mutableListOf<SqliteRow>()
        forEachRecord(entry.rootPage) { rowid, values ->
            rows += SqliteRow(
                columns.mapIndexed { index, column -> column.name to column.valueOf(values.getOrNull(index), index < values.size, rowid) }
                    .toMap()
            )
        }
        return rows
    }

    private class SchemaEntry(val type: String, val name: String, val rootPage: Int, val sql: String?)

    private fun forEachRecord(rootPage: Int, action: (rowid: Long, values: List<Any?>) -> Unit) {
        val pending = ArrayDeque<Int>()
        val visited = HashSet<Int>()
        pending.addLast(rootPage)
        while (pending.isNotEmpty()) {
            val page = pending.removeLast()
            if (page !in 1..pageCount || !visited.add(page)) throw LegacyDatabaseException("Bad b-tree page $page")
            val header = pageHeaderOffset(page)
            when (bytes.uint8(header)) {
                TABLE_LEAF -> readLeafCells(page, header, action)
                TABLE_INTERIOR -> pending.addAll(interiorChildrenInVisitOrder(page, header))
                else -> throw LegacyDatabaseException("Page $page is not a table b-tree page")
            }
        }
    }

    private fun pageHeaderOffset(page: Int) = (page - 1) * pageSize + if (page == 1) DATABASE_HEADER_SIZE else 0

    private fun cellOffsets(
        page: Int,
        header: Int,
        headerSize: Int
    ): List<Int> {
        val count = bytes.uint16(header + CELL_COUNT_OFFSET)
        val pageStart = (page - 1) * pageSize
        return (0 until count).map { index ->
            val pointer = bytes.uint16(header + headerSize + index * CELL_POINTER_SIZE)
            if (pointer < CELL_POINTER_SIZE || pointer >= usableSize) throw LegacyDatabaseException("Bad cell pointer on page $page")
            pageStart + pointer
        }
    }

    // Children are pushed so that the stack pops them leftmost first, which keeps rows in rowid order.
    private fun interiorChildrenInVisitOrder(page: Int, header: Int): List<Int> {
        val rightmost = bytes.int32(header + RIGHTMOST_POINTER_OFFSET)
        val left = cellOffsets(page, header, INTERIOR_HEADER_SIZE).map { bytes.int32(it) }
        return listOf(rightmost) + left.reversed()
    }

    private fun readLeafCells(
        page: Int,
        header: Int,
        action: (rowid: Long, values: List<Any?>) -> Unit
    ) {
        cellOffsets(page, header, LEAF_HEADER_SIZE).forEach { cell ->
            val payloadSize = readVarint(cell)
            val rowid = readVarint(cell + payloadSize.length)
            val payloadStart = cell + payloadSize.length + rowid.length
            action(rowid.value, decodeRecord(readPayload(payloadStart, payloadSize.value)))
        }
    }

    private fun readPayload(start: Int, declaredSize: Long): ByteArray {
        if (declaredSize < 0 || declaredSize > bytes.size.toLong() * 2) throw LegacyDatabaseException("Bad payload size")
        val size = declaredSize.toInt()
        val local = localPayloadSize(size)
        val payload = ByteArray(size)
        val overflows = local < size
        checkRange(start, local + if (overflows) Int.SIZE_BYTES else 0)
        bytes.copyInto(payload, 0, start, start + local)
        var written = local
        var next = if (overflows) bytes.int32(start + local) else 0
        val visited = HashSet<Int>()
        while (written < size) {
            if (next !in 1..pageCount || !visited.add(next)) throw LegacyDatabaseException("Bad overflow page $next")
            val pageStart = (next - 1) * pageSize
            val chunk = minOf(size - written, usableSize - Int.SIZE_BYTES)
            bytes.copyInto(payload, written, pageStart + Int.SIZE_BYTES, pageStart + Int.SIZE_BYTES + chunk)
            written += chunk
            next = bytes.int32(pageStart)
        }
        return payload
    }

    // How much of a table leaf cell's payload is stored on the page itself (file format, section 1.6).
    private fun localPayloadSize(size: Int): Int {
        val maxLocal = usableSize - TABLE_LEAF_MAX_LOCAL_OVERHEAD
        if (size <= maxLocal) return size
        val minLocal = (usableSize - MIN_LOCAL_OVERHEAD) * MIN_LOCAL_NUMERATOR / MIN_LOCAL_DENOMINATOR - MIN_LOCAL_SUBTRAHEND
        val candidate = minLocal + (size - minLocal) % (usableSize - Int.SIZE_BYTES)
        return if (candidate <= maxLocal) candidate else minLocal
    }

    private fun checkRange(start: Int, length: Int) {
        if (start < 0 || length < 0 || start + length > bytes.size) throw LegacyDatabaseException("Read outside the database")
    }

    private fun readVarint(offset: Int): Varint {
        checkRange(offset, 1)
        return bytes.varint(offset).also { checkRange(offset, it.length) }
    }

    private fun decodeRecord(payload: ByteArray): List<Any?> {
        val headerSize = payload.varint(0)
        if (headerSize.value < headerSize.length || headerSize.value > payload.size) throw LegacyDatabaseException("Bad record header")
        val types = mutableListOf<Long>()
        var offset = headerSize.length
        while (offset < headerSize.value) {
            val type = payload.varint(offset)
            types += type.value
            offset += type.length
        }
        var body = headerSize.value.toInt()
        return types.map { type ->
            val length = type.serialTypeLength()
            if (body + length > payload.size) throw LegacyDatabaseException("Record body cut short")
            payload.serialValue(type, body, length).also { body += length }
        }
    }
}

internal class Varint(val value: Long, val length: Int)

// SQLite varints: big-endian groups of 7 bits with a continuation bit; the ninth byte carries 8 bits.
internal fun ByteArray.varint(offset: Int): Varint {
    var value = 0L
    for (index in 0 until VARINT_GROUPED_BYTES) {
        if (offset + index >= size) throw LegacyDatabaseException("Varint cut short")
        val byte = uint8(offset + index)
        value = value shl VARINT_GROUP_BITS or (byte and VARINT_GROUP_MASK).toLong()
        if (byte and VARINT_CONTINUATION == 0) return Varint(value, index + 1)
    }
    val last = offset + VARINT_GROUPED_BYTES
    if (last >= size) throw LegacyDatabaseException("Varint cut short")
    return Varint(value shl Byte.SIZE_BITS or uint8(last).toLong(), VARINT_GROUPED_BYTES + 1)
}

// The number of body bytes a record value of this serial type takes: fixed for types 0 to 9, then BLOBs
// (even types) and text (odd types) of (type - 12) / 2 bytes. Types 10 and 11 are reserved.
internal fun Long.serialTypeLength(): Int = when {
    this >= FIRST_VARIABLE_SERIAL_TYPE -> ((this - FIRST_VARIABLE_SERIAL_TYPE) / 2).toInt()
    this >= 0 && this < FIXED_SERIAL_TYPE_LENGTHS.size -> FIXED_SERIAL_TYPE_LENGTHS[toInt()]
    else -> throw LegacyDatabaseException("Reserved serial type $this")
}

internal fun ByteArray.serialValue(
    type: Long,
    offset: Int,
    length: Int
): Any? = when {
    type == SERIAL_NULL -> null
    type in 1L..SERIAL_LARGEST_INTEGER -> signedBigEndian(offset, length)
    type == SERIAL_FLOAT -> Double.fromBits(signedBigEndian(offset, Long.SIZE_BYTES))
    type == SERIAL_ZERO -> 0L
    type == SERIAL_ONE -> 1L
    type % 2 == 0L -> copyOfRange(offset, offset + length)
    else -> String(this, offset, length, Charsets.UTF_8)
}

private fun ByteArray.signedBigEndian(offset: Int, length: Int): Long {
    var value = this[offset].toLong() // sign-extends the first byte
    for (index in 1 until length) value = value shl Byte.SIZE_BITS or uint8(offset + index).toLong()
    return value
}

/**
 * The columns a CREATE TABLE statement declares, in order. SQLite keeps the statement up to date when
 * columns are added, so rows written before an ALTER TABLE simply have fewer values; those take the
 * column's DEFAULT. Table constraints (PRIMARY KEY(...), UNIQUE(...), ...) are skipped.
 */
internal fun parseCreateTableColumns(sql: String): List<SqliteColumn> {
    val open = sql.indexOf('(')
    val close = sql.lastIndexOf(')')
    if (open < 0 || close <= open) throw LegacyDatabaseException("Unreadable CREATE TABLE statement")
    return splitTopLevel(sql.substring(open + 1, close))
        .map { tokenizeSql(it) }
        .filter { tokens -> tokens.isNotEmpty() && tokens.first().uppercase() !in TABLE_CONSTRAINTS }
        .map { tokens -> columnOf(tokens) }
}

// The stored value, a default for rows written before the column was added, the rowid for its alias.
private fun SqliteColumn.valueOf(
    stored: Any?,
    present: Boolean,
    rowid: Long
): Any? {
    val value = if (present) stored else defaultValue
    return when {
        isRowidAlias && value == null -> rowid
        hasRealAffinity && value is Long -> value.toDouble()
        else -> value
    }
}

private fun columnOf(tokens: List<String>): SqliteColumn {
    val upper = tokens.map { it.uppercase() }
    val typeName = upper.drop(1).takeWhile { it !in COLUMN_CONSTRAINT_STARTS }.joinToString(" ")
    val primaryKey = upper.indices.any { upper[it] == "PRIMARY" && upper.getOrNull(it + 1) == "KEY" }
    val descending = upper.contains("DESC")
    val defaultAt = upper.indexOf("DEFAULT")
    return SqliteColumn(
        name = unquoteIdentifier(tokens.first()),
        isRowidAlias = typeName == "INTEGER" && primaryKey && !descending,
        defaultValue = if (defaultAt >= 0) defaultLiteral(tokens.drop(defaultAt + 1)) else null,
        hasRealAffinity = hasRealAffinity(typeName)
    )
}

// SQLite's affinity rules (datatype3.html, section 3.1), in their order; only REAL changes what is read.
private fun hasRealAffinity(typeName: String): Boolean = when {
    "INT" in typeName -> false
    listOf("CHAR", "CLOB", "TEXT").any { it in typeName } -> false
    "BLOB" in typeName || typeName.isEmpty() -> false
    else -> listOf("REAL", "FLOA", "DOUB").any { it in typeName }
}

private fun defaultLiteral(tokens: List<String>): Any? {
    val first = tokens.firstOrNull() ?: return null
    val signed = first == "-" || first == "+"
    val sign = if (first == "-") -1 else 1
    val literal = (if (signed) tokens.getOrNull(1) else first) ?: return null
    val quoted = literal.length >= 2 && (literal.startsWith("'") || literal.startsWith("\""))
    return when {
        quoted -> literal.substring(1, literal.length - 1).replace("''", "'")
        literal.uppercase() == "TRUE" -> 1L
        literal.uppercase() == "FALSE" -> 0L
        literal.toLongOrNull() != null -> sign * literal.toLong()
        literal.toDoubleOrNull() != null -> sign * literal.toDouble()
        else -> null // NULL, CURRENT_TIME, an expression: nothing the migration reads
    }
}

private fun splitTopLevel(body: String): List<String> {
    val parts = mutableListOf<String>()
    val current = StringBuilder()
    var depth = 0
    var quote: Char? = null
    for (char in body) {
        val inQuote = quote != null
        quote = nextQuoteState(quote, char)
        depth += if (inQuote) 0 else parenthesisDelta(char)
        val separates = char == ',' && depth == 0 && !inQuote
        if (separates) parts += current.toString()
        if (separates) current.clear() else current.append(char)
    }
    parts += current.toString()
    return parts.map { it.trim() }.filter { it.isNotEmpty() }
}

// Inside a quote only its closing character ends it; outside, a quote character opens one.
private fun nextQuoteState(quote: Char?, char: Char): Char? = when {
    quote != null -> quote.takeIf { char != closingQuote(it) }
    char in QUOTES -> char
    else -> null
}

private fun parenthesisDelta(char: Char) = when (char) {
    '(' -> 1
    ')' -> -1
    else -> 0
}

// Words, quoted names and strings, numbers and single punctuation marks; enough for column definitions.
private fun tokenizeSql(definition: String): List<String> {
    val tokens = mutableListOf<String>()
    var index = 0
    while (index < definition.length) {
        val char = definition[index]
        val end = when {
            char in QUOTES -> definition.endOfQuoted(index)
            char.isWordPart() -> definition.endOfWord(index)
            else -> index + 1
        }
        if (!char.isWhitespace()) tokens += definition.substring(index, end)
        index = end
    }
    return tokens
}

private fun Char.isWordPart() = isLetterOrDigit() || this == '_' || this == '.'

// A doubled quote character inside a quoted string or name stands for itself ('it''s').
private fun String.endOfQuoted(start: Int): Int {
    val quote = closingQuote(this[start])
    var close = indexOf(quote, start + 1)
    while (close >= 0 && isDoubledQuote(close, quote)) close = indexOf(quote, close + 2)
    return if (close < 0) length else close + 1
}

// Brackets cannot be doubled: ']' always closes a bracketed name.
private fun String.isDoubledQuote(index: Int, quote: Char) = quote != ']' && getOrNull(index + 1) == quote

private fun String.endOfWord(start: Int): Int = (start until length).firstOrNull { !this[it].isWordPart() } ?: length

private fun unquoteIdentifier(token: String): String =
    if (token.length >= 2 && token.first() in QUOTES) token.substring(1, token.length - 1) else token

private fun closingQuote(open: Char) = if (open == '[') ']' else open

private val QUOTES = setOf('\'', '"', '`', '[')
private val TABLE_CONSTRAINTS = setOf("CONSTRAINT", "PRIMARY", "UNIQUE", "CHECK", "FOREIGN")
private val COLUMN_CONSTRAINT_STARTS = setOf(
    "CONSTRAINT", "PRIMARY", "NOT", "NULL", "UNIQUE", "CHECK", "DEFAULT", "COLLATE", "REFERENCES", "GENERATED", "AS", "("
)
private val WITHOUT_ROWID = Regex("\\)\\s*WITHOUT\\s+ROWID", RegexOption.IGNORE_CASE)

// Body bytes of serial types 0 to 9: NULL, five integer widths, a 64-bit integer, a float, then 0 and 1.
private val FIXED_SERIAL_TYPE_LENGTHS = intArrayOf(0, 1, 2, 3, 4, 6, 8, 8, 0, 0)
private const val FIRST_VARIABLE_SERIAL_TYPE = 12L
private const val SERIAL_NULL = 0L
private const val SERIAL_LARGEST_INTEGER = 6L
private const val SERIAL_FLOAT = 7L
private const val SERIAL_ZERO = 8L
private const val SERIAL_ONE = 9L
private const val VARINT_GROUPED_BYTES = 8
private const val VARINT_GROUP_BITS = 7
private const val VARINT_GROUP_MASK = 0x7f
private const val VARINT_CONTINUATION = 0x80

private const val RESERVED_BYTES_OFFSET = 20
private const val TEXT_ENCODING_OFFSET = 56
private const val UTF8_ENCODING = 1
private const val DATABASE_HEADER_SIZE = 100
private const val MIN_USABLE_SIZE = 480
private const val SCHEMA_ROOT_PAGE = 1
private const val SCHEMA_ROOT_PAGE_COLUMN = 3
private const val SCHEMA_SQL_COLUMN = 4
private const val TABLE_INTERIOR = 0x05
private const val TABLE_LEAF = 0x0d
private const val CELL_COUNT_OFFSET = 3
private const val RIGHTMOST_POINTER_OFFSET = 8
private const val LEAF_HEADER_SIZE = 8
private const val INTERIOR_HEADER_SIZE = 12
private const val CELL_POINTER_SIZE = 2
private const val TABLE_LEAF_MAX_LOCAL_OVERHEAD = 35
private const val MIN_LOCAL_OVERHEAD = 12
private const val MIN_LOCAL_NUMERATOR = 32
private const val MIN_LOCAL_DENOMINATOR = 255
private const val MIN_LOCAL_SUBTRAHEND = 23
