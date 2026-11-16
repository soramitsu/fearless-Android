package jp.co.soramitsu.wallet.impl.presentation.balance.optionswallet

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.account.api.domain.model.LightMetaAccount
import jp.co.soramitsu.core.models.CryptoType
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.UniversalWalletPasswordNotice
import jp.co.soramitsu.account.api.domain.UniversalWalletSlot
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.wallet.impl.WalletTestModels
import jp.co.soramitsu.wallet.impl.awaitFirst
import jp.co.soramitsu.wallet.impl.awaitValue
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OptionsWalletViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val router = mockk<WalletRouter>(relaxed = true)
    private val selected = WalletTestModels.metaAccount(id = 1L, name = "Main")
    private val other = WalletTestModels.metaAccount(id = 2L, name = "Savings", ethereum = null)
    private val accounts = mockk<AccountInteractor>(relaxed = true) {
        every { selectedLightMetaAccountFlow() } returns flowOf(selected)
        coEvery { getLightMetaAccount(1L) } returns selected
        coEvery { getLightMetaAccount(2L) } returns other
    }
    private val row = UniversalWalletPasswordNotice.Row(UniversalWalletSlot.SOLANA, "old", "new")
    private val notice = mockk<UniversalWalletPasswordNotice>(relaxed = true) {
        coEvery { rows(1L) } returnsMany listOf(listOf(row), emptyList())
        coEvery { switch(1L) } returns listOf(row)
    }

    @Test
    fun selectedWalletOffersEveryOptionAndRoutesThem() {
        val viewModel = viewModel(1L, notice)

        val state = viewModel.state.awaitFirst { it.showOldAddressesButton }
        assertTrue(state.isSelected)
        assertTrue(state.showScoreButton)
        assertTrue(state.showDetailsButton)

        viewModel.onOldAddressesClick()
        assertEquals(listOf(row), viewModel.showUniversalPasswordNotice.value!!.peekContent().rows)
        viewModel.onUniversalPasswordSwitch()
        assertEquals(listOf(row), viewModel.universalPasswordSwitchResult.awaitValue()!!.peekContent().rows)
        viewModel.onUniversalPasswordNoticeClosed()
        viewModel.onWalletDetailsClick()
        viewModel.onBackupWalletClick()
        viewModel.onShowWalletScoreClick()
        viewModel.onDeleteWalletClick()
        viewModel.onCloseClick()

        verify { notice.dismiss(1L) }
        verify { router.openAccountDetails(1L) }
        verify { router.openBackupWalletScreen(1L) }
        verify { router.openScoreDetailsScreen(1L) }
        assertEquals(1L, viewModel.deleteWalletConfirmation.value!!.peekContent())
        verify { router.back() }
    }

    @Test
    fun anotherWalletCanBeRenamedAndDeleted() {
        val viewModel = viewModel(2L, UniversalWalletPasswordNotice.NONE, allowDetails = false)

        val state = viewModel.state.awaitFirst { !it.isSelected }
        assertFalse(state.showScoreButton)
        assertFalse(state.showDetailsButton)
        viewModel.onOldAddressesClick()
        assertEquals(null, viewModel.showUniversalPasswordNotice.value)

        viewModel.state.awaitFirst { true }
        Thread.sleep(50)
        viewModel.onChangeWalletNameClick()
        viewModel.deleteWalletConfirmed()

        verify { router.openRenameWallet(2L, any()) }
        coVerify { accounts.deleteAccount(2L) }
    }

    @Test
    fun failingNoticeOffersNoOldAddresses() {
        val failing = mockk<UniversalWalletPasswordNotice>(relaxed = true) { coEvery { rows(any()) } throws IllegalStateException("locked") }
        val viewModel = viewModel(1L, failing)

        assertFalse(viewModel.state.awaitFirst { it.showScoreButton }.showOldAddressesButton)
    }

    @Test
    fun walletDetailsNeedAnAccountBesideTon() {
        val tonOnly = WalletTestModels.metaAccount(substrate = null, ethereum = null, ton = ByteArray(32))

        assertFalse(walletDetailsAvailable(tonOnly, allowWalletDetails = true))
        assertTrue(walletDetailsAvailable(WalletTestModels.metaAccount(), allowWalletDetails = true))
        assertFalse(walletDetailsAvailable(WalletTestModels.metaAccount(), allowWalletDetails = false))
    }


    @Test
    fun `adding TON to universal wallets retains account details and Nexus address access`() {
        for ((substrate, ethereum) in listOf(true to true, true to false, false to true)) {
            assertTrue(walletDetailsAvailable(wallet(substrate, ethereum, ton = false), allowWalletDetails = true))
            assertTrue(walletDetailsAvailable(wallet(substrate, ethereum, ton = true), allowWalletDetails = true))
        }
    }

    @Test
    fun `native TON only wallets retain their existing hidden details behavior`() {
        val nativeTon = wallet(substrate = false, ethereum = false, ton = true)
        assertFalse(walletDetailsAvailable(nativeTon, allowWalletDetails = true))
    }

    @Test
    fun `explicitly disabled details stay hidden for every wallet root combination`() {
        for (roots in 0..7) {
            val account = wallet(
                substrate = roots and 1 != 0,
                ethereum = roots and 2 != 0,
                ton = roots and 4 != 0
            )
            assertFalse(walletDetailsAvailable(account, allowWalletDetails = false))
        }
    }

    private fun wallet(
        substrate: Boolean,
        ethereum: Boolean,
        ton: Boolean
    ) = LightMetaAccount(
        id = 1L,
        substratePublicKey = if (substrate) ByteArray(32) { 1 } else null,
        substrateCryptoType = if (substrate) CryptoType.SR25519 else null,
        substrateAccountId = if (substrate) ByteArray(32) { 1 } else null,
        ethereumAddress = if (ethereum) ByteArray(20) { 2 } else null,
        ethereumPublicKey = if (ethereum) ByteArray(33) { 3 } else null,
        tonPublicKey = if (ton) ByteArray(32) { 4 } else null,
        isSelected = true,
        name = "Restored wallet",
        isBackedUp = true,
        initialized = true
    )

    private fun viewModel(walletId: Long, notice: UniversalWalletPasswordNotice, allowDetails: Boolean = true) = OptionsWalletViewModel(
        SavedStateHandle(mapOf(OptionsWalletFragment.KEY_WALLET_ID to walletId, OptionsWalletFragment.KEY_ALLOW_WALLET_DETAILS to allowDetails)),
        accounts, router, notice
    )
}
