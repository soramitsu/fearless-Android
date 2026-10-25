package jp.co.soramitsu.common.data.secrets.v2

import jp.co.soramitsu.common.data.secrets.v1.Keypair
import jp.co.soramitsu.common.data.secrets.v3.RecoveryAssessment
import jp.co.soramitsu.common.data.secrets.v3.RecoveryIssue
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException
import jp.co.soramitsu.common.utils.DEFAULT_DERIVATION_PATH
import jp.co.soramitsu.common.utils.deriveSeed32
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.junction.BIP32JunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.junction.JunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair as FearlessKeypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.encrypt.seed.ethereum.EthereumSeedFactory
import jp.co.soramitsu.fearless_utils.scale.toHexString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ChainAccountSecretValidatorTest {

    @Test
    fun validatesEdAndSubstrateEcdsaChainAccountsOnJvm() {
        listOf(
            CryptoType.ED25519 to EncryptionType.ED25519,
            CryptoType.ECDSA to EncryptionType.ECDSA
        ).forEachIndexed { index, (cryptoType, encryptionType) ->
            val seed = ByteArray(32) { (index + 1).toByte() }
            val keypair = SubstrateKeypairFactory.generate(
                encryptionType = encryptionType,
                seed = seed,
                junctions = emptyList()
            )
            val encoded = ChainAccountSecrets(
                keyPair = keypair,
                seed = seed,
                derivationPath = ""
            ).toHexString()

            val canonical = ChainAccountSecretValidator.validateAndSanitize(
                encoded = encoded,
                expectedAccountId = keypair.publicKey.substrateAccountId(),
                expectedPublicKey = keypair.publicKey,
                expectedCryptoType = cryptoType
            )

            val decoded = ChainAccountSecrets.read(canonical)
            assertArrayEquals(
                keypair.privateKey,
                decoded[ChainAccountSecrets.Keypair][KeyPairSchema.PrivateKey]
            )
            assertArrayEquals(seed, decoded[ChainAccountSecrets.Seed])
        }
    }

    @Test
    fun validatesEthereumDirectSeedChainAccountWithTwentyByteAddress() {
        val privateKey = ByteArray(32).also { it[it.lastIndex] = 1 }
        val keypair = EthereumKeypairFactory.createWithPrivateKey(privateKey)
        val encoded = ChainAccountSecrets(
            keyPair = keypair,
            seed = privateKey,
            derivationPath = BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH
        ).toHexString()

        val canonical = ChainAccountSecretValidator.validateAndSanitize(
            encoded = encoded,
            expectedAccountId =
            keypair.publicKey.ethereumAddressFromPublicKey(),
            expectedPublicKey = keypair.publicKey,
            expectedCryptoType = CryptoType.ECDSA
        )

        val decoded = ChainAccountSecrets.read(canonical)
        assertArrayEquals(
            privateKey,
            decoded[ChainAccountSecrets.Keypair][KeyPairSchema.PrivateKey]
        )
        assertEquals(
            BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH,
            decoded[ChainAccountSecrets.DerivationPath]
        )
    }

    @Test
    fun validatesEthereumMnemonicRecoveryAgainstBip32Path() {
        val mnemonic = MnemonicCreator.fromWords(MNEMONIC_WORDS)
        val decodedPath = BIP32JunctionDecoder.decode(
            BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH
        )
        val seed = EthereumSeedFactory.deriveSeed32(
            mnemonicWords = mnemonic.words,
            password = decodedPath.password
        ).seed
        val keypair = EthereumKeypairFactory.generate(
            seed = seed,
            junctions = decodedPath.junctions
        )
        val encoded = ChainAccountSecrets(
            keyPair = keypair,
            entropy = mnemonic.entropy,
            seed = seed,
            derivationPath = BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH
        ).toHexString()

        val canonical = ChainAccountSecretValidator.validateAndSanitize(
            encoded = encoded,
            expectedAccountId =
            keypair.publicKey.ethereumAddressFromPublicKey(),
            expectedPublicKey = keypair.publicKey,
            expectedCryptoType = CryptoType.ECDSA
        )

        val decoded = ChainAccountSecrets.read(canonical)
        assertArrayEquals(mnemonic.entropy, decoded[ChainAccountSecrets.Entropy])
        assertArrayEquals(seed, decoded[ChainAccountSecrets.Seed])
    }

    @Test
    fun rejectsEthereumAddressThatDoesNotMatchPublicKey() {
        val privateKey = ByteArray(32).also { it[it.lastIndex] = 1 }
        val keypair = EthereumKeypairFactory.createWithPrivateKey(privateKey)
        val encoded = ChainAccountSecrets(
            keyPair = keypair,
            seed = privateKey
        ).toHexString()
        val wrongAddress =
            keypair.publicKey.ethereumAddressFromPublicKey().clone().also {
                it[0] = (it[0].toInt() xor 1).toByte()
            }

        assertThrows(WalletPublicIdentityIntegrityException::class.java) {
            ChainAccountSecretValidator.validateAndSanitize(
                encoded = encoded,
                expectedAccountId = wrongAddress,
                expectedPublicKey = keypair.publicKey,
                expectedCryptoType = CryptoType.ECDSA
            )
        }
    }

    @Test
    fun rejectsNonEcdsaCryptoTypeForEthereumAddress() {
        val privateKey = ByteArray(32).also { it[it.lastIndex] = 1 }
        val keypair = EthereumKeypairFactory.createWithPrivateKey(privateKey)
        val encoded = ChainAccountSecrets(
            keyPair = keypair,
            seed = privateKey
        ).toHexString()

        assertThrows(WalletPublicIdentityIntegrityException::class.java) {
            ChainAccountSecretValidator.validateAndSanitize(
                encoded = encoded,
                expectedAccountId =
                keypair.publicKey.ethereumAddressFromPublicKey(),
                expectedPublicKey = keypair.publicKey,
                expectedCryptoType = CryptoType.ED25519
            )
        }
    }

    @Test
    fun rejectsInvalidEthereumPrivateScalarAsLocalCorruption() {
        val validPrivateKey = ByteArray(32).also { it[it.lastIndex] = 1 }
        val publicKey = EthereumKeypairFactory
            .createWithPrivateKey(validPrivateKey)
            .publicKey
        val encoded = ChainAccountSecrets(
            keyPair = Keypair(
                publicKey = publicKey,
                privateKey = ByteArray(32)
            )
        ).toHexString()

        assertThrows(ChainAccountSecretCorruptionException::class.java) {
            ChainAccountSecretValidator.validateAndSanitize(
                encoded = encoded,
                expectedAccountId = publicKey.ethereumAddressFromPublicKey(),
                expectedPublicKey = publicKey,
                expectedCryptoType = CryptoType.ECDSA
            )
        }
    }

    @Test
    fun ethereumMnemonicWhosePathCannotRederiveTheKeyStillSigns() {
        val mnemonic = MnemonicCreator.fromWords(MNEMONIC_WORDS)
        val expectedPath = BIP32JunctionDecoder.decode(
            BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH
        )
        val seed = EthereumSeedFactory.deriveSeed32(
            mnemonicWords = mnemonic.words,
            password = expectedPath.password
        ).seed
        val keypair = EthereumKeypairFactory.generate(
            seed = seed,
            junctions = expectedPath.junctions
        )
        val address = keypair.publicKey.ethereumAddressFromPublicKey()
        listOf(
            "m/44'/60'/0'/0/1" to setOf(RecoveryIssue.PATH_MALFORMED),
            "//44//60//0/0/first" to setOf(RecoveryIssue.PATH_MALFORMED),
            "//44//60//0/0/2147483648" to setOf(RecoveryIssue.PATH_MALFORMED),
            "//44//60//0/0/1" to setOf(RecoveryIssue.ENTROPY_MISMATCH, RecoveryIssue.SEED_MISMATCH),
            null to setOf(RecoveryIssue.ETH_ENTROPY_WITHOUT_PATH),
            "/" + "1".repeat(2_048) to setOf(RecoveryIssue.PATH_OVERSIZED)
        ).forEach { (path, issues) ->
            val encoded = ChainAccountSecrets(
                keyPair = keypair,
                entropy = mnemonic.entropy,
                seed = seed,
                derivationPath = path
            ).toHexString()

            // The keypair is the durable identity: signing is unaffected.
            assertEquals(
                encoded,
                ChainAccountSecretValidator.validateAndSanitize(
                    encoded = encoded,
                    expectedAccountId = address,
                    expectedPublicKey = keypair.publicKey,
                    expectedCryptoType = CryptoType.ECDSA
                )
            )
            val validated = ChainAccountSecretValidator.validate(
                encoded = encoded,
                expectedAccountId = address,
                expectedPublicKey = keypair.publicKey,
                expectedCryptoType = CryptoType.ECDSA
            )
            assertEquals(path, RecoveryAssessment.Unverified(issues), validated.assessment)
            assertThrows(ChainAccountSecretCorruptionException::class.java) {
                validated.requireVerifiedRecovery()
            }
        }
    }

    @Test
    fun ethereumJsonImportSeedThatIsNotThePrivateKeyIsUnverified() {
        // 3.x importChainFromJson stored the keystore's seed field, which
        // need not be the private key the keypair was created from (C06).
        val privateKey = ByteArray(32).also { it[it.lastIndex] = 5 }
        val keypair = EthereumKeypairFactory.createWithPrivateKey(privateKey)
        val address = keypair.publicKey.ethereumAddressFromPublicKey()
        val encoded = ChainAccountSecrets(
            keyPair = keypair,
            seed = ByteArray(32) { 6 }
        ).toHexString()

        assertEquals(
            encoded,
            ChainAccountSecretValidator.validateAndSanitize(
                encoded = encoded,
                expectedAccountId = address,
                expectedPublicKey = keypair.publicKey,
                expectedCryptoType = CryptoType.ECDSA
            )
        )
        assertEquals(
            RecoveryAssessment.Unverified(setOf(RecoveryIssue.SEED_MISMATCH)),
            ChainAccountSecretValidator.validate(
                encoded = encoded,
                expectedAccountId = address,
                expectedPublicKey = keypair.publicKey,
                expectedCryptoType = CryptoType.ECDSA
            ).assessment
        )
        assertEquals(
            ValidatedChainSecret(
                canonical = ChainAccountSecrets(keyPair = keypair).toHexString(),
                assessment = RecoveryAssessment.None
            ),
            ChainAccountSecretValidator.validate(
                encoded = ChainAccountSecrets(keyPair = keypair).toHexString(),
                expectedAccountId = address,
                expectedPublicKey = keypair.publicKey,
                expectedCryptoType = CryptoType.ECDSA
            )
        )
    }

    @Test
    fun runtimeReadNeverInvokesRecoveryCryptographyAndHexIsNormalized() {
        val stored = Keypair(
            publicKey = substratePublicKey(4),
            privateKey = privateKey(4)
        )
        val cryptography = TestCryptography(
            recover = { _ ->
                Keypair(
                    publicKey = stored.publicKey.copyOf(),
                    privateKey = stored.privateKey.copyOf()
                )
            }
        )
        val engine = ChainAccountSecretValidationEngine(cryptography)
        val encoded = ChainAccountSecrets(
            keyPair = stored,
            entropy = ENTROPY,
            seed = SEED,
            derivationPath = HARD_PATH
        ).toHexString()

        listOf(encoded, encoded.removePrefix("0x").uppercase()).forEach { persisted ->
            assertEquals(
                encoded,
                engine.validateAndSanitize(
                    encoded = persisted,
                    expectedAccountId = stored.publicKey,
                    expectedPublicKey = stored.publicKey,
                    expectedCryptoType = CryptoType.ED25519
                )
            )
        }
        assertEquals(0, cryptography.recoveryCalls)

        assertEquals(
            ValidatedChainSecret(encoded, RecoveryAssessment.Verified),
            engine.validate(
                encoded = encoded,
                expectedAccountId = stored.publicKey,
                expectedPublicKey = stored.publicKey,
                expectedCryptoType = CryptoType.ED25519
            )
        )
        assertEquals(2, cryptography.recoveryCalls)
    }

    @Test
    fun sr25519RecoveryAcceptsDifferentNonceWithSameScalarAndPublic() {
        val stored = sr25519Keypair(nonce = nonce(1))
        val recoveredNonces = mutableListOf<ByteArray>()
        val engine = ChainAccountSecretValidationEngine(
            TestCryptography(
                recover = { _ ->
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
        val encoded = ChainAccountSecrets(
            keyPair = stored,
            entropy = ENTROPY,
            seed = SEED,
            derivationPath = SOFT_PATH
        ).toHexString()

        listOf(stored.publicKey, null).forEach { expectedPublicKey ->
            val validated = engine.validate(
                encoded = encoded,
                expectedAccountId = stored.publicKey,
                expectedPublicKey = expectedPublicKey,
                expectedCryptoType = CryptoType.SR25519
            )

            assertEquals(ValidatedChainSecret(encoded, RecoveryAssessment.Verified), validated)
            assertArrayEquals(
                nonce(1),
                ChainAccountSecrets.read(validated.canonical)[ChainAccountSecrets.Keypair][KeyPairSchema.Nonce]
            )
        }
        assertEquals(4, recoveredNonces.size)

        // Rows without a recorded crypto type infer SR25519 from the nonce.
        assertEquals(
            ValidatedChainSecret(encoded, RecoveryAssessment.Verified),
            engine.validate(
                encoded = encoded,
                expectedAccountId = stored.publicKey,
                expectedPublicKey = null,
                expectedCryptoType = null
            )
        )
    }

    @Test
    fun sr25519RecoveryRejectsDifferentScalarOrPublicKey() {
        val stored = sr25519Keypair(nonce = nonce(1))
        val encoded = ChainAccountSecrets(
            keyPair = stored,
            seed = SEED,
            derivationPath = SOFT_PATH
        ).toHexString()
        listOf(
            Keypair(
                publicKey = stored.publicKey.copyOf(),
                privateKey = privateKey(2),
                nonce = nonce(1)
            ),
            Keypair(
                publicKey = substratePublicKey(2),
                privateKey = stored.privateKey.copyOf(),
                nonce = nonce(1)
            )
        ).forEach { recovered ->
            val engine = ChainAccountSecretValidationEngine(
                TestCryptography(recover = { _ -> recovered })
            )

            assertEquals(
                RecoveryAssessment.Unverified(setOf(RecoveryIssue.SEED_MISMATCH)),
                engine.validate(
                    encoded = encoded,
                    expectedAccountId = stored.publicKey,
                    expectedPublicKey = stored.publicKey,
                    expectedCryptoType = CryptoType.SR25519
                ).assessment
            )
        }
    }

    @Test
    fun sr25519StoredNonceMustStillBeThirtyTwoBytes() {
        val stored = sr25519Keypair(nonce = ByteArray(31) { (it + 7).toByte() })
        val engine = ChainAccountSecretValidationEngine(
            TestCryptography(
                recover = { _ ->
                    Keypair(
                        publicKey = stored.publicKey.copyOf(),
                        privateKey = stored.privateKey.copyOf(),
                        nonce = nonce(2)
                    )
                }
            )
        )

        val encoded = ChainAccountSecrets(
            keyPair = stored,
            seed = SEED,
            derivationPath = SOFT_PATH
        ).toHexString()
        assertThrows(ChainAccountSecretCorruptionException::class.java) {
            engine.validateAndSanitize(
                encoded = encoded,
                expectedAccountId = stored.publicKey,
                expectedPublicKey = stored.publicKey,
                expectedCryptoType = CryptoType.SR25519
            )
        }
        assertThrows(ChainAccountSecretCorruptionException::class.java) {
            engine.validate(
                encoded = encoded,
                expectedAccountId = stored.publicKey,
                expectedPublicKey = stored.publicKey,
                expectedCryptoType = CryptoType.SR25519
            )
        }
    }

    @Test
    fun ed25519RecoveryStillRequiresNullNonce() {
        val stored = Keypair(
            publicKey = substratePublicKey(3),
            privateKey = privateKey(3)
        )
        val encoded = ChainAccountSecrets(
            keyPair = stored,
            entropy = ENTROPY,
            seed = SEED,
            derivationPath = HARD_PATH
        ).toHexString()
        val withNonce = ChainAccountSecretValidationEngine(
            TestCryptography(
                recover = { _ ->
                    Keypair(
                        publicKey = stored.publicKey.copyOf(),
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
            withNonce.validate(
                encoded = encoded,
                expectedAccountId = stored.publicKey,
                expectedPublicKey = stored.publicKey,
                expectedCryptoType = CryptoType.ED25519
            ).assessment
        )

        val withoutNonce = ChainAccountSecretValidationEngine(
            TestCryptography(
                recover = { _ ->
                    Keypair(
                        publicKey = stored.publicKey.copyOf(),
                        privateKey = stored.privateKey.copyOf()
                    )
                }
            )
        )
        assertEquals(
            ValidatedChainSecret(encoded, RecoveryAssessment.Verified),
            withoutNonce.validate(
                encoded = encoded,
                expectedAccountId = stored.publicKey,
                expectedPublicKey = stored.publicKey,
                expectedCryptoType = CryptoType.ED25519
            )
        )
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
                val encoded = ChainAccountSecrets(
                    keyPair = keypair,
                    seed = SEED,
                    derivationPath = SOFT_PATH
                ).toHexString()

                assertEquals(
                    encoded,
                    ChainAccountSecretValidator.validateAndSanitize(
                        encoded = encoded,
                        expectedAccountId = accountId,
                        expectedPublicKey = keypair.publicKey,
                        expectedCryptoType = cryptoType
                    )
                )
                assertEquals(
                    cryptoType.name,
                    ValidatedChainSecret(
                        canonical = encoded,
                        assessment = RecoveryAssessment.Unverified(setOf(RecoveryIssue.PATH_MALFORMED))
                    ),
                    ChainAccountSecretValidator.validate(
                        encoded = encoded,
                        expectedAccountId = accountId,
                        expectedPublicKey = keypair.publicKey,
                        expectedCryptoType = cryptoType
                    )
                )
            }
    }

    private class TestCryptography(
        private val recover: (CryptoType) -> FearlessKeypair
    ) : ChainAccountCryptography {

        var recoveryCalls = 0
            private set

        override fun deriveAccountId(
            publicKey: ByteArray,
            identityKind: ChainAccountIdentityKind
        ): ByteArray = publicKey.copyOf(32)

        override fun decodeDerivationPath(
            derivationPath: String,
            identityKind: ChainAccountIdentityKind
        ): JunctionDecoder.DecodeResult = SubstrateJunctionDecoder.decode(derivationPath)

        override fun provesOwnership(
            keypair: FearlessKeypair,
            cryptoType: CryptoType
        ): Boolean = true

        override fun recoverFromEntropy(
            entropy: ByteArray,
            decodedPath: JunctionDecoder.DecodeResult?,
            cryptoType: CryptoType,
            identityKind: ChainAccountIdentityKind
        ): FearlessKeypair {
            recoveryCalls++
            return recover(cryptoType)
        }

        override fun recoverFromSeed(
            seed: ByteArray,
            decodedPath: JunctionDecoder.DecodeResult?,
            cryptoType: CryptoType,
            identityKind: ChainAccountIdentityKind,
            mnemonicDerived: Boolean
        ): FearlessKeypair {
            recoveryCalls++
            return recover(cryptoType)
        }
    }

    private companion object {
        const val MNEMONIC_WORDS =
            "bottom drive obey lake curtain smoke basket hold race lonely fit walk"

        // Ends in a soft junction, the cohort whose SR25519 nonce is random.
        const val SOFT_PATH = "//polkadot/0"
        const val HARD_PATH = "//polkadot//0"
        val ENTROPY = ByteArray(16) { (it * 7 + 3).toByte() }
        val SEED = ByteArray(32) { (it * 3 + 1).toByte() }

        fun privateKey(variant: Int): ByteArray {
            return ByteArray(32) { index -> ((index + 1) * 5 + variant).toByte() }
        }

        fun substratePublicKey(variant: Int): ByteArray {
            return ByteArray(32) { index -> (index * 11 + variant).toByte() }
        }

        fun nonce(variant: Int): ByteArray {
            return ByteArray(32) { index -> (index * 13 + variant).toByte() }
        }

        fun sr25519Keypair(nonce: ByteArray): FearlessKeypair {
            return Keypair(
                publicKey = substratePublicKey(1),
                privateKey = privateKey(1),
                nonce = nonce
            )
        }
    }
}
