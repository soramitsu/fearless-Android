package jp.co.soramitsu.common.data.secrets.v3

import java.math.BigInteger
import jp.co.soramitsu.common.data.secrets.WalletSecretScalePreflight
import jp.co.soramitsu.common.data.secrets.v1.Keypair
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.storage.encrypt.MAX_WALLET_SECRET_PLAINTEXT_CHARS
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageUnavailableException
import jp.co.soramitsu.common.utils.SolanaKeyDerivation
import jp.co.soramitsu.common.utils.deriveSeed32
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.model.SecuritySource
import jp.co.soramitsu.core.model.WithDerivationPath
import jp.co.soramitsu.core.model.WithMnemonic
import jp.co.soramitsu.core.model.WithSeed
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.Signer
import jp.co.soramitsu.fearless_utils.encrypt.junction.BIP32JunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.junction.JunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair as FearlessKeypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.encrypt.seed.ethereum.EthereumSeedFactory
import jp.co.soramitsu.fearless_utils.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.fearless_utils.exceptions.Bip39Exception
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.scale.toHexString
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.mnemonic.Mnemonic

/**
 * A deterministic violation confined to one decoded V3 root-secret payload.
 *
 * Only this exception permits a caller to quarantine the exact active
 * ciphertext. Cryptographic provider/native failures use
 * [WalletSecureStorageUnavailableException] and must leave storage untouched.
 */
class WalletRootSecretCorruptionException(
    message: String,
    cause: Throwable? = null
) : IllegalArgumentException(message, cause)

/**
 * Identity-bound V3 root-secret validation used by runtime stores and the
 * adjacent database integrity migration.
 */
interface WalletRootSecretValidation {

    fun validateSubstrateAndSanitize(
        encoded: String,
        expectedPublicKey: ByteArray,
        expectedCryptoType: CryptoType,
        expectedAccountId: ByteArray
    ): String

    fun validateEthereumAndSanitize(
        encoded: String,
        expectedPublicKey: ByteArray,
        expectedAddress: ByteArray
    ): String

    fun validateTonAndSanitize(
        encoded: String,
        expectedPublicKey: ByteArray
    ): String
}

fun interface LegacySubstrateSecretValidation {

    fun validate(
        source: SecuritySource,
        expectedPublicKey: ByteArray,
        expectedCryptoType: CryptoType,
        expectedAccountId: ByteArray
    )
}

object WalletRootSecretValidator :
    WalletRootSecretValidation,
    LegacySubstrateSecretValidation {

    private val engine =
        WalletRootSecretValidationEngine(ProductionWalletRootCryptography)

    override fun validateSubstrateAndSanitize(
        encoded: String,
        expectedPublicKey: ByteArray,
        expectedCryptoType: CryptoType,
        expectedAccountId: ByteArray
    ): String {
        return engine.validateSubstrateAndSanitize(
            encoded = encoded,
            expectedPublicKey = expectedPublicKey,
            expectedCryptoType = expectedCryptoType,
            expectedAccountId = expectedAccountId
        )
    }

    override fun validateEthereumAndSanitize(
        encoded: String,
        expectedPublicKey: ByteArray,
        expectedAddress: ByteArray
    ): String {
        return engine.validateEthereumAndSanitize(
            encoded = encoded,
            expectedPublicKey = expectedPublicKey,
            expectedAddress = expectedAddress
        )
    }

    override fun validateTonAndSanitize(
        encoded: String,
        expectedPublicKey: ByteArray
    ): String {
        return engine.validateTonAndSanitize(
            encoded = encoded,
            expectedPublicKey = expectedPublicKey
        )
    }

    fun validateSubstrateKeypair(
        keypair: FearlessKeypair,
        expectedPublicKey: ByteArray,
        expectedCryptoType: CryptoType,
        expectedAccountId: ByteArray
    ) {
        engine.validateSubstrateKeypair(
            keypair = keypair,
            expectedPublicKey = expectedPublicKey,
            expectedCryptoType = expectedCryptoType,
            expectedAccountId = expectedAccountId
        )
    }

    fun validateEthereumKeypair(
        keypair: FearlessKeypair,
        expectedPublicKey: ByteArray,
        expectedAddress: ByteArray
    ) {
        engine.validateEthereumKeypair(
            keypair = keypair,
            expectedPublicKey = expectedPublicKey,
            expectedAddress = expectedAddress
        )
    }

    fun validateTonKeypair(
        keypair: FearlessKeypair,
        expectedPublicKey: ByteArray
    ) {
        engine.validateTonKeypair(
            keypair = keypair,
            expectedPublicKey = expectedPublicKey
        )
    }

    /**
     * Validates the retained pre-v2 fallback before it can reach a signer or
     * export flow. Unlike a public-key-only comparison, this proves private-key
     * ownership and verifies every available recovery representation.
     */
    fun validateLegacySubstrateSource(
        source: SecuritySource,
        expectedPublicKey: ByteArray,
        expectedCryptoType: CryptoType,
        expectedAccountId: ByteArray
    ) {
        engine.validateLegacySubstrateSource(
            source = source,
            expectedPublicKey = expectedPublicKey,
            expectedCryptoType = expectedCryptoType,
            expectedAccountId = expectedAccountId
        )
    }

    override fun validate(
        source: SecuritySource,
        expectedPublicKey: ByteArray,
        expectedCryptoType: CryptoType,
        expectedAccountId: ByteArray
    ) {
        validateLegacySubstrateSource(
            source = source,
            expectedPublicKey = expectedPublicKey,
            expectedCryptoType = expectedCryptoType,
            expectedAccountId = expectedAccountId
        )
    }
}

internal interface WalletRootCryptography {

    fun deriveSubstrateAccountId(publicKey: ByteArray): ByteArray

    fun decodeSubstratePath(path: String): JunctionDecoder.DecodeResult

    fun provesSubstrateOwnership(
        keypair: FearlessKeypair,
        cryptoType: CryptoType
    ): Boolean

    fun recoverSubstrateFromEntropy(
        entropy: ByteArray,
        decodedPath: JunctionDecoder.DecodeResult?,
        cryptoType: CryptoType
    ): FearlessKeypair

    fun recoverSubstrateFromSeed(
        seed: ByteArray,
        decodedPath: JunctionDecoder.DecodeResult?,
        cryptoType: CryptoType
    ): FearlessKeypair

    fun recoverSubstrateFromMnemonic(
        mnemonicText: String,
        decodedPath: JunctionDecoder.DecodeResult?,
        cryptoType: CryptoType
    ): FearlessKeypair

    fun deriveEthereumPublicKey(privateKey: ByteArray): ByteArray

    fun deriveEthereumAddress(publicKey: ByteArray): ByteArray

    fun decodeEthereumPath(path: String): JunctionDecoder.DecodeResult

    fun recoverEthereumFromEntropy(
        entropy: ByteArray,
        decodedPath: JunctionDecoder.DecodeResult
    ): FearlessKeypair

    fun deriveTonPublicKey(privateKey: ByteArray): ByteArray

    fun isValidBip39Mnemonic(mnemonicText: String): Boolean

    fun isValidTonMnemonic(mnemonicWords: List<String>): Boolean

    fun recoverTonFromMnemonic(mnemonicWords: List<String>): FearlessKeypair
}

internal class WalletRootSecretValidationEngine(
    private val cryptography: WalletRootCryptography
) : WalletRootSecretValidation {

    override fun validateSubstrateAndSanitize(
        encoded: String,
        expectedPublicKey: ByteArray,
        expectedCryptoType: CryptoType,
        expectedAccountId: ByteArray
    ): String {
        ensureBounded(encoded)
        val secrets = decodeSubstrate(encoded)
        val keypairStruct = secrets[SubstrateSecrets.SubstrateKeypair]
        val keypair = keypairStruct.toKeypair()

        validateSubstrateKeypair(
            keypair = keypair,
            expectedPublicKey = expectedPublicKey,
            expectedCryptoType = expectedCryptoType,
            expectedAccountId = expectedAccountId
        )

        val entropy = secrets[SubstrateSecrets.Entropy]
        val seed = secrets[SubstrateSecrets.Seed]
        val derivationPath = secrets[SubstrateSecrets.SubstrateDerivationPath]
            .takeIf { entropy != null || seed != null }
        validateSubstrateRecovery(
            entropy = entropy,
            seed = seed,
            derivationPath = derivationPath,
            expectedCryptoType = expectedCryptoType,
            expectedKeypair = keypair
        )

        return SubstrateSecrets(
            substrateKeyPair = keypair,
            entropy = entropy,
            seed = seed,
            substrateDerivationPath = derivationPath
        ).toHexString()
    }

    override fun validateEthereumAndSanitize(
        encoded: String,
        expectedPublicKey: ByteArray,
        expectedAddress: ByteArray
    ): String {
        ensureBounded(encoded)
        val secrets = decodeEthereum(encoded)
        val keypair = secrets[EthereumSecrets.EthereumKeypair].toKeypair()

        validateEthereumKeypair(
            keypair = keypair,
            expectedPublicKey = expectedPublicKey,
            expectedAddress = expectedAddress
        )

        val entropy = secrets[EthereumSecrets.Entropy]
        val seed = secrets[EthereumSecrets.Seed]
        val derivationPath = secrets[EthereumSecrets.EthereumDerivationPath]
            .takeIf { entropy != null }
        validateEthereumRecovery(
            entropy = entropy,
            storedSeed = seed,
            derivationPath = derivationPath,
            expectedKeypair = keypair
        )

        return EthereumSecrets(
            entropy = entropy,
            seed = seed,
            ethereumKeypair = keypair,
            ethereumDerivationPath = derivationPath
        ).toHexString()
    }

    override fun validateTonAndSanitize(
        encoded: String,
        expectedPublicKey: ByteArray
    ): String {
        ensureBounded(encoded)
        val secrets = decodeTon(encoded)
        val seed = secrets[TonSecrets.Seed]
        val keypair = Keypair(
            publicKey = secrets[TonSecrets.PublicKey],
            privateKey = secrets[TonSecrets.PrivateKey]
        )

        validateTonKeypair(
            keypair = keypair,
            expectedPublicKey = expectedPublicKey
        )
        ensureLocal(
            seed.isNotEmpty() && seed.size <= MAX_TON_SEED_BYTES,
            "A TON secret has an invalid seed shape"
        )
        val mnemonicText = try {
            seed.decodeToString(throwOnInvalidSequence = true)
        } catch (failure: Exception) {
            throw WalletRootSecretCorruptionException(
                "A TON mnemonic is not valid UTF-8",
                failure
            )
        }
        ensureLocal(
            mnemonicText.isNotEmpty() &&
                mnemonicText.length <= MAX_TON_MNEMONIC_CHARS &&
                mnemonicText == mnemonicText.trim() &&
                mnemonicText.split(' ').joinToString(" ") == mnemonicText,
            "A TON mnemonic is not canonical UTF-8 words"
        )
        val mnemonicWords = mnemonicText.split(' ')
        ensureLocal(
            mnemonicWords.size in VALID_MNEMONIC_WORD_COUNTS,
            "A TON mnemonic has an invalid word count"
        )

        val validBip39 = performCryptography("BIP39 mnemonic validation") {
            cryptography.isValidBip39Mnemonic(mnemonicText)
        }
        val validTon = performCryptography("TON mnemonic validation") {
            cryptography.isValidTonMnemonic(mnemonicWords)
        }
        ensureLocal(
            validBip39 || validTon,
            "A TON mnemonic is invalid"
        )

        val recovered = performCryptography("TON mnemonic recovery") {
            cryptography.recoverTonFromMnemonic(mnemonicWords)
        }
        ensureLocal(
            recovered.privateKey.contentEquals(keypair.privateKey) &&
                recovered.publicKey.contentEquals(keypair.publicKey),
            "A TON mnemonic derives a different keypair"
        )

        return secrets.toHexString()
    }

    fun validateSubstrateKeypair(
        keypair: FearlessKeypair,
        expectedPublicKey: ByteArray,
        expectedCryptoType: CryptoType,
        expectedAccountId: ByteArray
    ) {
        val expectedPublicKeyBytes = expectedCryptoType.substratePublicKeyBytes()
        ensurePublicIdentity(
            expectedPublicKey.size == expectedPublicKeyBytes &&
                expectedAccountId.size == ACCOUNT_ID_BYTES,
            "A Substrate database identity has an invalid shape"
        )
        val durableAccountId =
            performCryptography("durable Substrate account-id derivation") {
                cryptography.deriveSubstrateAccountId(expectedPublicKey)
            }
        ensurePublicIdentity(
            durableAccountId.contentEquals(expectedAccountId),
            "A Substrate database public key does not match its account id"
        )
        ensureLocal(
            keypair.publicKey.size == expectedPublicKeyBytes &&
                keypair.privateKey.size == PRIVATE_KEY_BYTES,
            "A Substrate keypair has an invalid shape"
        )
        val nonce = (keypair as? Sr25519Keypair)?.nonce
        when (expectedCryptoType) {
            CryptoType.SR25519 -> ensureLocal(
                nonce?.size == SR25519_NONCE_BYTES,
                "An SR25519 keypair has an invalid nonce"
            )

            CryptoType.ED25519 -> ensureLocal(
                nonce == null,
                "An ED25519 keypair contains an unexpected nonce"
            )

            CryptoType.ECDSA -> {
                ensureLocal(
                    nonce == null,
                    "An ECDSA keypair contains an unexpected nonce"
                )
                ensureLocal(
                    keypair.privateKey.isValidEcdsaScalar(),
                    "An ECDSA private key is outside the curve"
                )
            }
        }
        ensureLocal(
            keypair.publicKey.contentEquals(expectedPublicKey),
            "A Substrate secret does not match its public identity"
        )
        val ownsPublicKey =
            performCryptography("Substrate private-key ownership proof") {
                cryptography.provesSubstrateOwnership(
                    keypair = keypair,
                    cryptoType = expectedCryptoType
                )
            }
        ensureLocal(
            ownsPublicKey,
            "A Substrate private key cannot prove its public key"
        )
    }

    fun validateEthereumKeypair(
        keypair: FearlessKeypair,
        expectedPublicKey: ByteArray,
        expectedAddress: ByteArray
    ) {
        ensurePublicIdentity(
            expectedPublicKey.size == ECDSA_PUBLIC_KEY_BYTES &&
                expectedAddress.size == ETHEREUM_ADDRESS_BYTES,
            "An Ethereum database identity has an invalid shape"
        )
        val durableAddress =
            performCryptography("durable Ethereum address derivation") {
                cryptography.deriveEthereumAddress(expectedPublicKey)
            }
        ensurePublicIdentity(
            durableAddress.contentEquals(expectedAddress),
            "An Ethereum database public key does not match its address"
        )
        ensureLocal(
            keypair.publicKey.size == ECDSA_PUBLIC_KEY_BYTES &&
                keypair.privateKey.size == PRIVATE_KEY_BYTES &&
                (keypair as? Sr25519Keypair)?.nonce == null,
            "An Ethereum keypair has an invalid shape"
        )
        ensureLocal(
            keypair.privateKey.isValidEcdsaScalar(),
            "An Ethereum private key is outside the curve"
        )
        ensureLocal(
            keypair.publicKey.contentEquals(expectedPublicKey),
            "An Ethereum secret does not match its public identity"
        )
        val derivedPublicKey =
            performCryptography("Ethereum public-key derivation") {
                cryptography.deriveEthereumPublicKey(keypair.privateKey)
            }
        ensureLocal(
            derivedPublicKey.contentEquals(keypair.publicKey),
            "An Ethereum private key does not own its public key"
        )
    }

    fun validateTonKeypair(
        keypair: FearlessKeypair,
        expectedPublicKey: ByteArray
    ) {
        ensurePublicIdentity(
            expectedPublicKey.size == TON_PUBLIC_KEY_BYTES,
            "A TON database identity has an invalid shape"
        )
        ensureLocal(
            keypair.publicKey.size == TON_PUBLIC_KEY_BYTES &&
                keypair.privateKey.size == PRIVATE_KEY_BYTES,
            "A TON keypair has an invalid shape"
        )
        ensureLocal(
            keypair.publicKey.contentEquals(expectedPublicKey),
            "A TON secret does not match its public identity"
        )
        val derivedPublicKey = performCryptography("TON public-key derivation") {
            cryptography.deriveTonPublicKey(keypair.privateKey)
        }
        ensureLocal(
            derivedPublicKey.contentEquals(keypair.publicKey),
            "A TON private key does not own its public key"
        )
    }

    fun validateLegacySubstrateSource(
        source: SecuritySource,
        expectedPublicKey: ByteArray,
        expectedCryptoType: CryptoType,
        expectedAccountId: ByteArray
    ) {
        validateSubstrateKeypair(
            keypair = source.keypair,
            expectedPublicKey = expectedPublicKey,
            expectedCryptoType = expectedCryptoType,
            expectedAccountId = expectedAccountId
        )

        validateSubstrateRecovery(
            entropy = null,
            seed = (source as? WithSeed)?.seed,
            mnemonicText = (source as? WithMnemonic)?.mnemonic,
            derivationPath = (source as? WithDerivationPath)?.derivationPath,
            expectedCryptoType = expectedCryptoType,
            expectedKeypair = source.keypair
        )
    }

    private fun validateSubstrateRecovery(
        entropy: ByteArray?,
        seed: ByteArray?,
        mnemonicText: String? = null,
        derivationPath: String?,
        expectedCryptoType: CryptoType,
        expectedKeypair: FearlessKeypair
    ) {
        ensureLocal(
            derivationPath == null ||
                derivationPath.length <= MAX_DERIVATION_PATH_CHARS,
            "A Substrate derivation path is oversized"
        )
        val decodedPath = decodeSubstratePath(derivationPath)

        entropy?.let {
            ensureLocal(
                it.size in VALID_ENTROPY_LENGTHS,
                "A Substrate entropy value has an invalid length"
            )
            val recovered = performCryptography("Substrate entropy recovery") {
                cryptography.recoverSubstrateFromEntropy(
                    entropy = it,
                    decodedPath = decodedPath,
                    cryptoType = expectedCryptoType
                )
            }
            ensureRecoveredSubstrateMatches(
                recovered = recovered,
                expected = expectedKeypair,
                source = "entropy"
            )
        }

        mnemonicText?.let {
            ensureLocal(
                it.isNotEmpty() &&
                    it.length <= MAX_SUBSTRATE_MNEMONIC_CHARS &&
                    it == it.trim() &&
                    it.split(' ').joinToString(" ") == it,
                "A Substrate mnemonic is not canonical words"
            )
            ensureLocal(
                it.split(' ').size in VALID_MNEMONIC_WORD_COUNTS,
                "A Substrate mnemonic has an invalid word count"
            )
            val recovered = try {
                cryptography.recoverSubstrateFromMnemonic(
                    mnemonicText = it,
                    decodedPath = decodedPath,
                    cryptoType = expectedCryptoType
                )
            } catch (failure: Bip39Exception) {
                throw WalletRootSecretCorruptionException(
                    "A Substrate mnemonic is invalid",
                    failure
                )
            } catch (failure: WalletSecureStorageUnavailableException) {
                throw failure
            } catch (failure: Exception) {
                throw operationalFailure(
                    operation = "Substrate mnemonic recovery",
                    cause = failure
                )
            }
            ensureRecoveredSubstrateMatches(
                recovered = recovered,
                expected = expectedKeypair,
                source = "mnemonic"
            )
        }

        seed?.let {
            ensureLocal(
                it.size == PRIVATE_KEY_BYTES,
                "A Substrate recovery seed has an invalid length"
            )
            val recovered = performCryptography("Substrate seed recovery") {
                cryptography.recoverSubstrateFromSeed(
                    seed = it,
                    decodedPath = decodedPath,
                    cryptoType = expectedCryptoType
                )
            }
            ensureRecoveredSubstrateMatches(
                recovered = recovered,
                expected = expectedKeypair,
                source = "seed"
            )
        }
    }

    private fun decodeSubstratePath(
        derivationPath: String?
    ): JunctionDecoder.DecodeResult? {
        val nonEmptyPath = derivationPath?.takeIf(String::isNotEmpty)
            ?: return null
        return try {
            cryptography.decodeSubstratePath(nonEmptyPath)
        } catch (failure: JunctionDecoder.DecodingError) {
            throw WalletRootSecretCorruptionException(
                "A Substrate derivation path is malformed",
                failure
            )
        } catch (failure: WalletSecureStorageUnavailableException) {
            throw failure
        } catch (failure: Exception) {
            throw operationalFailure(
                operation = "Substrate derivation-path decoding",
                cause = failure
            )
        }
    }

    private fun ensureRecoveredSubstrateMatches(
        recovered: FearlessKeypair,
        expected: FearlessKeypair,
        source: String
    ) {
        val recoveredNonce = (recovered as? Sr25519Keypair)?.nonce
        val expectedNonce = (expected as? Sr25519Keypair)?.nonce
        ensureLocal(
            recovered.privateKey.contentEquals(expected.privateKey) &&
                recovered.publicKey.contentEquals(expected.publicKey) &&
                recoveredNonce.contentEqualsNullable(expectedNonce),
            "Substrate $source derives a different keypair"
        )
    }

    private fun validateEthereumRecovery(
        entropy: ByteArray?,
        storedSeed: ByteArray?,
        derivationPath: String?,
        expectedKeypair: FearlessKeypair
    ) {
        ensureLocal(
            derivationPath == null ||
                derivationPath.length <= MAX_DERIVATION_PATH_CHARS,
            "An Ethereum derivation path is oversized"
        )
        val decodedPath = decodeEthereumPath(derivationPath)
        storedSeed?.let {
            ensureLocal(
                it.size == PRIVATE_KEY_BYTES &&
                    it.contentEquals(expectedKeypair.privateKey),
                "An Ethereum recovery seed differs from its private key"
            )
        }
        entropy?.let {
            ensureLocal(
                it.size in VALID_ENTROPY_LENGTHS,
                "An Ethereum entropy value has an invalid length"
            )
            val requiredPath = decodedPath ?: throw WalletRootSecretCorruptionException(
                "Ethereum entropy requires a derivation path"
            )
            val recovered = performCryptography("Ethereum entropy recovery") {
                cryptography.recoverEthereumFromEntropy(
                    entropy = it,
                    decodedPath = requiredPath
                )
            }
            ensureLocal(
                recovered.privateKey.contentEquals(expectedKeypair.privateKey) &&
                    recovered.publicKey.contentEquals(expectedKeypair.publicKey),
                "Ethereum entropy and path derive a different keypair"
            )
        }
    }

    private fun decodeEthereumPath(
        derivationPath: String?
    ): JunctionDecoder.DecodeResult? {
        val nonEmptyPath = derivationPath?.takeIf(String::isNotEmpty)
            ?: return null
        return try {
            cryptography.decodeEthereumPath(nonEmptyPath)
        } catch (failure: JunctionDecoder.DecodingError) {
            throw WalletRootSecretCorruptionException(
                "An Ethereum derivation path is malformed",
                failure
            )
        } catch (failure: WalletSecureStorageUnavailableException) {
            throw failure
        } catch (failure: Exception) {
            throw operationalFailure(
                operation = "Ethereum derivation-path decoding",
                cause = failure
            )
        }
    }

    private fun decodeSubstrate(encoded: String) = try {
        WalletSecretScalePreflight.requireSubstrateV3(encoded)
        SubstrateSecrets.read(encoded)
    } catch (failure: Exception) {
        throw WalletRootSecretCorruptionException(
            "A Substrate secret payload is malformed",
            failure
        )
    }

    private fun decodeEthereum(encoded: String) = try {
        WalletSecretScalePreflight.requireEthereumV3(encoded)
        EthereumSecrets.read(encoded)
    } catch (failure: Exception) {
        throw WalletRootSecretCorruptionException(
            "An Ethereum secret payload is malformed",
            failure
        )
    }

    private fun decodeTon(encoded: String) = try {
        WalletSecretScalePreflight.requireTonV3(encoded)
        TonSecrets.read(encoded)
    } catch (failure: Exception) {
        throw WalletRootSecretCorruptionException(
            "A TON secret payload is malformed",
            failure
        )
    }

    private inline fun <T> performCryptography(
        operation: String,
        block: () -> T
    ): T {
        return try {
            block()
        } catch (failure: WalletSecureStorageUnavailableException) {
            throw failure
        } catch (failure: Exception) {
            throw operationalFailure(operation, failure)
        }
    }
}

private object ProductionWalletRootCryptography : WalletRootCryptography {

    override fun deriveSubstrateAccountId(publicKey: ByteArray): ByteArray {
        return publicKey.substrateAccountId()
    }

    override fun decodeSubstratePath(
        path: String
    ): JunctionDecoder.DecodeResult {
        return SubstrateJunctionDecoder.decode(path)
    }

    override fun provesSubstrateOwnership(
        keypair: FearlessKeypair,
        cryptoType: CryptoType
    ): Boolean {
        if (cryptoType == CryptoType.ECDSA) {
            return EthereumKeypairFactory
                .createWithPrivateKey(keypair.privateKey)
                .publicKey
                .contentEquals(keypair.publicKey)
        }
        val encryptionType = cryptoType.toEncryptionType()
        val signature = Signer.sign(
            multiChainEncryption = MultiChainEncryption.Substrate(encryptionType),
            message = PRIVATE_KEY_PROOF_MESSAGE,
            keypair = keypair
        ).signature
        return when (cryptoType) {
            CryptoType.SR25519 -> Signer.verifySr25519(
                message = PRIVATE_KEY_PROOF_MESSAGE,
                signature = signature,
                publicKeyBytes = keypair.publicKey
            )

            CryptoType.ED25519 -> Signer.verifyEd25519(
                message = PRIVATE_KEY_PROOF_MESSAGE,
                signature = signature,
                publicKeyBytes = keypair.publicKey
            )

            CryptoType.ECDSA -> error("ECDSA uses public-key derivation")
        }
    }

    override fun recoverSubstrateFromEntropy(
        entropy: ByteArray,
        decodedPath: JunctionDecoder.DecodeResult?,
        cryptoType: CryptoType
    ): FearlessKeypair {
        val mnemonic = MnemonicCreator.fromEntropy(entropy)
        val seed = SubstrateSeedFactory.deriveSeed32(
            mnemonicWords = mnemonic.words,
            password = decodedPath?.password
        ).seed
        return recoverSubstrateFromSeed(seed, decodedPath, cryptoType)
    }

    override fun recoverSubstrateFromSeed(
        seed: ByteArray,
        decodedPath: JunctionDecoder.DecodeResult?,
        cryptoType: CryptoType
    ): FearlessKeypair {
        return SubstrateKeypairFactory.generate(
            encryptionType = cryptoType.toEncryptionType(),
            seed = seed,
            junctions = decodedPath?.junctions.orEmpty()
        )
    }

    override fun recoverSubstrateFromMnemonic(
        mnemonicText: String,
        decodedPath: JunctionDecoder.DecodeResult?,
        cryptoType: CryptoType
    ): FearlessKeypair {
        val mnemonic = MnemonicCreator.fromWords(mnemonicText)
        val seed = SubstrateSeedFactory.deriveSeed32(
            mnemonicWords = mnemonic.words,
            password = decodedPath?.password
        ).seed
        return recoverSubstrateFromSeed(seed, decodedPath, cryptoType)
    }

    override fun deriveEthereumPublicKey(privateKey: ByteArray): ByteArray {
        return EthereumKeypairFactory.createWithPrivateKey(privateKey).publicKey
    }

    override fun deriveEthereumAddress(publicKey: ByteArray): ByteArray {
        return publicKey.ethereumAddressFromPublicKey()
    }

    override fun decodeEthereumPath(
        path: String
    ): JunctionDecoder.DecodeResult {
        return BIP32JunctionDecoder.decode(path)
    }

    override fun recoverEthereumFromEntropy(
        entropy: ByteArray,
        decodedPath: JunctionDecoder.DecodeResult
    ): FearlessKeypair {
        val mnemonic = MnemonicCreator.fromEntropy(entropy)
        val seed = EthereumSeedFactory.deriveSeed32(
            mnemonicWords = mnemonic.words,
            password = decodedPath.password
        ).seed
        return EthereumKeypairFactory.generate(
            seed = seed,
            junctions = decodedPath.junctions
        )
    }

    override fun deriveTonPublicKey(privateKey: ByteArray): ByteArray {
        return SolanaKeyDerivation.publicKeyFromPrivateKey(privateKey)
    }

    override fun isValidBip39Mnemonic(mnemonicText: String): Boolean {
        return try {
            MnemonicCreator.fromWords(mnemonicText).words == mnemonicText
        } catch (_: Bip39Exception) {
            false
        }
    }

    override fun isValidTonMnemonic(
        mnemonicWords: List<String>
    ): Boolean {
        return Mnemonic.isValid(mnemonicWords)
    }

    override fun recoverTonFromMnemonic(
        mnemonicWords: List<String>
    ): FearlessKeypair {
        val seed = Mnemonic.toSeed(mnemonicWords)
        val privateKey = PrivateKeyEd25519(seed)
        return Keypair(
            publicKey = privateKey.publicKey().key.toByteArray(),
            privateKey = privateKey.key.toByteArray()
        )
    }
}

private fun EncodableStruct<KeyPairSchema>.toKeypair(): FearlessKeypair {
    return Keypair(
        publicKey = this[KeyPairSchema.PublicKey],
        privateKey = this[KeyPairSchema.PrivateKey],
        nonce = this[KeyPairSchema.Nonce]
    )
}

private fun ensureBounded(encoded: String) {
    ensureLocal(
        encoded.isNotEmpty() &&
            encoded.length <= MAX_WALLET_SECRET_PLAINTEXT_CHARS,
        "A wallet root secret is empty or exceeds the safe decode limit"
    )
}

private fun ensureLocal(condition: Boolean, message: String) {
    if (!condition) {
        throw WalletRootSecretCorruptionException(message)
    }
}

private fun ensurePublicIdentity(condition: Boolean, message: String) {
    if (!condition) {
        throw WalletPublicIdentityIntegrityException(message)
    }
}

private fun operationalFailure(
    operation: String,
    cause: Exception
): WalletSecureStorageUnavailableException {
    return WalletSecureStorageUnavailableException(
        "Wallet cryptography is unavailable during $operation",
        cause
    )
}

private fun CryptoType.toEncryptionType(): EncryptionType = when (this) {
    CryptoType.SR25519 -> EncryptionType.SR25519
    CryptoType.ED25519 -> EncryptionType.ED25519
    CryptoType.ECDSA -> EncryptionType.ECDSA
}

private fun CryptoType.substratePublicKeyBytes(): Int = when (this) {
    CryptoType.SR25519,
    CryptoType.ED25519 -> SUBSTRATE_PUBLIC_KEY_BYTES
    CryptoType.ECDSA -> ECDSA_PUBLIC_KEY_BYTES
}

private fun ByteArray.isValidEcdsaScalar(): Boolean {
    val scalar = BigInteger(1, this)
    return scalar.signum() > 0 && scalar < ECDSA_CURVE_ORDER
}

private fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean {
    return when {
        this == null || other == null -> this == null && other == null
        else -> contentEquals(other)
    }
}

private const val ACCOUNT_ID_BYTES = 32
private const val PRIVATE_KEY_BYTES = 32
private const val SUBSTRATE_PUBLIC_KEY_BYTES = 32
private const val ECDSA_PUBLIC_KEY_BYTES = 33
private const val ETHEREUM_ADDRESS_BYTES = 20
private const val SR25519_NONCE_BYTES = 32
private const val TON_PUBLIC_KEY_BYTES = 32
private const val MAX_TON_SEED_BYTES = 8_192
private const val MAX_TON_MNEMONIC_CHARS = 8_192
private const val MAX_SUBSTRATE_MNEMONIC_CHARS = 8_192
private const val MAX_DERIVATION_PATH_CHARS = 2_048
private val VALID_ENTROPY_LENGTHS = setOf(16, 20, 24, 28, 32)
private val VALID_MNEMONIC_WORD_COUNTS = setOf(12, 15, 18, 21, 24)
private val ECDSA_CURVE_ORDER = BigInteger(
    "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141",
    16
)
private val PRIVATE_KEY_PROOF_MESSAGE =
    "fearless-wallet-root-secret-integrity-v1".encodeToByteArray()
