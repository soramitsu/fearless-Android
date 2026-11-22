package jp.co.soramitsu.irohaconnect.impl.data

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.storage.envelope.WalletSecretLockedException
import jp.co.soramitsu.common.data.storage.pin.WalletViewOnlyAccess
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.irohaconnect.impl.WebVectors
import jp.co.soramitsu.irohaconnect.impl.domain.IrohaConnectAccount
import jp.co.soramitsu.irohaconnect.impl.domain.IrohaConnectActionError
import jp.co.soramitsu.irohaconnect.impl.domain.IrohaConnectActionException
import jp.co.soramitsu.irohaconnect.impl.protocol.IrohaConnectCrypto
import jp.co.soramitsu.irohaconnect.impl.protocol.IrohaConnectEncoding
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WalletIrohaConnectAccountProviderTest {

    private val publicKey = IrohaConnectEncoding.unhex(WebVectors.accountPublicKeyHex)
    private val taira = UniversalWalletRegistry.taira
    private val account = IrohaConnectAccount(1, WebVectors.accountAddress, "Wallet 1", WebVectors.accountPublicKeyHex)

    private fun meta(id: Long, key: ByteArray? = publicKey) = MetaAccount(
        id = id,
        chainAccounts = key?.let {
            mapOf(taira.id to MetaAccount.ChainAccount(id, null, it, it, CryptoType.ED25519, "Taira"))
        }.orEmpty(),
        favoriteChains = emptyMap(),
        substratePublicKey = null,
        substrateCryptoType = null,
        substrateAccountId = null,
        ethereumAddress = null,
        ethereumPublicKey = null,
        tonPublicKey = null,
        isSelected = id == 1L,
        isBackedUp = true,
        googleBackupAddress = null,
        name = "Wallet $id",
        initialized = true
    )

    private fun repository(entropy: ByteArray? = ByteArray(16), secretsFailure: Exception? = null) =
        mockk<AccountRepository> {
        coEvery { allMetaAccounts() } returns listOf(meta(1), meta(2, null))
        coEvery { getMetaAccount(1) } returns meta(1)
        coEvery { getChainAccountSecrets(1, taira.id) } answers {
            secretsFailure?.let { throw it }
            entropy?.let { value ->
                mockk<EncodableStruct<ChainAccountSecrets>> { every { get(ChainAccountSecrets.Entropy) } returns value.clone() }
            }
        }
        coEvery { getSubstrateSecrets(any()) } returns null
        coEvery { getEthereumSecrets(any()) } returns null
        coEvery { getTonSecrets(any()) } returns null
    }

    @After
    fun tearDown() {
        WalletViewOnlyAccess.pinResetCompleted()
    }

    @Test
    fun shouldListOnlyWalletsWithATairaAccount() = runTest {
        val provider = WalletIrohaConnectAccountProvider(repository())
        assertEquals(listOf(account), provider.accounts(taira.chainDiscriminant))
        assertEquals(emptyList<IrohaConnectAccount>(), provider.accounts(UniversalWalletRegistry.nexus.chainDiscriminant))
    }

    @Test
    fun shouldSignWithTheRederivedKey_whenItReproducesTheStoredAccount() = runTest {
        val provider = WalletIrohaConnectAccountProvider(repository())
        val signed = provider.sign(account, taira.chainDiscriminant, byteArrayOf(1, 2, 3))
        assertEquals(WebVectors.accountPublicKeyHex, signed.publicKeyHex)
        assertTrue(IrohaConnectCrypto.ed25519Verify(publicKey, byteArrayOf(1, 2, 3), signed.signature))
    }

    @Test
    fun shouldRefuseToSign_whenTheKeyOrSecretsDoNotMatch() = runTest {
        expect(IrohaConnectActionError.KEY_MISMATCH) {
            val otherRoot = repository(entropy = ByteArray(16) { 1 })
            WalletIrohaConnectAccountProvider(otherRoot).sign(account, taira.chainDiscriminant, byteArrayOf(1))
        }
        expect(IrohaConnectActionError.CANNOT_SIGN) {
            WalletIrohaConnectAccountProvider(repository(entropy = null)).sign(account, taira.chainDiscriminant, byteArrayOf(1))
        }
        expect(IrohaConnectActionError.ACCOUNT_UNAVAILABLE) {
            WalletIrohaConnectAccountProvider(repository()).sign(account.copy(publicKeyHex = "00".repeat(32)), taira.chainDiscriminant, byteArrayOf(1))
        }
        expect(IrohaConnectActionError.WALLET_LOCKED) {
            WalletIrohaConnectAccountProvider(repository(secretsFailure = WalletSecretLockedException())).sign(account, taira.chainDiscriminant, byteArrayOf(1))
        }
        WalletViewOnlyAccess.enter()
        expect(IrohaConnectActionError.WALLET_LOCKED) {
            WalletIrohaConnectAccountProvider(repository()).sign(account, taira.chainDiscriminant, byteArrayOf(1))
        }
    }

    private suspend fun expect(error: IrohaConnectActionError, block: suspend () -> Unit) {
        try {
            block()
            fail("expected $error")
        } catch (failure: IrohaConnectActionException) {
            assertEquals(error, failure.error)
        }
    }
}
