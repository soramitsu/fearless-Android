package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.TonSecrets
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretValidator
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.extensions.toHexString

/**
 * Checks exact Android V3 export sources against their semantic root slots. This is a narrow,
 * read-only component: V1, V2, iOS sources, watch wallets and installed-key export are not proved.
 * The caller owns and must erase [encoded] after use. No backup-completion path calls this object.
 */
@Suppress("MagicNumber") // Versioned semantic role and field values.
internal object PortableWalletV3OriginalSourceProof {
    private val codec = PortableWalletSemanticMaterial
    private val role = PortableWalletSemanticMaterial.Role
    private val field = PortableWalletSemanticMaterial.FieldId

    internal class Counts(val wallets: Int, val exactAndroidRoots: Int) {
        override fun toString(): String = "PortableWalletV3OriginalSourceProof.Counts(redacted)"
    }

    fun verify(encoded: ByteArray): Counts {
        return verifyRoots(encoded, allowOtherSlots = false)
    }

    /** Proves every V3 root in a mixed Android cohort; the caller must account for other slots. */
    internal fun verifyEmbedded(encoded: ByteArray): Counts = verifyRoots(encoded, allowOtherSlots = true)

    private fun verifyRoots(encoded: ByteArray, allowOtherSlots: Boolean): Counts {
        val signing = PortableWalletRootSigningProof.verify(encoded)
        val decoded = codec.decode(encoded)
        try {
            val proved = decoded.wallets.sumOf { verifyWallet(it, allowOtherSlots) }
            require(proved > 0 && proved == signing.substrateRoots + signing.evmRoots + signing.nativeTonRoots) {
                "V3 original source proof is incomplete"
            }
            return Counts(decoded.wallets.size, proved)
        } finally {
            decoded.clearSecrets()
        }
    }

    private fun verifyWallet(wallet: PortableWalletSemanticMaterial.Wallet, allowOtherSlots: Boolean): Int {
        val sources = wallet.slots.filter { slot ->
            if (slot.role != role.AUXILIARY_SOURCE) {
                false
            } else {
                val platform = slot.number(field.SOURCE_PLATFORM)
                val sourceRole = slot.number(field.SOURCE_SLOT_ROLE)
                !allowOtherSlots || platform == 1 && sourceRole in 11..13
            }
        }
        val roots = wallet.slots.filter { it.role in role.SUBSTRATE_ROOT..role.TON_ROOT }
        require(allowOtherSlots || wallet.slots.size == sources.size + roots.size) {
            "V1, V2, watch, favorite or other semantic slots are not V3 root proof"
        }
        roots.forEach { root ->
            val expectedRole = root.role + 10
            val originals = sources.filter {
                it.number(field.SOURCE_PLATFORM) == 1 &&
                    it.number(field.SOURCE_SLOT_ROLE) == expectedRole
            }
            require(originals.size == 1) { "V3 root lacks one exact Android original source" }
            verifyOriginal(root, originals.single())
        }
        // No original source may be silently orphaned or represented twice.
        require(sources.size == roots.size) { "V3 original source inventory is incomplete or unsupported" }
        return roots.size
    }

    private fun verifyOriginal(
        root: PortableWalletSemanticMaterial.Slot,
        original: PortableWalletSemanticMaterial.Slot
    ) {
        require(
            root.number(field.SOURCE_RECIPE) == 0 &&
                original.number(field.SOURCE_PLATFORM) == 1 &&
                original.number(field.SOURCE_SLOT_ROLE) == root.role + 10 &&
                original.number(field.BINDING_KIND) == root.role + 1 &&
                original.number(field.SOURCE_FORMAT) == 4 &&
                original.number(field.SOURCE_RECIPE) == 0
        ) { "V3 original source shape is unsupported" }
        val raw = original.value(field.SOURCE_BYTES)
        val exactHex = raw.toHexString(withPrefix = true)
        val publicKey = root.value(field.PUBLIC_KEY)
        val canonical = when (root.role) {
            role.SUBSTRATE_ROOT -> WalletRootSecretValidator.validateSubstrateAndSanitize(
                exactHex, publicKey, cryptoType(root), root.value(field.ACCOUNT_ID_OR_ADDRESS)
            )
            role.EVM_ROOT -> WalletRootSecretValidator.validateEthereumAndSanitize(
                exactHex, publicKey, root.value(field.ACCOUNT_ID_OR_ADDRESS)
            )
            role.TON_ROOT -> WalletRootSecretValidator.validateTonAndSanitize(exactHex, publicKey)
            else -> error("Unsupported V3 root role")
        }
        require(exactHex == canonical) { "V3 original source would lose export provenance" }
        when (root.role) {
            role.SUBSTRATE_ROOT -> verifySubstrateOriginal(root, raw)
            role.EVM_ROOT -> verifyEvmOriginal(root, raw)
            role.TON_ROOT -> verifyTonOriginal(root, raw)
        }
    }

    private fun verifySubstrateOriginal(root: PortableWalletSemanticMaterial.Slot, raw: ByteArray) {
        val source = SubstrateSecrets.read(raw)
        val pair = source[SubstrateSecrets.SubstrateKeypair]
        val public = pair[KeyPairSchema.PublicKey]
        val private = pair[KeyPairSchema.PrivateKey]
        val nonce = pair[KeyPairSchema.Nonce]
        val entropy = source[SubstrateSecrets.Entropy]
        val seed = source[SubstrateSecrets.Seed]
        try {
            requirePair(root, public, private, nonce)
            requireRecovery(root, entropy, seed, source[SubstrateSecrets.SubstrateDerivationPath])
        } finally {
            listOfNotNull(public, private, nonce, entropy, seed).forEach { it.fill(0) }
        }
    }

    private fun verifyEvmOriginal(root: PortableWalletSemanticMaterial.Slot, raw: ByteArray) {
        val source = EthereumSecrets.read(raw)
        val pair = source[EthereumSecrets.EthereumKeypair]
        val public = pair[KeyPairSchema.PublicKey]
        val private = pair[KeyPairSchema.PrivateKey]
        val nonce = pair[KeyPairSchema.Nonce]
        val entropy = source[EthereumSecrets.Entropy]
        val seed = source[EthereumSecrets.Seed]
        try {
            requirePair(root, public, private, nonce)
            requireRecovery(root, entropy, seed, source[EthereumSecrets.EthereumDerivationPath])
        } finally {
            listOfNotNull(public, private, nonce, entropy, seed).forEach { it.fill(0) }
        }
    }

    private fun verifyTonOriginal(root: PortableWalletSemanticMaterial.Slot, raw: ByteArray) {
        require(root.optional(field.ENTROPY) == null && root.optional(field.MNEMONIC) == null) {
            "Android V3 TON root has unsupported recovery fields"
        }
        val source = TonSecrets.read(raw)
        val sourcePublic = source[TonSecrets.PublicKey]
        val private = source[TonSecrets.PrivateKey]
        val seed = source[TonSecrets.Seed]
        try {
            require(
                sourcePublic.contentEquals(root.value(field.PUBLIC_KEY)) &&
                    private.contentEquals(root.value(field.PRIVATE_KEY)) &&
                    seed.contentEquals(root.value(field.SEED))
            ) {
                "TON semantic source differs from its V3 original"
            }
        } finally {
            listOf(sourcePublic, private, seed).forEach { it.fill(0) }
        }
    }

    private fun requirePair(
        root: PortableWalletSemanticMaterial.Slot,
        public: ByteArray,
        private: ByteArray,
        nonce: ByteArray?
    ) {
        require(
            public.contentEquals(root.value(field.PUBLIC_KEY)) &&
            private.contentEquals(root.value(field.PRIVATE_KEY)) &&
            nonce.contentEqualsOptional(root.optional(field.NONCE))
        ) {
            "Semantic key differs from its V3 original"
        }
    }

    private fun requireRecovery(
        root: PortableWalletSemanticMaterial.Slot,
        entropy: ByteArray?,
        seed: ByteArray?,
        path: String?
    ) {
        require(
            entropy.contentEqualsOptional(root.optional(field.ENTROPY)) &&
            seed.contentEqualsOptional(root.optional(field.SEED)) &&
            path == root.optional(field.DERIVATION_PATH)?.toString(Charsets.UTF_8)
        ) {
            "Semantic recovery material differs from its V3 original"
        }
    }

    private fun cryptoType(root: PortableWalletSemanticMaterial.Slot): CryptoType = when (
        root.number(field.CRYPTO_TYPE)
    ) {
        1 -> CryptoType.SR25519
        2 -> CryptoType.ED25519
        3 -> CryptoType.ECDSA
        else -> error("Unsupported V3 Substrate crypto type")
    }

    private fun PortableWalletSemanticMaterial.Slot.value(id: Int): ByteArray = fields.single { it.id == id }.value

    private fun PortableWalletSemanticMaterial.Slot.optional(id: Int): ByteArray? =
        fields.firstOrNull { it.id == id }?.value

    private fun PortableWalletSemanticMaterial.Slot.number(id: Int): Int = value(id)[0].toInt() and 0xff

    private fun ByteArray?.contentEqualsOptional(other: ByteArray?): Boolean =
        if (this == null || other == null) this == null && other == null else contentEquals(other)
}
