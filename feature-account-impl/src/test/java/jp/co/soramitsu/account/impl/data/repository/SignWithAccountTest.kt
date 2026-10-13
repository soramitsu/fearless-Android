package jp.co.soramitsu.account.impl.data.repository

import java.lang.reflect.Method
import java.lang.reflect.Proxy
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.interfaces.signWithAccount
import jp.co.soramitsu.account.api.domain.model.Account
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.secrets.v1.Keypair
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException
import jp.co.soramitsu.common.data.storage.encrypt.WalletRecoveryRequiredException
import jp.co.soramitsu.core.model.SecuritySource
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.Signer
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import kotlinx.coroutines.runBlocking
import org.bouncycastle.util.encoders.Hex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SignWithAccountTest {

    @Test
    fun `current secret recovery error never falls back to legacy security source`() {
        val recoveryFailure = WalletRecoveryRequiredException()
        val calls = RepositoryCalls()
        val repository = repository(
            calls = calls,
            currentSecrets = { throw recoveryFailure },
            legacySecuritySource = {
                throw AssertionError("Legacy signing source must not be queried")
            }
        )

        val thrown = assertThrows(WalletRecoveryRequiredException::class.java) {
            runBlocking {
                repository.signWithAccount(ACCOUNT, MESSAGE)
            }
        }

        assertEquals(recoveryFailure.message, thrown.message)
        assertEquals(1, calls.findMetaAccount)
        assertEquals(1, calls.getCurrentSecrets)
        assertEquals(0, calls.getLegacySecuritySource)
    }

    @Test
    fun `valid current secret signs without querying legacy security source`() {
        val calls = RepositoryCalls()
        val repository = repository(
            calls = calls,
            currentSecrets = { CURRENT_SECRETS },
            legacySecuritySource = {
                throw AssertionError("A valid current secret must not use legacy fallback")
            }
        )

        val signature = runBlocking {
            repository.signWithAccount(ACCOUNT, MESSAGE)
        }

        assertTrue(Signer.verifyEd25519(MESSAGE, signature, CURRENT_PUBLIC_KEY))
        assertFalse(Signer.verifyEd25519(MESSAGE, signature, LEGACY_PUBLIC_KEY))
        assertEquals(1, calls.findMetaAccount)
        assertEquals(1, calls.getCurrentSecrets)
        assertEquals(0, calls.getLegacySecuritySource)
    }

    @Test
    fun `decoded current secret for another wallet fails before signing or fallback`() {
        val calls = RepositoryCalls()
        val repository = repository(
            calls = calls,
            currentSecrets = { SWAPPED_CURRENT_SECRETS },
            legacySecuritySource = {
                throw AssertionError(
                    "A mismatched current secret must not use legacy fallback"
                )
            }
        )

        assertThrows(WalletRecoveryRequiredException::class.java) {
            runBlocking {
                repository.signWithAccount(ACCOUNT, MESSAGE)
            }
        }

        assertEquals(1, calls.findMetaAccount)
        assertEquals(1, calls.getCurrentSecrets)
        assertEquals(0, calls.getLegacySecuritySource)
    }

    @Test
    fun `same public key with wrong private key fails before signing`() {
        val calls = RepositoryCalls()
        val repository = repository(
            calls = calls,
            currentSecrets = { WRONG_PRIVATE_CURRENT_SECRETS },
            legacySecuritySource = {
                throw AssertionError(
                    "A private-key ownership failure must not use legacy fallback"
                )
            }
        )

        assertThrows(WalletRecoveryRequiredException::class.java) {
            runBlocking {
                repository.signWithAccount(ACCOUNT, MESSAGE)
            }
        }

        assertEquals(1, calls.getCurrentSecrets)
        assertEquals(0, calls.getLegacySecuritySource)
    }

    @Test
    fun `caller crypto type cannot override durable wallet crypto type before secret access`() {
        val calls = RepositoryCalls()
        val wrongCryptoRequest = ACCOUNT.copy(cryptoType = CryptoType.SR25519)
        val repository = repository(
            calls = calls,
            account = wrongCryptoRequest,
            currentSecrets = { CURRENT_SECRETS },
            legacySecuritySource = {
                throw AssertionError("A current signing request must not fall back")
            }
        )

        assertThrows(WalletPublicIdentityIntegrityException::class.java) {
            runBlocking {
                repository.signWithAccount(wrongCryptoRequest, MESSAGE)
            }
        }

        assertEquals(0, calls.getCurrentSecrets)
        assertEquals(0, calls.getLegacySecuritySource)
    }

    @Test
    fun `truly absent current secret uses guarded legacy fallback`() {
        val calls = RepositoryCalls()
        val repository = repository(
            calls = calls,
            account = FALLBACK_ACCOUNT,
            metaAccount = FALLBACK_META_ACCOUNT,
            currentSecrets = { null },
            legacySecuritySource = { LEGACY_SECURITY_SOURCE }
        )

        val signature = runBlocking {
            repository.signWithAccount(FALLBACK_ACCOUNT, MESSAGE)
        }

        assertTrue(Signer.verifyEd25519(MESSAGE, signature, LEGACY_PUBLIC_KEY))
        assertFalse(Signer.verifyEd25519(MESSAGE, signature, CURRENT_PUBLIC_KEY))
        assertEquals(1, calls.findMetaAccount)
        assertEquals(1, calls.getCurrentSecrets)
        assertEquals(1, calls.getLegacySecuritySource)
    }

    private fun repository(
        calls: RepositoryCalls,
        account: Account = ACCOUNT,
        metaAccount: MetaAccount = META_ACCOUNT,
        currentSecrets: () -> EncodableStruct<SubstrateSecrets>?,
        legacySecuritySource: () -> SecuritySource
    ): AccountRepository {
        return interfaceProxy { method, arguments ->
            when (method.name) {
                "findMetaAccount" -> {
                    calls.findMetaAccount += 1
                    val requestedAccountId = arguments?.first() as ByteArray
                    check(requestedAccountId.contentEquals(account.accountId))
                    metaAccount
                }

                "getSubstrateSecrets" -> {
                    calls.getCurrentSecrets += 1
                    check(arguments?.first() == META_ID)
                    currentSecrets()
                }

                "getSecuritySource" -> {
                    calls.getLegacySecuritySource += 1
                    check(arguments?.first() == account.address)
                    legacySecuritySource()
                }

                else -> unexpectedCall(method)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> interfaceProxy(
        crossinline handler: (Method, Array<out Any?>?) -> Any?
    ): T {
        return Proxy.newProxyInstance(
            T::class.java.classLoader,
            arrayOf(T::class.java)
        ) { proxy, method, arguments ->
            when (method.name) {
                "toString" -> "${T::class.java.simpleName}Proxy"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === arguments?.firstOrNull()
                else -> handler(method, arguments)
            }
        } as T
    }

    private fun unexpectedCall(method: Method): Nothing {
        throw UnsupportedOperationException(
            "Unexpected repository call while signing: ${method.name}"
        )
    }

    private class RepositoryCalls {
        var findMetaAccount = 0
        var getCurrentSecrets = 0
        var getLegacySecuritySource = 0
    }

    private companion object {

        const val META_ID = 73L

        val MESSAGE = "Fearless guarded signing".encodeToByteArray()
        val CURRENT_PRIVATE_KEY = Hex.decode(
            "9d61b19deffd5a60ba844af492ec2cc4" +
                "4449c5697b326919703bac031cae7f60"
        )
        val CURRENT_PUBLIC_KEY = Hex.decode(
            "d75a980182b10ab7d54bfed3c964073" +
                "a0ee172f3daa62325af021a68f707511a"
        )
        val LEGACY_PRIVATE_KEY = Hex.decode(
            "4ccd089b28ff96da9db6c346ec114e0" +
                "f5b8a319f35aba624da8cf6ed4fb8a6fb"
        )
        val LEGACY_PUBLIC_KEY = Hex.decode(
            "3d4017c3e843895a92b70aa74d1b7eb" +
                "c9c982ccf2ec4968cc0cd55f12af4660c"
        )

        val ACCOUNT = Account(
            address = "test-address",
            name = "Test account",
            accountIdHex = Hex.toHexString(CURRENT_PUBLIC_KEY),
            cryptoType = CryptoType.ED25519,
            position = 0
        )

        val FALLBACK_ACCOUNT = Account(
            address = "legacy-test-address",
            name = "Legacy test account",
            accountIdHex = Hex.toHexString(LEGACY_PUBLIC_KEY),
            cryptoType = CryptoType.ED25519,
            position = 0
        )

        val META_ACCOUNT = MetaAccount(
            id = META_ID,
            chainAccounts = emptyMap(),
            favoriteChains = emptyMap(),
            substratePublicKey = CURRENT_PUBLIC_KEY,
            substrateCryptoType = CryptoType.ED25519,
            substrateAccountId = ACCOUNT.accountId,
            ethereumAddress = null,
            ethereumPublicKey = null,
            tonPublicKey = null,
            isSelected = true,
            isBackedUp = true,
            googleBackupAddress = null,
            name = "Test wallet",
            initialized = true
        )

        val FALLBACK_META_ACCOUNT = META_ACCOUNT.copy(
            substratePublicKey = LEGACY_PUBLIC_KEY,
            substrateAccountId = FALLBACK_ACCOUNT.accountId,
            name = "Legacy test wallet"
        )

        val CURRENT_SECRETS = SubstrateSecrets(
            substrateKeyPair = Keypair(
                publicKey = CURRENT_PUBLIC_KEY,
                privateKey = CURRENT_PRIVATE_KEY
            )
        )

        val SWAPPED_CURRENT_SECRETS = SubstrateSecrets(
            substrateKeyPair = Keypair(
                publicKey = LEGACY_PUBLIC_KEY,
                privateKey = LEGACY_PRIVATE_KEY
            )
        )

        val WRONG_PRIVATE_CURRENT_SECRETS = SubstrateSecrets(
            substrateKeyPair = Keypair(
                publicKey = CURRENT_PUBLIC_KEY,
                privateKey = LEGACY_PRIVATE_KEY
            )
        )

        val LEGACY_SECURITY_SOURCE = SecuritySource.Unspecified(
            Keypair(
                publicKey = LEGACY_PUBLIC_KEY,
                privateKey = LEGACY_PRIVATE_KEY
            )
        )
    }
}
