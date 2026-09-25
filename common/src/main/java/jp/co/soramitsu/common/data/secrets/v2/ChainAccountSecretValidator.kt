package jp.co.soramitsu.common.data.secrets.v2

import java.math.BigInteger
import jp.co.soramitsu.common.data.secrets.WalletSecretScalePreflight
import jp.co.soramitsu.common.data.secrets.v1.Keypair
import jp.co.soramitsu.common.data.storage.encrypt.MAX_WALLET_SECRET_PLAINTEXT_CHARS
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageUnavailableException
import jp.co.soramitsu.common.utils.deriveSeed32
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.isValidEthereumCompressedPublicKey
import jp.co.soramitsu.common.utils.substrateAccountId
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
import jp.co.soramitsu.fearless_utils.scale.toHexString

/**
 * A record-local chain-account payload violation. Only this exception permits
 * callers to quarantine the exact active secret. Operational cryptography and
 * provider failures use [WalletSecureStorageUnavailableException] instead.
 */
class ChainAccountSecretCorruptionException(
    message: String,
    cause: Throwable? = null
) : IllegalArgumentException(message, cause)

/**
 * Production validation contract used by storage and database migration.
 */
fun interface ChainAccountSecretValidation {

    fun validateAndSanitize(
        encoded: String,
        expectedAccountId: ByteArray,
        expectedPublicKey: ByteArray?,
        expectedCryptoType: CryptoType?
    ): String
}

/**
 * Validates legacy chain-account signing material against its durable public
 * identity. The validator is pure: callers decide whether to keep, replace, or
 * quarantine the exact encrypted payload after this function returns.
 */
object ChainAccountSecretValidator : ChainAccountSecretValidation {

    private val engine =
        ChainAccountSecretValidationEngine(ProductionChainAccountCryptography)

    /**
     * Returns a canonical payload after validating public identity, private-key
     * ownership, and any recovery material that can be cryptographically bound.
     */
    override fun validateAndSanitize(
        encoded: String,
        expectedAccountId: ByteArray,
        expectedPublicKey: ByteArray?,
        expectedCryptoType: CryptoType?
    ): String {
        return engine.validateAndSanitize(
            encoded = encoded,
            expectedAccountId = expectedAccountId,
            expectedPublicKey = expectedPublicKey,
            expectedCryptoType = expectedCryptoType
        )
    }

    fun validateAndSanitize(
        encoded: String,
        expectedAccountId: ByteArray
    ): String {
        return validateAndSanitize(
            encoded = encoded,
            expectedAccountId = expectedAccountId,
            expectedPublicKey = null,
            expectedCryptoType = null
        )
    }

    /**
     * Validates one decoded keypair without reading or mutating storage.
     */
    fun validateKeypair(
        keypair: FearlessKeypair,
        expectedAccountId: ByteArray,
        expectedPublicKey: ByteArray? = null,
        expectedCryptoType: CryptoType
    ) {
        engine.validateKeypair(
            keypair = keypair,
            expectedAccountId = expectedAccountId,
            expectedPublicKey = expectedPublicKey,
            expectedCryptoType = expectedCryptoType
        )
    }
}

internal interface ChainAccountCryptography {

    fun deriveAccountId(
        publicKey: ByteArray,
        identityKind: ChainAccountIdentityKind
    ): ByteArray

    fun decodeDerivationPath(
        derivationPath: String,
        identityKind: ChainAccountIdentityKind
    ): JunctionDecoder.DecodeResult

    fun provesOwnership(
        keypair: FearlessKeypair,
        cryptoType: CryptoType
    ): Boolean

    fun recoverFromEntropy(
        entropy: ByteArray,
        decodedPath: JunctionDecoder.DecodeResult?,
        cryptoType: CryptoType,
        identityKind: ChainAccountIdentityKind
    ): FearlessKeypair

    fun recoverFromSeed(
        seed: ByteArray,
        decodedPath: JunctionDecoder.DecodeResult?,
        cryptoType: CryptoType,
        identityKind: ChainAccountIdentityKind,
        mnemonicDerived: Boolean
    ): FearlessKeypair
}

internal enum class ChainAccountIdentityKind {
    SUBSTRATE,
    ETHEREUM
}

internal class ChainAccountSecretValidationEngine(
    private val cryptography: ChainAccountCryptography
) : ChainAccountSecretValidation {

    override fun validateAndSanitize(
        encoded: String,
        expectedAccountId: ByteArray,
        expectedPublicKey: ByteArray?,
        expectedCryptoType: CryptoType?
    ): String {
        ensureLocal(
            encoded.isNotEmpty() &&
                encoded.length <= MAX_WALLET_SECRET_PLAINTEXT_CHARS,
            "A chain-account secret is empty or exceeds the safe decode limit"
        )
        val secrets = decodeSecret(encoded)
        val keypairStruct = secrets[ChainAccountSecrets.Keypair]
        val keypair = Keypair(
            publicKey = keypairStruct[KeyPairSchema.PublicKey],
            privateKey = keypairStruct[KeyPairSchema.PrivateKey],
            nonce = keypairStruct[KeyPairSchema.Nonce]
        )
        val cryptoType = expectedCryptoType ?: inferCryptoType(keypair)
        val identityKind = expectedAccountId.requireIdentityKind()

        validateKeypair(
            keypair = keypair,
            expectedAccountId = expectedAccountId,
            expectedPublicKey = expectedPublicKey,
            expectedCryptoType = cryptoType,
            identityKind = identityKind
        )

        val entropy = secrets[ChainAccountSecrets.Entropy]
        val seed = secrets[ChainAccountSecrets.Seed]
        val derivationPath = secrets[ChainAccountSecrets.DerivationPath]
            .takeIf { entropy != null || seed != null }
        validateRecoveryMaterial(
            entropy = entropy,
            seed = seed,
            derivationPath = derivationPath,
            cryptoType = cryptoType,
            identityKind = identityKind,
            expectedKeypair = keypair
        )

        return ChainAccountSecrets(
            keyPair = keypair,
            entropy = entropy,
            seed = seed,
            derivationPath = derivationPath
        ).toHexString()
    }

    fun validateKeypair(
        keypair: FearlessKeypair,
        expectedAccountId: ByteArray,
        expectedPublicKey: ByteArray?,
        expectedCryptoType: CryptoType
    ) {
        val identityKind = expectedAccountId.requireIdentityKind()
        validateKeypair(
            keypair = keypair,
            expectedAccountId = expectedAccountId,
            expectedPublicKey = expectedPublicKey,
            expectedCryptoType = expectedCryptoType,
            identityKind = identityKind
        )
    }

    private fun validateKeypair(
        keypair: FearlessKeypair,
        expectedAccountId: ByteArray,
        expectedPublicKey: ByteArray?,
        expectedCryptoType: CryptoType,
        identityKind: ChainAccountIdentityKind
    ) {
        val expectedPublicKeyBytes = expectedCryptoType.publicKeyBytes()
        ensurePublicIdentity(
            identityKind != ChainAccountIdentityKind.ETHEREUM ||
                expectedCryptoType == CryptoType.ECDSA,
            "An Ethereum chain-account identity must use ECDSA"
        )
        expectedPublicKey?.let {
            ensurePublicIdentity(
                it.size == expectedPublicKeyBytes,
                "A chain-account database public key has an invalid length"
            )
            ensurePublicIdentity(
                identityKind != ChainAccountIdentityKind.ETHEREUM ||
                    it.isValidEthereumCompressedPublicKey(),
                "An Ethereum chain-account database public key is malformed"
            )
            val durableAccountId =
                performCryptography("durable account-id derivation") {
                    cryptography.deriveAccountId(it, identityKind)
                }
            ensurePublicIdentity(
                durableAccountId.contentEquals(expectedAccountId),
                "A chain-account database public key does not match its account id"
            )
        }
        ensureLocal(
            keypair.publicKey.size == expectedPublicKeyBytes,
            "A chain-account keypair has an invalid public-key length"
        )
        ensureLocal(
            identityKind != ChainAccountIdentityKind.ETHEREUM ||
                keypair.publicKey.isValidEthereumCompressedPublicKey(),
            "An Ethereum chain-account keypair public key is malformed"
        )
        ensureLocal(
            keypair.privateKey.size == PRIVATE_KEY_BYTES,
            "A chain-account private key has an invalid length"
        )

        val nonce = (keypair as? Sr25519Keypair)?.nonce
        when (expectedCryptoType) {
            CryptoType.SR25519 -> ensureLocal(
                nonce?.size == SR25519_NONCE_BYTES,
                "An SR25519 chain-account keypair has an invalid nonce"
            )

            CryptoType.ED25519 -> ensureLocal(
                nonce == null,
                "An ED25519 chain-account keypair contains an unexpected nonce"
            )

            CryptoType.ECDSA -> {
                ensureLocal(
                    nonce == null,
                    "An ECDSA chain-account keypair contains an unexpected nonce"
                )
                ensureLocal(
                    keypair.privateKey.isValidEcdsaScalar(),
                    "An ECDSA chain-account private key is outside the curve"
                )
            }
        }

        expectedPublicKey?.let {
            ensureLocal(
                keypair.publicKey.contentEquals(it),
                "A chain-account secret does not match its public identity"
            )
        }
        if (expectedPublicKey == null) {
            val derivedAccountId = performCryptography("account-id derivation") {
                cryptography.deriveAccountId(
                    keypair.publicKey,
                    identityKind
                )
            }
            ensureLocal(
                derivedAccountId.contentEquals(expectedAccountId),
                "A chain-account public key does not match its account id"
            )
        }
        val ownsPublicKey = performCryptography("private-key ownership proof") {
            cryptography.provesOwnership(keypair, expectedCryptoType)
        }
        ensureLocal(
            ownsPublicKey,
            "A chain-account private key cannot prove its public key"
        )
    }

    private fun decodeSecret(encoded: String) = try {
        WalletSecretScalePreflight.requireChainAccountV2(encoded)
        ChainAccountSecrets.read(encoded)
    } catch (failure: Exception) {
        throw ChainAccountSecretCorruptionException(
            "A chain-account secret payload is malformed",
            failure
        )
    }

    private fun inferCryptoType(keypair: FearlessKeypair): CryptoType {
        return when {
            keypair is Sr25519Keypair -> CryptoType.SR25519
            keypair.publicKey.size == SUBSTRATE_PUBLIC_KEY_BYTES ->
                CryptoType.ED25519
            keypair.publicKey.size == ECDSA_PUBLIC_KEY_BYTES ->
                CryptoType.ECDSA
            else -> throw ChainAccountSecretCorruptionException(
                "Unable to infer a chain-account crypto type"
            )
        }
    }

    private fun validateRecoveryMaterial(
        entropy: ByteArray?,
        seed: ByteArray?,
        derivationPath: String?,
        cryptoType: CryptoType,
        identityKind: ChainAccountIdentityKind,
        expectedKeypair: FearlessKeypair
    ) {
        ensureLocal(
            derivationPath == null ||
                derivationPath.length <= MAX_DERIVATION_PATH_CHARS,
            "A chain-account derivation path is oversized"
        )
        val decodedPath = decodePath(
            derivationPath = derivationPath,
            identityKind = identityKind
        )
        ensureLocal(
            identityKind != ChainAccountIdentityKind.ETHEREUM ||
                entropy == null ||
                decodedPath != null,
            "An Ethereum mnemonic chain account has no derivation path"
        )

        entropy?.let {
            ensureLocal(
                it.size in VALID_ENTROPY_LENGTHS,
                "A chain-account entropy value has an invalid length"
            )
            val recovered = performCryptography("entropy recovery") {
                cryptography.recoverFromEntropy(
                    entropy = it,
                    decodedPath = decodedPath,
                    cryptoType = cryptoType,
                    identityKind = identityKind
                )
            }
            ensureRecoveredKeypairMatches(
                recovered = recovered,
                expectedKeypair = expectedKeypair,
                source = "entropy"
            )
        }

        seed?.let {
            val expectedSeedBytes = if (
                identityKind == ChainAccountIdentityKind.ETHEREUM &&
                entropy != null
            ) {
                ETHEREUM_MNEMONIC_SEED_BYTES
            } else {
                PRIVATE_KEY_BYTES
            }
            ensureLocal(
                it.size == expectedSeedBytes,
                "A chain-account recovery seed has an invalid length"
            )
            val recovered = performCryptography("seed recovery") {
                cryptography.recoverFromSeed(
                    seed = it,
                    decodedPath = decodedPath,
                    cryptoType = cryptoType,
                    identityKind = identityKind,
                    mnemonicDerived = entropy != null
                )
            }
            ensureRecoveredKeypairMatches(
                recovered = recovered,
                expectedKeypair = expectedKeypair,
                source = "seed"
            )
        }
    }

    private fun decodePath(
        derivationPath: String?,
        identityKind: ChainAccountIdentityKind
    ): JunctionDecoder.DecodeResult? {
        val nonEmptyPath = derivationPath?.takeIf(String::isNotEmpty)
            ?: return null
        return try {
            cryptography.decodeDerivationPath(
                derivationPath = nonEmptyPath,
                identityKind = identityKind
            )
        } catch (failure: JunctionDecoder.DecodingError) {
            throw ChainAccountSecretCorruptionException(
                "A chain-account derivation path is malformed",
                failure
            )
        } catch (failure: WalletSecureStorageUnavailableException) {
            throw failure
        } catch (failure: Exception) {
            throw WalletSecureStorageUnavailableException(
                "Chain-account cryptography is unavailable during derivation-path decoding",
                failure
            )
        }
    }

    private fun ensureRecoveredKeypairMatches(
        recovered: FearlessKeypair,
        expectedKeypair: FearlessKeypair,
        source: String
    ) {
        val recoveredNonce = (recovered as? Sr25519Keypair)?.nonce
        val expectedNonce = (expectedKeypair as? Sr25519Keypair)?.nonce
        ensureLocal(
            recovered.privateKey.contentEquals(expectedKeypair.privateKey) &&
                recovered.publicKey.contentEquals(expectedKeypair.publicKey) &&
                recoveredNonce.contentEqualsNullable(expectedNonce),
            "Chain-account $source derives a different keypair"
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
            throw WalletSecureStorageUnavailableException(
                "Chain-account cryptography is unavailable during $operation",
                failure
            )
        }
    }
}

private object ProductionChainAccountCryptography : ChainAccountCryptography {

    override fun deriveAccountId(
        publicKey: ByteArray,
        identityKind: ChainAccountIdentityKind
    ): ByteArray {
        return when (identityKind) {
            ChainAccountIdentityKind.SUBSTRATE ->
                publicKey.substrateAccountId()

            ChainAccountIdentityKind.ETHEREUM ->
                publicKey.ethereumAddressFromPublicKey()
        }
    }

    override fun decodeDerivationPath(
        derivationPath: String,
        identityKind: ChainAccountIdentityKind
    ): JunctionDecoder.DecodeResult {
        return when (identityKind) {
            ChainAccountIdentityKind.SUBSTRATE ->
                SubstrateJunctionDecoder.decode(derivationPath)

            ChainAccountIdentityKind.ETHEREUM ->
                BIP32JunctionDecoder.decode(derivationPath)
        }
    }

    override fun provesOwnership(
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

    override fun recoverFromEntropy(
        entropy: ByteArray,
        decodedPath: JunctionDecoder.DecodeResult?,
        cryptoType: CryptoType,
        identityKind: ChainAccountIdentityKind
    ): FearlessKeypair {
        val mnemonic = MnemonicCreator.fromEntropy(entropy)
        return when (identityKind) {
            ChainAccountIdentityKind.SUBSTRATE -> {
                val seed = SubstrateSeedFactory.deriveSeed32(
                    mnemonicWords = mnemonic.words,
                    password = decodedPath?.password
                ).seed
                recoverFromSeed(
                    seed = seed,
                    decodedPath = decodedPath,
                    cryptoType = cryptoType,
                    identityKind = identityKind,
                    mnemonicDerived = true
                )
            }

            ChainAccountIdentityKind.ETHEREUM -> {
                val exactPath = checkNotNull(decodedPath) {
                    "An Ethereum mnemonic chain account has no derivation path"
                }
                val seed = EthereumSeedFactory.deriveSeed32(
                    mnemonicWords = mnemonic.words,
                    password = exactPath.password
                ).seed
                EthereumKeypairFactory.generate(
                    seed = seed,
                    junctions = exactPath.junctions
                )
            }
        }
    }

    override fun recoverFromSeed(
        seed: ByteArray,
        decodedPath: JunctionDecoder.DecodeResult?,
        cryptoType: CryptoType,
        identityKind: ChainAccountIdentityKind,
        mnemonicDerived: Boolean
    ): FearlessKeypair {
        return when (identityKind) {
            ChainAccountIdentityKind.SUBSTRATE ->
                SubstrateKeypairFactory.generate(
                    encryptionType = cryptoType.toEncryptionType(),
                    seed = seed,
                    junctions = decodedPath?.junctions.orEmpty()
                )

            ChainAccountIdentityKind.ETHEREUM -> {
                if (mnemonicDerived) {
                    val exactPath = checkNotNull(decodedPath) {
                        "An Ethereum mnemonic chain account has no derivation path"
                    }
                    EthereumKeypairFactory.generate(
                        seed = seed,
                        junctions = exactPath.junctions
                    )
                } else {
                    // Historical direct-seed and JSON imports stored a private
                    // ECDSA scalar here even when they also persisted the
                    // default BIP-32 path.
                    EthereumKeypairFactory.createWithPrivateKey(seed)
                }
            }
        }
    }
}

private fun ensureLocal(condition: Boolean, message: String) {
    if (!condition) {
        throw ChainAccountSecretCorruptionException(message)
    }
}

private fun ensurePublicIdentity(condition: Boolean, message: String) {
    if (!condition) {
        throw WalletPublicIdentityIntegrityException(message)
    }
}

private fun CryptoType.toEncryptionType(): EncryptionType = when (this) {
    CryptoType.SR25519 -> EncryptionType.SR25519
    CryptoType.ED25519 -> EncryptionType.ED25519
    CryptoType.ECDSA -> EncryptionType.ECDSA
}

private fun CryptoType.publicKeyBytes(): Int = when (this) {
    CryptoType.SR25519,
    CryptoType.ED25519 -> SUBSTRATE_PUBLIC_KEY_BYTES
    CryptoType.ECDSA -> ECDSA_PUBLIC_KEY_BYTES
}

private fun ByteArray.requireIdentityKind(): ChainAccountIdentityKind {
    return when (size) {
        SUBSTRATE_ACCOUNT_ID_BYTES -> ChainAccountIdentityKind.SUBSTRATE
        ETHEREUM_ADDRESS_BYTES -> ChainAccountIdentityKind.ETHEREUM
        else -> throw WalletPublicIdentityIntegrityException(
            "A chain-account identity has an invalid account-id length"
        )
    }
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

private const val SUBSTRATE_ACCOUNT_ID_BYTES = 32
private const val ETHEREUM_ADDRESS_BYTES = 20
private const val PRIVATE_KEY_BYTES = 32
private const val ETHEREUM_MNEMONIC_SEED_BYTES = 64
private const val SUBSTRATE_PUBLIC_KEY_BYTES = 32
private const val ECDSA_PUBLIC_KEY_BYTES = 33
private const val SR25519_NONCE_BYTES = 32
private const val MAX_DERIVATION_PATH_CHARS = 2_048
private val VALID_ENTROPY_LENGTHS = setOf(16, 20, 24, 28, 32)
private val ECDSA_CURVE_ORDER = BigInteger(
    "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141",
    16
)
private val PRIVATE_KEY_PROOF_MESSAGE =
    "fearless-chain-account-integrity-v1".encodeToByteArray()
