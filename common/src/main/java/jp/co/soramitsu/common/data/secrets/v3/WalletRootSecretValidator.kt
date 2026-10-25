package jp.co.soramitsu.common.data.secrets.v3

import java.math.BigInteger
import jp.co.soramitsu.common.data.secrets.WalletSecretScalePreflight
import jp.co.soramitsu.common.data.secrets.v1.Keypair
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.storage.encrypt.MAX_WALLET_SECRET_PLAINTEXT_CHARS
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageUnavailableException
import jp.co.soramitsu.common.utils.TonKeyDerivation
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
import jp.co.soramitsu.fearless_utils.encrypt.junction.JunctionType
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
 *
 * Validation has two tiers. Signing integrity decodes the payload and proves
 * that its keypair is the durable public identity; only a failure there is a
 * [WalletRootSecretCorruptionException]. Recovery binding re-derives the
 * keypair from the stored entropy, seed, mnemonic and path; a mismatch there
 * never makes a wallet unusable and is reported as a [RecoveryAssessment].
 */
interface WalletRootSecretValidation {

    /**
     * Signing integrity only. Returns the canonical re-encoding without
     * running any recovery derivation, so runtime signing reads stay cheap
     * and recovery drift can never quarantine a key that signs correctly.
     */
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

    /**
     * Signing integrity plus the recovery assessment, for export, backup and
     * any caller that derives something new from the recovery material.
     */
    fun validateSubstrate(
        encoded: String,
        expectedPublicKey: ByteArray,
        expectedCryptoType: CryptoType,
        expectedAccountId: ByteArray
    ): ValidatedRootSecret

    fun validateEthereum(
        encoded: String,
        expectedPublicKey: ByteArray,
        expectedAddress: ByteArray
    ): ValidatedRootSecret

    fun validateTon(
        encoded: String,
        expectedPublicKey: ByteArray
    ): ValidatedRootSecret
}

fun interface LegacySubstrateSecretValidation {

    /**
     * Proves signing integrity of a retained pre-v2 source and assesses its
     * recovery material.
     */
    fun validate(
        source: SecuritySource,
        expectedPublicKey: ByteArray,
        expectedCryptoType: CryptoType,
        expectedAccountId: ByteArray
    ): RecoveryAssessment
}

/**
 * Strict form for callers that use the recovery material itself: deriving a
 * new identity from it, or exporting or backing it up. Returns the canonical
 * payload only when every stored representation re-derives the signing key.
 */
fun ValidatedRootSecret.requireVerifiedRecovery(): String {
    assessment.requireVerifiedRecovery()
    return canonical
}

fun RecoveryAssessment.requireVerifiedRecovery() {
    when (this) {
        RecoveryAssessment.None,
        RecoveryAssessment.Verified -> Unit

        is RecoveryAssessment.VerifiedAsTyped -> throw WalletRootSecretCorruptionException(
            "A TON mnemonic re-derives its keypair only as typed ($variant)"
        )

        is RecoveryAssessment.Unverified -> throw WalletRootSecretCorruptionException(
            "Wallet recovery material does not re-derive its keypair: " +
                issues.sorted().joinToString()
        )
    }
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

    override fun validateSubstrate(
        encoded: String,
        expectedPublicKey: ByteArray,
        expectedCryptoType: CryptoType,
        expectedAccountId: ByteArray
    ): ValidatedRootSecret {
        return engine.validateSubstrate(
            encoded = encoded,
            expectedPublicKey = expectedPublicKey,
            expectedCryptoType = expectedCryptoType,
            expectedAccountId = expectedAccountId
        )
    }

    override fun validateEthereum(
        encoded: String,
        expectedPublicKey: ByteArray,
        expectedAddress: ByteArray
    ): ValidatedRootSecret {
        return engine.validateEthereum(
            encoded = encoded,
            expectedPublicKey = expectedPublicKey,
            expectedAddress = expectedAddress
        )
    }

    override fun validateTon(
        encoded: String,
        expectedPublicKey: ByteArray
    ): ValidatedRootSecret {
        return engine.validateTon(
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
     * Validates the retained pre-v2 fallback before it can reach a signer.
     * Unlike a public-key-only comparison, this proves private-key ownership.
     * Its recovery material is not checked here; see [assessLegacySubstrateSource].
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

    /**
     * Proves signing integrity of the retained pre-v2 fallback and assesses
     * every available recovery representation before an export flow.
     */
    fun assessLegacySubstrateSource(
        source: SecuritySource,
        expectedPublicKey: ByteArray,
        expectedCryptoType: CryptoType,
        expectedAccountId: ByteArray
    ): RecoveryAssessment {
        return engine.assessLegacySubstrateSource(
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
    ): RecoveryAssessment {
        return assessLegacySubstrateSource(
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
        return validateSubstrateSigning(
            encoded = encoded,
            expectedPublicKey = expectedPublicKey,
            expectedCryptoType = expectedCryptoType,
            expectedAccountId = expectedAccountId
        ).toHexString()
    }

    override fun validateSubstrate(
        encoded: String,
        expectedPublicKey: ByteArray,
        expectedCryptoType: CryptoType,
        expectedAccountId: ByteArray
    ): ValidatedRootSecret {
        val secrets = validateSubstrateSigning(
            encoded = encoded,
            expectedPublicKey = expectedPublicKey,
            expectedCryptoType = expectedCryptoType,
            expectedAccountId = expectedAccountId
        )
        // Encode before any recovery derivation can touch the decoded arrays.
        val canonical = secrets.toHexString()
        val entropy = secrets[SubstrateSecrets.Entropy]
        val seed = secrets[SubstrateSecrets.Seed]
        val issues = substrateRecoveryIssues(
            entropy = entropy,
            seed = seed,
            derivationPath = secrets[SubstrateSecrets.SubstrateDerivationPath],
            expectedCryptoType = expectedCryptoType,
            expectedKeypair = secrets[SubstrateSecrets.SubstrateKeypair].toKeypair()
        )
        return ValidatedRootSecret(
            canonical = canonical,
            assessment = recoveryAssessment(
                hasRecoveryMaterial = entropy != null || seed != null,
                issues = issues
            )
        )
    }

    override fun validateEthereumAndSanitize(
        encoded: String,
        expectedPublicKey: ByteArray,
        expectedAddress: ByteArray
    ): String {
        return validateEthereumSigning(
            encoded = encoded,
            expectedPublicKey = expectedPublicKey,
            expectedAddress = expectedAddress
        ).toHexString()
    }

    override fun validateEthereum(
        encoded: String,
        expectedPublicKey: ByteArray,
        expectedAddress: ByteArray
    ): ValidatedRootSecret {
        val secrets = validateEthereumSigning(
            encoded = encoded,
            expectedPublicKey = expectedPublicKey,
            expectedAddress = expectedAddress
        )
        // Encode before any recovery derivation can touch the decoded arrays.
        val canonical = secrets.toHexString()
        val entropy = secrets[EthereumSecrets.Entropy]
        val seed = secrets[EthereumSecrets.Seed]
        val issues = ethereumRecoveryIssues(
            entropy = entropy,
            storedSeed = seed,
            derivationPath = secrets[EthereumSecrets.EthereumDerivationPath],
            expectedKeypair = secrets[EthereumSecrets.EthereumKeypair].toKeypair()
        )
        return ValidatedRootSecret(
            canonical = canonical,
            assessment = recoveryAssessment(
                hasRecoveryMaterial = entropy != null || seed != null,
                issues = issues
            )
        )
    }

    override fun validateTonAndSanitize(
        encoded: String,
        expectedPublicKey: ByteArray
    ): String {
        return validateTonSigning(
            encoded = encoded,
            expectedPublicKey = expectedPublicKey
        ).toHexString()
    }

    override fun validateTon(
        encoded: String,
        expectedPublicKey: ByteArray
    ): ValidatedRootSecret {
        val secrets = validateTonSigning(
            encoded = encoded,
            expectedPublicKey = expectedPublicKey
        )
        val canonical = secrets.toHexString()
        val keypair = Keypair(
            publicKey = secrets[TonSecrets.PublicKey],
            privateKey = secrets[TonSecrets.PrivateKey]
        )
        return ValidatedRootSecret(
            canonical = canonical,
            assessment = tonRecoveryAssessment(
                seed = secrets[TonSecrets.Seed],
                expectedKeypair = keypair
            )
        )
    }

    /**
     * Signing integrity only. A path is retained only while entropy or a seed
     * exists, because on its own it cannot prove any recovery relationship.
     */
    private fun validateSubstrateSigning(
        encoded: String,
        expectedPublicKey: ByteArray,
        expectedCryptoType: CryptoType,
        expectedAccountId: ByteArray
    ): EncodableStruct<SubstrateSecrets> {
        ensureBounded(encoded)
        val secrets = decodeSubstrate(encoded)
        val keypair = secrets[SubstrateSecrets.SubstrateKeypair].toKeypair()

        validateSubstrateKeypair(
            keypair = keypair,
            expectedPublicKey = expectedPublicKey,
            expectedCryptoType = expectedCryptoType,
            expectedAccountId = expectedAccountId
        )

        val entropy = secrets[SubstrateSecrets.Entropy]
        val seed = secrets[SubstrateSecrets.Seed]
        return SubstrateSecrets(
            substrateKeyPair = keypair,
            entropy = entropy,
            seed = seed,
            substrateDerivationPath = secrets[SubstrateSecrets.SubstrateDerivationPath]
                .takeIf { entropy != null || seed != null }
        )
    }

    /**
     * Signing integrity only. An Ethereum path is meaningful only with
     * entropy; direct-import private-key signing remains intact without it.
     */
    private fun validateEthereumSigning(
        encoded: String,
        expectedPublicKey: ByteArray,
        expectedAddress: ByteArray
    ): EncodableStruct<EthereumSecrets> {
        ensureBounded(encoded)
        val secrets = decodeEthereum(encoded)
        val keypair = secrets[EthereumSecrets.EthereumKeypair].toKeypair()

        validateEthereumKeypair(
            keypair = keypair,
            expectedPublicKey = expectedPublicKey,
            expectedAddress = expectedAddress
        )

        val entropy = secrets[EthereumSecrets.Entropy]
        return EthereumSecrets(
            entropy = entropy,
            seed = secrets[EthereumSecrets.Seed],
            ethereumKeypair = keypair,
            ethereumDerivationPath = secrets[EthereumSecrets.EthereumDerivationPath]
                .takeIf { entropy != null }
        )
    }

    /**
     * Signing integrity only. The stored TON text is returned byte for byte:
     * 4.x derived the key from it exactly as typed, so it is never rewritten.
     */
    private fun validateTonSigning(
        encoded: String,
        expectedPublicKey: ByteArray
    ): EncodableStruct<TonSecrets> {
        ensureBounded(encoded)
        val secrets = decodeTon(encoded)
        validateTonKeypair(
            keypair = Keypair(
                publicKey = secrets[TonSecrets.PublicKey],
                privateKey = secrets[TonSecrets.PrivateKey]
            ),
            expectedPublicKey = expectedPublicKey
        )
        return secrets
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
    }

    fun assessLegacySubstrateSource(
        source: SecuritySource,
        expectedPublicKey: ByteArray,
        expectedCryptoType: CryptoType,
        expectedAccountId: ByteArray
    ): RecoveryAssessment {
        validateLegacySubstrateSource(
            source = source,
            expectedPublicKey = expectedPublicKey,
            expectedCryptoType = expectedCryptoType,
            expectedAccountId = expectedAccountId
        )

        val seed = (source as? WithSeed)?.seed
        val mnemonicText = (source as? WithMnemonic)?.mnemonic
        val issues = substrateRecoveryIssues(
            entropy = null,
            seed = seed,
            mnemonicText = mnemonicText,
            derivationPath = (source as? WithDerivationPath)?.derivationPath,
            expectedCryptoType = expectedCryptoType,
            expectedKeypair = source.keypair
        )
        return recoveryAssessment(
            hasRecoveryMaterial = seed != null || mnemonicText != null,
            issues = issues
        )
    }

    private fun substrateRecoveryIssues(
        entropy: ByteArray?,
        seed: ByteArray?,
        mnemonicText: String? = null,
        derivationPath: String?,
        expectedCryptoType: CryptoType,
        expectedKeypair: FearlessKeypair
    ): Set<RecoveryIssue> {
        if (entropy == null && seed == null && mnemonicText == null) {
            return emptySet()
        }
        val issues = substrateMaterialSizeIssues(entropy, seed, mnemonicText).toMutableSet()
        val readableMnemonic = mnemonicText?.takeIf {
            it.length <= MAX_SUBSTRATE_MNEMONIC_CHARS
        }

        val path = decodeRecoveryPath(
            derivationPath = derivationPath,
            operation = "Substrate derivation-path decoding",
            decode = cryptography::decodeSubstratePath
        )
        if (path is RecoveryPath.Unusable) {
            // A path that cannot be decoded binds nothing; deriving without
            // it would only report every representation as a mismatch.
            return issues + path.issue
        }
        val decodedPath = (path as? RecoveryPath.Decoded)?.result
        if (expectedCryptoType != CryptoType.SR25519 && decodedPath.hasSoftJunction()) {
            // Only SR25519 derives soft junctions (`/0`). No seed can have
            // produced an ED25519 or ECDSA key through this path, and asking
            // the key factory would fail the same way on every start.
            return issues + RecoveryIssue.PATH_MALFORMED
        }

        entropy?.takeIf { it.size in VALID_ENTROPY_LENGTHS }?.let {
            val recovered = performCryptography("Substrate entropy recovery") {
                cryptography.recoverSubstrateFromEntropy(
                    entropy = it,
                    decodedPath = decodedPath,
                    cryptoType = expectedCryptoType
                )
            }
            if (!recoveredSubstrateMatches(recovered, expectedKeypair, expectedCryptoType)) {
                issues += RecoveryIssue.ENTROPY_MISMATCH
            }
        }
        readableMnemonic
            ?.let { substrateMnemonicIssue(it, decodedPath, expectedCryptoType, expectedKeypair) }
            ?.let(issues::add)

        seed?.takeIf { it.size == PRIVATE_KEY_BYTES }?.let {
            val recovered = performCryptography("Substrate seed recovery") {
                cryptography.recoverSubstrateFromSeed(
                    seed = it,
                    decodedPath = decodedPath,
                    cryptoType = expectedCryptoType
                )
            }
            if (!recoveredSubstrateMatches(recovered, expectedKeypair, expectedCryptoType)) {
                issues += RecoveryIssue.SEED_MISMATCH
            }
        }
        return issues
    }

    /**
     * A BIP39 phrase derives through its entropy, so the spacing and
     * separators of the stored text never change the key (unlike a TON
     * phrase). It is judged only by whether its words re-derive the keypair,
     * read the same way every consumer of the text reads them.
     */
    private fun substrateMnemonicIssue(
        mnemonicText: String,
        decodedPath: JunctionDecoder.DecodeResult?,
        expectedCryptoType: CryptoType,
        expectedKeypair: FearlessKeypair
    ): RecoveryIssue? {
        val recovered = try {
            cryptography.recoverSubstrateFromMnemonic(
                mnemonicText = mnemonicText,
                decodedPath = decodedPath,
                cryptoType = expectedCryptoType
            )
        } catch (_: Bip39Exception) {
            return unreadableSubstrateMnemonicIssue(mnemonicText)
        } catch (failure: WalletSecureStorageUnavailableException) {
            throw failure
        } catch (failure: Exception) {
            throw operationalFailure(
                operation = "Substrate mnemonic recovery",
                cause = failure
            )
        }
        return RecoveryIssue.MNEMONIC_MISMATCH.takeUnless {
            recoveredSubstrateMatches(recovered, expectedKeypair, expectedCryptoType)
        }
    }

    /** Says why a stored BIP39 text could not be read as a phrase. */
    private fun unreadableSubstrateMnemonicIssue(mnemonicText: String): RecoveryIssue {
        val wordCount = mnemonicText.trim().split(BIP39_WORD_DELIMITERS).size
        return if (wordCount in VALID_MNEMONIC_WORD_COUNTS) {
            RecoveryIssue.MNEMONIC_INVALID
        } else {
            RecoveryIssue.MNEMONIC_WORDCOUNT
        }
    }

    private fun ethereumRecoveryIssues(
        entropy: ByteArray?,
        storedSeed: ByteArray?,
        derivationPath: String?,
        expectedKeypair: FearlessKeypair
    ): Set<RecoveryIssue> {
        val issues = mutableSetOf<RecoveryIssue>()
        storedSeed?.let {
            when {
                it.size != PRIVATE_KEY_BYTES -> issues += RecoveryIssue.SEED_LENGTH
                !it.contentEquals(expectedKeypair.privateKey) ->
                    issues += RecoveryIssue.SEED_MISMATCH
            }
        }
        entropy ?: return issues

        if (entropy.size !in VALID_ENTROPY_LENGTHS) {
            issues += RecoveryIssue.ENTROPY_LENGTH
        }
        val path = decodeRecoveryPath(
            derivationPath = derivationPath,
            operation = "Ethereum derivation-path decoding",
            decode = cryptography::decodeEthereumPath
        )
        when (path) {
            RecoveryPath.Absent -> issues += RecoveryIssue.ETH_ENTROPY_WITHOUT_PATH
            is RecoveryPath.Unusable -> issues += path.issue
            is RecoveryPath.Decoded -> if (
                entropy.size in VALID_ENTROPY_LENGTHS &&
                !ethereumEntropyMatches(entropy, path.result, expectedKeypair)
            ) {
                issues += RecoveryIssue.ENTROPY_MISMATCH
            }
        }
        return issues
    }

    private fun ethereumEntropyMatches(
        entropy: ByteArray,
        decodedPath: JunctionDecoder.DecodeResult,
        expectedKeypair: FearlessKeypair
    ): Boolean {
        val recovered = performCryptography("Ethereum entropy recovery") {
            cryptography.recoverEthereumFromEntropy(
                entropy = entropy,
                decodedPath = decodedPath
            )
        }
        return recovered.privateKey.contentEquals(expectedKeypair.privateKey) &&
            recovered.publicKey.contentEquals(expectedKeypair.publicKey)
    }

    /**
     * 4.x stored the TON phrase exactly as typed and derived the key from
     * `text.split(" ")`, so trailing, leading or double spaces and capitals
     * are part of that key. The exact text is tried first; normalized copies
     * are tried only to recognise a key, never to rewrite the stored text.
     */
    private fun tonRecoveryAssessment(
        seed: ByteArray,
        expectedKeypair: FearlessKeypair
    ): RecoveryAssessment {
        if (seed.isEmpty() || seed.size > MAX_TON_SEED_BYTES) {
            return RecoveryAssessment.Unverified(setOf(RecoveryIssue.MNEMONIC_WORDCOUNT))
        }
        val mnemonicText = try {
            seed.decodeToString(throwOnInvalidSequence = true)
        } catch (_: CharacterCodingException) {
            return RecoveryAssessment.Unverified(setOf(RecoveryIssue.MNEMONIC_NOT_UTF8))
        }
        if (mnemonicText.length > MAX_TON_MNEMONIC_CHARS) {
            return RecoveryAssessment.Unverified(setOf(RecoveryIssue.MNEMONIC_WORDCOUNT))
        }

        val asTyped = mnemonicText.split(' ')
        val normalized = mnemonicText.trim().split(MNEMONIC_WHITESPACE)
        val lowercased = normalized.map(String::lowercase)
        val isCanonical = mnemonicText == lowercased.joinToString(" ")
        if (recoversTon(asTyped, expectedKeypair)) {
            return if (isCanonical) {
                RecoveryAssessment.Verified
            } else {
                RecoveryAssessment.VerifiedAsTyped(TonPhraseVariant.AS_TYPED)
            }
        }
        val recognisedOnlyNormalized = sequenceOf(normalized, lowercased)
            .distinct()
            .filter { it != asTyped }
            .any { recoversTon(it, expectedKeypair) }
        if (recognisedOnlyNormalized) {
            return RecoveryAssessment.VerifiedAsTyped(TonPhraseVariant.NORMALIZED)
        }

        val issues = mutableSetOf(RecoveryIssue.MNEMONIC_MISMATCH)
        if (!isCanonical) {
            issues += RecoveryIssue.MNEMONIC_NONCANONICAL
        }
        if (normalized.size !in VALID_MNEMONIC_WORD_COUNTS) {
            issues += RecoveryIssue.MNEMONIC_WORDCOUNT
        }
        return RecoveryAssessment.Unverified(issues)
    }

    private fun recoversTon(
        mnemonicWords: List<String>,
        expectedKeypair: FearlessKeypair
    ): Boolean {
        val recovered = performCryptography("TON mnemonic recovery") {
            cryptography.recoverTonFromMnemonic(mnemonicWords)
        }
        if (recovered.privateKey.contentEquals(expectedKeypair.privateKey) &&
            recovered.publicKey.contentEquals(expectedKeypair.publicKey)
        ) return true

        val mnemonicText = mnemonicWords.joinToString(" ")
        val validBip39 = try {
            MnemonicCreator.fromWords(mnemonicText).words == mnemonicText
        } catch (_: Bip39Exception) {
            false
        }
        return validBip39 && performCryptography("Universal TON mnemonic recovery") {
            TonKeyDerivation.matchesUniversalMnemonicKeypair(
                mnemonicText, expectedKeypair.privateKey, expectedKeypair.publicKey
            )
        }
    }

    private fun decodeRecoveryPath(
        derivationPath: String?,
        operation: String,
        decode: (String) -> JunctionDecoder.DecodeResult
    ): RecoveryPath {
        val nonEmptyPath = derivationPath?.takeIf(String::isNotEmpty)
            ?: return RecoveryPath.Absent
        if (nonEmptyPath.length > MAX_DERIVATION_PATH_CHARS) {
            return RecoveryPath.Unusable(RecoveryIssue.PATH_OVERSIZED)
        }
        return try {
            RecoveryPath.Decoded(decode(nonEmptyPath))
        } catch (_: JunctionDecoder.DecodingError) {
            RecoveryPath.Unusable(RecoveryIssue.PATH_MALFORMED)
        } catch (_: BIP32JunctionDecoder.DecodingError) {
            // A junction that is not a BIP32 index; not a JunctionDecoder error.
            RecoveryPath.Unusable(RecoveryIssue.PATH_MALFORMED)
        } catch (failure: WalletSecureStorageUnavailableException) {
            throw failure
        } catch (failure: Exception) {
            throw operationalFailure(operation, failure)
        }
    }

    private fun decodeSubstrate(encoded: String) = try {
        val canonicalHex = WalletSecretScalePreflight.normalizePersistedHex(encoded)
        WalletSecretScalePreflight.requireSubstrateV3(canonicalHex)
        SubstrateSecrets.read(canonicalHex)
    } catch (failure: Exception) {
        throw WalletRootSecretCorruptionException(
            "A Substrate secret payload is malformed",
            failure
        )
    }

    private fun decodeEthereum(encoded: String) = try {
        val canonicalHex = WalletSecretScalePreflight.normalizePersistedHex(encoded)
        WalletSecretScalePreflight.requireEthereumV3(canonicalHex)
        EthereumSecrets.read(canonicalHex)
    } catch (failure: Exception) {
        throw WalletRootSecretCorruptionException(
            "An Ethereum secret payload is malformed",
            failure
        )
    }

    private fun decodeTon(encoded: String) = try {
        val canonicalHex = WalletSecretScalePreflight.normalizePersistedHex(encoded)
        WalletSecretScalePreflight.requireTonV3(canonicalHex)
        TonSecrets.read(canonicalHex)
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

private sealed interface RecoveryPath {

    data object Absent : RecoveryPath

    class Decoded(val result: JunctionDecoder.DecodeResult) : RecoveryPath

    class Unusable(val issue: RecoveryIssue) : RecoveryPath
}

private fun recoveredSubstrateMatches(
    recovered: FearlessKeypair,
    expected: FearlessKeypair,
    cryptoType: CryptoType
): Boolean {
    // schnorrkel draws a fresh random nonce for every soft junction
    // (`/0`, `//polkadot/0`), so an SR25519 nonce cannot be re-derived.
    // Any 32-byte nonce signs correctly: the stored one is kept verbatim
    // and only its shape is checked, by validateSubstrateKeypair.
    val recoveredNonce = (recovered as? Sr25519Keypair)?.nonce
    val expectedNonce = (expected as? Sr25519Keypair)?.nonce
    return recovered.privateKey.contentEquals(expected.privateKey) &&
        recovered.publicKey.contentEquals(expected.publicKey) &&
        (
            cryptoType == CryptoType.SR25519 ||
                recoveredNonce.contentEqualsNullable(expectedNonce)
            )
}

/** Stored material whose size alone shows that it re-derives no key. */
private fun substrateMaterialSizeIssues(
    entropy: ByteArray?,
    seed: ByteArray?,
    mnemonicText: String?
): Set<RecoveryIssue> = buildSet {
    if (entropy != null && entropy.size !in VALID_ENTROPY_LENGTHS) {
        add(RecoveryIssue.ENTROPY_LENGTH)
    }
    if (seed != null && seed.size != PRIVATE_KEY_BYTES) {
        add(RecoveryIssue.SEED_LENGTH)
    }
    if (mnemonicText != null && mnemonicText.length > MAX_SUBSTRATE_MNEMONIC_CHARS) {
        add(RecoveryIssue.MNEMONIC_WORDCOUNT)
    }
}

private fun JunctionDecoder.DecodeResult?.hasSoftJunction(): Boolean =
    this?.junctions.orEmpty().any { it.type == JunctionType.SOFT }

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
private val MNEMONIC_WHITESPACE = Regex("\\s+")
// The separators MnemonicCreator accepts between BIP39 words.
private val BIP39_WORD_DELIMITERS = Regex("[\\s,]+")
private val ECDSA_CURVE_ORDER = BigInteger(
    "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141",
    16
)
private val PRIVATE_KEY_PROOF_MESSAGE =
    "fearless-wallet-root-secret-integrity-v1".encodeToByteArray()
