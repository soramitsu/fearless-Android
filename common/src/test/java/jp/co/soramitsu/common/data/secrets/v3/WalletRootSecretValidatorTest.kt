package jp.co.soramitsu.common.data.secrets.v3

import java.security.ProviderException
import jp.co.soramitsu.common.data.secrets.v1.Keypair
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageUnavailableException
import jp.co.soramitsu.common.utils.SolanaKeyDerivation
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.junction.BIP32JunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.junction.JunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair as FearlessKeypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.scale.toHexString
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class WalletRootSecretValidatorTest {

    @Test
    fun samePublicKeyWithWrongPrivateKeyIsRejectedForEveryRootEcosystem() {
        listOf(CryptoType.SR25519, CryptoType.ED25519).forEach { cryptoType ->
            val publicKey = ByteArray(32) { (it + cryptoType.ordinal + 1).toByte() }
            val keypair = Keypair(
                publicKey = publicKey,
                privateKey = ByteArray(32) { (it + 41).toByte() },
                nonce = if (cryptoType == CryptoType.SR25519) {
                    ByteArray(32) { (it + 81).toByte() }
                } else {
                    null
                }
            )
            val engine = WalletRootSecretValidationEngine(
                TestCryptography(
                    proveSubstrate = { _, _ -> false }
                )
            )

            assertThrows(WalletRootSecretCorruptionException::class.java) {
                engine.validateSubstrateKeypair(
                    keypair = keypair,
                    expectedPublicKey = publicKey,
                    expectedCryptoType = cryptoType,
                    expectedAccountId = publicKey
                )
            }
        }

        val expectedSubstrate = EthereumKeypairFactory.createWithPrivateKey(
            privateKey(1)
        )
        val otherSubstrate = EthereumKeypairFactory.createWithPrivateKey(
            privateKey(2)
        )
        assertThrows(WalletRootSecretCorruptionException::class.java) {
            WalletRootSecretValidator.validateSubstrateKeypair(
                keypair = Keypair(
                    publicKey = expectedSubstrate.publicKey,
                    privateKey = otherSubstrate.privateKey
                ),
                expectedPublicKey = expectedSubstrate.publicKey,
                expectedCryptoType = CryptoType.ECDSA,
                expectedAccountId = expectedSubstrate.publicKey.substrateAccountId()
            )
        }

        val expectedEthereum = EthereumKeypairFactory.createWithPrivateKey(
            privateKey(3)
        )
        val otherEthereum = EthereumKeypairFactory.createWithPrivateKey(
            privateKey(4)
        )
        assertThrows(WalletRootSecretCorruptionException::class.java) {
            WalletRootSecretValidator.validateEthereumKeypair(
                keypair = Keypair(
                    publicKey = expectedEthereum.publicKey,
                    privateKey = otherEthereum.privateKey
                ),
                expectedPublicKey = expectedEthereum.publicKey,
                expectedAddress =
                    expectedEthereum.publicKey.ethereumAddressFromPublicKey()
            )
        }

        val expectedTonPrivate = privateKey(5)
        val otherTonPrivate = privateKey(6)
        val expectedTonPublic =
            SolanaKeyDerivation.publicKeyFromPrivateKey(expectedTonPrivate)
        assertThrows(WalletRootSecretCorruptionException::class.java) {
            WalletRootSecretValidator.validateTonKeypair(
                keypair = Keypair(
                    publicKey = expectedTonPublic,
                    privateKey = otherTonPrivate
                ),
                expectedPublicKey = expectedTonPublic
            )
        }
    }

    @Test
    fun providerFailuresForEveryOwnershipPathAreGlobalAndRetainCause() {
        listOf(
            CryptoType.SR25519 to 32,
            CryptoType.ED25519 to 32,
            CryptoType.ECDSA to 33
        ).forEach { (cryptoType, publicKeyBytes) ->
            val providerFailure = ProviderException(
                "Injected $cryptoType ownership provider failure"
            )
            val publicKey = ByteArray(publicKeyBytes) { (it + 11).toByte() }
            val keypair = Keypair(
                publicKey = publicKey,
                privateKey = privateKey(7),
                nonce = if (cryptoType == CryptoType.SR25519) {
                    ByteArray(32) { (it + 19).toByte() }
                } else {
                    null
                }
            )
            val engine = WalletRootSecretValidationEngine(
                TestCryptography(
                    proveSubstrate = { _, _ -> throw providerFailure }
                )
            )

            val thrown = assertThrows(
                WalletSecureStorageUnavailableException::class.java
            ) {
                engine.validateSubstrateKeypair(
                    keypair = keypair,
                    expectedPublicKey = publicKey,
                    expectedCryptoType = cryptoType,
                    expectedAccountId = publicKey.copyOf(32)
                )
            }
            assertSame(providerFailure, thrown.cause)
        }

        val ethereumFailure =
            ProviderException("Injected Ethereum public derivation failure")
        val ethereumKeypair = Keypair(
            publicKey = ByteArray(33) { (it + 31).toByte() },
            privateKey = privateKey(8)
        )
        val ethereumEngine = WalletRootSecretValidationEngine(
            TestCryptography(
                deriveEthereumPublic = { throw ethereumFailure }
            )
        )
        val thrownEthereum = assertThrows(
            WalletSecureStorageUnavailableException::class.java
        ) {
            ethereumEngine.validateEthereumKeypair(
                keypair = ethereumKeypair,
                expectedPublicKey = ethereumKeypair.publicKey,
                expectedAddress = ByteArray(20)
            )
        }
        assertSame(ethereumFailure, thrownEthereum.cause)

        val tonFailure =
            ProviderException("Injected TON public derivation failure")
        val tonKeypair = Keypair(
            publicKey = ByteArray(32) { (it + 61).toByte() },
            privateKey = privateKey(9)
        )
        val tonEngine = WalletRootSecretValidationEngine(
            TestCryptography(
                deriveTonPublic = { throw tonFailure }
            )
        )
        val thrownTon = assertThrows(
            WalletSecureStorageUnavailableException::class.java
        ) {
            tonEngine.validateTonKeypair(
                keypair = tonKeypair,
                expectedPublicKey = tonKeypair.publicKey
            )
        }
        assertSame(tonFailure, thrownTon.cause)
    }

    @Test
    fun malformedDurableIdentitiesAreNeverPayloadCorruption() {
        val engine = WalletRootSecretValidationEngine(TestCryptography())

        assertThrows(WalletPublicIdentityIntegrityException::class.java) {
            engine.validateSubstrateKeypair(
                keypair = Keypair(
                    publicKey = ByteArray(32),
                    privateKey = privateKey(10)
                ),
                expectedPublicKey = ByteArray(31),
                expectedCryptoType = CryptoType.ED25519,
                expectedAccountId = ByteArray(32)
            )
        }
        assertThrows(WalletPublicIdentityIntegrityException::class.java) {
            engine.validateEthereumKeypair(
                keypair = Keypair(
                    publicKey = ByteArray(33),
                    privateKey = privateKey(11)
                ),
                expectedPublicKey = ByteArray(33),
                expectedAddress = ByteArray(19)
            )
        }
        assertThrows(WalletPublicIdentityIntegrityException::class.java) {
            engine.validateTonKeypair(
                keypair = Keypair(
                    publicKey = ByteArray(32),
                    privateKey = privateKey(12)
                ),
                expectedPublicKey = ByteArray(31)
            )
        }
    }

    @Test
    fun inconsistentDurableIdentityPairsAndProviderFailuresAreNonLocal() {
        val substratePublicKey = ByteArray(32) { (it + 21).toByte() }
        val engine = WalletRootSecretValidationEngine(TestCryptography())
        assertThrows(WalletPublicIdentityIntegrityException::class.java) {
            engine.validateSubstrateKeypair(
                keypair = Keypair(
                    publicKey = substratePublicKey,
                    privateKey = privateKey(15)
                ),
                expectedPublicKey = substratePublicKey,
                expectedCryptoType = CryptoType.ED25519,
                expectedAccountId = ByteArray(32) { (it + 99).toByte() }
            )
        }

        val ethereumPublicKey = ByteArray(33) { (it + 31).toByte() }
        assertThrows(WalletPublicIdentityIntegrityException::class.java) {
            engine.validateEthereumKeypair(
                keypair = Keypair(
                    publicKey = ethereumPublicKey,
                    privateKey = privateKey(16)
                ),
                expectedPublicKey = ethereumPublicKey,
                expectedAddress = ByteArray(20) { 1 }
            )
        }

        val providerFailure =
            ProviderException("Injected durable identity digest failure")
        val providerEngine = WalletRootSecretValidationEngine(
            TestCryptography(
                deriveAccountId = { throw providerFailure }
            )
        )
        val thrown = assertThrows(
            WalletSecureStorageUnavailableException::class.java
        ) {
            providerEngine.validateSubstrateKeypair(
                keypair = Keypair(
                    publicKey = substratePublicKey,
                    privateKey = privateKey(17)
                ),
                expectedPublicKey = substratePublicKey,
                expectedCryptoType = CryptoType.ED25519,
                expectedAccountId = substratePublicKey
            )
        }
        assertSame(providerFailure, thrown.cause)
    }

    @Test
    fun invalidTonMnemonicIsLocalButMnemonicProviderFailureIsGlobal() {
        val keypair = Keypair(
            publicKey = ByteArray(32) { (it + 71).toByte() },
            privateKey = privateKey(13)
        )
        val encoded = TonSecrets(
            seed = List(12) { "invalid" }.joinToString(" ").encodeToByteArray(),
            tonKeypair = keypair
        ).toHexString()
        val localEngine = WalletRootSecretValidationEngine(
            TestCryptography(
                deriveTonPublic = { keypair.publicKey },
                validBip39 = { false },
                validTon = { false }
            )
        )

        assertThrows(WalletRootSecretCorruptionException::class.java) {
            localEngine.validateTonAndSanitize(
                encoded = encoded,
                expectedPublicKey = keypair.publicKey
            )
        }

        val providerFailure =
            ProviderException("Injected TON mnemonic provider failure")
        val providerEngine = WalletRootSecretValidationEngine(
            TestCryptography(
                deriveTonPublic = { keypair.publicKey },
                validBip39 = { throw providerFailure }
            )
        )
        val thrown = assertThrows(
            WalletSecureStorageUnavailableException::class.java
        ) {
            providerEngine.validateTonAndSanitize(
                encoded = encoded,
                expectedPublicKey = keypair.publicKey
            )
        }
        assertSame(providerFailure, thrown.cause)
    }

    @Test
    fun fatalCryptographyErrorEscapesUnchanged() {
        val fatal = AssertionError("Injected fatal root-crypto error")
        val publicKey = ByteArray(32) { (it + 91).toByte() }
        val engine = WalletRootSecretValidationEngine(
            TestCryptography(
                proveSubstrate = { _, _ -> throw fatal }
            )
        )

        val thrown = assertThrows(AssertionError::class.java) {
            engine.validateSubstrateKeypair(
                keypair = Keypair(
                    publicKey = publicKey,
                    privateKey = privateKey(14)
                ),
                expectedPublicKey = publicKey,
                expectedCryptoType = CryptoType.ED25519,
                expectedAccountId = publicKey
            )
        }
        assertSame(fatal, thrown)
    }

    private class TestCryptography(
        private val deriveAccountId: (ByteArray) -> ByteArray = {
            it.copyOf(32)
        },
        private val proveSubstrate: (
            FearlessKeypair,
            CryptoType
        ) -> Boolean = { _, _ -> true },
        private val deriveEthereumPublic: (ByteArray) -> ByteArray = {
            ByteArray(33)
        },
        private val deriveEthereumAddressValue: (ByteArray) -> ByteArray = {
            ByteArray(20)
        },
        private val deriveTonPublic: (ByteArray) -> ByteArray = {
            ByteArray(32)
        },
        private val validBip39: (String) -> Boolean = { true },
        private val validTon: (List<String>) -> Boolean = { true }
    ) : WalletRootCryptography {

        override fun deriveSubstrateAccountId(
            publicKey: ByteArray
        ): ByteArray = deriveAccountId(publicKey)

        override fun decodeSubstratePath(
            path: String
        ): JunctionDecoder.DecodeResult = SubstrateJunctionDecoder.decode(path)

        override fun provesSubstrateOwnership(
            keypair: FearlessKeypair,
            cryptoType: CryptoType
        ): Boolean = proveSubstrate(keypair, cryptoType)

        override fun recoverSubstrateFromEntropy(
            entropy: ByteArray,
            decodedPath: JunctionDecoder.DecodeResult?,
            cryptoType: CryptoType
        ): FearlessKeypair = error("Unexpected Substrate entropy recovery")

        override fun recoverSubstrateFromSeed(
            seed: ByteArray,
            decodedPath: JunctionDecoder.DecodeResult?,
            cryptoType: CryptoType
        ): FearlessKeypair = error("Unexpected Substrate seed recovery")

        override fun recoverSubstrateFromMnemonic(
            mnemonicText: String,
            decodedPath: JunctionDecoder.DecodeResult?,
            cryptoType: CryptoType
        ): FearlessKeypair = error("Unexpected Substrate mnemonic recovery")

        override fun deriveEthereumPublicKey(
            privateKey: ByteArray
        ): ByteArray = deriveEthereumPublic(privateKey)

        override fun deriveEthereumAddress(
            publicKey: ByteArray
        ): ByteArray = deriveEthereumAddressValue(publicKey)

        override fun decodeEthereumPath(
            path: String
        ): JunctionDecoder.DecodeResult = BIP32JunctionDecoder.decode(path)

        override fun recoverEthereumFromEntropy(
            entropy: ByteArray,
            decodedPath: JunctionDecoder.DecodeResult
        ): FearlessKeypair = error("Unexpected Ethereum entropy recovery")

        override fun deriveTonPublicKey(
            privateKey: ByteArray
        ): ByteArray = deriveTonPublic(privateKey)

        override fun isValidBip39Mnemonic(
            mnemonicText: String
        ): Boolean = validBip39(mnemonicText)

        override fun isValidTonMnemonic(
            mnemonicWords: List<String>
        ): Boolean = validTon(mnemonicWords)

        override fun recoverTonFromMnemonic(
            mnemonicWords: List<String>
        ): FearlessKeypair = error("Unexpected TON mnemonic recovery")
    }

    private companion object {
        fun privateKey(variant: Int): ByteArray {
            return ByteArray(32) { index ->
                ((index + 1) * 5 + variant).toByte()
            }
        }
    }
}
