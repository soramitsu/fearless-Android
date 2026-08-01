package jp.co.soramitsu.common.data.secrets

import jp.co.soramitsu.common.data.storage.encrypt.MAX_WALLET_SECRET_PLAINTEXT_CHARS

private const val MAX_SOURCE_TYPE_BYTES = 64
private const val MAX_RECOVERY_MATERIAL_BYTES = 8_192
private const val MAX_PRIVATE_KEY_BYTES = 64
private const val MAX_PUBLIC_KEY_BYTES = 65
private const val MAX_NONCE_BYTES = 64
private const val MAX_SECRET_STRING_BYTES = 8_192
private const val MAX_SCHEMA_NESTING = 1
private const val HEX_PREFIX_CHARS = 2

/**
 * Allocation-safe structural preflight for every persisted wallet-secret
 * SCALE schema.
 *
 * The parser walks the canonical hexadecimal text in place. It validates all
 * compact lengths and field bounds before the allocating SCALE decoder is
 * allowed to see the payload.
 */
object WalletSecretScalePreflight {

    /**
     * Preflights the three-field signing-data schema shipped in wallet 0.4.x.
     */
    fun requireLegacyV04SigningData(encoded: String) {
        encoded.preflight("legacy V0.4 signing data") {
            keyPair("legacy signing data")
        }
    }

    /**
     * Preflights a standalone V2 `KeyPairSchema` payload.
     */
    fun requireKeyPairV2(encoded: String) {
        encoded.preflight("V2 keypair") {
            keyPair("keypair")
        }
    }

    /**
     * Preflights the seven-field V1 `SourceInternal` schema.
     */
    fun requireSourceV1(encoded: String) {
        encoded.preflight("V1 source") {
            string("source type", MAX_SOURCE_TYPE_BYTES)
            byteArray("private key", MAX_PRIVATE_KEY_BYTES)
            byteArray("public key", MAX_PUBLIC_KEY_BYTES)
            optional("nonce") {
                byteArray("nonce", MAX_NONCE_BYTES)
            }
            optional("seed") {
                byteArray("seed", MAX_RECOVERY_MATERIAL_BYTES)
            }
            optional("mnemonic") {
                string("mnemonic", MAX_SECRET_STRING_BYTES)
            }
            optional("derivation path") {
                string("derivation path", MAX_SECRET_STRING_BYTES)
            }
        }
    }

    /**
     * Preflights the current seven-field V2 `MetaAccountSecrets` schema.
     */
    fun requireMetaAccountV2(encoded: String) {
        encoded.preflight("V2 meta-account") {
            metaAccountV69Fields()
            optional("TON keypair") {
                keyPair("TON keypair")
            }
        }
    }

    /**
     * Preflights the V2 `ChainAccountSecrets` schema.
     */
    fun requireChainAccountV2(encoded: String) {
        encoded.preflight("V2 chain-account") {
            optionalRecoveryByteArray("entropy")
            optionalRecoveryByteArray("seed")
            keyPair("chain keypair")
            optionalSecretString("derivation path")
        }
    }

    /**
     * Preflights the V3 `SubstrateSecrets` schema.
     */
    fun requireSubstrateV3(encoded: String) {
        encoded.preflight("V3 Substrate") {
            optionalRecoveryByteArray("entropy")
            optionalRecoveryByteArray("seed")
            keyPair("Substrate keypair")
            optionalSecretString("Substrate derivation path")
        }
    }

    /**
     * Preflights the V3 `EthereumSecrets` schema.
     */
    fun requireEthereumV3(encoded: String) {
        encoded.preflight("V3 Ethereum") {
            optionalRecoveryByteArray("entropy")
            optionalRecoveryByteArray("seed")
            keyPair("Ethereum keypair")
            optionalSecretString("Ethereum derivation path")
        }
    }

    /**
     * Preflights the V3 `TonSecrets` schema.
     */
    fun requireTonV3(encoded: String) {
        encoded.preflight("V3 TON") {
            byteArray("seed", MAX_RECOVERY_MATERIAL_BYTES)
            byteArray("private key", MAX_PRIVATE_KEY_BYTES)
            byteArray("public key", MAX_PUBLIC_KEY_BYTES)
        }
    }

    /**
     * Preflights the exact six-field meta-account layout written before TON.
     */
    fun requireLegacyV69(encoded: String) {
        encoded.preflight("legacy V69 meta-account") {
            metaAccountV69Fields()
        }
    }

    /**
     * Accepts exactly one of the two supported meta-account layouts.
     *
     * This is for migration boundaries which deliberately support both the
     * historical six-field V69 payload and the complete seven-field current V2
     * payload. It does not ignore arbitrary trailing bytes.
     */
    fun requireMetaAccountV2OrLegacyV69(
        encoded: String
    ): WalletMetaAccountScaleLayout {
        var layout = WalletMetaAccountScaleLayout.LEGACY_V69
        encoded.preflight("V2 or legacy V69 meta-account") {
            metaAccountV69Fields()
            if (hasRemaining()) {
                optional("TON keypair") {
                    keyPair("TON keypair")
                }
                layout = WalletMetaAccountScaleLayout.CURRENT_V2
            }
        }
        return layout
    }

    private fun ScaleHexCursor.metaAccountV69Fields() {
        optionalRecoveryByteArray("entropy")
        optionalRecoveryByteArray("seed")
        keyPair("Substrate keypair")
        optionalSecretString("Substrate derivation path")
        optional("Ethereum keypair") {
            keyPair("Ethereum keypair")
        }
        optionalSecretString("Ethereum derivation path")
    }

    private fun ScaleHexCursor.keyPair(label: String) {
        nested(label) {
            byteArray("$label private key", MAX_PRIVATE_KEY_BYTES)
            byteArray("$label public key", MAX_PUBLIC_KEY_BYTES)
            optional("$label nonce") {
                byteArray("$label nonce", MAX_NONCE_BYTES)
            }
        }
    }

    private fun ScaleHexCursor.optionalRecoveryByteArray(label: String) {
        optional(label) {
            byteArray(label, MAX_RECOVERY_MATERIAL_BYTES)
        }
    }

    private fun ScaleHexCursor.optionalSecretString(label: String) {
        optional(label) {
            string(label, MAX_SECRET_STRING_BYTES)
        }
    }
}

private fun String.preflight(
    schemaName: String,
    readFields: ScaleHexCursor.() -> Unit
) {
    val cursor = ScaleHexCursor(this, schemaName)
    cursor.readFields()
    cursor.requireFinished()
}

/**
 * A cursor over hex characters rather than a decoded byte array. Claimed SCALE
 * lengths can therefore never influence allocation.
 */
@Suppress("LargeClass", "MagicNumber")
private class ScaleHexCursor(
    private val encoded: String,
    private val schemaName: String
) {

    private val byteCount: Int
    private var bytePosition = 0
    private var schemaDepth = 0

    init {
        requireLocal(
            encoded.length <= MAX_WALLET_SECRET_PLAINTEXT_CHARS,
            "$schemaName payload exceeds the safe hexadecimal limit"
        )
        requireLocal(
            encoded.startsWith("0x"),
            "$schemaName payload must have a canonical 0x prefix"
        )
        requireLocal(
            encoded.length > HEX_PREFIX_CHARS &&
                (encoded.length - HEX_PREFIX_CHARS) % 2 == 0,
            "$schemaName payload must contain a non-empty even number of hexadecimal digits"
        )
        for (index in HEX_PREFIX_CHARS until encoded.length) {
            requireLocal(
                encoded[index] in '0'..'9' || encoded[index] in 'a'..'f',
                "$schemaName payload contains noncanonical hexadecimal text"
            )
        }
        byteCount = (encoded.length - HEX_PREFIX_CHARS) / 2
    }

    fun byteArray(label: String, maximumBytes: Int) {
        val length = compactLength(label, maximumBytes)
        skip(length, label)
    }

    fun string(label: String, maximumBytes: Int) {
        val length = compactLength(label, maximumBytes)
        requireRemaining(length, label)
        requireCanonicalUtf8(
            start = bytePosition,
            length = length,
            label = label
        )
        bytePosition += length
    }

    fun optional(label: String, readValue: ScaleHexCursor.() -> Unit) {
        when (readByte("$label option discriminant")) {
            0 -> Unit
            1 -> readValue()
            else -> fail("$schemaName $label has an invalid option discriminant")
        }
    }

    fun nested(label: String, readValue: ScaleHexCursor.() -> Unit) {
        schemaDepth += 1
        requireLocal(
            schemaDepth <= MAX_SCHEMA_NESTING,
            "$schemaName $label exceeds the supported schema nesting"
        )
        try {
            readValue()
        } finally {
            schemaDepth -= 1
        }
    }

    fun hasRemaining(): Boolean = bytePosition < byteCount

    fun requireFinished() {
        requireLocal(
            bytePosition == byteCount,
            "$schemaName payload contains trailing bytes"
        )
    }

    private fun compactLength(label: String, maximumBytes: Int): Int {
        val first = readByte("$label compact length")
        val value = when (first and 0b11) {
            0b00 -> first ushr 2
            0b01 -> readTwoByteCompact(first, label)
            0b10 -> readFourByteCompact(first, label)
            else -> readBigIntegerCompact(first, label)
        }
        requireLocal(
            value <= maximumBytes,
            "$schemaName $label claims $value bytes; maximum is $maximumBytes"
        )
        return value
    }

    private fun readTwoByteCompact(first: Int, label: String): Int {
        val second = readByte("$label two-byte compact length")
        val value = (first or (second shl 8)) ushr 2
        requireLocal(
            value >= 1 shl 6,
            "$schemaName $label uses a noncanonical two-byte compact length"
        )
        return value
    }

    private fun readFourByteCompact(first: Int, label: String): Int {
        var encodedValue = first.toLong()
        repeat(3) { byteIndex ->
            encodedValue = encodedValue or (
                readByte("$label four-byte compact length").toLong() shl (8 * (byteIndex + 1))
                )
        }
        val value = encodedValue ushr 2
        requireLocal(
            value >= 1L shl 14,
            "$schemaName $label uses a noncanonical four-byte compact length"
        )
        return value.toInt()
    }

    private fun readBigIntegerCompact(first: Int, label: String): Int {
        val encodedBytes = (first ushr 2) + 4
        requireRemaining(encodedBytes, "$label big-integer compact length")

        val mostSignificantByte = byteAt(bytePosition + encodedBytes - 1)
        requireLocal(
            mostSignificantByte != 0,
            "$schemaName $label uses a noncanonical big-integer compact length"
        )

        if (encodedBytes > Int.SIZE_BYTES) {
            fail("$schemaName $label compact length exceeds the supported integer range")
        }

        var value = 0L
        repeat(encodedBytes) { byteIndex ->
            value = value or (byteAt(bytePosition + byteIndex).toLong() shl (8 * byteIndex))
        }
        bytePosition += encodedBytes

        requireLocal(
            value >= 1L shl 30,
            "$schemaName $label uses a noncanonical big-integer compact length"
        )
        requireLocal(
            value <= Int.MAX_VALUE.toLong(),
            "$schemaName $label compact length exceeds the supported integer range"
        )
        return value.toInt()
    }

    private fun skip(length: Int, label: String) {
        requireRemaining(length, label)
        bytePosition += length
    }

    private fun requireRemaining(length: Int, label: String) {
        requireLocal(
            length >= 0 && length <= byteCount - bytePosition,
            "$schemaName $label is truncated"
        )
    }

    @Suppress("CyclomaticComplexMethod")
    private fun requireCanonicalUtf8(start: Int, length: Int, label: String) {
        val end = start + length
        var index = start
        while (index < end) {
            val first = byteAt(index)
            val sequenceBytes = when {
                first <= 0x7f -> 1
                first in 0xc2..0xdf -> {
                    requireContinuation(
                        sequenceStart = index,
                        end = end,
                        offset = 1,
                        label = label
                    )
                    2
                }

                first == 0xe0 -> {
                    requireByteRange(
                        sequenceStart = index,
                        end = end,
                        offset = 1,
                        range = 0xa0..0xbf,
                        label = label
                    )
                    requireContinuation(
                        sequenceStart = index,
                        end = end,
                        offset = 2,
                        label = label
                    )
                    3
                }

                first in 0xe1..0xec || first in 0xee..0xef -> {
                    requireContinuation(
                        sequenceStart = index,
                        end = end,
                        offset = 1,
                        label = label
                    )
                    requireContinuation(
                        sequenceStart = index,
                        end = end,
                        offset = 2,
                        label = label
                    )
                    3
                }

                first == 0xed -> {
                    requireByteRange(
                        sequenceStart = index,
                        end = end,
                        offset = 1,
                        range = 0x80..0x9f,
                        label = label
                    )
                    requireContinuation(
                        sequenceStart = index,
                        end = end,
                        offset = 2,
                        label = label
                    )
                    3
                }

                first == 0xf0 -> {
                    requireByteRange(
                        sequenceStart = index,
                        end = end,
                        offset = 1,
                        range = 0x90..0xbf,
                        label = label
                    )
                    requireContinuation(
                        sequenceStart = index,
                        end = end,
                        offset = 2,
                        label = label
                    )
                    requireContinuation(
                        sequenceStart = index,
                        end = end,
                        offset = 3,
                        label = label
                    )
                    4
                }

                first in 0xf1..0xf3 -> {
                    requireContinuation(
                        sequenceStart = index,
                        end = end,
                        offset = 1,
                        label = label
                    )
                    requireContinuation(
                        sequenceStart = index,
                        end = end,
                        offset = 2,
                        label = label
                    )
                    requireContinuation(
                        sequenceStart = index,
                        end = end,
                        offset = 3,
                        label = label
                    )
                    4
                }

                first == 0xf4 -> {
                    requireByteRange(
                        sequenceStart = index,
                        end = end,
                        offset = 1,
                        range = 0x80..0x8f,
                        label = label
                    )
                    requireContinuation(
                        sequenceStart = index,
                        end = end,
                        offset = 2,
                        label = label
                    )
                    requireContinuation(
                        sequenceStart = index,
                        end = end,
                        offset = 3,
                        label = label
                    )
                    4
                }

                else -> fail("$schemaName $label is not canonical UTF-8")
            }
            index += sequenceBytes
        }
    }

    private fun requireContinuation(
        sequenceStart: Int,
        end: Int,
        offset: Int,
        label: String
    ) {
        requireByteRange(
            sequenceStart = sequenceStart,
            end = end,
            offset = offset,
            range = 0x80..0xbf,
            label = label
        )
    }

    private fun requireByteRange(
        sequenceStart: Int,
        end: Int,
        offset: Int,
        range: IntRange,
        label: String
    ) {
        requireLocal(
            sequenceStart <= end - 1 - offset &&
                byteAt(sequenceStart + offset) in range,
            "$schemaName $label is not canonical UTF-8"
        )
    }

    private fun readByte(label: String): Int {
        requireRemaining(1, label)
        return byteAt(bytePosition++)
    }

    private fun byteAt(position: Int): Int {
        val characterIndex = HEX_PREFIX_CHARS + position * 2
        return (hexNibble(encoded[characterIndex]) shl 4) or
            hexNibble(encoded[characterIndex + 1])
    }

    private fun hexNibble(character: Char): Int {
        return if (character <= '9') {
            character - '0'
        } else {
            character - 'a' + 10
        }
    }

    private fun requireLocal(condition: Boolean, message: String) {
        if (!condition) {
            fail(message)
        }
    }

    private fun fail(message: String): Nothing {
        throw WalletSecretScaleCorruptionException(message)
    }
}
