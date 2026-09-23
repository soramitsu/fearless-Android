package jp.co.soramitsu.account.impl.data.repository

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * Versioned, platform-neutral plaintext inventory for an encrypted wallet-material envelope.
 * This codec proves byte structure and semantic completeness only. No restore installer consumes it.
 *
 * Wire grammar (all integers unsigned big endian): ASCII FPWMSM01, u8 version 1, u16 wallet count,
 * u16 selected wallet index; then ordered wallets. List order is authoritative; u32 source position
 * preserves historical presentation state and may tie across wallets. A wallet is 16 ID bytes, u32 source position,
 * u8 initialized, u16-length UTF-8 name, u8 metadata count and ascending metadata TLVs, u16 slot
 * count and ascending slots. A metadata TLV is u8 ID, u16 length, bytes. A slot is u8 role,
 * u16-length UTF-8 key, u8 field count and ascending field TLVs (u8 ID, u16 length, bytes).
 * Empty slot keys belong to roots; chain and favorite slots use their nonempty chain ID as key.
 * No duplicate, unknown, or trailing item is accepted. Callers must erase plaintext after use.
 */
@Suppress("LargeClass", "MagicNumber") // Wire tags and bounds are explicit protocol values.
internal object PortableWalletSemanticMaterial {
    private val MAGIC = "FPWMSM01".toByteArray(Charsets.US_ASCII)
    private const val VERSION = 1
    private const val MAX_BYTES = 256 * 1024 - 44 - 16 // FPBKAEAD v1 minus FPWMLE01 header.
    private const val MAX_WALLETS = 128
    private const val MAX_SLOTS = 1_412 // Roots, 128 chains/favorites/watch identities, 1024 auxiliary.
    private const val MAX_AUXILIARY = 1_024
    private const val MAX_WATCH = 128
    private const val MAX_CHAINS = 128
    private const val MAX_TEXT = 2_048
    private const val MAX_SECRET = 32 * 1024
    private const val MAX_PUBLIC = 128
    private const val ID_BYTES = 16

    internal object Role {
        const val SUBSTRATE_ROOT = 1
        const val EVM_ROOT = 2
        const val TON_ROOT = 3
        const val LEGACY_SUBSTRATE = 4
        const val CHAIN_ACCOUNT = 5
        const val FAVORITE_CHAIN = 6
        const val AUXILIARY_SOURCE = 7
        const val WATCH_IDENTITY = 8
    }

    internal object FieldId {
        const val PUBLIC_KEY = 1
        const val PRIVATE_KEY = 2
        const val NONCE = 3
        const val ENTROPY = 4
        const val SEED = 5
        const val DERIVATION_PATH = 6
        const val ACCOUNT_ID_OR_ADDRESS = 7
        const val CRYPTO_TYPE = 8
        const val CHAIN_NAME = 9
        const val INITIALIZED_OR_FAVORITE = 10
        const val SOURCE_RECIPE = 11
        const val MNEMONIC = 12
        const val TON_CONTRACT_VERSION = 13
        const val TON_ADDRESS_ENCODING = 14
        const val SOURCE_PLATFORM = 15
        const val SOURCE_SLOT_ROLE = 16
        const val BINDING_KIND = 17
        const val BINDING_CHAIN_ID = 18
        const val SOURCE_FORMAT = 19
        const val SOURCE_BYTES = 20
        const val BINDING_ACCOUNT_ID = 21
        const val WATCH_ECOSYSTEM = 22
        const val WATCH_CHAIN_ID = 23
    }

    internal object MetadataId {
        const val ASSET_KEYS_ORDER = 1
        const val UNUSED_CHAIN_IDS = 2
        const val SELECTED_CURRENCY = 3
        const val NETWORK_MANAGEMENT_FILTER = 4
        const val ASSET_VISIBILITY = 5
        const val FAVORITE_CHAIN_IDS = 6
        const val ASSET_FILTER_OPTIONS = 7
        const val ZERO_BALANCE_ASSETS_HIDDEN = 8
        const val CAN_EXPORT_ETHEREUM_MNEMONIC = 9
    }

    internal class Snapshot(val selectedIndex: Int, val wallets: List<Wallet>) {
        fun clearSecrets() = wallets.forEach(Wallet::clearSecrets)
        override fun toString(): String = "PortableWalletSemanticMaterial.Snapshot(redacted)"
    }

    internal class Wallet(
        val portableId: ByteArray,
        val sourcePosition: Long,
        val initialized: Boolean,
        val name: String,
        val metadata: List<Metadata>,
        val slots: List<Slot>
    ) {
        fun clearSecrets() {
            portableId.fill(0)
            metadata.forEach { it.value.fill(0) }
            slots.forEach(Slot::clearSecrets)
        }
        override fun toString(): String = "PortableWalletSemanticMaterial.Wallet(redacted)"
    }

    internal class Metadata(val id: Int, val value: ByteArray)

    internal class Slot(val role: Int, val key: String, val fields: List<Field>) {
        fun clearSecrets() = fields.forEach { it.value.fill(0) }
        override fun toString(): String = "PortableWalletSemanticMaterial.Slot(redacted)"
    }

    internal class Field(val id: Int, val value: ByteArray)

    fun encode(snapshot: Snapshot): ByteArray {
        requireValid(snapshot)
        val output = BoundedOutputStream()
        try {
            DataOutputStream(output).use { it.writeSnapshot(snapshot) }
            return output.toByteArray()
        } finally {
            output.clear()
        }
    }

    private fun DataOutputStream.writeSnapshot(snapshot: Snapshot) {
        write(MAGIC)
        writeByte(VERSION)
        writeShort(snapshot.wallets.size)
        writeShort(snapshot.selectedIndex)
        snapshot.wallets.forEach { writeWallet(it) }
    }

    private fun DataOutputStream.writeWallet(wallet: Wallet) {
        write(wallet.portableId)
        writeInt(wallet.sourcePosition.toInt())
        writeByte(if (wallet.initialized) 1 else 0)
        writeText(wallet.name)
        writeByte(wallet.metadata.size)
        wallet.metadata.forEach { item ->
            writeByte(item.id)
            writeValue(item.value)
        }
        writeShort(wallet.slots.size)
        wallet.slots.forEach { writeSlot(it) }
    }

    private fun DataOutputStream.writeSlot(slot: Slot) {
        writeByte(slot.role)
        writeText(slot.key)
        writeByte(slot.fields.size)
        slot.fields.forEach { item ->
            writeByte(item.id)
            writeValue(item.value)
        }
    }

    fun decode(encoded: ByteArray): Snapshot {
        require(encoded.size in 30..MAX_BYTES) { "Semantic wallet material size is invalid" }
        val allocated = ArrayList<ByteArray>()
        try {
            val reader = DataInputStream(ByteArrayInputStream(encoded))
            val magic = reader.readBytesExact(MAGIC.size, allocated)
            require(magic.contentEquals(MAGIC) && reader.readUnsignedByte() == VERSION) {
                "Semantic wallet material version is unsupported"
            }
            val count = reader.readUnsignedShort()
            require(count in 1..MAX_WALLETS) { "Semantic wallet count is invalid" }
            val selected = reader.readUnsignedShort()
            require(selected < count) { "Semantic wallet selection is invalid" }
            val wallets = ArrayList<Wallet>(count)
            repeat(count) {
                val id = reader.readBytesExact(ID_BYTES, allocated)
                val position = reader.readInt().toLong() and 0xffff_ffffL
                val initialized = reader.readCanonicalBoolean()
                val name = reader.readText(allowEmpty = true, allocated = allocated)
                val metadataCount = reader.readUnsignedByte()
                require(metadataCount <= MetadataId.CAN_EXPORT_ETHEREUM_MNEMONIC) {
                    "Semantic wallet metadata count is invalid"
                }
                val metadata = ArrayList<Metadata>(metadataCount)
                repeat(metadataCount) {
                    val metadataId = reader.readUnsignedByte()
                    metadata += Metadata(metadataId, reader.readValue(allocated))
                }
                val slotCount = reader.readUnsignedShort()
                require(slotCount in 1..MAX_SLOTS) { "Semantic wallet slot count is invalid" }
                val slots = ArrayList<Slot>(slotCount)
                repeat(slotCount) {
                    val role = reader.readUnsignedByte()
                    val key = reader.readText(allowEmpty = true, allocated = allocated)
                    val fieldCount = reader.readUnsignedByte()
                    require(fieldCount in 1..FieldId.WATCH_CHAIN_ID) {
                        "Semantic wallet field count is invalid"
                    }
                    val fields = ArrayList<Field>(fieldCount)
                    repeat(fieldCount) {
                        fields += Field(reader.readUnsignedByte(), reader.readValue(allocated))
                    }
                    slots += Slot(role, key, fields)
                }
                wallets += Wallet(id, position, initialized, name, metadata, slots)
            }
            require(reader.available() == 0) { "Semantic wallet material has trailing bytes" }
            return Snapshot(selected, wallets).also(::requireValid)
        } catch (failure: EOFException) {
            allocated.forEach { it.fill(0) }
            throw IllegalArgumentException("Semantic wallet material is truncated", failure)
        } catch (failure: Exception) {
            allocated.forEach { it.fill(0) }
            throw failure
        }
    }

    @Suppress("CyclomaticComplexMethod") // One pass enforces the complete wallet inventory contract.
    private fun requireValid(snapshot: Snapshot) {
        require(
            snapshot.wallets.size in 1..MAX_WALLETS &&
            snapshot.selectedIndex in snapshot.wallets.indices
        ) {
            "Semantic wallet selection is invalid"
        }
        snapshot.wallets.forEachIndexed { index, wallet ->
            require(
                wallet.portableId.size == ID_BYTES && wallet.portableId.any { it != 0.toByte() } &&
                wallet.sourcePosition in 0..0xffff_ffffL
            ) { "Semantic wallet identity is invalid" }
            require(snapshot.wallets.take(index).none { it.portableId.contentEquals(wallet.portableId) }) {
                "Semantic wallet identity is duplicated"
            }
            requireText(wallet.name, allowEmpty = true)
            require(wallet.metadata.size <= MetadataId.CAN_EXPORT_ETHEREUM_MNEMONIC) {
                "Semantic wallet metadata count is invalid"
            }
            requireStrictAscending(wallet.metadata.map(Metadata::id))
            wallet.metadata.forEach(::requireMetadata)
            require(wallet.slots.size in 1..MAX_SLOTS) { "Semantic wallet slot count is invalid" }
            var previous: Slot? = null
            var chainCount = 0
            var favoriteCount = 0
            var materialCount = 0
            var auxiliaryCount = 0
            var watchCount = 0
            wallet.slots.forEach { slot ->
                requireSlot(slot)
                previous?.let { prior ->
                    val sameRoleAndAscendingKey = slot.role == prior.role &&
                        compareUtf8(prior.key, slot.key) < 0
                    require(
                        slot.role > prior.role || sameRoleAndAscendingKey
                    ) {
                        "Semantic wallet slots are unordered or duplicated"
                    }
                }
                previous = slot
                when (slot.role) {
                    Role.CHAIN_ACCOUNT -> chainCount++
                    Role.FAVORITE_CHAIN -> favoriteCount++
                    Role.AUXILIARY_SOURCE -> {
                        require(slot.key == auxiliaryCount.toString(16).padStart(4, '0')) {
                            "Semantic auxiliary source ordinals are not canonical"
                        }
                        auxiliaryCount++
                    }
                    Role.WATCH_IDENTITY -> {
                        require(slot.key == watchCount.toString(16).padStart(4, '0')) {
                            "Semantic watch identity ordinals are not canonical"
                        }
                        watchCount++
                        materialCount++
                    }
                    else -> materialCount++
                }
            }
            require(
                chainCount <= MAX_CHAINS && favoriteCount <= MAX_CHAINS &&
                auxiliaryCount <= MAX_AUXILIARY && watchCount <= MAX_WATCH && materialCount > 0
            ) {
                "Semantic wallet slot inventory is invalid"
            }
            require(favoriteCount == 0 || wallet.metadata.none { it.id == MetadataId.FAVORITE_CHAIN_IDS }) {
                "Semantic wallet favorites have two sources"
            }
            wallet.slots.filter { it.role == Role.AUXILIARY_SOURCE }
                .forEach { requireAuxiliaryBinding(it, wallet.slots) }
        }
    }

    private fun requireMetadata(metadata: Metadata) {
        require(
            metadata.id in MetadataId.ASSET_KEYS_ORDER..MetadataId.CAN_EXPORT_ETHEREUM_MNEMONIC &&
            metadata.value.size <= MAX_SECRET
        ) { "Semantic wallet metadata is invalid" }
        when (metadata.id) {
            MetadataId.ASSET_KEYS_ORDER, MetadataId.UNUSED_CHAIN_IDS,
            MetadataId.FAVORITE_CHAIN_IDS, MetadataId.ASSET_FILTER_OPTIONS ->
                requireStringList(metadata.value)
            MetadataId.SELECTED_CURRENCY, MetadataId.NETWORK_MANAGEMENT_FILTER ->
                requireTextBytes(metadata.value, allowEmpty = true)
            MetadataId.ASSET_VISIBILITY -> requireVisibilityMap(metadata.value)
            MetadataId.ZERO_BALANCE_ASSETS_HIDDEN,
            MetadataId.CAN_EXPORT_ETHEREUM_MNEMONIC -> requireBooleanValue(metadata.value)
        }
    }

    @Suppress("CyclomaticComplexMethod") // Role-specific allowlists are intentionally explicit.
    private fun requireSlot(slot: Slot) {
        require(slot.role in Role.SUBSTRATE_ROOT..Role.WATCH_IDENTITY) {
            "Semantic wallet slot role is unsupported"
        }
        requireText(slot.key, allowEmpty = slot.role <= Role.LEGACY_SUBSTRATE)
        val isRootSlot = slot.role <= Role.LEGACY_SUBSTRATE
        require(isRootSlot == slot.key.isEmpty()) {
            "Semantic wallet slot key is invalid"
        }
        require(slot.fields.size in 1..FieldId.WATCH_CHAIN_ID) {
            "Semantic wallet field count is invalid"
        }
        requireStrictAscending(slot.fields.map(Field::id))
        val allowed = when (slot.role) {
            Role.SUBSTRATE_ROOT -> setOf(1, 2, 3, 4, 5, 6, 7, 8, 11)
            Role.EVM_ROOT -> setOf(1, 2, 3, 4, 5, 6, 7, 11)
            Role.TON_ROOT -> setOf(1, 2, 4, 5, 7, 11, 12, 13, 14)
            Role.LEGACY_SUBSTRATE -> setOf(1, 2, 3, 4, 5, 6, 7, 8, 11, 12)
            Role.CHAIN_ACCOUNT -> setOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)
            Role.FAVORITE_CHAIN -> setOf(10)
            Role.AUXILIARY_SOURCE -> setOf(11, 15, 16, 17, 18, 19, 20, 21)
            else -> setOf(1, 7, 8, 9, 10, 13, 14, 22, 23)
        }
        val required = when (slot.role) {
            Role.SUBSTRATE_ROOT -> setOf(1, 2, 7, 8, 11)
            Role.EVM_ROOT -> setOf(1, 2, 7, 11)
            Role.TON_ROOT -> setOf(1, 2, 7, 11, 13, 14)
            Role.LEGACY_SUBSTRATE -> setOf(1, 2, 7, 8, 11)
            Role.CHAIN_ACCOUNT -> setOf(1, 2, 7, 8, 9, 10, 11)
            Role.FAVORITE_CHAIN -> setOf(10)
            Role.AUXILIARY_SOURCE -> setOf(11, 15, 16, 17, 19, 20)
            else -> setOf(7, 22)
        }
        val present = slot.fields.map(Field::id).toSet()
        require(present.all { it in allowed } && present.containsAll(required)) {
            "Semantic wallet slot fields are unsupported or incomplete"
        }
        slot.fields.forEach { requireField(slot.role, it) }
        if (slot.role == Role.AUXILIARY_SOURCE) requireAuxiliarySource(slot, present)
        if (slot.role == Role.WATCH_IDENTITY) requireWatchIdentity(slot, present)
        if (slot.role == Role.TON_ROOT) requireTonAddress(slot)
        if (slot.role == Role.LEGACY_SUBSTRATE) {
            val recipe = slot.fields.single { it.id == FieldId.SOURCE_RECIPE }.value[0].toInt()
            val hasMnemonic = FieldId.MNEMONIC in present
            val hasEntropy = FieldId.ENTROPY in present
            val isMnemonicRecipe = recipe in setOf(1, 4)
            require(
                hasMnemonic == hasEntropy &&
                isMnemonicRecipe == hasMnemonic &&
                (recipe !in setOf(3, 5) || FieldId.DERIVATION_PATH !in present) &&
                (recipe != 5 || FieldId.SEED !in present)
            ) {
                "Semantic V1 derivation provenance is inconsistent"
            }
        }
    }

    @Suppress("CyclomaticComplexMethod") // Every supported platform slot and binding is enumerated.
    private fun requireAuxiliarySource(slot: Slot, present: Set<Int>) {
        val platform = slot.number(FieldId.SOURCE_PLATFORM)
        val sourceRole = slot.number(FieldId.SOURCE_SLOT_ROLE)
        val binding = slot.number(FieldId.BINDING_KIND)
        val format = slot.number(FieldId.SOURCE_FORMAT)
        val hasChainId = FieldId.BINDING_CHAIN_ID in present
        val hasAccountId = FieldId.BINDING_ACCOUNT_ID in present
        require(
            hasChainId == (binding == 5) && hasAccountId == (binding == 5)
        ) {
            "Semantic auxiliary account binding is incomplete"
        }
        val compatible = when (platform) {
            1 -> when (sourceRole) {
                10 -> binding == 2 && format == 2 // Android V1 source SCALE.
                11 -> binding == 2 && format == 4 // Android Substrate V3 SCALE.
                12 -> binding == 3 && format == 4 // Android EVM V3 SCALE.
                13 -> binding == 4 && format == 4 // Android TON V3 SCALE.
                14 -> binding == 5 && format == 3 // Android chain V2 SCALE.
                else -> false
            }
            2 -> format == 1 && when (sourceRole) {
                1, 5, 7 -> binding in setOf(1, 2, 5) // Substrate secret/seed/path.
                2, 6, 8 -> binding in setOf(1, 3, 5) // EVM secret/seed/path.
                3 -> binding in setOf(1, 4, 5) // TON secret can be account scoped in iOS Keychain.
                4 -> binding in 1..5 // Entropy may be attached at several levels.
                9 -> binding == 1 // Universal wallet source.
                else -> false
            }
            else -> false
        }
        require(compatible) { "Semantic auxiliary source role is unsupported" }
    }

    private fun requireWatchIdentity(slot: Slot, present: Set<Int>) {
        val ecosystem = slot.number(FieldId.WATCH_ECOSYSTEM)
        val hasChainId = FieldId.WATCH_CHAIN_ID in present
        val hasCryptoType = FieldId.CRYPTO_TYPE in present
        val hasTonContractVersion = FieldId.TON_CONTRACT_VERSION in present
        val hasTonAddressEncoding = FieldId.TON_ADDRESS_ENCODING in present
        require(
            hasChainId == (ecosystem == 4) &&
            hasCryptoType == (ecosystem == 1 || ecosystem == 4) &&
            hasTonContractVersion == (ecosystem == 3) &&
            hasTonAddressEncoding == (ecosystem == 3) &&
            (ecosystem !in setOf(1, 4) || FieldId.PUBLIC_KEY in present)
        ) {
            "Semantic watch identity is incomplete"
        }
        if (ecosystem != 3) {
            require(slot.value(FieldId.ACCOUNT_ID_OR_ADDRESS).size <= MAX_PUBLIC) {
                "Semantic watch address is oversized"
            }
        } else {
            requireTonAddress(slot)
        }
    }

    private fun requireTonAddress(slot: Slot) {
        val address = slot.value(FieldId.ACCOUNT_ID_OR_ADDRESS)
        when (slot.number(FieldId.TON_ADDRESS_ENCODING)) {
            1 -> require(address.size == 33) { "Canonical TON account address must be 33 bytes" }
            2 -> requireTextBytes(address, allowEmpty = false) // TonSwift JSON bytes, not transcoded.
        }
    }

    private fun requireAuxiliaryBinding(slot: Slot, allSlots: List<Slot>) {
        val binding = slot.number(FieldId.BINDING_KIND)
        val corresponding = when (binding) {
            1 -> true // A wallet-level Keychain slot is bound to the containing portable wallet ID.
            2 -> allSlots.any { it.role == Role.SUBSTRATE_ROOT || it.role == Role.LEGACY_SUBSTRATE }
            3 -> allSlots.any { it.role == Role.EVM_ROOT }
            4 -> allSlots.any { it.role == Role.TON_ROOT }
            5 -> {
                val chainId = strictUtf8(slot.value(FieldId.BINDING_CHAIN_ID))
                val accountId = slot.value(FieldId.BINDING_ACCOUNT_ID)
                allSlots.any { chain ->
                    chain.role == Role.CHAIN_ACCOUNT && chain.key == chainId &&
                        chain.value(FieldId.ACCOUNT_ID_OR_ADDRESS).contentEquals(accountId)
                }
            }
            else -> false
        }
        require(corresponding) { "Semantic auxiliary source has no matching account" }
    }

    private fun Slot.value(id: Int): ByteArray = fields.single { it.id == id }.value

    private fun Slot.number(id: Int): Int = value(id)[0].toInt() and 0xff

    @Suppress("CyclomaticComplexMethod") // Field type and bounds dispatch follows the wire grammar.
    private fun requireField(role: Int, field: Field) {
        val value = field.value
        when (field.id) {
            FieldId.PUBLIC_KEY, FieldId.ACCOUNT_ID_OR_ADDRESS -> {
                if (field.id == FieldId.ACCOUNT_ID_OR_ADDRESS && role == Role.LEGACY_SUBSTRATE) {
                    requireTextBytes(value, allowEmpty = false)
                } else if (field.id == FieldId.ACCOUNT_ID_OR_ADDRESS &&
                    role in setOf(Role.TON_ROOT, Role.WATCH_IDENTITY)
                ) {
                    require(value.size in 1..MAX_TEXT) { "Semantic TON address is invalid" }
                } else {
                    require(value.size in 1..MAX_PUBLIC) { "Semantic public identity is invalid" }
                }
            }
            FieldId.PRIVATE_KEY, FieldId.NONCE, FieldId.ENTROPY, FieldId.SEED ->
                require(value.size in 1..MAX_SECRET) { "Semantic secret field size is invalid" }
            FieldId.DERIVATION_PATH, FieldId.CHAIN_NAME -> requireTextBytes(value, allowEmpty = true)
            FieldId.MNEMONIC -> requireTextBytes(value, allowEmpty = false)
            FieldId.CRYPTO_TYPE -> requireOneByteIn(value, 1..3)
            FieldId.INITIALIZED_OR_FAVORITE -> requireBooleanValue(value)
            FieldId.SOURCE_RECIPE -> requireOneByteIn(
                value,
                when (role) {
                    Role.LEGACY_SUBSTRATE -> 1..5
                    Role.AUXILIARY_SOURCE -> 0..5
                    else -> 0..0
                }
            )
            FieldId.TON_CONTRACT_VERSION -> requireOneByteIn(value, 2..2) // Released Wallet V4R2 only.
            FieldId.TON_ADDRESS_ENCODING -> requireOneByteIn(value, 1..2)
            FieldId.SOURCE_PLATFORM -> requireOneByteIn(value, 1..2)
            FieldId.SOURCE_SLOT_ROLE -> requireOneByteIn(value, 1..14)
            FieldId.BINDING_KIND -> requireOneByteIn(value, 1..5)
            FieldId.BINDING_CHAIN_ID -> requireTextBytes(value, allowEmpty = false)
            FieldId.SOURCE_FORMAT -> requireOneByteIn(value, 1..4)
            FieldId.SOURCE_BYTES -> require(value.size in 1..MAX_SECRET) {
                "Semantic auxiliary source size is invalid"
            }
            FieldId.BINDING_ACCOUNT_ID -> require(value.size in 1..MAX_PUBLIC) {
                "Semantic auxiliary account ID is invalid"
            }
            FieldId.WATCH_ECOSYSTEM -> requireOneByteIn(value, 1..4)
            FieldId.WATCH_CHAIN_ID -> requireTextBytes(value, allowEmpty = false)
            else -> throw IllegalArgumentException("Semantic wallet field is unsupported")
        }
    }

    private fun requireStringList(value: ByteArray) {
        val reader = DataInputStream(ByteArrayInputStream(value))
        require(value.size >= 2) { "Semantic string list is truncated" }
        val count = reader.readUnsignedShort()
        require(count <= MAX_CHAINS) { "Semantic string list is oversized" }
        repeat(count) { reader.readTextFromValue() }
        require(reader.available() == 0) { "Semantic string list has trailing bytes" }
    }

    private fun requireVisibilityMap(value: ByteArray) {
        val reader = DataInputStream(ByteArrayInputStream(value))
        require(value.size >= 2) { "Semantic visibility map is truncated" }
        val count = reader.readUnsignedShort()
        require(count <= MAX_CHAINS) { "Semantic visibility map is oversized" }
        var previous: String? = null
        repeat(count) {
            val key = reader.readTextFromValue()
            require(key.isNotEmpty() && (previous == null || compareUtf8(previous!!, key) < 0)) {
                "Semantic visibility map is unordered or duplicated"
            }
            previous = key
            reader.readCanonicalBoolean()
        }
        require(reader.available() == 0) { "Semantic visibility map has trailing bytes" }
    }

    private fun DataInputStream.readTextFromValue(): String {
        require(available() >= 2) { "Semantic metadata text is truncated" }
        val size = readUnsignedShort()
        require(size <= MAX_TEXT && size <= available()) { "Semantic metadata text size is invalid" }
        val bytes = ByteArray(size)
        try {
            readFully(bytes)
            return strictUtf8(bytes)
        } finally {
            bytes.fill(0)
        }
    }

    private fun requireOneByteIn(value: ByteArray, range: IntRange) {
        require(value.size == 1) { "Semantic wallet enum value is invalid" }
        val unsigned = value[0].toInt() and 0xff
        require(unsigned in range) {
            "Semantic wallet enum value is invalid"
        }
    }

    private fun requireBooleanValue(value: ByteArray) = requireOneByteIn(value, 0..1)

    private fun requireText(value: String, allowEmpty: Boolean) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        try {
            requireTextBytes(bytes, allowEmpty)
        } finally {
            bytes.fill(0)
        }
    }

    private fun requireTextBytes(value: ByteArray, allowEmpty: Boolean) {
        require(
            value.size <= MAX_TEXT && (allowEmpty || value.isNotEmpty()) &&
            strictUtf8(value).toByteArray(Charsets.UTF_8).contentEquals(value)
        ) {
            "Semantic wallet UTF-8 text is invalid"
        }
    }

    private fun strictUtf8(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (failure: CharacterCodingException) {
        throw IllegalArgumentException("Semantic wallet UTF-8 text is invalid", failure)
    }

    private fun requireStrictAscending(values: List<Int>) {
        require(values.zipWithNext().all { (left, right) -> left < right }) {
            "Semantic wallet fields are unordered or duplicated"
        }
    }

    private fun compareUtf8(left: String, right: String): Int {
        val a = left.toByteArray(Charsets.UTF_8)
        val b = right.toByteArray(Charsets.UTF_8)
        for (index in 0 until minOf(a.size, b.size)) {
            val difference = (a[index].toInt() and 0xff) - (b[index].toInt() and 0xff)
            if (difference != 0) return difference
        }
        return a.size - b.size
    }

    private fun DataOutputStream.writeText(value: String) = writeValue(value.toByteArray(Charsets.UTF_8))

    private fun DataOutputStream.writeValue(value: ByteArray) {
        require(value.size <= 0xffff) { "Semantic wallet field exceeds u16 length" }
        writeShort(value.size)
        write(value)
    }

    private fun DataInputStream.readText(allowEmpty: Boolean, allocated: MutableList<ByteArray>): String {
        val value = readValue(allocated, MAX_TEXT)
        requireTextBytes(value, allowEmpty)
        return strictUtf8(value)
    }

    private fun DataInputStream.readValue(allocated: MutableList<ByteArray>, max: Int = MAX_SECRET): ByteArray {
        val size = readUnsignedShort()
        require(size <= max && size <= available()) { "Semantic wallet field length is invalid" }
        return readBytesExact(size, allocated)
    }

    private fun DataInputStream.readBytesExact(size: Int, allocated: MutableList<ByteArray>): ByteArray =
        ByteArray(size).also {
            allocated += it
            readFully(it)
        }

    private fun DataInputStream.readCanonicalBoolean(): Boolean = when (readUnsignedByte()) {
        0 -> false
        1 -> true
        else -> throw IllegalArgumentException("Semantic wallet boolean is invalid")
    }

    private class BoundedOutputStream : ByteArrayOutputStream() {
        fun clear() {
            buf.fill(0)
            reset()
        }

        override fun write(value: Int) {
            require(count < MAX_BYTES) { "Semantic wallet material exceeds envelope limit" }
            super.write(value)
        }

        override fun write(
            value: ByteArray,
            offset: Int,
            length: Int
        ) {
            require(length <= MAX_BYTES - count) { "Semantic wallet material exceeds envelope limit" }
            super.write(value, offset, length)
        }
    }
}
