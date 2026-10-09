package jp.co.soramitsu.account.impl.data.repository

import java.nio.ByteBuffer

/**
 * Shared Android/iOS plaintext envelope grammar for a *local* wallet-material draft. It is not an
 * installer contract: the sole v1 derivation mode explicitly says that the inner source format is
 * opaque to the other platform. Only an authenticated-encryption envelope may carry these bytes.
 *
 * Bytes: ASCII "FPWMLE01", u8 version (1), u8 origin, u8 source format, u8 derivation mode,
 * big-endian u32 payload length, then exact payload bytes. No optional or trailing fields exist.
 * The matching iOS codec and both sets of synthetic golden-vector tests define the same grammar.
 */
internal object PortableWalletMaterialEnvelope {
    private val MAGIC = "FPWMLE01".toByteArray(Charsets.US_ASCII)
    private const val VERSION = 1
    private const val HEADER_SIZE = 16
    private const val BYTE_MASK = 0xff
    private const val MAX_ENCODED_BYTES = 256 * 1024 - 44 // Existing FPBKAEAD v1 plaintext ceiling.
    private const val MAX_PAYLOAD_BYTES = MAX_ENCODED_BYTES - HEADER_SIZE
    private const val SEMANTIC_SOURCE_WIRE_VALUE = 3

    internal enum class Origin(val wireValue: Int) { ANDROID(1), IOS(2) }
    internal enum class SourceFormat(val wireValue: Int) {
        ANDROID_DRAFT_V2(1),

        /** Reserved for the existing iOS in-memory draft; no serializer or installer is wired. */
        IOS_KEYCHAIN_V2_INVENTORY(2),

        /** Shared semantic payload grammar; installation remains disabled on both platforms. */
        PORTABLE_SEMANTIC_V1(SEMANTIC_SOURCE_WIRE_VALUE)
    }
    internal enum class DerivationMode(val wireValue: Int) { LOCAL_OPAQUE(0), PORTABLE(1) }

    internal class Record(
        val origin: Origin,
        val sourceFormat: SourceFormat,
        val derivationMode: DerivationMode,
        val payload: ByteArray
    ) {
        fun clearPayload() = payload.fill(0)
        override fun toString(): String = "PortableWalletMaterialEnvelope.Record(redacted)"
    }

    fun encode(record: Record): ByteArray {
        requireSupported(record.origin, record.sourceFormat, record.derivationMode)
        require(record.payload.size in 1..MAX_PAYLOAD_BYTES) { "Wallet material envelope size is invalid" }
        val size = record.payload.size
        return ByteBuffer.allocate(HEADER_SIZE + size).apply {
            put(MAGIC)
            put(VERSION.toByte())
            put(record.origin.wireValue.toByte())
            put(record.sourceFormat.wireValue.toByte())
            put(record.derivationMode.wireValue.toByte())
            putInt(size)
            put(record.payload)
        }.array()
    }

    fun decode(encoded: ByteArray): Record {
        require(encoded.size in HEADER_SIZE + 1..MAX_ENCODED_BYTES) {
            "Wallet material envelope size is invalid"
        }
        val reader = ByteBuffer.wrap(encoded)
        val magic = ByteArray(MAGIC.size)
        reader.get(magic)
        require(magic.contentEquals(MAGIC) && unsigned(reader.get()) == VERSION) {
            "Wallet material envelope version is unsupported"
        }
        val originCode = unsigned(reader.get())
        val sourceCode = unsigned(reader.get())
        val derivationCode = unsigned(reader.get())
        val origin = Origin.entries.singleOrNull { it.wireValue == originCode }
        val sourceFormat = SourceFormat.entries.singleOrNull { it.wireValue == sourceCode }
        val derivationMode = DerivationMode.entries.singleOrNull { it.wireValue == derivationCode }
        require(origin != null && sourceFormat != null && derivationMode != null) {
            "Wallet material envelope source is unsupported"
        }
        requireSupported(origin, sourceFormat, derivationMode)
        val size = reader.int
        require(size in 1..MAX_PAYLOAD_BYTES && size == encoded.size - HEADER_SIZE) {
            "Wallet material envelope length is invalid"
        }
        val payload = ByteArray(size)
        reader.get(payload)
        return Record(origin, sourceFormat, derivationMode, payload)
    }

    /** Wrap only a structurally and cryptographically checked Android-local draft. */
    fun encodeValidatedAndroidDraft(draft: ByteArray): ByteArray {
        val validated = PortableWalletMaterialDraft.decode(draft)
        try {
            return encode(
                Record(
                    Origin.ANDROID,
                    SourceFormat.ANDROID_DRAFT_V2,
                    DerivationMode.LOCAL_OPAQUE,
                    draft
                )
            )
        } finally {
            validated.clearSecrets()
        }
    }

    private fun requireSupported(
        origin: Origin,
        source: SourceFormat,
        mode: DerivationMode
    ) {
        if (source == SourceFormat.PORTABLE_SEMANTIC_V1) {
            require(mode == DerivationMode.PORTABLE) {
                "Wallet material envelope source is unsupported"
            }
            return
        }
        val localSourceMatchesOrigin = when (origin) {
            Origin.ANDROID -> source == SourceFormat.ANDROID_DRAFT_V2
            Origin.IOS -> source == SourceFormat.IOS_KEYCHAIN_V2_INVENTORY
        }
        require(mode == DerivationMode.LOCAL_OPAQUE && localSourceMatchesOrigin) {
            "Wallet material envelope source is unsupported"
        }
    }

    private fun unsigned(value: Byte): Int = value.toInt() and BYTE_MASK
}
