package jp.co.soramitsu.coredb.migrations

import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretCorruptionException
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretValidation
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageUnavailableException
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.scale.toHexString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletSecretIntegrityValidatorTest {

    @Test
    fun identityCompletenessDistinguishesAbsentPartialAndUnknownCrypto() {
        val absent = identity()
        val partialEthereum = identity(ethereumPublicKey = ByteArray(33))
        val unknownSubstrate = identity(
            substratePublicKey = ByteArray(33),
            substrateAccountId = ByteArray(32),
            substrateCryptoTypeWasPresent = true
        )

        assertFalse(absent.hasPartialSubstrateIdentity)
        assertFalse(absent.hasPartialEthereumIdentity)
        assertTrue(partialEthereum.hasPartialEthereumIdentity)
        assertTrue(unknownSubstrate.hasPartialSubstrateIdentity)
    }

    @Test
    fun ethereumDirectImportKeepsSigningKeyAndSanitizesUnboundPath() {
        val keypair = EthereumKeypairFactory.createWithPrivateKey(privateKey(1))
        val encoded = EthereumSecrets(
            seed = keypair.privateKey,
            ethereumKeypair = keypair,
            ethereumDerivationPath = "//44//60//0/0/99"
        ).toHexString()

        val sanitized = WalletSecretIntegrityValidator.validateEthereumV3(
            encoded = encoded,
            identity = identity(
                ethereumPublicKey = keypair.publicKey,
                ethereumAddress = keypair.publicKey.ethereumAddressFromPublicKey()
            )
        )

        val secrets = EthereumSecrets.read(sanitized)
        assertNull(secrets[EthereumSecrets.EthereumDerivationPath])
        assertArrayEquals(keypair.privateKey, secrets[EthereumSecrets.Seed])
        assertArrayEquals(
            keypair.privateKey,
            secrets[EthereumSecrets.EthereumKeypair][KeyPairSchema.PrivateKey]
        )
    }

    @Test
    fun ethereumPrivateKeyMismatchIsRejected() {
        val expected = EthereumKeypairFactory.createWithPrivateKey(privateKey(2))
        val other = EthereumKeypairFactory.createWithPrivateKey(privateKey(3))
        val forged = EthereumSecrets(
            seed = other.privateKey,
            ethereumKeypair = jp.co.soramitsu.common.data.Keypair(
                publicKey = expected.publicKey,
                privateKey = other.privateKey
            )
        ).toHexString()

        assertThrows(WalletRootSecretCorruptionException::class.java) {
            WalletSecretIntegrityValidator.validateEthereumV3(
                encoded = forged,
                identity = identity(
                    ethereumPublicKey = expected.publicKey,
                    ethereumAddress = expected.publicKey.ethereumAddressFromPublicKey()
                )
            )
        }
    }

    @Test
    fun legacyEcdsaKeypairOnlySecretIsPreservedWithUnboundPathRemoved() {
        val keypair = EthereumKeypairFactory.createWithPrivateKey(privateKey(4))
        val encoded = TonMigration.MetaAccountSecretsV69 { secrets ->
            secrets[Entropy] = null
            secrets[Seed] = null
            secrets[SubstrateKeypair] = KeyPairSchema { encodedKeypair ->
                encodedKeypair[PublicKey] = keypair.publicKey
                encodedKeypair[PrivateKey] = keypair.privateKey
                encodedKeypair[Nonce] = null
            }
            secrets[SubstrateDerivationPath] = "//unbound"
            secrets[EthereumKeypair] = null
            secrets[EthereumDerivationPath] = null
        }.toHexString()

        val replacement =
            WalletSecretIntegrityValidator.validateLegacyAndPrepareReplacement(
                encoded = encoded,
                identity = identity(
                    substratePublicKey = keypair.publicKey,
                    substrateCryptoType = CryptoType.ECDSA,
                    substrateCryptoTypeWasPresent = true,
                    substrateAccountId = keypair.publicKey.substrateAccountId()
                )
            )

        val substrate = SubstrateSecrets.read(replacement.substratePlaintext)
        assertNull(substrate[SubstrateSecrets.SubstrateDerivationPath])
        assertArrayEquals(
            keypair.privateKey,
            substrate[SubstrateSecrets.SubstrateKeypair][KeyPairSchema.PrivateKey]
        )
        assertNull(replacement.ethereumPlaintext)
    }

    @Test
    fun validLegacyEthereumIdentityPreservesProviderOutageClassification() {
        val keypair = EthereumKeypairFactory.createWithPrivateKey(privateKey(5))
        val encoded = TonMigration.MetaAccountSecretsV69 { secrets ->
            secrets[Entropy] = null
            secrets[Seed] = null
            secrets[SubstrateKeypair] = KeyPairSchema { encodedKeypair ->
                encodedKeypair[PublicKey] = keypair.publicKey
                encodedKeypair[PrivateKey] = keypair.privateKey
                encodedKeypair[Nonce] = null
            }
            secrets[SubstrateDerivationPath] = null
            secrets[EthereumKeypair] = KeyPairSchema { encodedKeypair ->
                encodedKeypair[PublicKey] = keypair.publicKey
                encodedKeypair[PrivateKey] = keypair.privateKey
                encodedKeypair[Nonce] = null
            }
            secrets[EthereumDerivationPath] = null
        }.toHexString()
        val providerFailure =
            WalletSecureStorageUnavailableException("provider unavailable")
        val validation = object : WalletRootSecretValidation {
            override fun validateSubstrateAndSanitize(
                encoded: String,
                expectedPublicKey: ByteArray,
                expectedCryptoType: CryptoType,
                expectedAccountId: ByteArray
            ) = encoded

            override fun validateEthereumAndSanitize(
                encoded: String,
                expectedPublicKey: ByteArray,
                expectedAddress: ByteArray
            ): String = throw providerFailure

            override fun validateTonAndSanitize(
                encoded: String,
                expectedPublicKey: ByteArray
            ) = encoded
        }

        val thrown = assertThrows(
            WalletSecureStorageUnavailableException::class.java
        ) {
            WalletSecretIntegrityValidator.validateLegacyAndPrepareReplacement(
                encoded = encoded,
                identity = identity(
                    substratePublicKey = keypair.publicKey,
                    substrateCryptoType = CryptoType.ECDSA,
                    substrateCryptoTypeWasPresent = true,
                    substrateAccountId = keypair.publicKey.substrateAccountId(),
                    ethereumPublicKey = keypair.publicKey,
                    ethereumAddress =
                        keypair.publicKey.ethereumAddressFromPublicKey()
                ),
                walletRootSecretValidation = validation
            )
        }

        assertSame(providerFailure, thrown)
    }

    private fun identity(
        substratePublicKey: ByteArray? = null,
        substrateCryptoType: CryptoType? = null,
        substrateCryptoTypeWasPresent: Boolean = substrateCryptoType != null,
        substrateAccountId: ByteArray? = null,
        ethereumPublicKey: ByteArray? = null,
        ethereumAddress: ByteArray? = null
    ) = WalletPublicIdentity(
        metaId = 1,
        substratePublicKey = substratePublicKey,
        substrateCryptoType = substrateCryptoType,
        substrateCryptoTypeWasPresent = substrateCryptoTypeWasPresent,
        substrateAccountId = substrateAccountId,
        ethereumPublicKey = ethereumPublicKey,
        ethereumAddress = ethereumAddress,
        tonPublicKey = null
    )

    private fun privateKey(variant: Int): ByteArray {
        return ByteArray(32) { index ->
            (index * 5 + variant + 1).toByte()
        }
    }
}
