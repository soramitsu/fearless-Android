package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.common.data.secrets.v1.Keypair
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretValidator
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.model.SecuritySource
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId

/**
 * Read-only ownership and recovery proof for historical Android V1 Substrate source fields.
 * FPWMSM01 retains the typed original fields rather than an opaque V1 preference value; this
 * check cannot prove byte-identical preference serialization, other slot families, or installation.
 * The caller owns and must erase [encoded] after use. No backup-completion path invokes it.
 */
@Suppress("MagicNumber") // Versioned semantic role, field, recipe and crypto-type values.
internal object PortableWalletV1SourceProof {
    private val codec = PortableWalletSemanticMaterial
    private val role = PortableWalletSemanticMaterial.Role
    private val field = PortableWalletSemanticMaterial.FieldId

    internal class Counts(val wallets: Int, val provedLegacySources: Int, val otherSlots: Int) {
        override fun toString(): String = "PortableWalletV1SourceProof.Counts(redacted)"
    }

    fun verify(encoded: ByteArray): Counts {
        val decoded = codec.decode(encoded)
        try {
            val proved = decoded.wallets.sumOf { wallet ->
                val root = wallet.slots.singleOrNull { it.role == role.SUBSTRATE_ROOT }
                val sources = wallet.slots.filter { it.role == role.LEGACY_SUBSTRATE }
                sources.forEach { verifySource(it, root) }
                sources.size
            }
            require(proved > 0) { "Portable material has no V1 source to prove" }
            return Counts(
                decoded.wallets.size,
                proved,
                decoded.wallets.sumOf { it.slots.size } - proved
            )
        } finally {
            decoded.clearSecrets()
        }
    }

    private fun verifySource(slot: PortableWalletSemanticMaterial.Slot, root: PortableWalletSemanticMaterial.Slot?) {
        val publicKey = slot.value(field.PUBLIC_KEY)
        val address = slot.value(field.ACCOUNT_ID_OR_ADDRESS).decodeToString(throwOnInvalidSequence = true)
        val accountId = try {
            address.toAccountId()
        } catch (_: Exception) {
            throw IllegalArgumentException("V1 source address is invalid")
        }
        require(accountId.contentEquals(publicKey.substrateAccountId())) {
            "V1 source address differs from its signing identity"
        }
        if (root != null) {
            require(
                publicKey.contentEquals(root.value(field.PUBLIC_KEY)) &&
                    accountId.contentEquals(root.value(field.ACCOUNT_ID_OR_ADDRESS)) &&
                    slot.number(field.CRYPTO_TYPE) == root.number(field.CRYPTO_TYPE)
            ) {
                "V1 source differs from its wallet's V3 Substrate root"
            }
        }
        val entropy = slot.optional(field.ENTROPY)
        val mnemonic = slot.optional(field.MNEMONIC)?.decodeToString(throwOnInvalidSequence = true)
        require(mnemonic == null && entropy == null || mnemonic != null && entropy != null) {
            "V1 mnemonic entropy is incomplete"
        }
        if (mnemonic != null) {
            val derived = MnemonicCreator.fromWords(mnemonic).entropy
            try {
                require(derived.contentEquals(entropy)) { "V1 mnemonic entropy differs from its words" }
            } finally {
                derived.fill(0)
            }
        }
        val source = historicalSource(slot, mnemonic)
        val cryptoType = when (slot.number(field.CRYPTO_TYPE)) {
            1 -> CryptoType.SR25519
            2 -> CryptoType.ED25519
            3 -> CryptoType.ECDSA
            else -> error("Unsupported V1 Substrate crypto type")
        }
        WalletRootSecretValidator.validateLegacySubstrateSource(source, publicKey, cryptoType, accountId)
    }

    private fun historicalSource(slot: PortableWalletSemanticMaterial.Slot, mnemonic: String?): SecuritySource {
        val keypair = Keypair(
            slot.value(field.PUBLIC_KEY), slot.value(field.PRIVATE_KEY), slot.optional(field.NONCE)
        )
        val seed = slot.optional(field.SEED)
        val path = slot.optional(field.DERIVATION_PATH)?.decodeToString(throwOnInvalidSequence = true)
        return when (slot.number(field.SOURCE_RECIPE)) {
            1 -> SecuritySource.Specified.Create(seed, keypair, requireNotNull(mnemonic), path)
            2 -> SecuritySource.Specified.Seed(seed, keypair, path)
            3 -> SecuritySource.Specified.Json(seed, keypair)
            4 -> SecuritySource.Specified.Mnemonic(seed, keypair, requireNotNull(mnemonic), path)
            5 -> SecuritySource.Unspecified(keypair)
            else -> error("Unsupported V1 source recipe")
        }
    }

    private fun PortableWalletSemanticMaterial.Slot.value(id: Int): ByteArray = fields.single { it.id == id }.value

    private fun PortableWalletSemanticMaterial.Slot.optional(id: Int): ByteArray? =
        fields.firstOrNull { it.id == id }?.value

    private fun PortableWalletSemanticMaterial.Slot.number(id: Int): Int = value(id)[0].toInt() and 0xff
}
