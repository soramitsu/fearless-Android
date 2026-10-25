package jp.co.soramitsu.coredb.migrations

import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecretCorruptionException
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecretValidator
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.ValidatedChainSecret
import jp.co.soramitsu.common.data.secrets.v2.requireVerifiedRecovery
import jp.co.soramitsu.common.data.secrets.v3.RecoveryAssessment
import jp.co.soramitsu.common.data.secrets.v3.RecoveryIssue
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretCorruptionException
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretValidator
import jp.co.soramitsu.common.data.secrets.v3.requireVerifiedRecovery
import jp.co.soramitsu.common.utils.deriveSeed32
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.model.SecuritySource
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.fearless_utils.scale.toHexString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Real-crypto proof for SR25519 wallets whose derivation path ends in a soft
 * junction, using the SR25519 library shipped by the app.
 *
 * schnorrkel draws a fresh random nonce for every soft junction, so released
 * builds stored a nonce that no later derivation reproduces. The validators
 * must accept these wallets by their deterministic scalar and public key and
 * keep the stored nonce verbatim. All key material is synthetic.
 */
class Sr25519SoftPathRecoveryDeviceTest {

    @Test
    fun softJunctionRepeatsScalarAndPublicKeyButDrawsAFreshNonce() {
        SOFT_PATHS.forEach { path ->
            val first = deriveLikeRelease420(path)
            val second = deriveLikeRelease420(path)

            assertArrayEquals(path, first.keypair.privateKey, second.keypair.privateKey)
            assertArrayEquals(path, first.keypair.publicKey, second.keypair.publicKey)
            assertFalse(path, first.nonce.contentEquals(second.nonce))
        }

        val hardFirst = deriveLikeRelease420(HARD_PATH)
        val hardSecond = deriveLikeRelease420(HARD_PATH)
        assertArrayEquals(hardFirst.keypair.privateKey, hardSecond.keypair.privateKey)
        assertArrayEquals(hardFirst.nonce, hardSecond.nonce)
    }

    @Test
    fun walletRootValidatorAcceptsSoftPathSecretsAndKeepsTheStoredNonce() {
        SOFT_PATHS.forEach { path ->
            val stored = deriveLikeRelease420(path)
            val encoded = SubstrateSecrets(
                substrateKeyPair = stored.keypair,
                entropy = ENTROPY,
                seed = stored.seed,
                substrateDerivationPath = path
            ).toHexString()

            val validated = WalletRootSecretValidator.validateSubstrate(
                encoded = encoded,
                expectedPublicKey = stored.keypair.publicKey,
                expectedCryptoType = CryptoType.SR25519,
                expectedAccountId = stored.keypair.publicKey.substrateAccountId()
            )

            assertEquals(path, RecoveryAssessment.Verified, validated.assessment)
            assertEquals(path, encoded, validated.canonical)
            assertArrayEquals(
                path,
                stored.nonce,
                SubstrateSecrets.read(validated.canonical)[SubstrateSecrets.SubstrateKeypair][KeyPairSchema.Nonce]
            )

            assertEquals(
                path,
                RecoveryAssessment.Verified,
                WalletRootSecretValidator.assessLegacySubstrateSource(
                    source = SecuritySource.Specified.Mnemonic(
                        seed = stored.seed,
                        keypair = stored.keypair,
                        mnemonic = MNEMONIC.words,
                        derivationPath = path
                    ),
                    expectedPublicKey = stored.keypair.publicKey,
                    expectedCryptoType = CryptoType.SR25519,
                    expectedAccountId = stored.keypair.publicKey.substrateAccountId()
                )
            )
        }
    }

    @Test
    fun chainAccountValidatorAcceptsSoftPathSecretsAndKeepsTheStoredNonce() {
        SOFT_PATHS.forEach { path ->
            val stored = deriveLikeRelease420(path)
            val encoded = ChainAccountSecrets(
                keyPair = stored.keypair,
                entropy = ENTROPY,
                seed = stored.seed,
                derivationPath = path
            ).toHexString()
            val accountId = stored.keypair.publicKey.substrateAccountId()

            listOf(CryptoType.SR25519, null).forEach { cryptoType ->
                val validated = ChainAccountSecretValidator.validate(
                    encoded = encoded,
                    expectedAccountId = accountId,
                    expectedPublicKey = stored.keypair.publicKey.takeIf { cryptoType != null },
                    expectedCryptoType = cryptoType
                )

                assertEquals(path, ValidatedChainSecret(encoded, RecoveryAssessment.Verified), validated)
            }
        }
    }

    @Test
    fun softPathSecretsRecordedWithAnotherJunctionAreStillRejected() {
        val stored = deriveLikeRelease420("//polkadot/0")
        val wrongPath = "//polkadot/1"
        val rootEncoded = SubstrateSecrets(
            substrateKeyPair = stored.keypair,
            entropy = ENTROPY,
            seed = stored.seed,
            substrateDerivationPath = wrongPath
        ).toHexString()
        val chainEncoded = ChainAccountSecrets(
            keyPair = stored.keypair,
            seed = stored.seed,
            derivationPath = wrongPath
        ).toHexString()
        val accountId = stored.keypair.publicKey.substrateAccountId()

        // The recorded path re-derives another key, so the phrase and seed
        // are not exportable; the stored keypair itself still signs.
        assertEquals(
            RecoveryAssessment.Unverified(
                setOf(RecoveryIssue.ENTROPY_MISMATCH, RecoveryIssue.SEED_MISMATCH)
            ),
            WalletRootSecretValidator.validateSubstrate(
                encoded = rootEncoded,
                expectedPublicKey = stored.keypair.publicKey,
                expectedCryptoType = CryptoType.SR25519,
                expectedAccountId = accountId
            ).assessment
        )
        assertThrows(WalletRootSecretCorruptionException::class.java) {
            WalletRootSecretValidator.validateSubstrate(
                encoded = rootEncoded,
                expectedPublicKey = stored.keypair.publicKey,
                expectedCryptoType = CryptoType.SR25519,
                expectedAccountId = accountId
            ).requireVerifiedRecovery()
        }
        assertEquals(
            rootEncoded,
            WalletRootSecretValidator.validateSubstrateAndSanitize(
                encoded = rootEncoded,
                expectedPublicKey = stored.keypair.publicKey,
                expectedCryptoType = CryptoType.SR25519,
                expectedAccountId = accountId
            )
        )
        assertThrows(ChainAccountSecretCorruptionException::class.java) {
            ChainAccountSecretValidator.validate(
                encoded = chainEncoded,
                expectedAccountId = accountId,
                expectedPublicKey = stored.keypair.publicKey,
                expectedCryptoType = CryptoType.SR25519
            ).requireVerifiedRecovery()
        }
        assertEquals(
            chainEncoded,
            ChainAccountSecretValidator.validateAndSanitize(
                encoded = chainEncoded,
                expectedAccountId = accountId,
                expectedPublicKey = stored.keypair.publicKey,
                expectedCryptoType = CryptoType.SR25519
            )
        )
    }

    /** Derives secrets exactly like the 4.2.0 mnemonic import did. */
    private fun deriveLikeRelease420(path: String): SoftPathFixture {
        val decodedPath = SubstrateJunctionDecoder.decode(path)
        val seed = SubstrateSeedFactory.deriveSeed32(
            mnemonicWords = MNEMONIC.words,
            password = decodedPath.password
        ).seed
        val keypair = SubstrateKeypairFactory.generate(
            encryptionType = EncryptionType.SR25519,
            seed = seed,
            junctions = decodedPath.junctions
        ) as Sr25519Keypair
        return SoftPathFixture(seed = seed, keypair = keypair)
    }

    private class SoftPathFixture(
        val seed: ByteArray,
        val keypair: Sr25519Keypair
    ) {
        val nonce: ByteArray get() = keypair.nonce
    }

    private companion object {
        val SOFT_PATHS = listOf("/0", "//polkadot/0", "//kusama//staking/1///p@ss")
        const val HARD_PATH = "//polkadot//0///p@ss"

        val ENTROPY = ByteArray(16) { (it * 5 + 9).toByte() }
        val MNEMONIC = MnemonicCreator.fromEntropy(ENTROPY.copyOf())
    }
}
