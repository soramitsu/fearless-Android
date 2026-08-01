package jp.co.soramitsu.account.api.domain.interfaces

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import jp.co.soramitsu.account.api.domain.model.Account
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.Signer
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignWithAccountCryptoRoutingTest {

    @Test
    fun sr25519RootSignsWithIdentityBoundRootKeyAndCrypto() {
        assertRootSigningUsesIdentityBoundKey(
            cryptoType = CryptoType.SR25519,
            expectedKeypair = keypair(31, EncryptionType.SR25519),
            wrongKeypair = keypair(32, EncryptionType.SR25519)
        )
    }

    @Test
    fun sr25519ChildSignsWithBoundChildKeyWithoutReadingRootSecrets() {
        assertChildSigningUsesBoundKeyOnly(
            cryptoType = CryptoType.SR25519,
            rootKeypair = keypair(33, EncryptionType.SR25519),
            childKeypair = keypair(34, EncryptionType.SR25519),
            unrelatedChildKeypair = keypair(35, EncryptionType.SR25519)
        )
    }

    @Test
    fun substrateEcdsaRootSignsWithIdentityBoundRootKeyAndCrypto() {
        assertRootSigningUsesIdentityBoundKey(
            cryptoType = CryptoType.ECDSA,
            expectedKeypair = keypair(36, EncryptionType.ECDSA),
            wrongKeypair = keypair(37, EncryptionType.ECDSA)
        )
    }

    @Test
    fun substrateEcdsaChildSignsWithBoundChildKeyWithoutReadingRootSecrets() {
        assertChildSigningUsesBoundKeyOnly(
            cryptoType = CryptoType.ECDSA,
            rootKeypair = keypair(38, EncryptionType.ECDSA),
            childKeypair = keypair(39, EncryptionType.ECDSA),
            unrelatedChildKeypair = keypair(40, EncryptionType.ECDSA)
        )
    }

    private fun assertRootSigningUsesIdentityBoundKey(
        cryptoType: CryptoType,
        expectedKeypair: Keypair,
        wrongKeypair: Keypair
    ) {
        val metaAccount = metaAccount(
            rootKeypair = expectedKeypair,
            rootCryptoType = cryptoType
        )
        var rootSecretReads = 0
        var chainSecretReads = 0
        var legacySecretReads = 0
        val repository = repository { method, _ ->
            when (method.name) {
                "findMetaAccount" -> metaAccount
                "getSubstrateSecrets" -> {
                    rootSecretReads += 1
                    SubstrateSecrets(expectedKeypair)
                }

                "getChainAccountSecrets" -> {
                    chainSecretReads += 1
                    ChainAccountSecrets(wrongKeypair)
                }

                "getSecuritySource" -> {
                    legacySecretReads += 1
                    error("A present V3 root must not read a V1 secret")
                }

                else -> unexpectedCall(method)
            }
        }

        val signature = runBlocking {
            repository.signWithAccount(
                account = account(metaAccount.substrateAccountId!!, cryptoType),
                message = MESSAGE
            )
        }

        assertSignatureUsesKeypair(
            cryptoType = cryptoType,
            signature = signature,
            expectedKeypair = expectedKeypair,
            wrongKeypair = wrongKeypair
        )
        assertEquals(1, rootSecretReads)
        assertEquals(0, chainSecretReads)
        assertEquals(0, legacySecretReads)
    }

    private fun assertChildSigningUsesBoundKeyOnly(
        cryptoType: CryptoType,
        rootKeypair: Keypair,
        childKeypair: Keypair,
        unrelatedChildKeypair: Keypair
    ) {
        val childAccount = chainAccount(childKeypair, cryptoType)
        val unrelatedChildAccount = chainAccount(unrelatedChildKeypair, cryptoType)
        val metaAccount = metaAccount(
            rootKeypair = rootKeypair,
            rootCryptoType = cryptoType,
            chainAccounts = linkedMapOf(
                CHAIN_ID to childAccount,
                UNRELATED_CHAIN_ID to unrelatedChildAccount
            )
        )
        var childSecretReads = 0
        var rootSecretReads = 0
        var legacySecretReads = 0
        val repository = repository { method, args ->
            when (method.name) {
                "findMetaAccount" -> metaAccount
                "getChainAccountSecrets" -> {
                    childSecretReads += 1
                    assertEquals(META_ID, args?.getOrNull(0))
                    assertEquals(CHAIN_ID, args?.getOrNull(1))
                    ChainAccountSecrets(childKeypair)
                }

                "getSubstrateSecrets" -> {
                    rootSecretReads += 1
                    SubstrateSecrets(rootKeypair)
                }

                "getSecuritySource" -> {
                    legacySecretReads += 1
                    error("A child signing request must never read a root V1 secret")
                }

                else -> unexpectedCall(method)
            }
        }

        val signature = runBlocking {
            repository.signWithAccount(
                account = account(childAccount.accountId, cryptoType),
                message = MESSAGE
            )
        }

        assertSignatureUsesKeypair(
            cryptoType = cryptoType,
            signature = signature,
            expectedKeypair = childKeypair,
            wrongKeypair = rootKeypair
        )
        assertSignatureDoesNotUseKeypair(
            cryptoType = cryptoType,
            signature = signature,
            wrongKeypair = unrelatedChildKeypair
        )
        assertEquals(1, childSecretReads)
        assertEquals(0, rootSecretReads)
        assertEquals(0, legacySecretReads)
    }

    private fun assertSignatureUsesKeypair(
        cryptoType: CryptoType,
        signature: ByteArray,
        expectedKeypair: Keypair,
        wrongKeypair: Keypair
    ) {
        when (cryptoType) {
            CryptoType.SR25519 -> {
                assertTrue(
                    Signer.verifySr25519(
                        message = MESSAGE,
                        signature = signature,
                        publicKeyBytes = expectedKeypair.publicKey
                    )
                )
                assertFalse(
                    Signer.verifySr25519(
                        message = MESSAGE,
                        signature = signature,
                        publicKeyBytes = wrongKeypair.publicKey
                    )
                )
            }

            CryptoType.ECDSA -> {
                val encryption = MultiChainEncryption.Substrate(EncryptionType.ECDSA)
                assertArrayEquals(
                    Signer.sign(encryption, MESSAGE, expectedKeypair).signature,
                    signature
                )
                assertFalse(
                    Signer.sign(encryption, MESSAGE, wrongKeypair)
                        .signature
                        .contentEquals(signature)
                )
            }

            CryptoType.ED25519 -> error("This native routing suite covers SR25519 and ECDSA")
        }
    }

    private fun assertSignatureDoesNotUseKeypair(
        cryptoType: CryptoType,
        signature: ByteArray,
        wrongKeypair: Keypair
    ) {
        when (cryptoType) {
            CryptoType.SR25519 -> assertFalse(
                Signer.verifySr25519(
                    message = MESSAGE,
                    signature = signature,
                    publicKeyBytes = wrongKeypair.publicKey
                )
            )

            CryptoType.ECDSA -> assertFalse(
                Signer.sign(
                    MultiChainEncryption.Substrate(EncryptionType.ECDSA),
                    MESSAGE,
                    wrongKeypair
                ).signature.contentEquals(signature)
            )

            CryptoType.ED25519 -> error("This native routing suite covers SR25519 and ECDSA")
        }
    }

    private fun keypair(seedByte: Int, encryptionType: EncryptionType): Keypair {
        return SubstrateKeypairFactory.generate(
            encryptionType = encryptionType,
            seed = ByteArray(32) { seedByte.toByte() },
            junctions = emptyList()
        )
    }

    private fun chainAccount(
        keypair: Keypair,
        cryptoType: CryptoType
    ): MetaAccount.ChainAccount {
        return MetaAccount.ChainAccount(
            metaId = META_ID,
            chain = null,
            publicKey = keypair.publicKey,
            accountId = keypair.publicKey.substrateAccountId(),
            cryptoType = cryptoType,
            accountName = "Child"
        )
    }

    private fun metaAccount(
        rootKeypair: Keypair,
        rootCryptoType: CryptoType,
        chainAccounts: Map<String, MetaAccount.ChainAccount> = emptyMap()
    ): MetaAccount {
        return MetaAccount(
            id = META_ID,
            chainAccounts = chainAccounts,
            favoriteChains = emptyMap(),
            substratePublicKey = rootKeypair.publicKey,
            substrateCryptoType = rootCryptoType,
            substrateAccountId = rootKeypair.publicKey.substrateAccountId(),
            ethereumAddress = null,
            ethereumPublicKey = null,
            tonPublicKey = null,
            isSelected = true,
            isBackedUp = true,
            googleBackupAddress = null,
            name = "Wallet",
            initialized = true
        )
    }

    private fun account(accountId: ByteArray, cryptoType: CryptoType): Account {
        return Account(
            address = "test-address",
            name = "Wallet",
            accountIdHex = accountId.toHexString(),
            cryptoType = cryptoType,
            position = 0
        )
    }

    private fun repository(
        handler: (Method, Array<out Any?>?) -> Any?
    ): AccountRepository {
        return Proxy.newProxyInstance(
            AccountRepository::class.java.classLoader,
            arrayOf(AccountRepository::class.java)
        ) { proxy, method, args ->
            when (method.name) {
                "toString" -> "AccountRepositoryProxy"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> handler(method, args)
            }
        } as AccountRepository
    }

    private fun unexpectedCall(method: Method): Nothing {
        throw UnsupportedOperationException("Unexpected method call in test: ${method.name}")
    }

    private companion object {
        const val META_ID = 42L
        const val CHAIN_ID = "test-chain"
        const val UNRELATED_CHAIN_ID = "unrelated-test-chain"
        val MESSAGE = "sign-with-account-crypto-routing".encodeToByteArray()
    }
}
