package jp.co.soramitsu.account.impl.domain.recovery

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.LightMetaAccount
import jp.co.soramitsu.account.api.domain.model.WalletRecoveryStatus
import jp.co.soramitsu.common.data.secrets.v3.RecoveryAssessment
import jp.co.soramitsu.common.data.secrets.v3.RecoveryIssue
import jp.co.soramitsu.common.data.secrets.v3.TonPhraseVariant
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletRecoveryItem
import jp.co.soramitsu.common.data.storage.encrypt.WalletRecoveryKind
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretArchive
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.fearless_utils.scale.toHexString
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletRecoveryInventoryTest {
    private val accounts = mockk<AccountRepository>()
    private val storage = HashMapEncryptedPreferences()

    @Test
    fun oneUnreadableWalletDoesNotHideOtherWalletsMissingKeysOrBackupAdvisories() = runTest {
        every { accounts.lightMetaAccountsFlow() } returns flowOf(listOf(wallet(1), wallet(2)))
        coEvery { accounts.recoveryAssessment(any(), any()) } returns null
        coEvery { accounts.walletRecoveryStatus(1) } throws IllegalStateException("unavailable")
        val missing = WalletRecoveryItem(2, WalletSecretScope.Ethereum, WalletRecoveryKind.MISSING_KEY, "2:ETHEREUM_SECRETS")
        coEvery { accounts.walletRecoveryStatus(2) } returns WalletRecoveryStatus(2, listOf(missing, missing))
        coEvery { accounts.recoveryAssessment(2, WalletEcosystem.Substrate) } returns RecoveryAssessment.Unverified(setOf(RecoveryIssue.SEED_MISMATCH))
        coEvery { accounts.recoveryAssessment(2, WalletEcosystem.Ton) } returns RecoveryAssessment.VerifiedAsTyped(TonPhraseVariant.AS_TYPED)
        storage.putEncryptedString(WalletSecretArchive.keyFor("3:ETHEREUM_SECRETS"), "opaque")

        val result = WalletRecoveryInventory(accounts, storage).observe().first()
        assertFalse(result.readFailed)
        assertTrue(result.wallets[0].readFailed)
        assertFalse(result.wallets[1].readFailed)
        assertEquals(1, result.wallets[1].items.size)
        assertEquals(WalletRecoveryKind.MISSING_KEY, result.wallets[1].items.single().kind)
        assertEquals(listOf(false, true), result.wallets[1].advisories.map { it.asTyped })
        assertEquals(1, result.archivedCount)
    }

    @Test
    fun networkRecoveryDriftOffersBackupOfThatExactNetworkKey() = runTest {
        val pair = jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory.createWithPrivateKey(ByteArray(32) { 3 })
        val accountId = pair.publicKey.ethereumAddressFromPublicKey()
        val hex = org.bouncycastle.util.encoders.Hex.toHexString(accountId)
        val wallet = wallet(1)
        every { wallet.universalWalletChainAccounts } returns mapOf("network-id" to
            LightMetaAccount.UniversalWalletChainAccount(pair.publicKey, accountId, jp.co.soramitsu.core.models.CryptoType.ECDSA))
        every { accounts.lightMetaAccountsFlow() } returns flowOf(listOf(wallet))
        coEvery { accounts.walletRecoveryStatus(1) } returns WalletRecoveryStatus(1, emptyList())
        coEvery { accounts.recoveryAssessment(any(), any()) } returns null
        val encoded = jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets(keyPair = pair, seed = ByteArray(32) { 8 }).toHexString()
        storage.putEncryptedString("1:$hex:ACCESS_SECRETS", encoded)
        val result = WalletRecoveryInventory(accounts, storage).observe().first()
        assertEquals(WalletRecoveryInventory.Advisory(WalletSecretScope.ChainAccount(hex), false, "network-id"), result.wallets.single().advisories.single())
        assertFalse(result.wallets.single().readFailed)
    }

    @Test
    fun archiveInventoryFailureCannotHideAvailableWalletActions() = runTest {
        every { accounts.lightMetaAccountsFlow() } returns flowOf(listOf(wallet(1)))
        coEvery { accounts.recoveryAssessment(any(), any()) } returns null
        coEvery { accounts.walletRecoveryStatus(1) } returns WalletRecoveryStatus(1, listOf(
            WalletRecoveryItem(1, WalletSecretScope.Substrate, WalletRecoveryKind.QUARANTINED, "1:SUBSTRATE_SECRETS")
        ))
        val unavailable = object : EncryptedPreferences by storage {
            override fun keysWithPrefixes(prefixes: Set<String>, maxResultCount: Int, maxKeyBytes: Int,
                maxTotalKeyBytes: Int, failOnOversizedMatch: Boolean): Set<String> = error("unavailable")
        }
        val result = WalletRecoveryInventory(accounts, unavailable).observe().first()
        assertEquals(null, result.archivedCount)
        assertEquals(1, result.wallets.single().items.size)
    }

    @Test
    fun failedWalletListShowsAnErrorRatherThanClaimingNoRecoveryIsNeeded() = runTest {
        every { accounts.lightMetaAccountsFlow() } returns flow { throw IllegalStateException("Room unavailable") }
        val result = WalletRecoveryInventory(accounts, storage).observe().first()
        assertTrue(result.readFailed)
        assertFalse(result.loading)
    }

    @Test
    fun cancellationIsNotConvertedIntoARecoveryError() = runTest {
        every { accounts.lightMetaAccountsFlow() } returns flowOf(listOf(wallet(1)))
        coEvery { accounts.walletRecoveryStatus(1) } throws CancellationException()
        assertTrue(runCatching { WalletRecoveryInventory(accounts, storage).observe().first() }.exceptionOrNull() is CancellationException)
    }

    private fun wallet(id: Long): LightMetaAccount = mockk {
        every { this@mockk.id } returns id
        every { name } returns "Wallet $id"
        every { universalWalletChainAccounts } returns emptyMap()
    }
}
