package jp.co.soramitsu.common.data.secrets.v2

import jp.co.soramitsu.common.data.secrets.v1.Keypair
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException
import jp.co.soramitsu.common.utils.DEFAULT_DERIVATION_PATH
import jp.co.soramitsu.common.utils.deriveSeed32
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.junction.BIP32JunctionDecoder
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
    fun rejectsEthereumMnemonicWhosePathDerivesAnotherKey() {
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
        val encoded = ChainAccountSecrets(
            keyPair = keypair,
            entropy = mnemonic.entropy,
            seed = seed,
            derivationPath = "m/44'/60'/0'/0/1"
        ).toHexString()

        assertThrows(ChainAccountSecretCorruptionException::class.java) {
            ChainAccountSecretValidator.validateAndSanitize(
                encoded = encoded,
                expectedAccountId =
                keypair.publicKey.ethereumAddressFromPublicKey(),
                expectedPublicKey = keypair.publicKey,
                expectedCryptoType = CryptoType.ECDSA
            )
        }
    }

    private companion object {
        const val MNEMONIC_WORDS =
            "bottom drive obey lake curtain smoke basket hold race lonely fit walk"
    }
}
