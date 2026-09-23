package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.TonSecrets
import jp.co.soramitsu.common.utils.tonAccountId
import jp.co.soramitsu.core.models.CryptoType
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Base64

/**
 * Pure Android draft-to-FPWMSM01 projection. Callers must erase returned plaintext and snapshots.
 * No backup, restore, signing, or installation path invokes this converter.
 *
 * Portable IDs are the first 16 bytes of SHA-256 over ASCII
 * `FPWMSM01/android-wallet-id/v1\0` followed by the positive durable Android wallet ID as
 * an unsigned, big-endian u64. This identifies a wallet record, never a chain account/address.
 */
@Suppress("LargeClass", "MagicNumber") // Complete wire projection remains together; tags are fixed IDs.
internal object PortableWalletDraftSemanticTranscoder {
    private val idDomain = "FPWMSM01/android-wallet-id/v1\u0000".toByteArray(Charsets.US_ASCII)
    private val semantic = PortableWalletSemanticMaterial
    private val field = PortableWalletSemanticMaterial.FieldId
    private val role = PortableWalletSemanticMaterial.Role

    /** The draft encoder revalidates an in-memory snapshot before any secret is projected. */
    fun toSemanticSnapshot(draft: PortableWalletMaterialDraft.Snapshot): PortableWalletSemanticMaterial.Snapshot {
        val validated = PortableWalletMaterialDraft.encode(draft)
        try {
            val result = build(draft)
            try {
                semantic.encode(result).fill(0) // The committed codec checks every output constraint.
                return result
            } catch (failure: Exception) {
                result.clearSecrets()
                throw failure
            }
        } finally {
            validated.fill(0)
        }
    }

    /** Decode a guarded local draft and return only canonical semantic bytes. */
    fun encodeDraftPlaintext(plaintext: ByteArray): ByteArray {
        val draft = PortableWalletMaterialDraft.decode(plaintext)
        try {
            val result = build(draft)
            try {
                return semantic.encode(result)
            } finally {
                result.clearSecrets()
            }
        } finally {
            draft.clearSecrets()
        }
    }

    private fun build(draft: PortableWalletMaterialDraft.Snapshot): PortableWalletSemanticMaterial.Snapshot {
        val allocated = ArrayList<ByteArray>()
        try {
            val ordered = draft.wallets.sortedWith(compareBy({ it.identity.position }, { it.identity.id }))
            val selected = ordered.indexOfFirst { it.identity.isSelected }
            require(selected >= 0) { "Portable wallet selection is missing" }
            val wallets = ordered.map { makeWallet(it, allocated) }
            return PortableWalletSemanticMaterial.Snapshot(selected, wallets)
        } catch (failure: Exception) {
            allocated.forEach { it.fill(0) }
            throw failure
        }
    }

    private fun makeWallet(
        draft: PortableWalletMaterialDraft.Wallet,
        allocated: MutableList<ByteArray>,
    ): PortableWalletSemanticMaterial.Wallet {
        val identity = draft.identity
        val slots = ArrayList<PortableWalletSemanticMaterial.Slot>()
        val auxiliary = ArrayList<PortableWalletSemanticMaterial.Slot>()
        draft.substrateSecret?.let { raw ->
            slots += substrateSlot(raw, identity, allocated)
            auxiliary += auxiliarySlot(auxiliary.size, 11, 2, 4, raw, allocated)
        }
        draft.ethereumSecret?.let { raw ->
            slots += ethereumSlot(raw, identity, allocated)
            auxiliary += auxiliarySlot(auxiliary.size, 12, 3, 4, raw, allocated)
        }
        draft.tonSecret?.let { raw ->
            slots += tonSlot(raw, identity, allocated)
            auxiliary += auxiliarySlot(auxiliary.size, 13, 4, 4, raw, allocated)
        }
        draft.legacySubstrateSource?.let { source ->
            slots += legacySlot(source, identity, allocated)
        }
        identity.chainAccounts.zip(draft.chainSecrets).forEach { (chain, raw) ->
            slots += chainSlot(chain, raw, allocated)
            auxiliary +=
                auxiliarySlot(
                    auxiliary.size, 14, 5, 3, raw, allocated,
                    chainId = chain.chainId, accountId = chain.accountId,
                )
        }
        identity.favoriteChains.forEach { favorite ->
            slots +=
                makeSlot(
                    role.FAVORITE_CHAIN, favorite.chainId,
                    oneByte(
                        field.INITIALIZED_OR_FAVORITE,
                        if (favorite.isFavorite) 1 else 0, allocated,
                    ),
                )
        }
        slots += auxiliary
        slots.sortWith(
            Comparator { left, right ->
                val roleOrder = left.role.compareTo(right.role)
                if (roleOrder != 0) roleOrder else compareUtf8(left.key, right.key)
            },
        )
        require(slots.any { it.role in role.SUBSTRATE_ROOT..role.CHAIN_ACCOUNT }) {
            "Watch-only draft wallets have no captured signing material"
        }
        return PortableWalletSemanticMaterial.Wallet(
            portableId = portableId(identity.id).also(allocated::add),
            sourcePosition = identity.position.toLong(),
            initialized = identity.initialized,
            name = identity.name,
            metadata = emptyList(),
            slots = slots,
        )
    }

    private fun substrateSlot(
        raw: ByteArray,
        identity: PortableWalletMaterialDraft.WalletIdentity,
        allocated: MutableList<ByteArray>,
    ): PortableWalletSemanticMaterial.Slot {
        val parsed = raw.copyOf()
        val borrowed = ArrayList<ByteArray>()
        try {
            val source = SubstrateSecrets.read(parsed)
            val pair = source[SubstrateSecrets.SubstrateKeypair]
            val publicKey = pair[KeyPairSchema.PublicKey].also(borrowed::add)
            val expected = publicIdentity(identity.substratePublicKey, allocated)
            require(publicKey.contentEquals(expected)) { "Substrate source public identity mismatch" }
            return makeSlot(
                role.SUBSTRATE_ROOT,
                "",
                listOfNotNull(
                    bytes(field.PUBLIC_KEY, publicKey, allocated),
                    bytes(field.PRIVATE_KEY, pair[KeyPairSchema.PrivateKey].also(borrowed::add), allocated),
                    pair[KeyPairSchema.Nonce]?.also(borrowed::add)?.let { bytes(field.NONCE, it, allocated) },
                    source[SubstrateSecrets.Entropy]?.also(borrowed::add)?.let { bytes(field.ENTROPY, it, allocated) },
                    source[SubstrateSecrets.Seed]?.also(borrowed::add)?.let { bytes(field.SEED, it, allocated) },
                    source[SubstrateSecrets.SubstrateDerivationPath]?.let { text(field.DERIVATION_PATH, it, allocated) },
                    bytes(field.ACCOUNT_ID_OR_ADDRESS, publicIdentity(identity.substrateAccountId, allocated), allocated),
                    oneByte(field.CRYPTO_TYPE, cryptoType(identity.substrateCryptoType), allocated),
                    oneByte(field.SOURCE_RECIPE, 0, allocated),
                ),
            )
        } finally {
            borrowed.forEach { it.fill(0) }
            parsed.fill(0)
        }
    }

    private fun ethereumSlot(
        raw: ByteArray,
        identity: PortableWalletMaterialDraft.WalletIdentity,
        allocated: MutableList<ByteArray>,
    ): PortableWalletSemanticMaterial.Slot {
        val parsed = raw.copyOf()
        val borrowed = ArrayList<ByteArray>()
        try {
            val source = EthereumSecrets.read(parsed)
            val pair = source[EthereumSecrets.EthereumKeypair]
            val publicKey = pair[KeyPairSchema.PublicKey].also(borrowed::add)
            val expected = publicIdentity(identity.ethereumPublicKey, allocated)
            require(publicKey.contentEquals(expected)) { "EVM source public identity mismatch" }
            return makeSlot(
                role.EVM_ROOT,
                "",
                listOfNotNull(
                    bytes(field.PUBLIC_KEY, publicKey, allocated),
                    bytes(field.PRIVATE_KEY, pair[KeyPairSchema.PrivateKey].also(borrowed::add), allocated),
                    pair[KeyPairSchema.Nonce]?.also(borrowed::add)?.let { bytes(field.NONCE, it, allocated) },
                    source[EthereumSecrets.Entropy]?.also(borrowed::add)?.let { bytes(field.ENTROPY, it, allocated) },
                    source[EthereumSecrets.Seed]?.also(borrowed::add)?.let { bytes(field.SEED, it, allocated) },
                    source[EthereumSecrets.EthereumDerivationPath]?.let { text(field.DERIVATION_PATH, it, allocated) },
                    bytes(field.ACCOUNT_ID_OR_ADDRESS, publicIdentity(identity.ethereumAddress, allocated), allocated),
                    oneByte(field.SOURCE_RECIPE, 0, allocated),
                ),
            )
        } finally {
            borrowed.forEach { it.fill(0) }
            parsed.fill(0)
        }
    }

    private fun tonSlot(
        raw: ByteArray,
        identity: PortableWalletMaterialDraft.WalletIdentity,
        allocated: MutableList<ByteArray>,
    ): PortableWalletSemanticMaterial.Slot {
        val parsed = raw.copyOf()
        val borrowed = ArrayList<ByteArray>()
        try {
            val source = TonSecrets.read(parsed)
            val publicKey = source[TonSecrets.PublicKey].also(borrowed::add)
            val expected = publicIdentity(identity.tonPublicKey, allocated)
            require(publicKey.contentEquals(expected)) { "TON source public identity mismatch" }
            return makeSlot(
                role.TON_ROOT,
                "",
                listOf(
                    bytes(field.PUBLIC_KEY, publicKey, allocated),
                    bytes(field.PRIVATE_KEY, source[TonSecrets.PrivateKey].also(borrowed::add), allocated),
                    bytes(field.SEED, source[TonSecrets.Seed].also(borrowed::add), allocated),
                    PortableWalletSemanticMaterial.Field(
                        field.ACCOUNT_ID_OR_ADDRESS,
                        canonicalTonAddress(publicKey).also(allocated::add),
                    ),
                    oneByte(field.SOURCE_RECIPE, 0, allocated),
                    oneByte(field.TON_CONTRACT_VERSION, 2, allocated),
                    oneByte(field.TON_ADDRESS_ENCODING, 1, allocated),
                ),
            )
        } finally {
            borrowed.forEach { it.fill(0) }
            parsed.fill(0)
        }
    }

    private fun legacySlot(
        source: PortableWalletMaterialDraft.LegacySubstrateSource,
        identity: PortableWalletMaterialDraft.WalletIdentity,
        allocated: MutableList<ByteArray>,
    ): PortableWalletSemanticMaterial.Slot {
        return makeSlot(
            role.LEGACY_SUBSTRATE,
            "",
            listOfNotNull(
                bytes(field.PUBLIC_KEY, source.publicKey, allocated),
                bytes(field.PRIVATE_KEY, source.privateKey, allocated),
                source.nonce?.let { bytes(field.NONCE, it, allocated) },
                source.entropy?.let { bytes(field.ENTROPY, it, allocated) },
                source.seed?.let { bytes(field.SEED, it, allocated) },
                source.derivationPath?.let { text(field.DERIVATION_PATH, it, allocated) },
                text(field.ACCOUNT_ID_OR_ADDRESS, source.accountAddress, allocated),
                oneByte(field.CRYPTO_TYPE, cryptoType(identity.substrateCryptoType), allocated),
                oneByte(field.SOURCE_RECIPE, sourceType(source.sourceType), allocated),
                source.mnemonic?.let { text(field.MNEMONIC, it, allocated) },
            ),
        )
    }

    private fun chainSlot(
        chain: PortableWalletMaterialDraft.ChainIdentity,
        raw: ByteArray,
        allocated: MutableList<ByteArray>,
    ): PortableWalletSemanticMaterial.Slot {
        val parsed = raw.copyOf()
        val borrowed = ArrayList<ByteArray>()
        try {
            val source = ChainAccountSecrets.read(parsed)
            val pair = source[ChainAccountSecrets.Keypair]
            val publicKey = pair[KeyPairSchema.PublicKey].also(borrowed::add)
            val expected = publicIdentity(chain.publicKey, allocated)
            require(publicKey.contentEquals(expected)) { "Chain source public identity mismatch" }
            return makeSlot(
                role.CHAIN_ACCOUNT,
                chain.chainId,
                listOfNotNull(
                    bytes(field.PUBLIC_KEY, publicKey, allocated),
                    bytes(field.PRIVATE_KEY, pair[KeyPairSchema.PrivateKey].also(borrowed::add), allocated),
                    pair[KeyPairSchema.Nonce]?.also(borrowed::add)?.let { bytes(field.NONCE, it, allocated) },
                    source[ChainAccountSecrets.Entropy]?.also(borrowed::add)?.let { bytes(field.ENTROPY, it, allocated) },
                    source[ChainAccountSecrets.Seed]?.also(borrowed::add)?.let { bytes(field.SEED, it, allocated) },
                    source[ChainAccountSecrets.DerivationPath]?.let { text(field.DERIVATION_PATH, it, allocated) },
                    bytes(field.ACCOUNT_ID_OR_ADDRESS, publicIdentity(chain.accountId, allocated), allocated),
                    oneByte(field.CRYPTO_TYPE, cryptoType(chain.cryptoType), allocated),
                    text(field.CHAIN_NAME, chain.name, allocated),
                    oneByte(field.INITIALIZED_OR_FAVORITE, if (chain.initialized) 1 else 0, allocated),
                    oneByte(field.SOURCE_RECIPE, 0, allocated),
                ),
            )
        } finally {
            borrowed.forEach { it.fill(0) }
            parsed.fill(0)
        }
    }

    private fun auxiliarySlot(
        ordinal: Int,
        sourceRole: Int,
        binding: Int,
        format: Int,
        raw: ByteArray,
        allocated: MutableList<ByteArray>,
        chainId: String? = null,
        accountId: String? = null,
    ): PortableWalletSemanticMaterial.Slot {
        return makeSlot(
            role.AUXILIARY_SOURCE,
            ordinal.toString(16).padStart(4, '0'),
            listOfNotNull(
                oneByte(field.SOURCE_RECIPE, 0, allocated),
                oneByte(field.SOURCE_PLATFORM, 1, allocated),
                oneByte(field.SOURCE_SLOT_ROLE, sourceRole, allocated),
                oneByte(field.BINDING_KIND, binding, allocated),
                chainId?.let { text(field.BINDING_CHAIN_ID, it, allocated) },
                oneByte(field.SOURCE_FORMAT, format, allocated),
                bytes(field.SOURCE_BYTES, raw, allocated),
                accountId?.let { bytes(field.BINDING_ACCOUNT_ID, publicIdentity(it, allocated), allocated) },
            ),
        )
    }

    private fun makeSlot(
        roleCode: Int,
        key: String,
        vararg fields: PortableWalletSemanticMaterial.Field,
    ) = makeSlot(roleCode, key, fields.toList())

    private fun makeSlot(
        roleCode: Int,
        key: String,
        fields: List<PortableWalletSemanticMaterial.Field>,
    ) = PortableWalletSemanticMaterial.Slot(roleCode, key, fields.sortedBy { it.id })

    private fun bytes(
        id: Int,
        source: ByteArray,
        allocated: MutableList<ByteArray>,
    ) = PortableWalletSemanticMaterial.Field(id, source.copyOf().also(allocated::add))

    private fun text(
        id: Int,
        value: String,
        allocated: MutableList<ByteArray>,
    ) = PortableWalletSemanticMaterial.Field(id, value.toByteArray(Charsets.UTF_8).also(allocated::add))

    private fun oneByte(
        id: Int,
        value: Int,
        allocated: MutableList<ByteArray>,
    ) = PortableWalletSemanticMaterial.Field(id, byteArrayOf(value.toByte()).also(allocated::add))

    private fun publicIdentity(value: String?, allocated: MutableList<ByteArray>): ByteArray {
        require(value != null) { "Portable public identity is missing" }
        val decoded = Base64.getDecoder().decode(value).also(allocated::add)
        require(
            decoded.isNotEmpty() && decoded.size <= 128 &&
                Base64.getEncoder().encodeToString(decoded) == value,
        ) {
            "Portable public identity is not canonical"
        }
        return decoded
    }

    private fun portableId(durableId: Long): ByteArray {
        require(durableId > 0) { "Durable Android wallet ID is invalid" }
        val id = ByteBuffer.allocate(Long.SIZE_BYTES).putLong(durableId).array()
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(idDomain)
            digest.update(id)
            val hashed = digest.digest()
            return hashed.copyOfRange(0, 16).also { hashed.fill(0) }
        } finally {
            id.fill(0)
        }
    }

    private fun canonicalTonAddress(publicKey: ByteArray): ByteArray {
        require(publicKey.size == 32) { "TON public key length is invalid" }
        val raw =
            try {
                publicKey.tonAccountId(isTestnet = false)
            } catch (_: Exception) {
                throw IllegalArgumentException("TON V4R2 address cannot be derived")
            }
        require(raw.length == 66 && raw.startsWith("0:")) { "TON V4R2 address is not workchain zero" }
        val result = ByteArray(33)
        for (index in 0 until 32) {
            val high = raw[2 + index * 2].digitToIntOrNull(16)
            val low = raw[3 + index * 2].digitToIntOrNull(16)
            require(high != null && low != null) { "TON V4R2 address is not canonical hexadecimal" }
            result[index + 1] = (high shl 4 or low).toByte()
        }
        return result
    }

    private fun cryptoType(name: String?): Int = when (name?.let(CryptoType::valueOf)) {
            CryptoType.SR25519 -> 1
            CryptoType.ED25519 -> 2
            CryptoType.ECDSA -> 3
            null -> throw IllegalArgumentException("Portable crypto type is missing")
    }

    private fun sourceType(source: PortableWalletMaterialDraft.SourceType): Int = when (source) {
            PortableWalletMaterialDraft.SourceType.CREATE -> 1
            PortableWalletMaterialDraft.SourceType.SEED -> 2
            PortableWalletMaterialDraft.SourceType.JSON -> 3
            PortableWalletMaterialDraft.SourceType.MNEMONIC -> 4
            PortableWalletMaterialDraft.SourceType.UNSPECIFIED -> 5
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
}
