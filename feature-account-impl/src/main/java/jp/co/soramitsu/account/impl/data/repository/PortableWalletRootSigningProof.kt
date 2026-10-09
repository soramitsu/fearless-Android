package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.common.data.secrets.v1.Keypair
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretValidator
import jp.co.soramitsu.common.utils.SolanaKeyDerivation
import jp.co.soramitsu.common.utils.SolanaSigner
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.SignatureWrapper
import jp.co.soramitsu.fearless_utils.encrypt.Signer
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ECDSAUtils
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.exceptions.Bip39Exception
import jp.co.soramitsu.fearless_utils.hash.Hasher.blake2b256
import jp.co.soramitsu.fearless_utils.hash.Hasher.keccak256
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.mnemonic.Mnemonic
import org.web3j.crypto.Sign
import java.math.BigInteger

/**
 * Read-only ownership proof for signed root slots in canonical FPWMSM01 plaintext.
 * No backup, restore, installer, signing, or export path calls this object. A positive result
 * proves only the counted roots; legacy sources, chain accounts, watch identities, auxiliary
 * records, recovery metadata, and the completeness of a wallet remain unproven.
 * The caller owns and must erase [encoded] after use.
 */
@Suppress("MagicNumber") // Fixed protocol tags and cryptographic key lengths.
internal object PortableWalletRootSigningProof {
    private val codec = PortableWalletSemanticMaterial
    private val role = PortableWalletSemanticMaterial.Role
    private val field = PortableWalletSemanticMaterial.FieldId
    private val proofDomain = "FPBK-IMPORTED-ROOT-PROOF-v1".toByteArray(Charsets.US_ASCII)

    internal class Counts(
        val wallets: Int,
        val substrateRoots: Int,
        val evmRoots: Int,
        val nativeTonRoots: Int,
        val unprovenSlots: Int,
        val unprovenRecoveryFields: Int,
    ) {
        override fun toString(): String = "PortableWalletRootSigningProof.Counts(redacted)"
    }

    fun verify(encoded: ByteArray): Counts {
        val decoded = codec.decode(encoded)
        try {
            val verified = decoded.wallets.map(::verifyWallet)
            return Counts(
                decoded.wallets.size,
                verified.sumOf(WalletCounts::substrate),
                verified.sumOf(WalletCounts::evm),
                verified.sumOf(WalletCounts::ton),
                verified.sumOf(WalletCounts::unproven),
                verified.sumOf(WalletCounts::unprovenRecovery),
            )
        } finally {
            decoded.clearSecrets()
        }
    }

    @Suppress("NestedBlockDepth") // One explicit branch per supported root slot.
    private fun verifyWallet(wallet: PortableWalletSemanticMaterial.Wallet): WalletCounts {
        var substrate = 0
        var evm = 0
        var ton = 0
        var unproven = 0
        var unprovenRecovery = 0
        wallet.slots.forEach { slot ->
            when (slot.role) {
                role.SUBSTRATE_ROOT -> {
                    verifySubstrate(slot, wallet.portableId)
                    substrate++
                    unprovenRecovery += slot.countUnprovenRecoveryFields(
                        includeNonce = slot.number(field.CRYPTO_TYPE) != 1,
                    )
                    // Released iOS ED25519 signs from Data.miniSeed (the first 32 bytes).
                    // Its remaining stored bytes have no signing proof and must still be
                    // preserved and separately qualified before any installer can run.
                    if (slot.number(field.CRYPTO_TYPE) == 2 &&
                        slot.value(field.PRIVATE_KEY).size == 64
                    ) {
                        unprovenRecovery++
                    }
                }
                role.EVM_ROOT -> {
                    verifyEvm(slot, wallet.portableId)
                    evm++
                    unprovenRecovery += slot.countUnprovenRecoveryFields(includeNonce = true)
                }
                role.TON_ROOT -> {
                    verifyTon(slot, wallet.portableId)
                    ton++
                    if (slot.optional(field.ENTROPY) != null) unprovenRecovery++
                }
                else -> unproven++
            }
        }
        return WalletCounts(substrate, evm, ton, unproven, unprovenRecovery)
    }

    private fun verifySubstrate(slot: PortableWalletSemanticMaterial.Slot, portableId: ByteArray) {
        val publicKey = slot.value(field.PUBLIC_KEY)
        val cryptoType = when (slot.number(field.CRYPTO_TYPE)) {
            1 -> CryptoType.SR25519
            2 -> CryptoType.ED25519
            3 -> CryptoType.ECDSA
            else -> error("The semantic codec accepted an invalid Substrate type")
        }
        val encryption = when (cryptoType) {
            CryptoType.SR25519 -> EncryptionType.SR25519
            CryptoType.ED25519 -> EncryptionType.ED25519
            CryptoType.ECDSA -> EncryptionType.ECDSA
        }
        val (privateKey, nonce) = unpackSubstrateSecret(slot, cryptoType)
        try {
            val keypair = Keypair(publicKey, privateKey, nonce)
            WalletRootSecretValidator.validateSubstrateKeypair(
                keypair = keypair,
                expectedPublicKey = publicKey,
                expectedCryptoType = cryptoType,
                expectedAccountId = slot.value(field.ACCOUNT_ID_OR_ADDRESS),
            )
            val message = proofMessage(portableId, slot.role, publicKey)
            try {
                val signature = Signer.sign(MultiChainEncryption.Substrate(encryption), message, keypair)
                require(
                    when (cryptoType) {
                        CryptoType.SR25519 -> Signer.verifySr25519(message, signature.signature, publicKey)
                        CryptoType.ED25519 -> Signer.verifyEd25519(message, signature.signature, publicKey)
                        CryptoType.ECDSA -> verifyEcdsa(message.blake2b256(), signature, publicKey)
                    },
                ) { "Substrate root cannot sign for its public identity" }
            } finally {
                message.fill(0)
            }
        } finally {
            privateKey.fill(0)
            nonce?.fill(0)
        }
    }

    private fun unpackSubstrateSecret(
        slot: PortableWalletSemanticMaterial.Slot,
        cryptoType: CryptoType,
    ): Pair<ByteArray, ByteArray?> {
        val secret = slot.value(field.PRIVATE_KEY)
        val storedNonce = slot.optional(field.NONCE)
        // Android V3 stores the SR scalar and nonce in separate fields. Released iOS
        // stores the same native SR secret as scalar||nonce in field 2. iOS ED25519
        // signs with the first 32 bytes of its stored seed, including a 64-byte seed.
        // A 64-byte value is never accepted for ECDSA or with an ambiguous nonce.
        when (cryptoType) {
            CryptoType.SR25519 -> require(
                secret.size == 64 && storedNonce == null ||
                    secret.size == 32 && storedNonce?.size == 32
            ) { "SR25519 secret has an invalid scalar or nonce" }
            CryptoType.ED25519 -> require(
                secret.size in setOf(32, 64) && storedNonce == null
            ) { "ED25519 secret has an invalid shape" }
            CryptoType.ECDSA -> require(
                secret.size == 32 && storedNonce == null
            ) { "ECDSA secret has an invalid shape" }
        }
        val nonce = if (cryptoType == CryptoType.SR25519) {
            if (secret.size == 64) {
                secret.copyOfRange(32, 64)
            } else {
                requireNotNull(storedNonce).copyOf()
            }
        } else {
            null
        }
        return secret.copyOfRange(0, 32) to nonce
    }

    private fun verifyEvm(slot: PortableWalletSemanticMaterial.Slot, portableId: ByteArray) {
        val publicKey = slot.value(field.PUBLIC_KEY)
        val keypair = Keypair(publicKey, slot.value(field.PRIVATE_KEY))
        WalletRootSecretValidator.validateEthereumKeypair(
            keypair = keypair,
            expectedPublicKey = publicKey,
            expectedAddress = slot.value(field.ACCOUNT_ID_OR_ADDRESS),
        )
        val message = proofMessage(portableId, slot.role, publicKey)
        try {
            val signature = Signer.sign(MultiChainEncryption.Ethereum, message, keypair)
            require(verifyEcdsa(message.keccak256(), signature, publicKey)) {
                "EVM root cannot sign for its public identity"
            }
        } finally {
            message.fill(0)
        }
    }

    private fun verifyTon(slot: PortableWalletSemanticMaterial.Slot, portableId: ByteArray) {
        val publicKey = slot.value(field.PUBLIC_KEY)
        val secret = slot.value(field.PRIVATE_KEY)
        require(publicKey.size == 32 && secret.size in setOf(32, 64)) {
            "TON root key shape is invalid"
        }
        if (secret.size == 64) {
            require(secret.copyOfRange(32, 64).contentEquals(publicKey)) {
                "iOS TON private key has a different public suffix"
            }
        }
        val seed = secret.copyOfRange(0, 32)
        try {
            require(SolanaKeyDerivation.publicKeyFromPrivateKey(seed).contentEquals(publicKey)) {
                "TON private key has a different public identity"
            }
            verifyTonAddress(slot, publicKey)
            verifyTonPhrase(slot, seed, requireAndroidPhrase = secret.size == 32)
            val message = proofMessage(portableId, slot.role, publicKey)
            try {
                val signature = SolanaSigner.signMessage(seed, message)
                require(SolanaSigner.verifyMessage(publicKey, message, signature)) {
                    "TON root cannot sign for its public identity"
                }
            } finally {
                message.fill(0)
            }
        } finally {
            seed.fill(0)
        }
    }

    private fun verifyTonAddress(slot: PortableWalletSemanticMaterial.Slot, publicKey: ByteArray) {
        PortableWalletTonAddressProof.verifyV4R2(
            publicKey,
            slot.value(field.ACCOUNT_ID_OR_ADDRESS),
            slot.number(field.TON_ADDRESS_ENCODING),
            slot.number(field.TON_CONTRACT_VERSION),
        )
    }

    private fun verifyTonPhrase(
        slot: PortableWalletSemanticMaterial.Slot,
        seed: ByteArray,
        requireAndroidPhrase: Boolean,
    ) {
        val androidPhrase = slot.optional(field.SEED)
        val iosPhrase = slot.optional(field.MNEMONIC)
        require(!requireAndroidPhrase || androidPhrase != null) {
            "Android TON recovery phrase is missing"
        }
        require(!requireAndroidPhrase || iosPhrase == null) {
            "Android TON phrase fields are ambiguous"
        }
        require(requireAndroidPhrase || iosPhrase != null) {
            "iOS TON recovery phrase is missing"
        }
        listOfNotNull(androidPhrase, iosPhrase).forEach { encoded ->
            val phrase = encoded.decodeToString(throwOnInvalidSequence = true)
            require(
                phrase.isNotEmpty() && phrase == phrase.trim() &&
                phrase.split(' ').joinToString(" ") == phrase
            ) {
                "TON phrase is not canonical words"
            }
            val words = phrase.split(' ')
            val validBip39 = try {
                MnemonicCreator.fromWords(phrase).words == phrase
            } catch (_: Bip39Exception) {
                false
            }
            require(
                words.size in setOf(12, 15, 18, 21, 24) &&
                (validBip39 || Mnemonic.isValid(words))
            ) {
                "TON phrase is invalid"
            }
            val recovered = Mnemonic.toSeed(words)
            try {
                require(PrivateKeyEd25519(recovered).key.toByteArray().contentEquals(seed)) {
                    "TON phrase derives a different private key"
                }
            } finally {
                recovered.fill(0)
            }
        }
    }

    private fun verifyEcdsa(
        hash: ByteArray,
        signature: SignatureWrapper,
        publicKey: ByteArray
    ): Boolean {
        require(signature is SignatureWrapper.Ecdsa) { "ECDSA signature type is invalid" }
        val recovered = Sign.signedMessageHashToKey(
            hash,
            Sign.SignatureData(signature.v, signature.r, signature.s),
        )
        return recovered == BigInteger(1, ECDSAUtils.decompressed(publicKey))
    }

    private fun proofMessage(
        portableId: ByteArray,
        slotRole: Int,
        publicKey: ByteArray
    ): ByteArray = proofDomain + portableId + byteArrayOf(slotRole.toByte()) + publicKey

    private fun PortableWalletSemanticMaterial.Slot.value(id: Int): ByteArray = fields.single { it.id == id }.value

    private fun PortableWalletSemanticMaterial.Slot.optional(id: Int): ByteArray? =
        fields.firstOrNull { it.id == id }?.value

    private fun PortableWalletSemanticMaterial.Slot.number(id: Int): Int = value(id)[0].toInt() and 0xff

    private fun PortableWalletSemanticMaterial.Slot.countUnprovenRecoveryFields(includeNonce: Boolean): Int =
        fields.count {
            it.id in setOf(field.ENTROPY, field.SEED, field.DERIVATION_PATH) ||
                includeNonce && it.id == field.NONCE
        }

    private data class WalletCounts(
        val substrate: Int,
        val evm: Int,
        val ton: Int,
        val unproven: Int,
        val unprovenRecovery: Int,
    )
}
