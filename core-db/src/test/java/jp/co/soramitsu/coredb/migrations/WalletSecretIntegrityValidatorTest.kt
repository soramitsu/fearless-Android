package jp.co.soramitsu.coredb.migrations

import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.RecoveryAssessment
import jp.co.soramitsu.common.data.secrets.v3.RecoveryIssue
import jp.co.soramitsu.common.data.secrets.v3.ValidatedRootSecret
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretCorruptionException
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretValidation
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretValidator
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageUnavailableException
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.migrations.shipped.ShippedSecretWriters
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.scale.toHexString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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

        val substrate = SubstrateSecrets.read(checkNotNull(replacement.substratePlaintext))
        assertNull(substrate[SubstrateSecrets.SubstrateDerivationPath])
        assertArrayEquals(
            keypair.privateKey,
            substrate[SubstrateSecrets.SubstrateKeypair][KeyPairSchema.PrivateKey]
        )
        assertEquals(LegacyRootOutcome.Absent, replacement.ethereum)
    }

    @Test
    fun legacySeedThatNoLongerRederivesTheKeyConvertsAndIsKeptVerbatim() {
        val keypair = EthereumKeypairFactory.createWithPrivateKey(privateKey(6))
        val driftedSeed = ByteArray(32) { 7 }
        val driftedLegacy = TonMigration.MetaAccountSecretsV69 { secrets ->
            secrets[Entropy] = null
            secrets[Seed] = driftedSeed
            secrets[SubstrateKeypair] = KeyPairSchema { encodedKeypair ->
                encodedKeypair[PublicKey] = keypair.publicKey
                encodedKeypair[PrivateKey] = keypair.privateKey
                encodedKeypair[Nonce] = null
            }
            secrets[SubstrateDerivationPath] = "//drifted"
            secrets[EthereumKeypair] = null
            secrets[EthereumDerivationPath] = null
        }.toHexString()
        val identity = identity(
            substratePublicKey = keypair.publicKey,
            substrateCryptoType = CryptoType.ECDSA,
            substrateCryptoTypeWasPresent = true,
            substrateAccountId = keypair.publicKey.substrateAccountId()
        )

        val replacement =
            WalletSecretIntegrityValidator.validateLegacyAndPrepareReplacement(
                encoded = driftedLegacy,
                identity = identity
            )

        // The keypair proves the identity, so the wallet converts and signs;
        // the seed and path are carried over as stored, never rewritten.
        val substratePlaintext = checkNotNull(replacement.substratePlaintext)
        val substrate = SubstrateSecrets.read(substratePlaintext)
        assertArrayEquals(
            keypair.privateKey,
            substrate[SubstrateSecrets.SubstrateKeypair][KeyPairSchema.PrivateKey]
        )
        assertArrayEquals(driftedSeed, substrate[SubstrateSecrets.Seed])
        assertEquals("//drifted", substrate[SubstrateSecrets.SubstrateDerivationPath])
        assertEquals(
            RecoveryAssessment.Unverified(setOf(RecoveryIssue.SEED_MISMATCH)),
            WalletRootSecretValidator.validateSubstrate(
                encoded = substratePlaintext,
                expectedPublicKey = keypair.publicKey,
                expectedCryptoType = CryptoType.ECDSA,
                expectedAccountId = keypair.publicKey.substrateAccountId()
            ).assessment
        )
        assertNull(replacement.ethereumPlaintext)
    }

    @Test
    fun ethereumSeedThatIsNotThePrivateKeyStaysActiveUnchanged() {
        val keypair = EthereumKeypairFactory.createWithPrivateKey(privateKey(6))
        val encoded = EthereumSecrets(
            seed = privateKey(8),
            ethereumKeypair = keypair
        ).toHexString()

        assertEquals(
            encoded,
            WalletSecretIntegrityValidator.validateEthereumV3(
                encoded = encoded,
                identity = identity(
                    ethereumPublicKey = keypair.publicKey,
                    ethereumAddress = keypair.publicKey.ethereumAddressFromPublicKey()
                )
            )
        )
    }

    @Test
    fun legacyKeypairThatDoesNotProveItsIdentityIsCorruptForItsEcosystemOnly() {
        val expected = EthereumKeypairFactory.createWithPrivateKey(privateKey(6))
        val other = EthereumKeypairFactory.createWithPrivateKey(privateKey(7))
        val forged = TonMigration.MetaAccountSecretsV69 { secrets ->
            secrets[Entropy] = null
            secrets[Seed] = null
            secrets[SubstrateKeypair] = KeyPairSchema { encodedKeypair ->
                encodedKeypair[PublicKey] = expected.publicKey
                encodedKeypair[PrivateKey] = other.privateKey
                encodedKeypair[Nonce] = null
            }
            secrets[SubstrateDerivationPath] = null
            secrets[EthereumKeypair] = null
            secrets[EthereumDerivationPath] = null
        }.toHexString()

        val replacement = WalletSecretIntegrityValidator.validateLegacyAndPrepareReplacement(
            encoded = forged,
            identity = identity(
                substratePublicKey = expected.publicKey,
                substrateCryptoType = CryptoType.ECDSA,
                substrateCryptoTypeWasPresent = true,
                substrateAccountId = expected.publicKey.substrateAccountId()
            )
        )

        assertTrue(replacement.substrate is LegacyRootOutcome.Corrupt)
        assertNull(replacement.substratePlaintext)
        assertEquals(LegacyRootOutcome.Absent, replacement.ethereum)
    }

    @Test
    fun undecodableLegacyPayloadIsStillCorruption() {
        val keypair = EthereumKeypairFactory.createWithPrivateKey(privateKey(6))

        assertThrows(WalletSecretIntegrityCorruptionException::class.java) {
            WalletSecretIntegrityValidator.validateLegacyAndPrepareReplacement(
                encoded = "0x0102",
                identity = identity(
                    substratePublicKey = keypair.publicKey,
                    substrateCryptoType = CryptoType.ECDSA,
                    substrateAccountId = keypair.publicKey.substrateAccountId()
                )
            )
        }
    }

    /**
     * AND-V2: 2.0.2-3.7.4 Import > Raw seed with the Ethereum step declined
     * stored the default Ethereum path and no Ethereum keypair. The payload
     * comes from the verbatim 3.7.4 writer; the path is dropped and the
     * Substrate key converts with its seed.
     */
    @Test
    fun legacyRawSeedWithDefaultEthPathAndNoEthKeypairConverts() {
        // SR25519 needs the shipped native library and runs on device
        // (TonMigrationSafetyTest, WalletSecretIntegrityMigrationTest).
        listOf(CryptoType.ED25519, CryptoType.ECDSA).forEach { cryptoType ->
            listOf("", "//fearless").forEach { path ->
                val wallet = ShippedSecretWriters.importFromSeed374(
                    seed = SEED_HEX,
                    derivationPath = path,
                    selectedEncryptionType = cryptoType,
                    ethSeed = null
                )
                val legacy = TonMigration.MetaAccountSecretsV69.read(wallet.accessSecretsHex)
                assertEquals(
                    ShippedSecretWriters.DEFAULT_DERIVATION_PATH,
                    legacy[TonMigration.MetaAccountSecretsV69.EthereumDerivationPath]
                )
                assertNull(legacy[TonMigration.MetaAccountSecretsV69.EthereumKeypair])
                assertNull(wallet.ethereumPublicKey)

                val replacement =
                    WalletSecretIntegrityValidator.validateLegacyAndPrepareReplacement(
                        encoded = wallet.accessSecretsHex,
                        identity = wallet.identity()
                    )

                val substratePlaintext = checkNotNull(replacement.substratePlaintext) {
                    "$cryptoType '$path' did not convert"
                }
                val substrate = SubstrateSecrets.read(substratePlaintext)
                assertArrayEquals(
                    wallet.substrateKeypair.privateKey,
                    substrate[SubstrateSecrets.SubstrateKeypair][KeyPairSchema.PrivateKey]
                )
                assertArrayEquals(SEED_HEX.fromHex(), substrate[SubstrateSecrets.Seed])
                assertEquals(path, substrate[SubstrateSecrets.SubstrateDerivationPath])
                assertEquals(LegacyRootOutcome.Absent, replacement.ethereum)
                // The seed still re-derives the key, so phrase/seed export
                // stays available after the upgrade.
                assertEquals(
                    RecoveryAssessment.Verified,
                    WalletRootSecretValidator.validateSubstrate(
                        encoded = substratePlaintext,
                        expectedPublicKey = wallet.substratePublicKey,
                        expectedCryptoType = cryptoType,
                        expectedAccountId = wallet.substrateAccountId
                    ).assessment
                )
            }
        }
    }

    @Test
    fun legacyRawSeedWithSuppliedEthSeedConvertsBothKeys() {
        val wallet = ShippedSecretWriters.importFromSeed374(
            seed = SEED_HEX,
            derivationPath = "",
            selectedEncryptionType = CryptoType.ED25519,
            ethSeed = ETH_SEED_HEX
        )

        val replacement = WalletSecretIntegrityValidator.validateLegacyAndPrepareReplacement(
            encoded = wallet.accessSecretsHex,
            identity = wallet.identity()
        )

        assertTrue(replacement.substrate is LegacyRootOutcome.Converted)
        val ethereum = EthereumSecrets.read(checkNotNull(replacement.ethereumPlaintext))
        assertArrayEquals(
            checkNotNull(wallet.ethereumKeypair).privateKey,
            ethereum[EthereumSecrets.EthereumKeypair][KeyPairSchema.PrivateKey]
        )
        // No entropy: the path binds nothing and is not carried over.
        assertNull(ethereum[EthereumSecrets.EthereumDerivationPath])
    }

    /**
     * A1b: 4.0.1-4.2.0 added an Ethereum key to a pre-4.0 raw-seed wallet in
     * its own V3 entry and set the row's Ethereum identity, while the
     * Substrate key stayed in the legacy payload without an Ethereum keypair.
     */
    @Test
    fun legacySeedWalletWith4xAddedEthereumConvertsSubstrateOnly() {
        val wallet = ShippedSecretWriters.importFromSeed374(
            seed = SEED_HEX,
            derivationPath = "",
            selectedEncryptionType = CryptoType.ED25519,
            ethSeed = null
        )
        val added = ShippedSecretWriters.importAdditionalAccount420(ETH_SEED_HEX)

        val replacement = WalletSecretIntegrityValidator.validateLegacyAndPrepareReplacement(
            encoded = wallet.accessSecretsHex,
            identity = wallet.identity(
                ethereumPublicKey = added.ethereumPublicKey,
                ethereumAddress = added.ethereumAddress
            )
        )

        assertTrue(replacement.substrate is LegacyRootOutcome.Converted)
        assertEquals(LegacyRootOutcome.Absent, replacement.ethereum)
    }

    @Test
    fun legacyEthMismatchScopesToEthereum() {
        val wallet = ShippedSecretWriters.importFromSeed374(
            seed = SEED_HEX,
            derivationPath = "",
            selectedEncryptionType = CryptoType.ED25519,
            ethSeed = null
        )
        val expected = EthereumKeypairFactory.createWithPrivateKey(privateKey(9))
        val other = EthereumKeypairFactory.createWithPrivateKey(privateKey(10))
        val encoded = ShippedSecretWriters.MetaAccountSecrets(
            substrateKeyPair = wallet.substrateKeypair,
            seed = SEED_HEX.fromHex(),
            substrateDerivationPath = "",
            ethereumKeypair = jp.co.soramitsu.common.data.Keypair(
                publicKey = expected.publicKey,
                privateKey = other.privateKey
            ),
            ethereumDerivationPath = ShippedSecretWriters.DEFAULT_DERIVATION_PATH
        ).toHexString()

        val replacement = WalletSecretIntegrityValidator.validateLegacyAndPrepareReplacement(
            encoded = encoded,
            identity = wallet.identity(
                ethereumPublicKey = expected.publicKey,
                ethereumAddress = expected.publicKey.ethereumAddressFromPublicKey()
            )
        )

        assertTrue(replacement.substrate is LegacyRootOutcome.Converted)
        assertTrue(replacement.ethereum is LegacyRootOutcome.Corrupt)
    }

    @Test
    fun legacySubstrateT1FailureStillConvertsEthereum() {
        val ethereum = EthereumKeypairFactory.createWithPrivateKey(privateKey(11))
        val substrate = EthereumKeypairFactory.createWithPrivateKey(privateKey(12))
        val other = EthereumKeypairFactory.createWithPrivateKey(privateKey(13))
        val encoded = ShippedSecretWriters.MetaAccountSecrets(
            substrateKeyPair = jp.co.soramitsu.common.data.Keypair(
                publicKey = substrate.publicKey,
                privateKey = other.privateKey
            ),
            seed = null,
            ethereumKeypair = ethereum
        ).toHexString()

        val replacement = WalletSecretIntegrityValidator.validateLegacyAndPrepareReplacement(
            encoded = encoded,
            identity = identity(
                substratePublicKey = substrate.publicKey,
                substrateCryptoType = CryptoType.ECDSA,
                substrateAccountId = substrate.publicKey.substrateAccountId(),
                ethereumPublicKey = ethereum.publicKey,
                ethereumAddress = ethereum.publicKey.ethereumAddressFromPublicKey()
            )
        )

        assertTrue(replacement.substrate is LegacyRootOutcome.Corrupt)
        assertTrue(replacement.ethereum is LegacyRootOutcome.Converted)
    }

    @Test
    fun legacyEthereumKeypairWithoutAnIdentityIsLeftForTheArchive() {
        val wallet = ShippedSecretWriters.importFromSeed374(
            seed = SEED_HEX,
            derivationPath = "",
            selectedEncryptionType = CryptoType.ED25519,
            ethSeed = ETH_SEED_HEX
        )

        val replacement = WalletSecretIntegrityValidator.validateLegacyAndPrepareReplacement(
            encoded = wallet.accessSecretsHex,
            identity = wallet.identity(ethereumPublicKey = null, ethereumAddress = null)
        )

        assertTrue(replacement.substrate is LegacyRootOutcome.Converted)
        assertEquals(LegacyRootOutcome.Absent, replacement.ethereum)
    }

    @Test
    fun partialLegacyEthereumIdentityIsAnIdentityProblemNotCorruption() {
        val wallet = ShippedSecretWriters.importFromSeed374(
            seed = SEED_HEX,
            derivationPath = "",
            selectedEncryptionType = CryptoType.ED25519,
            ethSeed = null
        )

        assertThrows(WalletPublicIdentityIntegrityException::class.java) {
            WalletSecretIntegrityValidator.validateLegacyAndPrepareReplacement(
                encoded = wallet.accessSecretsHex,
                identity = wallet.identity(
                    ethereumPublicKey = ByteArray(33) { 2 },
                    ethereumAddress = null
                )
            )
        }
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

            override fun validateSubstrate(
                encoded: String,
                expectedPublicKey: ByteArray,
                expectedCryptoType: CryptoType,
                expectedAccountId: ByteArray
            ) = ValidatedRootSecret(encoded, RecoveryAssessment.Verified)

            override fun validateEthereum(
                encoded: String,
                expectedPublicKey: ByteArray,
                expectedAddress: ByteArray
            ): ValidatedRootSecret = throw providerFailure

            override fun validateTon(
                encoded: String,
                expectedPublicKey: ByteArray
            ) = ValidatedRootSecret(encoded, RecoveryAssessment.Verified)
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

    private fun ShippedSecretWriters.LegacyWallet.identity(
        ethereumPublicKey: ByteArray? = this.ethereumPublicKey,
        ethereumAddress: ByteArray? = this.ethereumAddress
    ) = WalletPublicIdentity(
        metaId = 1,
        substratePublicKey = substratePublicKey,
        substrateCryptoType = substrateCryptoType,
        substrateCryptoTypeWasPresent = true,
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

    private companion object {
        const val SEED_HEX = "0x9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60"
        const val ETH_SEED_HEX = "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80"
    }
}
