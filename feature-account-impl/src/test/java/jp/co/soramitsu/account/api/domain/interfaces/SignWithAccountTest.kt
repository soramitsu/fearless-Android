package jp.co.soramitsu.account.api.domain.interfaces

import java.lang.reflect.Method
import java.lang.reflect.Proxy
import jp.co.soramitsu.account.api.domain.model.Account
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException
import jp.co.soramitsu.common.data.storage.encrypt.WalletRecoveryRequiredException
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.model.SecuritySource
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.Signer
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SignWithAccountTest {

    @Test
    fun `root account signs only with its identity-bound root keypair`() {
        val rootKeypair = keypair(1)
        val metaAccount = metaAccount(rootKeypair)
        var chainSecretReads = 0
        var legacySecretReads = 0
        val repository = repository { method, _ ->
            when (method.name) {
                "findMetaAccount" -> metaAccount
                "getSubstrateSecrets" -> SubstrateSecrets(rootKeypair)
                "getChainAccountSecrets" -> {
                    chainSecretReads += 1
                    null
                }

                "getSecuritySource" -> {
                    legacySecretReads += 1
                    error("A V3 root secret must not fall back to V1")
                }

                else -> unexpectedCall(method)
            }
        }

        val signature = runBlocking {
            repository.signWithAccount(
                account = account(metaAccount.substrateAccountId!!, CryptoType.ED25519),
                message = MESSAGE
            )
        }

        assertTrue(Signer.verifyEd25519(MESSAGE, signature, rootKeypair.publicKey))
        assertEquals(0, chainSecretReads)
        assertEquals(0, legacySecretReads)
    }

    @Test
    fun `chain account signs with child keypair and never reads root secret`() {
        val rootKeypair = keypair(2)
        val childKeypair = keypair(3)
        val childAccount = chainAccount(childKeypair)
        val metaAccount = metaAccount(
            rootKeypair = rootKeypair,
            chainAccounts = mapOf(CHAIN_ID to childAccount)
        )
        var rootSecretReads = 0
        val repository = repository { method, _ ->
            when (method.name) {
                "findMetaAccount" -> metaAccount
                "getChainAccountSecrets" -> ChainAccountSecrets(childKeypair)
                "getSubstrateSecrets" -> {
                    rootSecretReads += 1
                    SubstrateSecrets(rootKeypair)
                }

                else -> unexpectedCall(method)
            }
        }

        val signature = runBlocking {
            repository.signWithAccount(
                account = account(childAccount.accountId, childAccount.cryptoType),
                message = MESSAGE
            )
        }

        assertTrue(Signer.verifyEd25519(MESSAGE, signature, childKeypair.publicKey))
        assertFalse(Signer.verifyEd25519(MESSAGE, signature, rootKeypair.publicKey))
        assertEquals(0, rootSecretReads)
    }

    @Test
    fun `chain account rejects request crypto type mismatch before secret access`() {
        val childKeypair = keypair(4)
        val childAccount = chainAccount(childKeypair)
        val metaAccount = metaAccount(
            rootKeypair = keypair(5),
            chainAccounts = mapOf(CHAIN_ID to childAccount)
        )
        var secretReads = 0
        val repository = repository { method, _ ->
            when (method.name) {
                "findMetaAccount" -> metaAccount
                "getChainAccountSecrets" -> {
                    secretReads += 1
                    ChainAccountSecrets(childKeypair)
                }

                else -> unexpectedCall(method)
            }
        }

        assertThrows(WalletPublicIdentityIntegrityException::class.java) {
            runBlocking {
                repository.signWithAccount(
                    account = account(childAccount.accountId, CryptoType.SR25519),
                    message = MESSAGE
                )
            }
        }
        assertEquals(0, secretReads)
    }

    @Test
    fun `chain account rejects keypair bound to another durable identity`() {
        val expectedChildKeypair = keypair(6)
        val differentChildKeypair = keypair(7)
        val childAccount = chainAccount(expectedChildKeypair)
        val metaAccount = metaAccount(
            rootKeypair = keypair(8),
            chainAccounts = mapOf(CHAIN_ID to childAccount)
        )
        var rootSecretReads = 0
        val repository = repository { method, _ ->
            when (method.name) {
                "findMetaAccount" -> metaAccount
                "getChainAccountSecrets" -> ChainAccountSecrets(differentChildKeypair)
                "getSubstrateSecrets" -> {
                    rootSecretReads += 1
                    SubstrateSecrets(keypair(9))
                }

                else -> unexpectedCall(method)
            }
        }

        assertThrows(WalletRecoveryRequiredException::class.java) {
            runBlocking {
                repository.signWithAccount(
                    account = account(childAccount.accountId, childAccount.cryptoType),
                    message = MESSAGE
                )
            }
        }
        assertEquals(0, rootSecretReads)
    }

    @Test
    fun `missing chain secret requires recovery without falling back to root`() {
        val rootKeypair = keypair(10)
        val childAccount = chainAccount(keypair(11))
        val metaAccount = metaAccount(
            rootKeypair = rootKeypair,
            chainAccounts = mapOf(CHAIN_ID to childAccount)
        )
        var rootSecretReads = 0
        var legacySecretReads = 0
        val repository = repository { method, _ ->
            when (method.name) {
                "findMetaAccount" -> metaAccount
                "getChainAccountSecrets" -> null
                "getSubstrateSecrets" -> {
                    rootSecretReads += 1
                    SubstrateSecrets(rootKeypair)
                }

                "getSecuritySource" -> {
                    legacySecretReads += 1
                    error("A child account must never fall back to a root key")
                }

                else -> unexpectedCall(method)
            }
        }

        assertThrows(WalletRecoveryRequiredException::class.java) {
            runBlocking {
                repository.signWithAccount(
                    account = account(childAccount.accountId, childAccount.cryptoType),
                    message = MESSAGE
                )
            }
        }
        assertEquals(0, rootSecretReads)
        assertEquals(0, legacySecretReads)
    }

    @Test
    fun `root signing rejects an account id absent from durable identities`() {
        val rootKeypair = keypair(12)
        val metaAccount = metaAccount(rootKeypair)
        var rootSecretReads = 0
        val repository = repository { method, _ ->
            when (method.name) {
                "findMetaAccount" -> metaAccount
                "getSubstrateSecrets" -> {
                    rootSecretReads += 1
                    SubstrateSecrets(rootKeypair)
                }

                else -> unexpectedCall(method)
            }
        }

        assertThrows(WalletPublicIdentityIntegrityException::class.java) {
            runBlocking {
                repository.signWithAccount(
                    account = account(keypair(13).publicKey.substrateAccountId(), CryptoType.ED25519),
                    message = MESSAGE
                )
            }
        }
        assertEquals(0, rootSecretReads)
    }

    @Test
    fun `conflicting duplicate child identities are rejected before any secret access`() {
        val firstChildKeypair = keypair(14)
        val conflictingChildKeypair = keypair(15)
        val accountId = firstChildKeypair.publicKey.substrateAccountId()
        val firstChildAccount = chainAccount(firstChildKeypair)
        val conflictingChildAccount = chainAccount(
            keypair = conflictingChildKeypair,
            accountId = accountId
        )
        val metaAccount = metaAccount(
            rootKeypair = keypair(16),
            chainAccounts = linkedMapOf(
                CHAIN_ID to firstChildAccount,
                CONFLICTING_CHAIN_ID to conflictingChildAccount
            )
        )
        var chainSecretReads = 0
        var rootSecretReads = 0
        var legacySecretReads = 0
        val repository = repository { method, _ ->
            when (method.name) {
                "findMetaAccount" -> metaAccount
                "getChainAccountSecrets" -> {
                    chainSecretReads += 1
                    ChainAccountSecrets(firstChildKeypair)
                }

                "getSubstrateSecrets" -> {
                    rootSecretReads += 1
                    SubstrateSecrets(keypair(17))
                }

                "getSecuritySource" -> {
                    legacySecretReads += 1
                    SecuritySource.Unspecified(keypair(18))
                }

                else -> unexpectedCall(method)
            }
        }

        assertThrows(WalletPublicIdentityIntegrityException::class.java) {
            runBlocking {
                repository.signWithAccount(
                    account = account(accountId, CryptoType.ED25519),
                    message = MESSAGE
                )
            }
        }

        assertEquals(0, chainSecretReads)
        assertEquals(0, rootSecretReads)
        assertEquals(0, legacySecretReads)
    }

    @Test
    fun `missing V3 root signs with retained guarded V1 source`() {
        val rootKeypair = keypair(19)
        val metaAccount = metaAccount(rootKeypair)
        var currentSecretReads = 0
        var legacySecretReads = 0
        val repository = repository { method, args ->
            when (method.name) {
                "findMetaAccount" -> metaAccount
                "getSubstrateSecrets" -> {
                    currentSecretReads += 1
                    null
                }

                "getSecuritySource" -> {
                    legacySecretReads += 1
                    assertEquals("test-address", args?.firstOrNull())
                    SecuritySource.Unspecified(rootKeypair)
                }

                else -> unexpectedCall(method)
            }
        }

        val signature = runBlocking {
            repository.signWithAccount(
                account = account(metaAccount.substrateAccountId!!, CryptoType.ED25519),
                message = MESSAGE
            )
        }

        assertTrue(Signer.verifyEd25519(MESSAGE, signature, rootKeypair.publicKey))
        assertEquals(1, currentSecretReads)
        assertEquals(1, legacySecretReads)
    }

    @Test
    fun `corrupt V3 root is rejected without V1 fallback`() {
        val rootKeypair = keypair(20)
        val corruptKeypair = keypair(21)
        val metaAccount = metaAccount(rootKeypair)
        var legacySecretReads = 0
        val repository = repository { method, _ ->
            when (method.name) {
                "findMetaAccount" -> metaAccount
                "getSubstrateSecrets" -> SubstrateSecrets(corruptKeypair)
                "getSecuritySource" -> {
                    legacySecretReads += 1
                    SecuritySource.Unspecified(rootKeypair)
                }

                else -> unexpectedCall(method)
            }
        }

        assertThrows(WalletRecoveryRequiredException::class.java) {
            runBlocking {
                repository.signWithAccount(
                    account = account(metaAccount.substrateAccountId!!, CryptoType.ED25519),
                    message = MESSAGE
                )
            }
        }
        assertEquals(0, legacySecretReads)
    }

    @Test
    fun `corrupt retained V1 fallback is rejected`() {
        val rootKeypair = keypair(22)
        val corruptLegacyKeypair = keypair(23)
        val metaAccount = metaAccount(rootKeypair)
        var currentSecretReads = 0
        var legacySecretReads = 0
        val repository = repository { method, _ ->
            when (method.name) {
                "findMetaAccount" -> metaAccount
                "getSubstrateSecrets" -> {
                    currentSecretReads += 1
                    null
                }

                "getSecuritySource" -> {
                    legacySecretReads += 1
                    SecuritySource.Unspecified(corruptLegacyKeypair)
                }

                else -> unexpectedCall(method)
            }
        }

        assertThrows(WalletRecoveryRequiredException::class.java) {
            runBlocking {
                repository.signWithAccount(
                    account = account(metaAccount.substrateAccountId!!, CryptoType.ED25519),
                    message = MESSAGE
                )
            }
        }
        assertEquals(1, currentSecretReads)
        assertEquals(1, legacySecretReads)
    }

    private fun keypair(seedByte: Int): Keypair {
        return SubstrateKeypairFactory.generate(
            encryptionType = EncryptionType.ED25519,
            seed = ByteArray(32) { seedByte.toByte() },
            junctions = emptyList()
        )
    }

    private fun chainAccount(
        keypair: Keypair,
        accountId: ByteArray = keypair.publicKey.substrateAccountId()
    ): MetaAccount.ChainAccount {
        return MetaAccount.ChainAccount(
            metaId = META_ID,
            chain = null,
            publicKey = keypair.publicKey,
            accountId = accountId,
            cryptoType = CryptoType.ED25519,
            accountName = "Child"
        )
    }

    private fun metaAccount(
        rootKeypair: Keypair,
        chainAccounts: Map<String, MetaAccount.ChainAccount> = emptyMap()
    ): MetaAccount {
        return MetaAccount(
            id = META_ID,
            chainAccounts = chainAccounts,
            favoriteChains = emptyMap(),
            substratePublicKey = rootKeypair.publicKey,
            substrateCryptoType = CryptoType.ED25519,
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
        const val CONFLICTING_CHAIN_ID = "conflicting-test-chain"
        val MESSAGE = "sign-with-account".encodeToByteArray()
    }
}
