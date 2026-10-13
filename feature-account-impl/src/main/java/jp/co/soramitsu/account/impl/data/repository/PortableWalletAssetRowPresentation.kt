package jp.co.soramitsu.account.impl.data.repository

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException

/** Canonical value of FPWMSM01 metadata ID 12; this carries no signing authority. */
@Suppress("MagicNumber") // Wire values and the envelope's fixed 32 KiB metadata bound.
internal object PortableWalletAssetRowPresentation {
    private const val VERSION = 1
    private const val MAX_VALUE_BYTES = 32 * 1024
    private const val MAX_ACCOUNT_BYTES = 128

    // A canonical row needs at least 15 bytes, in addition to the 3-byte list header.
    private const val MAX_ROWS = (MAX_VALUE_BYTES - 3) / 15

    internal data class Row(
        val chainId: String,
        val assetId: String,
        val accountId: List<Byte>,
        val enabled: Int?,
        val sortIndex: Int,
        val markedNotNeed: Boolean,
        val chainAccountName: String?
    )

    fun encode(rows: List<Row>): ByteArray {
        requireRows(rows)
        val output = BoundedOutputStream()
        try {
            writeRows(output, rows)
            return output.toByteArray()
        } finally {
            output.clear()
        }
    }

    private fun writeRows(output: BoundedOutputStream, rows: List<Row>) {
        DataOutputStream(output).use { writer ->
            writer.writeByte(VERSION)
            writer.writeShort(rows.size)
            rows.forEach { row -> writer.writeRow(row) }
        }
    }

    private fun DataOutputStream.writeRow(row: Row) {
        writeText(row.chainId)
        writeText(row.assetId)
        writeShort(row.accountId.size)
        row.accountId.forEach { writeByte(it.toInt()) }
        writeByte(row.enabled?.plus(1) ?: 0)
        writeInt(row.sortIndex)
        writeByte(if (row.markedNotNeed) 1 else 0)
        writeByte(if (row.chainAccountName == null) 0 else 1)
        row.chainAccountName?.let { writeText(it) }
    }

    fun decode(value: ByteArray): List<Row> {
        require(value.size in 18..MAX_VALUE_BYTES) {
            "Android asset presentation size is invalid"
        }
        try {
            val reader = DataInputStream(ByteArrayInputStream(value))
            require(reader.readUnsignedByte() == VERSION) {
                "Android asset presentation version is unsupported"
            }
            val count = reader.readUnsignedShort()
            require(count in 1..MAX_ROWS) { "Android asset presentation row count is invalid" }
            val rows = ArrayList<Row>(count)
            repeat(count) {
                val chainId = reader.readText(allowEmpty = false)
                val assetId = reader.readText(allowEmpty = false)
                val accountIdLength = reader.readUnsignedShort()
                require(accountIdLength <= MAX_ACCOUNT_BYTES) {
                    "Android asset presentation account ID is oversized"
                }
                val accountId = ByteArray(accountIdLength)
                reader.readFully(accountId)
                val enabledCode = reader.readUnsignedByte()
                require(enabledCode in 0..2) {
                    "Android asset presentation enabled value is invalid"
                }
                val sortIndex = reader.readInt()
                val markedNotNeed = reader.readCanonicalBoolean()
                val hasName = reader.readCanonicalBoolean()
                val chainAccountName = if (hasName) reader.readText(allowEmpty = true) else null
                rows += Row(
                    chainId, assetId, accountId.toList(),
                    if (enabledCode == 0) null else enabledCode - 1,
                    sortIndex, markedNotNeed, chainAccountName
                )
                accountId.fill(0)
            }
            require(reader.available() == 0) { "Android asset presentation has trailing bytes" }
            requireRows(rows)
            return rows
        } catch (failure: EOFException) {
            throw IllegalArgumentException("Android asset presentation is truncated", failure)
        }
    }

    private fun requireRows(rows: List<Row>) {
        require(rows.size in 1..MAX_ROWS) {
            "Android asset presentation row count is invalid"
        }
        var previous: Row? = null
        rows.forEach { row ->
            require(row.accountId.size <= MAX_ACCOUNT_BYTES && row.enabled in setOf(null, 0, 1)) {
                "Android asset presentation row is invalid"
            }
            require(
                row.enabled != null || row.sortIndex != Int.MAX_VALUE ||
                    row.markedNotNeed || row.chainAccountName != null
            ) { "Android asset presentation contains a default-only row" }
            requireText(row.chainId, allowEmpty = false)
            requireText(row.assetId, allowEmpty = false)
            row.chainAccountName?.let { requireText(it, allowEmpty = true) }
            require(previous == null || compareKeys(previous!!, row) < 0) {
                "Android asset presentation rows are unordered or duplicated"
            }
            previous = row
        }
    }

    private fun DataOutputStream.writeText(value: String) {
        val bytes = PortableWalletSemanticMaterial.encodeMetadataText(value)
        try {
            writeShort(bytes.size)
            write(bytes)
        } finally {
            bytes.fill(0)
        }
    }

    private fun DataInputStream.readText(allowEmpty: Boolean): String {
        val size = readUnsignedShort()
        require(size <= 2_048 && (allowEmpty || size > 0)) {
            "Android asset presentation text size is invalid"
        }
        val bytes = ByteArray(size)
        try {
            readFully(bytes)
            val value = bytes.toString(Charsets.UTF_8)
            val canonical = PortableWalletSemanticMaterial.encodeMetadataText(value)
            try {
                require(bytes.contentEquals(canonical)) {
                    "Android asset presentation text is not exact UTF-8"
                }
            } finally {
                canonical.fill(0)
            }
            return value
        } finally {
            bytes.fill(0)
        }
    }

    private fun DataInputStream.readCanonicalBoolean(): Boolean {
        val code = readUnsignedByte()
        require(code in 0..1) { "Android asset presentation boolean is invalid" }
        return code == 1
    }

    private fun requireText(value: String, allowEmpty: Boolean) {
        val bytes = PortableWalletSemanticMaterial.encodeMetadataText(value)
        try {
            require(allowEmpty || bytes.isNotEmpty()) {
                "Android asset presentation key is empty"
            }
        } finally {
            bytes.fill(0)
        }
    }

    private fun compareKeys(left: Row, right: Row): Int {
        val chain = compareUnsigned(left.chainId.toByteArray(Charsets.UTF_8).toList(), right.chainId.toByteArray(Charsets.UTF_8).toList())
        if (chain != 0) return chain
        val asset = compareUnsigned(left.assetId.toByteArray(Charsets.UTF_8).toList(), right.assetId.toByteArray(Charsets.UTF_8).toList())
        if (asset != 0) return asset
        return compareUnsigned(left.accountId, right.accountId)
    }

    private fun compareUnsigned(left: List<Byte>, right: List<Byte>): Int {
        for (index in 0 until minOf(left.size, right.size)) {
            val difference = (left[index].toInt() and 0xff) - (right[index].toInt() and 0xff)
            if (difference != 0) return difference
        }
        return left.size - right.size
    }

    private class BoundedOutputStream : ByteArrayOutputStream() {
        override fun write(value: Int) {
            require(count < MAX_VALUE_BYTES) { "Android asset presentation is oversized" }
            super.write(value)
        }

        override fun write(
            value: ByteArray,
            offset: Int,
            length: Int
        ) {
            require(length >= 0 && count <= MAX_VALUE_BYTES - length) {
                "Android asset presentation is oversized"
            }
            super.write(value, offset, length)
        }

        fun clear() = buf.fill(0)
    }
}
