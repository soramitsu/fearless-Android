package jp.co.soramitsu.common.data.secrets.v2

import java.math.BigInteger
import jp.co.soramitsu.common.data.secrets.WalletSecretScalePreflight
import jp.co.soramitsu.common.data.secrets.v1.Keypair
import jp.co.soramitsu.common.data.secrets.v3.RecoveryAssessment
import jp.co.soramitsu.common.data.secrets.v3.RecoveryIssue
import jp.co.soramitsu.common.data.secrets.v3.recoveryAssessment
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
import jp.co.soramitsu.fearless_utils.encrypt.junction.JunctionType
import jp.co.soramitsu.fearless_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair as FearlessKeypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.encrypt.seed.ethereum.EthereumSeedFactory
import jp.co.soramitsu.fearless_utils.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
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
 *
 * Signing integrity proves that the stored keypair is the durable identity;
 * only a failure there is a [ChainAccountSecretCorruptionException]. Recovery
 * binding re-derives the keypair from the stored entropy, seed and path and is
 * reported as a [RecoveryAssessment], never as corruption.
 */
interface ChainAccountSecretValidation {

    /**
     * Signing integrity only. Returns the canonical re-encoding without
     * running any recovery derivation.
     */
    fun validateAndSanitize(
        encoded: String,
        expectedAccountId: ByteArray,
        expectedPublicKey: ByteArray?,
        expectedCryptoType: CryptoType?
    ): String

    /**
     * Signing integrity plus the recovery assessment, for export, backup and
     * any caller that derives something new from the recovery material.
     */
    fun validate(
        encoded: String,
        expectedAccountId: ByteArray,
        expectedPublicKey: ByteArray?,
        expectedCryptoType: CryptoType?
    ): ValidatedChainSecret
}

/**
 * A chain-account secret that passed signing integrity, re-encoded
 * canonically in memory, together with the assessment of its recovery
 * material.
 */
data class ValidatedChainSecret(
    val canonical: String,
    val assessment: RecoveryAssessment
)

/**
 * Strict form for callers that use the recovery material itself. Returns the
 * canonical payload only when every stored representation re-derives the
 * signing key.
 */
fun ValidatedChainSecret.requireVerifiedRecovery(): String {
    val unverified = assessment as? RecoveryAssessment.Unverified
    if (unverified != null) {
        throw ChainAccountSecretCorruptionException(
            "Chain-account recovery material does not re-derive its keypair: " +
                unverified.issues.sorted().joinToString()
        )
    }
    return canonical
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
     * Returns a canonical payload after validating public identity and
     * private-key ownership. Recovery material is kept but not re-derived.
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

    /**
     * Validates like [validateAndSanitize] and also assesses whether every
     * recovery representation re-derives the signing keypair.
     */
    override fun validate(
        encoded: String,
        expectedAccountId: ByteArray,
        expectedPublicKey: ByteArray?,
        expectedCryptoType: CryptoType?
    ): ValidatedChainSecret {
        return engine.validate(
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
        return validateSigning(
            encoded = encoded,
            expectedAccountId = expectedAccountId,
            expectedPublicKey = expectedPublicKey,
            expectedCryptoType = expectedCryptoType
        ).secrets.toHexString()
    }

    override fun validate(
        encoded: String,
        expectedAccountId: ByteArray,
        expectedPublicKey: ByteArray?,
        expectedCryptoType: CryptoType?
    ): ValidatedChainSecret {
        val signing = validateSigning(
            encoded = encoded,
            expectedAccountId = expectedAccountId,
            expectedPublicKey = expectedPublicKey,
            expectedCryptoType = expectedCryptoType
        )
        // Encode before any recovery derivation can touch the decoded arrays.
        val canonical = signing.secrets.toHexString()
        val entropy = signing.secrets[ChainAccountSecrets.Entropy]
        val seed = signing.secrets[ChainAccountSecrets.Seed]
        val issues = recoveryIssues(
            entropy = entropy,
            seed = seed,
            derivationPath = signing.secrets[ChainAccountSecrets.DerivationPath],
            cryptoType = signing.cryptoType,
            identityKind = signing.identityKind,
            expectedKeypair = signing.keypair
        )
        return ValidatedChainSecret(
            canonical = canonical,
            assessment = recoveryAssessment(
                hasRecoveryMaterial = entropy != null || seed != null,
                issues = issues
            )
        )
    }

    /**
     * Signing integrity only. A path is retained only while entropy or a seed
     * exists, because on its own it cannot prove any recovery relationship.
     */
    private fun validateSigning(
        encoded: String,
        expectedAccountId: ByteArray,
        expectedPublicKey: ByteArray?,
        expectedCryptoType: CryptoType?
    ): SigningSecret {
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
        return SigningSecret(
            secrets = ChainAccountSecrets(
                keyPair = keypair,
                entropy = entropy,
                seed = seed,
                derivationPath = secrets[ChainAccountSecrets.DerivationPath]
                    .takeIf { entropy != null || seed != null }
            ),
            keypair = keypair,
            cryptoType = cryptoType,
            identityKind = identityKind
        )
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
        val canonicalHex = WalletSecretScalePreflight.normalizePersistedHex(encoded)
        WalletSecretScalePreflight.requireChainAccountV2(canonicalHex)
        ChainAccountSecrets.read(canonicalHex)
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

    private fun recoveryIssues(
        entropy: ByteArray?,
        seed: ByteArray?,
        derivationPath: String?,
        cryptoType: CryptoType,
        identityKind: ChainAccountIdentityKind,
        expectedKeypair: FearlessKeypair
    ): Set<RecoveryIssue> {
        if (entropy == null && seed == null) {
            return emptySet()
        }
        val expectedSeedBytes = identityKind.expectedSeedBytes(mnemonicDerived = entropy != null)
        val issues = materialSizeIssues(entropy, seed, expectedSeedBytes).toMutableSet()

        val path = decodePath(
            derivationPath = derivationPath,
            identityKind = identityKind
        )
        if (path is RecoveryPath.Unusable) {
            // A path that cannot be decoded binds nothing; deriving without
            // it would only report every representation as a mismatch.
            return issues + path.issue
        }
        val decodedPath = (path as? RecoveryPath.Decoded)?.result
        if (
            identityKind == ChainAccountIdentityKind.ETHEREUM &&
            entropy != null &&
            decodedPath == null
        ) {
            // An Ethereum mnemonic account derives nothing without its path.
            return issues + RecoveryIssue.ETH_ENTROPY_WITHOUT_PATH
        }
        if (identityKind.derivesNoSoftJunction(cryptoType) && decodedPath.hasSoftJunction()) {
            // Only SR25519 derives soft junctions (`/0`). No seed can have
            // produced an ED25519 or ECDSA key through this path, and asking
            // the key factory would fail the same way on every start.
            return issues + RecoveryIssue.PATH_MALFORMED
        }

        entropy?.takeIf { it.size in VALID_ENTROPY_LENGTHS }?.let {
            val recovered = performCryptography("entropy recovery") {
                cryptography.recoverFromEntropy(
                    entropy = it,
                    decodedPath = decodedPath,
                    cryptoType = cryptoType,
                    identityKind = identityKind
                )
            }
            if (!recoveredKeypairMatches(recovered, expectedKeypair, cryptoType)) {
                issues += RecoveryIssue.ENTROPY_MISMATCH
            }
        }

        seed?.takeIf { it.size == expectedSeedBytes }?.let {
            val recovered = performCryptography("seed recovery") {
                cryptography.recoverFromSeed(
                    seed = it,
                    decodedPath = decodedPath,
                    cryptoType = cryptoType,
                    identityKind = identityKind,
                    mnemonicDerived = entropy != null
                )
            }
            if (!recoveredKeypairMatches(recovered, expectedKeypair, cryptoType)) {
                issues += RecoveryIssue.SEED_MISMATCH
            }
        }
        return issues
    }

    private fun decodePath(
        derivationPath: String?,
        identityKind: ChainAccountIdentityKind
    ): RecoveryPath {
        val nonEmptyPath = derivationPath?.takeIf(String::isNotEmpty)
            ?: return RecoveryPath.Absent
        if (nonEmptyPath.length > MAX_DERIVATION_PATH_CHARS) {
            return RecoveryPath.Unusable(RecoveryIssue.PATH_OVERSIZED)
        }
        return try {
            RecoveryPath.Decoded(
                cryptography.decodeDerivationPath(
                    derivationPath = nonEmptyPath,
                    identityKind = identityKind
                )
            )
        } catch (_: JunctionDecoder.DecodingError) {
            RecoveryPath.Unusable(RecoveryIssue.PATH_MALFORMED)
        } catch (_: BIP32JunctionDecoder.DecodingError) {
            // A junction that is not a BIP32 index; not a JunctionDecoder error.
            RecoveryPath.Unusable(RecoveryIssue.PATH_MALFORMED)
        } catch (failure: WalletSecureStorageUnavailableException) {
            throw failure
        } catch (failure: Exception) {
            throw WalletSecureStorageUnavailableException(
                "Chain-account cryptography is unavailable during derivation-path decoding",
                failure
            )
        }
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

private fun recoveredKeypairMatches(
    recovered: FearlessKeypair,
    expectedKeypair: FearlessKeypair,
    cryptoType: CryptoType
): Boolean {
    // schnorrkel draws a fresh random nonce for every soft junction
    // (`/0`, `//polkadot/0`), so an SR25519 nonce cannot be re-derived.
    // Any 32-byte nonce signs correctly: the stored one is kept verbatim
    // and only its shape is checked, by validateKeypair.
    val recoveredNonce = (recovered as? Sr25519Keypair)?.nonce
    val expectedNonce = (expectedKeypair as? Sr25519Keypair)?.nonce
    return recovered.privateKey.contentEquals(expectedKeypair.privateKey) &&
        recovered.publicKey.contentEquals(expectedKeypair.publicKey) &&
        (
            cryptoType == CryptoType.SR25519 ||
                recoveredNonce.contentEqualsNullable(expectedNonce)
            )
}

/** A mnemonic-derived Ethereum chain account stores the 64-byte BIP39 seed. */
private fun ChainAccountIdentityKind.expectedSeedBytes(mnemonicDerived: Boolean): Int =
    if (this == ChainAccountIdentityKind.ETHEREUM && mnemonicDerived) {
        ETHEREUM_MNEMONIC_SEED_BYTES
    } else {
        PRIVATE_KEY_BYTES
    }

/** Stored material whose size alone shows that it re-derives no key. */
private fun materialSizeIssues(
    entropy: ByteArray?,
    seed: ByteArray?,
    expectedSeedBytes: Int
): Set<RecoveryIssue> = buildSet {
    if (entropy != null && entropy.size !in VALID_ENTROPY_LENGTHS) {
        add(RecoveryIssue.ENTROPY_LENGTH)
    }
    if (seed != null && seed.size != expectedSeedBytes) {
        add(RecoveryIssue.SEED_LENGTH)
    }
}

/** Only SR25519 Substrate keys derive soft junctions (`/0`). */
private fun ChainAccountIdentityKind.derivesNoSoftJunction(cryptoType: CryptoType): Boolean =
    this == ChainAccountIdentityKind.SUBSTRATE && cryptoType != CryptoType.SR25519

private fun JunctionDecoder.DecodeResult?.hasSoftJunction(): Boolean =
    this?.junctions.orEmpty().any { it.type == JunctionType.SOFT }

private class SigningSecret(
    val secrets: EncodableStruct<ChainAccountSecrets>,
    val keypair: FearlessKeypair,
    val cryptoType: CryptoType,
    val identityKind: ChainAccountIdentityKind
)

private sealed interface RecoveryPath {

    data object Absent : RecoveryPath

    class Decoded(val result: JunctionDecoder.DecodeResult) : RecoveryPath

    class Unusable(val issue: RecoveryIssue) : RecoveryPath
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
