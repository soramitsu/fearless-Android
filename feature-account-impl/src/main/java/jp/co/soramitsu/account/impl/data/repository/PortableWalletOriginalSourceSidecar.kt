package jp.co.soramitsu.account.impl.data.repository

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Base64
import java.util.Collections
import java.util.UUID

/**
 * The encrypted post-install retention candidate for original opaque source records. It stores
 * exact AUXILIARY_SOURCE slot bytes and wallet-source commitments, without duplicating normalized
 * root, V1 or V2 target slots. Opaque source bytes can themselves contain wallet secrets and must
 * remain encrypted. Until target secret slots have been durably written and verified,
 * the encrypted cohort journal remains their sole receiving copy and cannot be removed.
 *
 * Wire: FPWOS001, version, operation UUID, after-image SHA-256, ordered wallets with local ID,
 * portable ID, uint32 source position, SHA-256 of the one-wallet canonical FPWMSM01 encoding,
 * and exact ordered auxiliary slots; final SHA-256. All numeric fields are unsigned big endian.
 * An installer must bind this sidecar to committed Room identity and target secret readback before
 * retaining it while retiring the cohort journal.
 */
internal object PortableWalletOriginalSourceSidecar {
    private val magic = "FPWOS001".toByteArray(Charsets.US_ASCII)
    private val role = PortableWalletSemanticMaterial.Role
    private const val version = 1
    private const val maxBytes = 320 * 1024
    private const val headerBytes = 8 + 1 + 36 + 32 + Short.SIZE_BYTES
    private const val walletHeaderBytes = Long.SIZE_BYTES + 16 + Int.SIZE_BYTES + 32 + Short.SIZE_BYTES
    private const val digestBytes = 32
    private const val maxWallets = 128
    private const val maxAuxiliaryPerWallet = 1_024
    private const val maxSourceValueBytes = 32 * 1_024
    private val requiredAuxiliaryFields = setOf(11, 15, 16, 17, 19, 20)
    private val allowedAuxiliaryFields = requiredAuxiliaryFields + setOf(18, 21)

    internal class Record internal constructor(
        val operationId: String,
        val afterImageSha256: String,
        wallets: List<Wallet>,
    ) {
        val wallets: List<Wallet> = Collections.unmodifiableList(wallets.toList())
        fun clearSecrets() = wallets.forEach(Wallet::clearSecrets)
        override fun toString(): String = "PortableWalletOriginalSourceSidecar.Record(redacted)"
    }

    internal class Wallet internal constructor(
        val localMetaId: Long,
        portableId: ByteArray,
        val sourcePosition: Long,
        semanticSha256: ByteArray,
        sources: List<Source>,
    ) {
        private val id = portableId.copyOf()
        private val semanticDigest = semanticSha256.copyOf()
        val sources: List<Source> = Collections.unmodifiableList(sources.toList())
        fun portableIdCopy(): ByteArray = id.copyOf()
        fun semanticSha256Copy(): ByteArray = semanticDigest.copyOf()
        internal fun clearSecrets() {
            id.fill(0)
            semanticDigest.fill(0)
            sources.forEach(Source::clearSecrets)
        }
        override fun toString(): String = "PortableWalletOriginalSourceSidecar.Wallet(redacted)"
    }

    internal class Source internal constructor(val ordinal: String, fields: List<Pair<Int, ByteArray>>) {
        private val values = fields.map { it.first to it.second.copyOf() }
        fun fieldCopy(id: Int): ByteArray? = values.singleOrNull { it.first == id }?.second?.copyOf()
        fun fieldIds(): List<Int> = values.map(Pair<Int, ByteArray>::first)
        internal fun clearSecrets() = values.forEach { it.second.fill(0) }
        override fun toString(): String = "PortableWalletOriginalSourceSidecar.Source(redacted)"
    }

    fun encode(
        afterImage: PortableWalletCohortAfterImage.Record,
        token: PortableWalletCohortJournalStore.Token,
    ): String {
        requireValidOperationId(token.operationId)
        val afterBytes = PortableWalletCohortAfterImage.encode(afterImage)
        val semantic = afterImage.semanticCopy()
        val ids = afterImage.localMetaIdsCopy()
        try {
            val afterDigest = MessageDigest.getInstance("SHA-256").digest(afterBytes)
            try {
                require(afterDigest.toLowerHex() == token.afterImageSha256) {
                    "Portable original-source commitment differs from its cohort"
                }
                val source = PortableWalletSemanticMaterial.decode(semantic)
                try {
                    require(source.wallets.size == ids.size) { "Portable original-source wallet count changed" }
                    val auxiliaries = source.wallets.map { wallet ->
                        wallet.slots.filter { it.role == role.AUXILIARY_SOURCE }
                    }
                    val size = headerBytes + digestBytes + source.wallets.size * walletHeaderBytes +
                        auxiliaries.sumOf { slots ->
                            slots.sumOf { slot ->
                                4 + 1 + slot.fields.sumOf { 1 + Short.SIZE_BYTES + it.value.size }
                            }
                        }
                    require(size in headerBytes + walletHeaderBytes + digestBytes..maxBytes) {
                        "Portable original-source sidecar is oversized"
                    }
                    val wire = ByteBuffer.allocate(size).apply {
                        put(magic)
                        put(version.toByte())
                        put(token.operationId.toByteArray(Charsets.US_ASCII))
                        put(afterDigest)
                        putShort(source.wallets.size.toShort())
                        source.wallets.forEachIndexed { index, wallet ->
                            putLong(ids[index])
                            put(wallet.portableId)
                            putInt(wallet.sourcePosition.toInt())
                            val oneWallet = PortableWalletSemanticMaterial.encode(
                                PortableWalletSemanticMaterial.Snapshot(0, listOf(wallet))
                            )
                            val walletDigest = MessageDigest.getInstance("SHA-256").digest(oneWallet)
                            try {
                                put(walletDigest)
                            } finally {
                                oneWallet.fill(0)
                                walletDigest.fill(0)
                            }
                            val slots = auxiliaries[index]
                            putShort(slots.size.toShort())
                            slots.forEach { slot ->
                                val key = slot.key.toByteArray(Charsets.US_ASCII)
                                require(key.size == 4) { "Portable auxiliary ordinal is invalid" }
                                put(key)
                                put(slot.fields.size.toByte())
                                slot.fields.forEach { field ->
                                    put(field.id.toByte())
                                    putShort(field.value.size.toShort())
                                    put(field.value)
                                }
                            }
                        }
                    }.array()
                    try {
                        val digest = MessageDigest.getInstance("SHA-256").apply {
                            update(wire, 0, size - digestBytes)
                        }.digest()
                        try {
                            System.arraycopy(digest, 0, wire, size - digestBytes, digestBytes)
                            return Base64.getEncoder().encodeToString(wire)
                        } finally {
                            digest.fill(0)
                        }
                    } finally {
                        wire.fill(0)
                    }
                } finally {
                    source.clearSecrets()
                }
            } finally {
                afterDigest.fill(0)
            }
        } finally {
            afterBytes.fill(0)
            semantic.fill(0)
            ids.fill(0)
        }
    }

    /** Bounded canonical reader for retained encrypted sidecars after journal retirement. */
    fun decode(stored: String): Record {
        require(stored.isNotEmpty() && stored.length <= (maxBytes + 2) / 3 * 4) {
            "Portable original-source sidecar size is invalid"
        }
        val wire = try {
            Base64.getDecoder().decode(stored)
        } catch (failure: IllegalArgumentException) {
            throw IllegalArgumentException("Portable original-source sidecar is malformed", failure)
        }
        val allocated = ArrayList<ByteArray>()
        val constructedSources = ArrayList<Source>()
        val wallets = ArrayList<Wallet>()
        try {
            require(wire.size in headerBytes + walletHeaderBytes + digestBytes..maxBytes &&
                Base64.getEncoder().encodeToString(wire) == stored
            ) { "Portable original-source wire is invalid" }
            val reader = ByteBuffer.wrap(wire)
            val foundMagic = ByteArray(magic.size)
            reader.get(foundMagic)
            require(foundMagic.contentEquals(magic) && reader.get().toInt() == version) {
                "Portable original-source version is unsupported"
            }
            val operationId = ByteArray(36).also { reader.get(it) }.toString(Charsets.US_ASCII)
            requireValidOperationId(operationId)
            val afterDigest = ByteArray(digestBytes).also { reader.get(it); allocated += it }
            val count = reader.short.toInt() and 0xffff
            require(count in 1..maxWallets) { "Portable original-source wallet count is invalid" }
            repeat(count) {
                val localMetaId = reader.long
                val portableId = ByteArray(16).also { reader.get(it); allocated += it }
                val position = reader.int.toLong() and 0xffff_ffffL
                val semanticDigest = ByteArray(digestBytes).also { reader.get(it); allocated += it }
                val sourceCount = reader.short.toInt() and 0xffff
                require(localMetaId > 0 && portableId.any { it != 0.toByte() } &&
                    sourceCount <= maxAuxiliaryPerWallet
                ) { "Portable original-source wallet binding is invalid" }
                val sources = ArrayList<Source>(sourceCount)
                repeat(sourceCount) { sourceIndex ->
                    val ordinal = ByteArray(4).also { reader.get(it) }.toString(Charsets.US_ASCII)
                    require(ordinal == sourceIndex.toString(16).padStart(4, '0')) {
                        "Portable original-source ordinal is invalid"
                    }
                    val fieldCount = reader.get().toInt() and 0xff
                    require(fieldCount in requiredAuxiliaryFields.size..allowedAuxiliaryFields.size) {
                        "Portable original-source field count is invalid"
                    }
                    val fields = ArrayList<Pair<Int, ByteArray>>(fieldCount)
                    repeat(fieldCount) {
                        val id = reader.get().toInt() and 0xff
                        val length = reader.short.toInt() and 0xffff
                        require(id in allowedAuxiliaryFields &&
                            (fields.isEmpty() || id > fields.last().first) &&
                            length in 1..maxSourceValueBytes && reader.remaining() >= length + digestBytes
                        ) { "Portable original-source field is invalid" }
                        fields += id to ByteArray(length).also { reader.get(it); allocated += it }
                    }
                    require(fields.map(Pair<Int, ByteArray>::first).containsAll(requiredAuxiliaryFields)) {
                        "Portable original-source fields are incomplete"
                    }
                    PortableWalletSemanticMaterial.requireValidAuxiliarySourceSlot(
                        PortableWalletSemanticMaterial.Slot(
                            role.AUXILIARY_SOURCE,
                            ordinal,
                            fields.map { PortableWalletSemanticMaterial.Field(it.first, it.second) },
                        )
                    )
                    sources += Source(ordinal, fields).also(constructedSources::add)
                }
                wallets += Wallet(localMetaId, portableId, position, semanticDigest, sources)
            }
            require(reader.remaining() == digestBytes) { "Portable original-source wire has trailing bytes" }
            val expectedDigest = MessageDigest.getInstance("SHA-256").apply {
                update(wire, 0, wire.size - digestBytes)
            }.digest()
            try {
                val actualDigest = ByteArray(digestBytes).also(reader::get)
                try {
                    require(MessageDigest.isEqual(expectedDigest, actualDigest)) {
                        "Portable original-source digest differs"
                    }
                } finally {
                    actualDigest.fill(0)
                }
            } finally {
                expectedDigest.fill(0)
            }
            require(wallets.map(Wallet::localMetaId).distinct().size == wallets.size) {
                "Portable original-source local IDs are duplicated"
            }
            require(wallets.map { it.portableIdCopy().toList() }.distinct().size == wallets.size) {
                "Portable original-source portable IDs are duplicated"
            }
            return Record(operationId, afterDigest.toLowerHex(), wallets)
        } catch (failure: Exception) {
            wallets.forEach(Wallet::clearSecrets)
            constructedSources.forEach(Source::clearSecrets)
            throw IllegalArgumentException("Portable original-source sidecar is invalid", failure)
        } finally {
            allocated.forEach { it.fill(0) }
            wire.fill(0)
        }
    }

    private fun ByteArray.toLowerHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun requireValidOperationId(operationId: String) {
        val parsed = runCatching { UUID.fromString(operationId) }.getOrNull()
        require(parsed != null && parsed.toString() == operationId &&
            parsed.version() == 4 && parsed.variant() == 2
        ) { "Portable original-source operation is invalid" }
    }
}
