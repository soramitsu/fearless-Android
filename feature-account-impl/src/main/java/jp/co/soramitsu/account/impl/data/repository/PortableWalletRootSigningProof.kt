package jp.co.soramitsu.account.impl.data.repository

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import jp.co.soramitsu.common.data.secrets.v1.Keypair
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretValidator
import jp.co.soramitsu.common.utils.SolanaKeyDerivation
import jp.co.soramitsu.common.utils.SolanaSigner
import jp.co.soramitsu.common.utils.tonAccountId
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
import java.io.StringReader
import java.math.BigInteger
import java.util.Base64

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
        val keypair = Keypair(publicKey, slot.value(field.PRIVATE_KEY), slot.optional(field.NONCE))
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
        require(slot.number(field.TON_CONTRACT_VERSION) == 2) { "TON contract is not V4R2" }
        val address = slot.value(field.ACCOUNT_ID_OR_ADDRESS)
        val raw = publicKey.tonAccountId(isTestnet = false)
        require(raw.startsWith("0:") && raw.length == 66) { "TON V4R2 address is invalid" }
        val expectedHash = raw.substring(2).hexToBytes()
        when (slot.number(field.TON_ADDRESS_ENCODING)) {
            1 -> require(
                address.size == 33 && address[0] == 0.toByte() &&
                address.copyOfRange(1, 33).contentEquals(expectedHash)
            ) {
                "TON V4R2 address differs from its public key"
            }
            2 -> verifyIosTonAddress(address, expectedHash)
            else -> error("The semantic codec accepted an invalid TON address encoding")
        }
        expectedHash.fill(0)
    }

    private fun verifyIosTonAddress(encoded: ByteArray, expectedHash: ByteArray) {
        val text = encoded.decodeToString(throwOnInvalidSequence = true)
        JsonReader(StringReader(text)).use { reader ->
            readIosTonAddress(reader, expectedHash)
        }
    }

    private fun readIosTonAddress(reader: JsonReader, expectedHash: ByteArray) {
        var workchain: Int? = null
        var hash: ByteArray? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "workchain" -> {
                    require(workchain == null && reader.peek() == JsonToken.NUMBER) {
                        "iOS TON workchain is duplicated or invalid"
                    }
                    workchain = reader.nextInt()
                }
                "hash" -> {
                    require(hash == null && reader.peek() == JsonToken.STRING) {
                        "iOS TON hash is duplicated or invalid"
                    }
                    val base64 = reader.nextString()
                    hash = Base64.getDecoder().decode(base64)
                    require(Base64.getEncoder().encodeToString(hash) == base64) {
                        "iOS TON hash is not canonical Base64"
                    }
                }
                else -> throw IllegalArgumentException("iOS TON address has an unknown field")
            }
        }
        reader.endObject()
        require(
            reader.peek() == JsonToken.END_DOCUMENT && workchain == 0 &&
                hash?.contentEquals(expectedHash) == true
        ) {
            "iOS TON V4R2 address differs from its public key"
        }
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

    private fun String.hexToBytes(): ByteArray {
        require(length == 64) { "TON address hash is invalid" }
        return ByteArray(32) { index ->
            val high = this[index * 2].digitToIntOrNull(16)
            val low = this[index * 2 + 1].digitToIntOrNull(16)
            require(high != null && low != null) { "TON address hash is invalid" }
            (high shl 4 or low).toByte()
        }
    }

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
