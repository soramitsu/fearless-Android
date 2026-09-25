package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.common.data.secrets.v1.Keypair
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecretValidator
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.SignatureWrapper
import jp.co.soramitsu.fearless_utils.encrypt.Signer
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ECDSAUtils
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.hash.Hasher.blake2b256
import jp.co.soramitsu.fearless_utils.hash.Hasher.keccak256
import org.web3j.crypto.Sign
import java.math.BigInteger

/**
 * Read-only proof for original V2 chain-account keys in canonical FPWMSM01 material.
 * A reviewed caller must supply the exact genesis IDs and identity kinds it accepts. No
 * production receiving, installer, export or backup-completion path invokes this object.
 * The proof does not qualify V1, V3, iOS chain-source shapes, or the complete wallet cohort.
 * The caller owns and must erase [encoded] after use.
 */
@Suppress("MagicNumber") // Versioned role and field tags, fixed genesis and account-ID lengths.
internal object PortableWalletChainSigningProof {
    private val codec = PortableWalletSemanticMaterial
    private val role = PortableWalletSemanticMaterial.Role
    private val field = PortableWalletSemanticMaterial.FieldId
    private val domain = "FPBK-IMPORTED-V2-CHAIN-PROOF-v1".toByteArray(Charsets.US_ASCII)
    private val genesisPattern = Regex("0x[0-9a-f]{64}")

    internal enum class IdentityKind { SUBSTRATE, ETHEREUM }

    internal class ApprovedGenesis(val id: String, val identityKind: IdentityKind) {
        init {
            require(genesisPattern.matches(id)) { "Approved genesis ID is not canonical" }
        }

        override fun toString(): String = "PortableWalletChainSigningProof.ApprovedGenesis(redacted)"
    }

    internal class Counts(val wallets: Int, val chainAccounts: Int, val exactOriginalSources: Int) {
        override fun toString(): String = "PortableWalletChainSigningProof.Counts(redacted)"
    }

    /** Every chain slot must have policy, a single exact V2 source and a live signing proof. */
    fun verify(encoded: ByteArray, approvedGenesis: List<ApprovedGenesis>): Counts {
        require(
            approvedGenesis.size in 1..128 &&
            approvedGenesis.map(ApprovedGenesis::id).toSet().size == approvedGenesis.size
        ) { "Chain proof policy is empty, duplicated or oversized" }
        val byGenesis = approvedGenesis.associateBy(ApprovedGenesis::id)
        val decoded = codec.decode(encoded)
        try {
            val count = decoded.wallets.sumOf { verifyWalletChains(it, byGenesis) }
            require(count > 0) { "Material has no V2 chain account to prove" }
            return Counts(decoded.wallets.size, count, count)
        } finally {
            decoded.clearSecrets()
        }
    }

    private fun verifyWalletChains(
        wallet: PortableWalletSemanticMaterial.Wallet,
        byGenesis: Map<String, ApprovedGenesis>,
    ): Int {
        val chains = wallet.slots.filter { it.role == role.CHAIN_ACCOUNT }
        chains.forEach { chain ->
            val approved = requireNotNull(byGenesis[chain.key]) {
                "Chain account has no approved genesis ID"
            }
            verifyChain(wallet, chain, approved)
        }
        return chains.size
    }

    private fun verifyChain(
        wallet: PortableWalletSemanticMaterial.Wallet,
        chain: PortableWalletSemanticMaterial.Slot,
        approved: ApprovedGenesis,
    ) {
        val accountId = chain.value(field.ACCOUNT_ID_OR_ADDRESS)
        val publicKey = chain.value(field.PUBLIC_KEY)
        val cryptoType = when (chain.number(field.CRYPTO_TYPE)) {
            1 -> CryptoType.SR25519
            2 -> CryptoType.ED25519
            3 -> CryptoType.ECDSA
            else -> error("Semantic codec accepted an invalid V2 crypto type")
        }
        require(
            when (approved.identityKind) {
                IdentityKind.SUBSTRATE -> accountId.size == 32
                IdentityKind.ETHEREUM -> accountId.size == 20 && cryptoType == CryptoType.ECDSA
            },
        ) { "Chain account has the wrong identity kind for its genesis" }
        require(chain.number(field.SOURCE_RECIPE) == 0) { "V2 source recipe is unsupported" }

        val originals = wallet.slots.filter { source ->
            source.role == role.AUXILIARY_SOURCE && source.number(field.BINDING_KIND) == 5 &&
                source.value(field.BINDING_CHAIN_ID).contentEquals(chain.key.toByteArray(Charsets.UTF_8)) &&
                source.value(field.BINDING_ACCOUNT_ID).contentEquals(accountId)
        }
        require(originals.size == 1) { "V2 chain account lacks one exact original source" }
        val original = originals.single()
        require(
            original.number(field.SOURCE_PLATFORM) == 1 &&
                original.number(field.SOURCE_SLOT_ROLE) == 14 &&
                original.number(field.SOURCE_FORMAT) == 3 &&
                original.number(field.SOURCE_RECIPE) == 0
        ) { "V2 original source shape is unsupported" }
        verifyOriginalAndSigning(wallet.portableId, chain, original, cryptoType, approved.identityKind)
    }

    private fun verifyOriginalAndSigning(
        portableId: ByteArray,
        chain: PortableWalletSemanticMaterial.Slot,
        original: PortableWalletSemanticMaterial.Slot,
        cryptoType: CryptoType,
        identityKind: IdentityKind,
    ) {
        val accountId = chain.value(field.ACCOUNT_ID_OR_ADDRESS)
        val publicKey = chain.value(field.PUBLIC_KEY)
        val originalBytes = original.value(field.SOURCE_BYTES)
        // The production V2 validator proves private-key ownership and any recorded
        // entropy/seed/path. Requiring byte-for-byte canonical output preserves the exact
        // export source instead of silently normalizing away recovery material.
        val originalHex = originalBytes.toHexString(withPrefix = true)
        val canonical = ChainAccountSecretValidator.validateAndSanitize(
            encoded = originalHex,
            expectedAccountId = accountId,
            expectedPublicKey = publicKey,
            expectedCryptoType = cryptoType,
        )
        require(originalHex == canonical) { "V2 original source would lose export provenance" }

        val source = ChainAccountSecrets.read(originalBytes)
        val pair = source[ChainAccountSecrets.Keypair]
        val sourcePublic = pair[KeyPairSchema.PublicKey]
        val sourcePrivate = pair[KeyPairSchema.PrivateKey]
        val sourceNonce = pair[KeyPairSchema.Nonce]
        val sourceEntropy = source[ChainAccountSecrets.Entropy]
        val sourceSeed = source[ChainAccountSecrets.Seed]
        try {
            require(
                sourcePublic.contentEquals(publicKey) &&
                    sourcePrivate.contentEquals(chain.value(field.PRIVATE_KEY)) &&
                    sourceNonce.contentEqualsOptional(chain.optional(field.NONCE)) &&
                    sourceEntropy.contentEqualsOptional(chain.optional(field.ENTROPY)) &&
                    sourceSeed.contentEqualsOptional(chain.optional(field.SEED)) &&
                    source[ChainAccountSecrets.DerivationPath] ==
                    chain.optional(field.DERIVATION_PATH)?.toString(Charsets.UTF_8)
            ) { "V2 semantic key or recovery material differs from its original source" }
        } finally {
            sourcePublic.fill(0)
            sourcePrivate.fill(0)
            sourceNonce?.fill(0)
            sourceEntropy?.fill(0)
            sourceSeed?.fill(0)
        }
        verifySignature(portableId, chain, cryptoType, identityKind)
    }

    private fun verifySignature(
        portableId: ByteArray,
        chain: PortableWalletSemanticMaterial.Slot,
        cryptoType: CryptoType,
        identityKind: IdentityKind,
    ) {
        val publicKey = chain.value(field.PUBLIC_KEY)
        val message = domain + portableId + chain.key.toByteArray(Charsets.US_ASCII) +
            chain.value(field.ACCOUNT_ID_OR_ADDRESS) + publicKey
        try {
            val keypair = Keypair(publicKey, chain.value(field.PRIVATE_KEY), chain.optional(field.NONCE))
            val encryption = when (identityKind) {
                IdentityKind.ETHEREUM -> MultiChainEncryption.Ethereum
                IdentityKind.SUBSTRATE -> MultiChainEncryption.Substrate(
                    when (cryptoType) {
                        CryptoType.SR25519 -> EncryptionType.SR25519
                        CryptoType.ED25519 -> EncryptionType.ED25519
                        CryptoType.ECDSA -> EncryptionType.ECDSA
                    },
                )
            }
            val signature = Signer.sign(encryption, message, keypair)
            require(
                when (cryptoType) {
                    CryptoType.SR25519 -> Signer.verifySr25519(message, signature.signature, publicKey)
                    CryptoType.ED25519 -> Signer.verifyEd25519(message, signature.signature, publicKey)
                    CryptoType.ECDSA -> {
                        require(signature is SignatureWrapper.Ecdsa) { "V2 ECDSA signature type is invalid" }
                        val hash = when (identityKind) {
                            IdentityKind.SUBSTRATE -> message.blake2b256()
                            IdentityKind.ETHEREUM -> message.keccak256()
                        }
                        val recovered = Sign.signedMessageHashToKey(
                            hash, Sign.SignatureData(signature.v, signature.r, signature.s),
                        )
                        recovered == BigInteger(1, ECDSAUtils.decompressed(publicKey))
                    }
                },
            ) { "V2 original key cannot sign for its chain identity" }
        } finally {
            message.fill(0)
        }
    }

    private fun PortableWalletSemanticMaterial.Slot.value(id: Int): ByteArray = fields.single { it.id == id }.value

    private fun PortableWalletSemanticMaterial.Slot.optional(id: Int): ByteArray? =
        fields.firstOrNull { it.id == id }?.value

    private fun PortableWalletSemanticMaterial.Slot.number(id: Int): Int = value(id)[0].toInt() and 0xff

    private fun ByteArray?.contentEqualsOptional(other: ByteArray?): Boolean =
        if (this == null || other == null) this == null && other == null else contentEquals(other)
}
