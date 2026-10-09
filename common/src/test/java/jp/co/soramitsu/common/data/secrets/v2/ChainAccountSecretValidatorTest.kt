package jp.co.soramitsu.common.data.secrets.v2

import java.security.ProviderException
import jp.co.soramitsu.common.data.secrets.v1.Keypair
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageUnavailableException
import jp.co.soramitsu.common.utils.deriveSeed32
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.junction.JunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair as FearlessKeypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.fearless_utils.scale.toHexString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class ChainAccountSecretValidatorTest {

    @Test
    fun directEcdsaSigningKeyRemainsUsableWhileUnboundPathIsRemoved() {
        val keypair = directEcdsaKeypair(1)
        val encoded = ChainAccountSecrets(
            keyPair = keypair,
            derivationPath = "//unbound"
        ).toHexString()

        val canonical = ChainAccountSecretValidator.validateAndSanitize(
            encoded = encoded,
            expectedAccountId = keypair.publicKey.substrateAccountId(),
            expectedPublicKey = keypair.publicKey,
            expectedCryptoType = CryptoType.ECDSA
        )

        val secrets = ChainAccountSecrets.read(canonical)
        assertNull(secrets[ChainAccountSecrets.DerivationPath])
        assertArrayEquals(
            keypair.privateKey,
            secrets[ChainAccountSecrets.Keypair][KeyPairSchema.PrivateKey]
        )
    }

    @Test
    fun samePublicKeyWithWrongEcdsaPrivateKeyIsRejected() {
        val expected = directEcdsaKeypair(2)
        val other = directEcdsaKeypair(3)
        val forged = Keypair(
            publicKey = expected.publicKey,
            privateKey = other.privateKey
        )

        assertThrows(ChainAccountSecretCorruptionException::class.java) {
            ChainAccountSecretValidator.validateKeypair(
                keypair = forged,
                expectedAccountId = expected.publicKey.substrateAccountId(),
                expectedPublicKey = expected.publicKey,
                expectedCryptoType = CryptoType.ECDSA
            )
        }
    }

    @Test
    fun inconsistentDatabasePublicKeyAndAccountIdIsNotPayloadCorruption() {
        val keypair = directEcdsaKeypair(4)

        assertThrows(WalletPublicIdentityIntegrityException::class.java) {
            ChainAccountSecretValidator.validateKeypair(
                keypair = keypair,
                expectedAccountId = ByteArray(32) { 0x5a },
                expectedPublicKey = keypair.publicKey,
                expectedCryptoType = CryptoType.ECDSA
            )
        }
    }

    @Test
    fun boundEntropySeedAndPathMustRecoverTheStoredEcdsaKeypair() {
        val entropy = ByteArray(16) { (it * 5 + 7).toByte() }
        val path = "//hard///password"
        val decodedPath = SubstrateJunctionDecoder.decode(path)
        val mnemonic = MnemonicCreator.fromEntropy(entropy)
        val seed = SubstrateSeedFactory.deriveSeed32(
            mnemonicWords = mnemonic.words,
            password = decodedPath.password
        ).seed
        val keypair = SubstrateKeypairFactory.generate(
            encryptionType = EncryptionType.ECDSA,
            seed = seed,
            junctions = decodedPath.junctions
        )
        val encoded = ChainAccountSecrets(
            keyPair = keypair,
            entropy = entropy,
            seed = seed,
            derivationPath = path
        ).toHexString()

        val canonical = ChainAccountSecretValidator.validateAndSanitize(
            encoded = encoded,
            expectedAccountId = keypair.publicKey.substrateAccountId(),
            expectedPublicKey = keypair.publicKey,
            expectedCryptoType = CryptoType.ECDSA
        )

        val secrets = ChainAccountSecrets.read(canonical)
        assertEquals(path, secrets[ChainAccountSecrets.DerivationPath])
        assertArrayEquals(seed, secrets[ChainAccountSecrets.Seed])
    }

    @Test
    fun srEdAndEcdsaOwnershipProviderFailuresAreGlobalAndRetainCause() {
        val fixtures = listOf(
            CryptoFixture(
                cryptoType = CryptoType.SR25519,
                keypair = Keypair(
                    publicKey = ByteArray(32) { (it + 1).toByte() },
                    privateKey = ByteArray(32) { (it + 2).toByte() },
                    nonce = ByteArray(32) { (it + 3).toByte() }
                )
            ),
            CryptoFixture(
                cryptoType = CryptoType.ED25519,
                keypair = Keypair(
                    publicKey = ByteArray(32) { (it + 4).toByte() },
                    privateKey = ByteArray(32) { (it + 5).toByte() }
                )
            ),
            CryptoFixture(
                cryptoType = CryptoType.ECDSA,
                keypair = directEcdsaKeypair(6)
            )
        )

        fixtures.forEach { fixture ->
            val providerFailure = ProviderException(
                "Injected ${fixture.cryptoType} ownership failure"
            )
            val engine = ChainAccountSecretValidationEngine(
                TestCryptography(
                    proveOwnership = { _, _ -> throw providerFailure }
                )
            )
            val encoded = ChainAccountSecrets(
                keyPair = fixture.keypair
            ).toHexString()

            val thrown = assertThrows(
                WalletSecureStorageUnavailableException::class.java
            ) {
                engine.validateAndSanitize(
                    encoded = encoded,
                    expectedAccountId =
                        fixture.keypair.publicKey.substrateAccountId(),
                    expectedPublicKey = fixture.keypair.publicKey,
                    expectedCryptoType = fixture.cryptoType
                )
            }

            assertSame(providerFailure, thrown.cause)
        }
    }

    @Test
    fun entropyAndSeedDeriverFailuresAreGlobalAndRetainCause() {
        val keypair = directEcdsaKeypair(7)
        val entropyFailure = ProviderException("Injected mnemonic derivation failure")
        val entropyEngine = ChainAccountSecretValidationEngine(
            TestCryptography(
                recoverEntropy = { _, _, _ -> throw entropyFailure }
            )
        )
        val entropyEncoded = ChainAccountSecrets(
            keyPair = keypair,
            entropy = ByteArray(16) { (it + 9).toByte() }
        ).toHexString()

        val thrownEntropy = assertThrows(
            WalletSecureStorageUnavailableException::class.java
        ) {
            entropyEngine.validateAndSanitize(
                encoded = entropyEncoded,
                expectedAccountId = keypair.publicKey.substrateAccountId(),
                expectedPublicKey = keypair.publicKey,
                expectedCryptoType = CryptoType.ECDSA
            )
        }
        assertSame(entropyFailure, thrownEntropy.cause)

        val seedFailure = ProviderException("Injected seed derivation failure")
        val seedEngine = ChainAccountSecretValidationEngine(
            TestCryptography(
                recoverSeed = { _, _, _ -> throw seedFailure }
            )
        )
        val seedEncoded = ChainAccountSecrets(
            keyPair = keypair,
            seed = ByteArray(32) { (it + 11).toByte() }
        ).toHexString()

        val thrownSeed = assertThrows(
            WalletSecureStorageUnavailableException::class.java
        ) {
            seedEngine.validateAndSanitize(
                encoded = seedEncoded,
                expectedAccountId = keypair.publicKey.substrateAccountId(),
                expectedPublicKey = keypair.publicKey,
                expectedCryptoType = CryptoType.ECDSA
            )
        }
        assertSame(seedFailure, thrownSeed.cause)
    }

    @Test
    fun derivationPathProviderFailureIsGlobalAndRetainsCause() {
        val keypair = directEcdsaKeypair(8)
        val providerFailure = ProviderException(
            "Injected derivation-path digest failure"
        )
        val engine = ChainAccountSecretValidationEngine(
            TestCryptography(
                decodePath = { throw providerFailure }
            )
        )

        val thrown = assertThrows(
            WalletSecureStorageUnavailableException::class.java
        ) {
            engine.validateAndSanitize(
                encoded = ChainAccountSecrets(
                    keyPair = keypair,
                    seed = ByteArray(32) { (it + 17).toByte() },
                    derivationPath = "//this-junction-is-long-enough-to-require-digest-normalization"
                ).toHexString(),
                expectedAccountId = keypair.publicKey.substrateAccountId(),
                expectedPublicKey = keypair.publicKey,
                expectedCryptoType = CryptoType.ECDSA
            )
        }

        assertSame(providerFailure, thrown.cause)
    }

    @Test
    fun fatalCryptographyErrorEscapesUnchanged() {
        val keypair = directEcdsaKeypair(9)
        val fatal = AssertionError("Injected fatal crypto error")
        val engine = ChainAccountSecretValidationEngine(
            TestCryptography(
                proveOwnership = { _, _ -> throw fatal }
            )
        )

        val thrown = assertThrows(AssertionError::class.java) {
            engine.validateAndSanitize(
                encoded = ChainAccountSecrets(keyPair = keypair).toHexString(),
                expectedAccountId = keypair.publicKey.substrateAccountId(),
                expectedPublicKey = keypair.publicKey,
                expectedCryptoType = CryptoType.ECDSA
            )
        }

        assertSame(fatal, thrown)
    }

    private fun directEcdsaKeypair(variant: Int) =
        EthereumKeypairFactory.createWithPrivateKey(
            ByteArray(32) { index -> (index * 11 + variant + 1).toByte() }
        )

    private data class CryptoFixture(
        val cryptoType: CryptoType,
        val keypair: FearlessKeypair
    )

    private class TestCryptography(
        private val decodePath: (
            String
        ) -> JunctionDecoder.DecodeResult = SubstrateJunctionDecoder::decode,
        private val proveOwnership: (
            FearlessKeypair,
            CryptoType
        ) -> Boolean = { _, _ -> true },
        private val recoverEntropy: (
            ByteArray,
            JunctionDecoder.DecodeResult?,
            CryptoType
        ) -> FearlessKeypair = { _, _, _ -> error("Unexpected entropy recovery") },
        private val recoverSeed: (
            ByteArray,
            JunctionDecoder.DecodeResult?,
            CryptoType
        ) -> FearlessKeypair = { _, _, _ -> error("Unexpected seed recovery") }
    ) : ChainAccountCryptography {

        override fun deriveAccountId(publicKey: ByteArray): ByteArray {
            return publicKey.substrateAccountId()
        }

        override fun decodeDerivationPath(
            derivationPath: String
        ): JunctionDecoder.DecodeResult {
            return decodePath(derivationPath)
        }

        override fun provesOwnership(
            keypair: FearlessKeypair,
            cryptoType: CryptoType
        ): Boolean {
            return proveOwnership(keypair, cryptoType)
        }

        override fun recoverFromEntropy(
            entropy: ByteArray,
            decodedPath: JunctionDecoder.DecodeResult?,
            cryptoType: CryptoType
        ): FearlessKeypair {
            return recoverEntropy(entropy, decodedPath, cryptoType)
        }

        override fun recoverFromSeed(
            seed: ByteArray,
            decodedPath: JunctionDecoder.DecodeResult?,
            cryptoType: CryptoType
        ): FearlessKeypair {
            return recoverSeed(seed, decodedPath, cryptoType)
        }
    }
}
