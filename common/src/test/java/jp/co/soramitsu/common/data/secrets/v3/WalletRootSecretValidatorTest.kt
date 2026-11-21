package jp.co.soramitsu.common.data.secrets.v3

import java.security.ProviderException
import jp.co.soramitsu.common.data.secrets.WalletSecretVectors
import jp.co.soramitsu.common.data.secrets.v1.Keypair
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageUnavailableException
import jp.co.soramitsu.common.utils.DEFAULT_DERIVATION_PATH
import jp.co.soramitsu.common.utils.SolanaKeyDerivation
import jp.co.soramitsu.common.utils.deriveSeed32
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.crypto.SubstratePasswordRule
import jp.co.soramitsu.core.model.SecuritySource
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.junction.BIP32JunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.junction.JunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair as FearlessKeypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.fearless_utils.exceptions.Bip39Exception
import jp.co.soramitsu.fearless_utils.scale.toHexString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
    fun invalidTonMnemonicIsUnverifiedButMnemonicProviderFailureIsGlobal() {
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
                recoverTon = {
                    Keypair(
                        publicKey = ByteArray(32),
                        privateKey = privateKey(99)
                    )
                }
            )
        )

        // The keypair is the durable identity, so the wallet keeps signing;
        // only the phrase loses its export eligibility.
        assertEquals(
            encoded,
            localEngine.validateTonAndSanitize(
                encoded = encoded,
                expectedPublicKey = keypair.publicKey
            )
        )
        val validated = localEngine.validateTon(
            encoded = encoded,
            expectedPublicKey = keypair.publicKey
        )
        assertEquals(encoded, validated.canonical)
        assertEquals(
            RecoveryAssessment.Unverified(setOf(RecoveryIssue.MNEMONIC_MISMATCH)),
            validated.assessment
        )

        val providerFailure =
            ProviderException("Injected TON mnemonic provider failure")
        val providerEngine = WalletRootSecretValidationEngine(
            TestCryptography(
                deriveTonPublic = { keypair.publicKey },
                recoverTon = { throw providerFailure }
            )
        )
        assertEquals(
            encoded,
            providerEngine.validateTonAndSanitize(
                encoded = encoded,
                expectedPublicKey = keypair.publicKey
            )
        )
        val thrown = assertThrows(
            WalletSecureStorageUnavailableException::class.java
        ) {
            providerEngine.validateTon(
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

    @Test
    fun sr25519RecoveryAcceptsDifferentNonceWithSameScalarAndPublic() {
        val stored = sr25519Keypair(nonce = nonce(1))
        val recoveredNonces = mutableListOf<ByteArray>()
        val engine = WalletRootSecretValidationEngine(
            TestCryptography(
                recoverSubstrate = { _ ->
                    // A soft junction re-derives the same scalar and public
                    // key with a fresh random nonce on every call.
                    val freshNonce = nonce(recoveredNonces.size + 2)
                    recoveredNonces += freshNonce
                    Keypair(
                        publicKey = stored.publicKey.copyOf(),
                        privateKey = stored.privateKey.copyOf(),
                        nonce = freshNonce
                    )
                }
            )
        )
        val encoded = SubstrateSecrets(
            substrateKeyPair = stored,
            entropy = ENTROPY,
            seed = SEED,
            substrateDerivationPath = SOFT_PATH
        ).toHexString()

        val validated = engine.validateSubstrate(
            encoded = encoded,
            expectedPublicKey = stored.publicKey,
            expectedCryptoType = CryptoType.SR25519,
            expectedAccountId = stored.publicKey
        )

        assertEquals(2, recoveredNonces.size)
        assertEquals(RecoveryAssessment.Verified, validated.assessment)
        assertEquals(encoded, validated.canonical)
        assertArrayEquals(
            nonce(1),
            SubstrateSecrets.read(validated.canonical)[SubstrateSecrets.SubstrateKeypair][KeyPairSchema.Nonce]
        )

        assertEquals(
            RecoveryAssessment.Verified,
            engine.assessLegacySubstrateSource(
                source = SecuritySource.Specified.Mnemonic(
                    seed = SEED,
                    keypair = stored,
                    mnemonic = MNEMONIC,
                    derivationPath = SOFT_PATH
                ),
                expectedPublicKey = stored.publicKey,
                expectedCryptoType = CryptoType.SR25519,
                expectedAccountId = stored.publicKey
            )
        )
        assertEquals(4, recoveredNonces.size)
    }

    @Test
    fun sr25519RecoveryRejectsDifferentScalarOrPublicKey() {
        val stored = sr25519Keypair(nonce = nonce(1))
        val encoded = SubstrateSecrets(
            substrateKeyPair = stored,
            entropy = ENTROPY,
            seed = SEED,
            substrateDerivationPath = SOFT_PATH
        ).toHexString()
        listOf(
            Keypair(
                publicKey = stored.publicKey.copyOf(),
                privateKey = privateKey(22),
                nonce = nonce(1)
            ),
            Keypair(
                publicKey = substratePublicKey(2),
                privateKey = stored.privateKey.copyOf(),
                nonce = nonce(1)
            )
        ).forEach { recovered ->
            val engine = WalletRootSecretValidationEngine(
                TestCryptography(recoverSubstrate = { _ -> recovered })
            )

            assertEquals(
                RecoveryAssessment.Unverified(
                    setOf(RecoveryIssue.ENTROPY_MISMATCH, RecoveryIssue.SEED_MISMATCH)
                ),
                engine.validateSubstrate(
                    encoded = encoded,
                    expectedPublicKey = stored.publicKey,
                    expectedCryptoType = CryptoType.SR25519,
                    expectedAccountId = stored.publicKey
                ).assessment
            )
            assertEquals(
                RecoveryAssessment.Unverified(setOf(RecoveryIssue.MNEMONIC_MISMATCH)),
                engine.assessLegacySubstrateSource(
                    source = SecuritySource.Specified.Mnemonic(
                        seed = null,
                        keypair = stored,
                        mnemonic = MNEMONIC,
                        derivationPath = SOFT_PATH
                    ),
                    expectedPublicKey = stored.publicKey,
                    expectedCryptoType = CryptoType.SR25519,
                    expectedAccountId = stored.publicKey
                )
            )
        }
    }

    @Test
    fun sr25519StoredNonceMustStillBeThirtyTwoBytes() {
        val stored = sr25519Keypair(nonce = ByteArray(31) { (it + 7).toByte() })
        val engine = WalletRootSecretValidationEngine(
            TestCryptography(
                recoverSubstrate = { _ ->
                    Keypair(
                        publicKey = stored.publicKey.copyOf(),
                        privateKey = stored.privateKey.copyOf(),
                        nonce = nonce(2)
                    )
                }
            )
        )

        val encoded = SubstrateSecrets(
            substrateKeyPair = stored,
            seed = SEED,
            substrateDerivationPath = SOFT_PATH
        ).toHexString()
        assertThrows(WalletRootSecretCorruptionException::class.java) {
            engine.validateSubstrateAndSanitize(
                encoded = encoded,
                expectedPublicKey = stored.publicKey,
                expectedCryptoType = CryptoType.SR25519,
                expectedAccountId = stored.publicKey
            )
        }
        assertThrows(WalletRootSecretCorruptionException::class.java) {
            engine.validateSubstrate(
                encoded = encoded,
                expectedPublicKey = stored.publicKey,
                expectedCryptoType = CryptoType.SR25519,
                expectedAccountId = stored.publicKey
            )
        }
    }

    @Test
    fun ed25519RecoveryStillRequiresNullNonce() {
        listOf(
            CryptoType.ED25519 to 32,
            CryptoType.ECDSA to 33
        ).forEach { (cryptoType, publicKeyBytes) ->
            val publicKey = ByteArray(publicKeyBytes) { (it + 3).toByte() }
            val stored = Keypair(
                publicKey = publicKey,
                privateKey = privateKey(23)
            )
            val encoded = SubstrateSecrets(
                substrateKeyPair = stored,
                entropy = ENTROPY,
                seed = SEED,
                substrateDerivationPath = HARD_PATH
            ).toHexString()
            val withNonce = WalletRootSecretValidationEngine(
                TestCryptography(
                    recoverSubstrate = { _ ->
                        Keypair(
                            publicKey = publicKey.copyOf(),
                            privateKey = stored.privateKey.copyOf(),
                            nonce = nonce(3)
                        )
                    }
                )
            )

            assertEquals(
                RecoveryAssessment.Unverified(
                    setOf(RecoveryIssue.ENTROPY_MISMATCH, RecoveryIssue.SEED_MISMATCH)
                ),
                withNonce.validateSubstrate(
                    encoded = encoded,
                    expectedPublicKey = publicKey,
                    expectedCryptoType = cryptoType,
                    expectedAccountId = publicKey.copyOf(32)
                ).assessment
            )

            val withoutNonce = WalletRootSecretValidationEngine(
                TestCryptography(
                    recoverSubstrate = { _ ->
                        Keypair(
                            publicKey = publicKey.copyOf(),
                            privateKey = stored.privateKey.copyOf()
                        )
                    }
                )
            )
            assertEquals(
                ValidatedRootSecret(encoded, RecoveryAssessment.Verified),
                withoutNonce.validateSubstrate(
                    encoded = encoded,
                    expectedPublicKey = publicKey,
                    expectedCryptoType = cryptoType,
                    expectedAccountId = publicKey.copyOf(32)
                )
            )
        }
    }

    @Test
    fun entropyMismatchKeepsKeypairAndReportsUnverified() {
        val stored = ed25519Keypair(variant = 24)
        val engine = WalletRootSecretValidationEngine(
            TestCryptography(
                recoverSubstrateEntropy = { _ -> ed25519Keypair(variant = 25) },
                recoverSubstrateSeed = { _ -> stored.copy() }
            )
        )
        val encoded = SubstrateSecrets(
            substrateKeyPair = stored,
            entropy = ENTROPY,
            seed = SEED,
            substrateDerivationPath = HARD_PATH
        ).toHexString()

        assertEquals(encoded, engine.validateSubstrateAndSanitize(stored, encoded))
        val validated = engine.validateSubstrate(
            encoded = encoded,
            expectedPublicKey = stored.publicKey,
            expectedCryptoType = CryptoType.ED25519,
            expectedAccountId = stored.publicKey
        )

        // The drifted entropy is kept byte for byte, never stripped or rewritten.
        assertEquals(
            ValidatedRootSecret(
                canonical = encoded,
                assessment = RecoveryAssessment.Unverified(setOf(RecoveryIssue.ENTROPY_MISMATCH))
            ),
            validated
        )
        assertThrows(WalletRootSecretCorruptionException::class.java) {
            validated.requireVerifiedRecovery()
        }
    }

    @Test
    fun seedLengthNot32KeepsKeypair() {
        val stored = ed25519Keypair(variant = 26)
        val longSeed = ByteArray(64) { (it + 1).toByte() }
        val cryptography = TestCryptography()
        val engine = WalletRootSecretValidationEngine(cryptography)
        val encoded = SubstrateSecrets(
            substrateKeyPair = stored,
            seed = longSeed
        ).toHexString()

        assertEquals(encoded, engine.validateSubstrateAndSanitize(stored, encoded))
        assertEquals(
            RecoveryAssessment.Unverified(setOf(RecoveryIssue.SEED_LENGTH)),
            engine.validateSubstrate(
                encoded = encoded,
                expectedPublicKey = stored.publicKey,
                expectedCryptoType = CryptoType.ED25519,
                expectedAccountId = stored.publicKey
            ).assessment
        )
        // A 1.x source with a 64-byte seed signs with its keypair as well.
        val legacySource = SecuritySource.Specified.Seed(
            seed = longSeed,
            keypair = stored,
            derivationPath = null
        )
        engine.validateLegacySubstrateSource(
            source = legacySource,
            expectedPublicKey = stored.publicKey,
            expectedCryptoType = CryptoType.ED25519,
            expectedAccountId = stored.publicKey
        )
        assertEquals(
            RecoveryAssessment.Unverified(setOf(RecoveryIssue.SEED_LENGTH)),
            engine.assessLegacySubstrateSource(
                source = legacySource,
                expectedPublicKey = stored.publicKey,
                expectedCryptoType = CryptoType.ED25519,
                expectedAccountId = stored.publicKey
            )
        )
        assertEquals(0, cryptography.recoveryCalls)
    }

    @Test
    fun malformedPathKeepsKeypair() {
        val stored = ed25519Keypair(variant = 27)
        listOf(
            "polkadot" to RecoveryIssue.PATH_MALFORMED,
            "//polkadot///one///two" to RecoveryIssue.PATH_MALFORMED,
            "//" + "a".repeat(2_047) to RecoveryIssue.PATH_OVERSIZED
        ).forEach { (path, issue) ->
            val cryptography = TestCryptography()
            val engine = WalletRootSecretValidationEngine(cryptography)
            val encoded = SubstrateSecrets(
                substrateKeyPair = stored,
                entropy = ENTROPY,
                seed = SEED,
                substrateDerivationPath = path
            ).toHexString()

            assertEquals(encoded, engine.validateSubstrateAndSanitize(stored, encoded))
            assertEquals(
                path,
                RecoveryAssessment.Unverified(setOf(issue)),
                engine.validateSubstrate(
                    encoded = encoded,
                    expectedPublicKey = stored.publicKey,
                    expectedCryptoType = CryptoType.ED25519,
                    expectedAccountId = stored.publicKey
                ).assessment
            )
            assertEquals(0, cryptography.recoveryCalls)
        }
    }

    @Test
    fun legacyMnemonicDriftIsUnverifiedNotCorrupt() {
        val stored = legacyMnemonicKeypair(MNEMONIC)
        val accountId = stored.publicKey.substrateAccountId()
        val otherPhrase = "legal winner thank year wave sausage worth useful legal winner thank yellow"
        listOf(
            otherPhrase to RecoveryIssue.MNEMONIC_MISMATCH,
            MNEMONIC.substringBeforeLast(' ') to RecoveryIssue.MNEMONIC_WORDCOUNT,
            "" to RecoveryIssue.MNEMONIC_WORDCOUNT,
            "$MNEMONIC ".repeat(700) to RecoveryIssue.MNEMONIC_WORDCOUNT,
            MNEMONIC.replace("walk", "walked") to RecoveryIssue.MNEMONIC_INVALID,
            MNEMONIC.replace("walk", "wall") to RecoveryIssue.MNEMONIC_INVALID,
            MNEMONIC.uppercase() to RecoveryIssue.MNEMONIC_INVALID
        ).forEach { (mnemonic, issue) ->
            val source = SecuritySource.Specified.Mnemonic(
                seed = null,
                keypair = stored,
                mnemonic = mnemonic,
                derivationPath = null
            )

            // The keypair is the durable identity: signing is unaffected.
            WalletRootSecretValidator.validateLegacySubstrateSource(
                source = source,
                expectedPublicKey = stored.publicKey,
                expectedCryptoType = CryptoType.ED25519,
                expectedAccountId = accountId
            )
            assertEquals(
                mnemonic.take(80),
                RecoveryAssessment.Unverified(setOf(issue)),
                WalletRootSecretValidator.assessLegacySubstrateSource(
                    source = source,
                    expectedPublicKey = stored.publicKey,
                    expectedCryptoType = CryptoType.ED25519,
                    expectedAccountId = accountId
                )
            )
        }
    }

    @Test
    fun legacyMnemonicProviderFailureIsGlobalWhileAnUnreadablePhraseIsNot() {
        val stored = ed25519Keypair(variant = 28)
        val source = SecuritySource.Specified.Mnemonic(
            seed = null,
            keypair = stored,
            mnemonic = MNEMONIC,
            derivationPath = null
        )
        val unreadable = WalletRootSecretValidationEngine(
            TestCryptography(recoverSubstrate = { _ -> throw Bip39Exception() })
        )
        assertEquals(
            RecoveryAssessment.Unverified(setOf(RecoveryIssue.MNEMONIC_INVALID)),
            unreadable.assessLegacySubstrateSource(
                source = source,
                expectedPublicKey = stored.publicKey,
                expectedCryptoType = CryptoType.ED25519,
                expectedAccountId = stored.publicKey
            )
        )

        val providerFailure = ProviderException("Injected mnemonic provider failure")
        val unavailable = WalletRootSecretValidationEngine(
            TestCryptography(recoverSubstrate = { _ -> throw providerFailure })
        )
        val thrown = assertThrows(WalletSecureStorageUnavailableException::class.java) {
            unavailable.assessLegacySubstrateSource(
                source = source,
                expectedPublicKey = stored.publicKey,
                expectedCryptoType = CryptoType.ED25519,
                expectedAccountId = stored.publicKey
            )
        }
        assertSame(providerFailure, thrown.cause)
    }

    @Test
    fun legacyMnemonicSpacingNeverCountsAsDrift() {
        // A BIP39 phrase derives through its entropy, so text stored with
        // irregular spacing or separators re-derives exactly the same key.
        val stored = legacyMnemonicKeypair(MNEMONIC)
        val accountId = stored.publicKey.substrateAccountId()
        listOf(
            MNEMONIC,
            "$MNEMONIC ",
            " $MNEMONIC",
            MNEMONIC.replace(" ", "  "),
            MNEMONIC.replace(" ", "\n"),
            MNEMONIC.replace(" ", ", ")
        ).forEach { mnemonic ->
            assertEquals(
                mnemonic,
                RecoveryAssessment.Verified,
                WalletRootSecretValidator.assessLegacySubstrateSource(
                    source = SecuritySource.Specified.Mnemonic(
                        seed = null,
                        keypair = stored,
                        mnemonic = mnemonic,
                        derivationPath = null
                    ),
                    expectedPublicKey = stored.publicKey,
                    expectedCryptoType = CryptoType.ED25519,
                    expectedAccountId = accountId
                )
            )
        }
    }

    @Test
    fun softJunctionOnAKeyThatCannotDeriveItIsUnverifiedNotAnOutage() {
        // Only SR25519 derives soft junctions. Re-deriving an ED25519 or ECDSA
        // key through one fails in the key factory on every attempt, which
        // must never be mistaken for unavailable cryptography.
        listOf(EncryptionType.ED25519 to CryptoType.ED25519, EncryptionType.ECDSA to CryptoType.ECDSA)
            .forEach { (encryptionType, cryptoType) ->
                val keypair = SubstrateKeypairFactory.generate(
                    encryptionType = encryptionType,
                    seed = SEED,
                    junctions = SubstrateJunctionDecoder.decode(HARD_PATH).junctions
                )
                val accountId = keypair.publicKey.substrateAccountId()
                val encoded = SubstrateSecrets(
                    substrateKeyPair = keypair,
                    entropy = ENTROPY,
                    seed = SEED,
                    substrateDerivationPath = SOFT_PATH
                ).toHexString()

                assertEquals(
                    encoded,
                    WalletRootSecretValidator.validateSubstrateAndSanitize(
                        encoded = encoded,
                        expectedPublicKey = keypair.publicKey,
                        expectedCryptoType = cryptoType,
                        expectedAccountId = accountId
                    )
                )
                assertEquals(
                    cryptoType.name,
                    ValidatedRootSecret(
                        canonical = encoded,
                        assessment = RecoveryAssessment.Unverified(setOf(RecoveryIssue.PATH_MALFORMED))
                    ),
                    WalletRootSecretValidator.validateSubstrate(
                        encoded = encoded,
                        expectedPublicKey = keypair.publicKey,
                        expectedCryptoType = cryptoType,
                        expectedAccountId = accountId
                    )
                )
                assertEquals(
                    cryptoType.name,
                    RecoveryAssessment.Unverified(setOf(RecoveryIssue.PATH_MALFORMED)),
                    WalletRootSecretValidator.assessLegacySubstrateSource(
                        source = SecuritySource.Specified.Seed(
                            seed = SEED,
                            keypair = keypair,
                            derivationPath = SOFT_PATH
                        ),
                        expectedPublicKey = keypair.publicKey,
                        expectedCryptoType = cryptoType,
                        expectedAccountId = accountId
                    )
                )
            }
    }

    @Test
    fun ethereumPathThatIsNotBip32IsUnverifiedNotAnOutage() {
        val keypair = EthereumKeypairFactory.createWithPrivateKey(privateKey(29))
        val address = keypair.publicKey.ethereumAddressFromPublicKey()
        listOf(
            "44/60/0/0/0" to RecoveryIssue.PATH_MALFORMED,
            "//44//60//0/0/first" to RecoveryIssue.PATH_MALFORMED,
            "//44//60//0/0/2147483648" to RecoveryIssue.PATH_MALFORMED,
            "//44//60//0/0/-1" to RecoveryIssue.PATH_MALFORMED,
            "/" + "1".repeat(2_048) to RecoveryIssue.PATH_OVERSIZED
        ).forEach { (path, issue) ->
            val encoded = EthereumSecrets(
                entropy = ENTROPY,
                seed = keypair.privateKey,
                ethereumKeypair = keypair,
                ethereumDerivationPath = path
            ).toHexString()

            assertEquals(
                encoded,
                WalletRootSecretValidator.validateEthereumAndSanitize(
                    encoded = encoded,
                    expectedPublicKey = keypair.publicKey,
                    expectedAddress = address
                )
            )
            assertEquals(
                path.take(40),
                ValidatedRootSecret(
                    canonical = encoded,
                    assessment = RecoveryAssessment.Unverified(setOf(issue))
                ),
                WalletRootSecretValidator.validateEthereum(
                    encoded = encoded,
                    expectedPublicKey = keypair.publicKey,
                    expectedAddress = address
                )
            )
        }
    }

    @Test
    fun ethSeedNotEqualPrivateKeyIsT2() {
        val keypair = EthereumKeypairFactory.createWithPrivateKey(privateKey(30))
        val address = keypair.publicKey.ethereumAddressFromPublicKey()
        val otherSeed = EthereumSecrets(
            seed = privateKey(31),
            ethereumKeypair = keypair
        ).toHexString()

        assertEquals(
            otherSeed,
            WalletRootSecretValidator.validateEthereumAndSanitize(
                encoded = otherSeed,
                expectedPublicKey = keypair.publicKey,
                expectedAddress = address
            )
        )
        assertEquals(
            RecoveryAssessment.Unverified(setOf(RecoveryIssue.SEED_MISMATCH)),
            WalletRootSecretValidator.validateEthereum(
                encoded = otherSeed,
                expectedPublicKey = keypair.publicKey,
                expectedAddress = address
            ).assessment
        )

        val entropyWithoutPath = EthereumSecrets(
            entropy = ENTROPY,
            seed = keypair.privateKey,
            ethereumKeypair = keypair
        ).toHexString()
        assertEquals(
            RecoveryAssessment.Unverified(setOf(RecoveryIssue.ETH_ENTROPY_WITHOUT_PATH)),
            WalletRootSecretValidator.validateEthereum(
                encoded = entropyWithoutPath,
                expectedPublicKey = keypair.publicKey,
                expectedAddress = address
            ).assessment
        )

        // A direct private-key import keeps signing and exporting its key;
        // the unbound default path it stored is dropped from the canonical form.
        val directImport = EthereumSecrets(
            seed = keypair.privateKey,
            ethereumKeypair = keypair,
            ethereumDerivationPath = BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH
        ).toHexString()
        assertEquals(
            ValidatedRootSecret(
                canonical = EthereumSecrets(
                    seed = keypair.privateKey,
                    ethereumKeypair = keypair
                ).toHexString(),
                assessment = RecoveryAssessment.Verified
            ),
            WalletRootSecretValidator.validateEthereum(
                encoded = directImport,
                expectedPublicKey = keypair.publicKey,
                expectedAddress = address
            )
        )
    }

    @Test
    fun universalTonRecoveryIsVerifiedWithoutWeakeningSigningOrRewritingMismatchedMaterial() {
        val phrase = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val derived = jp.co.soramitsu.common.utils.TonKeyDerivation.deriveAccount(phrase)
        val keypair = Keypair(derived.publicKey, derived.privateKey)
        val encoded = TonSecrets(seed = phrase.encodeToByteArray(), tonKeypair = keypair).toHexString()
        assertEquals(encoded, WalletRootSecretValidator.validateTonAndSanitize(encoded, derived.publicKey))
        assertEquals(RecoveryAssessment.Verified, WalletRootSecretValidator.validateTon(encoded, derived.publicKey).assessment)
        assertThrows(WalletRootSecretCorruptionException::class.java) {
            WalletRootSecretValidator.validateTonAndSanitize(encoded, ByteArray(32) { 7 })
        }
        val wrongPhrase = "legal winner thank year wave sausage worth useful legal winner thank yellow"
        val mismatched = TonSecrets(seed = wrongPhrase.encodeToByteArray(), tonKeypair = keypair).toHexString()
        assertEquals(mismatched, WalletRootSecretValidator.validateTonAndSanitize(mismatched, derived.publicKey))
        assertEquals(
            RecoveryAssessment.Unverified(setOf(RecoveryIssue.MNEMONIC_MISMATCH)),
            WalletRootSecretValidator.validateTon(mismatched, derived.publicKey).assessment
        )
    }

    @Test
    fun tonCanonicalIsVerified() {
        assertTonVariant("canonical", RecoveryAssessment.Verified)
    }

    @Test
    fun tonTrailingSpaceDerivedKeyIsVerifiedAsTyped() {
        assertTonVariant(
            "trailing-space",
            RecoveryAssessment.VerifiedAsTyped(TonPhraseVariant.AS_TYPED)
        )
    }

    @Test
    fun tonUppercaseDerivedKeyIsVerifiedAsTyped() {
        assertTonVariant(
            "upper",
            RecoveryAssessment.VerifiedAsTyped(TonPhraseVariant.AS_TYPED)
        )
    }

    @Test
    fun tonDoubleSpaceDerivedKeyIsVerifiedAsTyped() {
        assertTonVariant(
            "double-space",
            RecoveryAssessment.VerifiedAsTyped(TonPhraseVariant.AS_TYPED)
        )
    }

    @Test
    fun tonNormalizedPhraseNeverStandsInForTheTypedKey() {
        val vectors = WalletSecretVectors.ton().groupBy { it.typedText.trim().lowercase() }
        val canonical = WalletSecretVectors.ton().first { it.variant == "canonical" }
        val trailing = vectors.getValue(canonical.typedText).first { it.variant == "trailing-space" }

        // The key 4.x derived from a phrase typed with a trailing space is a
        // different account from the canonical phrase: exporting the
        // normalized words would restore an empty wallet.
        val typedKey = Keypair(publicKey = trailing.publicKey, privateKey = trailing.privateKey)
        val normalizedText = TonSecrets(
            seed = canonical.typedText.encodeToByteArray(),
            tonKeypair = typedKey
        ).toHexString()
        assertEquals(
            RecoveryAssessment.Unverified(setOf(RecoveryIssue.MNEMONIC_MISMATCH)),
            WalletRootSecretValidator.validateTon(normalizedText, typedKey.publicKey).assessment
        )

        // The reverse (a stored non-canonical text whose key only the
        // normalized words derive) is recognised, and still never rewritten.
        val canonicalKey = Keypair(publicKey = canonical.publicKey, privateKey = canonical.privateKey)
        val typedText = TonSecrets(
            seed = trailing.typedText.encodeToByteArray(),
            tonKeypair = canonicalKey
        ).toHexString()
        assertEquals(
            ValidatedRootSecret(
                canonical = typedText,
                assessment = RecoveryAssessment.VerifiedAsTyped(TonPhraseVariant.NORMALIZED)
            ),
            WalletRootSecretValidator.validateTon(typedText, canonicalKey.publicKey)
        )
    }

    @Test
    fun tonNonUtf8SeedIsUnverifiedNotCorrupt() {
        val keypair = Keypair(
            publicKey = ByteArray(32) { (it + 41).toByte() },
            privateKey = privateKey(32)
        )
        val cryptography = TestCryptography(deriveTonPublic = { keypair.publicKey })
        val engine = WalletRootSecretValidationEngine(cryptography)
        listOf(
            byteArrayOf(0xff.toByte(), 0xfe.toByte(), 0x41) to RecoveryIssue.MNEMONIC_NOT_UTF8,
            ByteArray(0) to RecoveryIssue.MNEMONIC_WORDCOUNT
        ).forEach { (seed, issue) ->
            val encoded = TonSecrets(seed = seed, tonKeypair = keypair).toHexString()

            assertEquals(
                encoded,
                engine.validateTonAndSanitize(
                    encoded = encoded,
                    expectedPublicKey = keypair.publicKey
                )
            )
            assertEquals(
                RecoveryAssessment.Unverified(setOf(issue)),
                engine.validateTon(
                    encoded = encoded,
                    expectedPublicKey = keypair.publicKey
                ).assessment
            )
        }
        assertEquals(0, cryptography.recoveryCalls)
    }

    @Test
    fun uppercaseHexAndMissingPrefixAreNormalized() {
        val stored = ed25519Keypair(variant = 33)
        val engine = WalletRootSecretValidationEngine(TestCryptography())
        val canonical = SubstrateSecrets(
            substrateKeyPair = stored,
            seed = SEED,
            substrateDerivationPath = HARD_PATH
        ).toHexString()
        val digits = canonical.removePrefix("0x")

        listOf(
            digits,
            "0X$digits",
            "0x${digits.uppercase()}",
            digits.uppercase(),
            " \n$canonical\t "
        ).forEach { persisted ->
            assertEquals(persisted, canonical, engine.validateSubstrateAndSanitize(stored, persisted))
        }
        listOf(
            canonical.dropLast(1),
            canonical.replaceRange(4, 5, "g"),
            "0x"
        ).forEach { corrupt ->
            assertThrows(corrupt, WalletRootSecretCorruptionException::class.java) {
                engine.validateSubstrateAndSanitize(stored, corrupt)
            }
        }

        val ton = Keypair(
            publicKey = ByteArray(32) { (it + 51).toByte() },
            privateKey = privateKey(34)
        )
        val tonCanonical = TonSecrets(
            seed = MNEMONIC.encodeToByteArray(),
            tonKeypair = ton
        ).toHexString()
        assertEquals(
            tonCanonical,
            WalletRootSecretValidationEngine(
                TestCryptography(deriveTonPublic = { ton.publicKey })
            ).validateTonAndSanitize(
                encoded = tonCanonical.removePrefix("0x").uppercase(),
                expectedPublicKey = ton.publicKey
            )
        )
    }

    @Test
    fun runtimeGetNeverInvokesRecoveryCryptography() {
        val substrate = ed25519Keypair(variant = 35)
        val ethereum = Keypair(
            publicKey = ByteArray(33) { (it + 31).toByte() },
            privateKey = privateKey(36)
        )
        val ton = Keypair(
            publicKey = ByteArray(32) { (it + 61).toByte() },
            privateKey = privateKey(37)
        )
        val cryptography = TestCryptography(
            deriveEthereumPublic = { ethereum.publicKey },
            deriveTonPublic = { ton.publicKey },
            recoverSubstrate = { _ -> substrate.copy() },
            recoverEthereum = { ethereum.copy() },
            recoverTon = { ton.copy() }
        )
        val engine = WalletRootSecretValidationEngine(cryptography)
        val substrateEncoded = SubstrateSecrets(
            substrateKeyPair = substrate,
            entropy = ENTROPY,
            seed = SEED,
            substrateDerivationPath = HARD_PATH
        ).toHexString()
        val ethereumEncoded = EthereumSecrets(
            entropy = ENTROPY,
            seed = ethereum.privateKey,
            ethereumKeypair = ethereum,
            ethereumDerivationPath = BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH
        ).toHexString()
        val tonEncoded = TonSecrets(
            seed = MNEMONIC.encodeToByteArray(),
            tonKeypair = ton
        ).toHexString()

        engine.validateSubstrateAndSanitize(substrate, substrateEncoded)
        engine.validateEthereumAndSanitize(
            encoded = ethereumEncoded,
            expectedPublicKey = ethereum.publicKey,
            expectedAddress = ByteArray(20)
        )
        engine.validateTonAndSanitize(
            encoded = tonEncoded,
            expectedPublicKey = ton.publicKey
        )
        engine.validateLegacySubstrateSource(
            source = SecuritySource.Specified.Mnemonic(
                seed = SEED,
                keypair = substrate,
                mnemonic = MNEMONIC,
                derivationPath = HARD_PATH
            ),
            expectedPublicKey = substrate.publicKey,
            expectedCryptoType = CryptoType.ED25519,
            expectedAccountId = substrate.publicKey
        )
        assertEquals(0, cryptography.recoveryCalls)

        assertEquals(
            RecoveryAssessment.Verified,
            engine.validateSubstrate(
                encoded = substrateEncoded,
                expectedPublicKey = substrate.publicKey,
                expectedCryptoType = CryptoType.ED25519,
                expectedAccountId = substrate.publicKey
            ).assessment
        )
        assertEquals(
            RecoveryAssessment.Verified,
            engine.validateEthereum(
                encoded = ethereumEncoded,
                expectedPublicKey = ethereum.publicKey,
                expectedAddress = ByteArray(20)
            ).assessment
        )
        assertEquals(
            RecoveryAssessment.Verified,
            engine.validateTon(
                encoded = tonEncoded,
                expectedPublicKey = ton.publicKey
            ).assessment
        )
        assertEquals(4, cryptography.recoveryCalls)
    }

    @Test
    fun recoveryProviderFailureIsGlobalAndNeverAnAssessment() {
        val stored = ed25519Keypair(variant = 38)
        val providerFailure = ProviderException("Injected seed recovery failure")
        val engine = WalletRootSecretValidationEngine(
            TestCryptography(recoverSubstrate = { _ -> throw providerFailure })
        )
        val encoded = SubstrateSecrets(
            substrateKeyPair = stored,
            seed = SEED
        ).toHexString()

        assertEquals(encoded, engine.validateSubstrateAndSanitize(stored, encoded))
        val thrown = assertThrows(WalletSecureStorageUnavailableException::class.java) {
            engine.validateSubstrate(
                encoded = encoded,
                expectedPublicKey = stored.publicKey,
                expectedCryptoType = CryptoType.ED25519,
                expectedAccountId = stored.publicKey
            )
        }
        assertSame(providerFailure, thrown.cause)
    }

    @Test
    fun requireVerifiedRecoveryAcceptsOnlyVerifiedOrAbsentMaterial() {
        listOf(RecoveryAssessment.None, RecoveryAssessment.Verified).forEach {
            assertEquals("0x00", ValidatedRootSecret("0x00", it).requireVerifiedRecovery())
        }
        listOf(
            RecoveryAssessment.VerifiedAsTyped(TonPhraseVariant.AS_TYPED),
            RecoveryAssessment.VerifiedAsTyped(TonPhraseVariant.NORMALIZED),
            RecoveryAssessment.Unverified(setOf(RecoveryIssue.SEED_MISMATCH))
        ).forEach {
            assertThrows(WalletRootSecretCorruptionException::class.java) {
                ValidatedRootSecret("0x00", it).requireVerifiedRecovery()
            }
        }
    }

    private fun assertTonVariant(variant: String, expected: RecoveryAssessment) {
        val vectors = WalletSecretVectors.ton().filter { it.variant == variant }
        // Three TON-native and three BIP39 phrases, as 4.2.0 stored them.
        assertEquals(6, vectors.size)
        vectors.forEach { vector ->
            val keypair = Keypair(publicKey = vector.publicKey, privateKey = vector.privateKey)
            val encoded = TonSecrets(
                seed = vector.typedText.encodeToByteArray(),
                tonKeypair = keypair
            ).toHexString()

            assertEquals(
                encoded,
                WalletRootSecretValidator.validateTonAndSanitize(encoded, keypair.publicKey)
            )
            assertEquals(
                vector.typedText,
                ValidatedRootSecret(encoded, expected),
                WalletRootSecretValidator.validateTon(encoded, keypair.publicKey)
            )
        }
    }

    private fun WalletRootSecretValidationEngine.validateSubstrateAndSanitize(
        stored: FearlessKeypair,
        encoded: String
    ): String = validateSubstrateAndSanitize(
        encoded = encoded,
        expectedPublicKey = stored.publicKey,
        expectedCryptoType = CryptoType.ED25519,
        expectedAccountId = stored.publicKey
    )

    private fun FearlessKeypair.copy(): FearlessKeypair = Keypair(
        publicKey = publicKey.copyOf(),
        privateKey = privateKey.copyOf()
    )

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
        private val recoverSubstrate: ((CryptoType) -> FearlessKeypair)? = null,
        private val recoverSubstrateEntropy: ((CryptoType) -> FearlessKeypair)? = recoverSubstrate,
        private val recoverSubstrateSeed: ((CryptoType) -> FearlessKeypair)? = recoverSubstrate,
        private val recoverEthereum: (() -> FearlessKeypair)? = null,
        private val recoverTon: ((List<String>) -> FearlessKeypair)? = null
    ) : WalletRootCryptography {

        var recoveryCalls = 0
            private set

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
            cryptoType: CryptoType,
            passwordRule: SubstratePasswordRule
        ): FearlessKeypair {
            recoveryCalls++
            return recoverSubstrateEntropy?.invoke(cryptoType)
                ?: error("Unexpected Substrate entropy recovery")
        }

        override fun recoverSubstrateFromSeed(
            seed: ByteArray,
            decodedPath: JunctionDecoder.DecodeResult?,
            cryptoType: CryptoType
        ): FearlessKeypair {
            recoveryCalls++
            return recoverSubstrateSeed?.invoke(cryptoType)
                ?: error("Unexpected Substrate seed recovery")
        }

        override fun recoverSubstrateFromMnemonic(
            mnemonicText: String,
            decodedPath: JunctionDecoder.DecodeResult?,
            cryptoType: CryptoType
        ): FearlessKeypair {
            recoveryCalls++
            return recoverSubstrate?.invoke(cryptoType)
                ?: error("Unexpected Substrate mnemonic recovery")
        }

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
        ): FearlessKeypair {
            recoveryCalls++
            return recoverEthereum?.invoke()
                ?: error("Unexpected Ethereum entropy recovery")
        }

        override fun deriveTonPublicKey(
            privateKey: ByteArray
        ): ByteArray = deriveTonPublic(privateKey)

        override fun recoverTonFromMnemonic(
            mnemonicWords: List<String>
        ): FearlessKeypair {
            recoveryCalls++
            return recoverTon?.invoke(mnemonicWords)
                ?: error("Unexpected TON mnemonic recovery")
        }
    }

    private companion object {
        // Ends in a soft junction, the cohort whose SR25519 nonce is random.
        const val SOFT_PATH = "//polkadot/0"
        const val HARD_PATH = "//polkadot//0"
        const val MNEMONIC =
            "bottom drive obey lake curtain smoke basket hold race lonely fit walk"
        val ENTROPY = ByteArray(16) { (it * 7 + 3).toByte() }
        val SEED = ByteArray(32) { (it * 3 + 1).toByte() }

        fun privateKey(variant: Int): ByteArray {
            return ByteArray(32) { index ->
                ((index + 1) * 5 + variant).toByte()
            }
        }

        fun substratePublicKey(variant: Int): ByteArray {
            return ByteArray(32) { index -> (index * 11 + variant).toByte() }
        }

        fun nonce(variant: Int): ByteArray {
            return ByteArray(32) { index -> (index * 13 + variant).toByte() }
        }

        /** Derives an ED25519 key from a phrase the way a 1.x mnemonic import did. */
        fun legacyMnemonicKeypair(mnemonic: String): FearlessKeypair {
            return SubstrateKeypairFactory.generate(
                encryptionType = EncryptionType.ED25519,
                seed = SubstrateSeedFactory.deriveSeed32(mnemonic, null).seed,
                junctions = emptyList()
            )
        }

        fun ed25519Keypair(variant: Int): FearlessKeypair {
            return Keypair(
                publicKey = substratePublicKey(variant),
                privateKey = privateKey(variant)
            )
        }

        fun sr25519Keypair(nonce: ByteArray): FearlessKeypair {
            return Keypair(
                publicKey = substratePublicKey(1),
                privateKey = privateKey(21),
                nonce = nonce
            )
        }
    }
}
