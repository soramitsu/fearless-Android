package jp.co.soramitsu.wallet.impl.presentation.balance.walletselector.light

import android.graphics.drawable.PictureDrawable
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.interfaces.NomisScoreInteractor
import jp.co.soramitsu.account.api.domain.interfaces.TotalBalanceUseCase
import jp.co.soramitsu.account.api.domain.model.LightMetaAccount
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.NomisScoreData
import jp.co.soramitsu.account.api.domain.model.PortfolioBalance
import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.navigation.payload.WalletSelectorPayload
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** The light wallet selector used by other screens: list failures, the external selection mode and the result. */
@OptIn(ExperimentalCoroutinesApi::class)
class WalletSelectorViewModelCoverageTest {
    @get:Rule val instantTask = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<AccountRepository>()
    private val scores = mockk<NomisScoreInteractor>()
    private val total = mockk<TotalBalanceUseCase>()
    private val router = mockk<WalletRouter>(relaxed = true)
    private val icons = mockk<AddressIconGenerator>(relaxed = true)
    private val icon = mockk<PictureDrawable>()
    private val resources = mockk<ResourceManager>(relaxed = true)
    private val models = mutableListOf<WalletSelectorViewModel>()

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        every { repository.lightMetaAccountsFlow() } returns MutableStateFlow<List<LightMetaAccount>>(listOf(ready().meta))
        every { total.observe(7) } returns MutableStateFlow<TotalBalance>(ready())
        every { scores.observeNomisScores() } returns MutableStateFlow<List<NomisScoreData>>(emptyList())
        every { resources.getString(any()) } answers { "text-${firstArg<Int>()}" }
        every { resources.getQuantityString(any(), any(), *anyVararg()) } returns "hidden"
        coEvery { icons.createWalletIcon(any(), any()) } returns icon
    }

    @After fun teardown() {
        models.forEach { it.coroutineContext.cancel() }
        Dispatchers.resetMain()
    }

    @Test fun `an unreadable score source keeps the wallets without scores`() = runTest(dispatcher) {
        every { scores.observeNomisScores() } returns failing("scores unreadable")
        val vm = create(); runCurrent()

        val row = vm.state.value.wallets.single()
        assertEquals("Main", row.title)
        assertNull(row.score)
        assertEquals(row, vm.state.value.selectedWallet)
    }

    @Test fun `an unreadable wallet roster empties the list and reports the error`() = runTest(dispatcher) {
        every { repository.lightMetaAccountsFlow() } returns failing("roster unreadable")
        val vm = create(); runCurrent()

        assertTrue(vm.state.value.wallets.isEmpty())
        assertEquals("roster unreadable", vm.errorLiveData.value?.peekContent())
    }

    @Test fun `the external mode shows only the wallet the caller selected`() = runTest(dispatcher) {
        val none = create(mode = WalletSelectionMode.ExternalSelectedWallet); runCurrent()
        val chosen = create(mode = WalletSelectionMode.ExternalSelectedWallet, selectedWalletId = 7L); runCurrent()

        assertEquals(1, none.state.value.wallets.size)
        assertNull(none.state.value.selectedWallet)
        assertEquals(7L, chosen.state.value.selectedWallet?.id)
    }

    @Test fun `picking a wallet returns it to the caller under its tag`() = runTest(dispatcher) {
        val vm = create(); runCurrent()

        vm.onWalletSelected(WalletItemViewState(id = 9L, title = "Nine", walletIcon = icon, isSelected = false))
        runCurrent()
        vm.onBackClicked()

        verify { router.setWalletSelectorPayload(WalletSelectorPayload(TAG, 9L)) }
        verify { router.backWithResult(WalletSelectorFragment.RESULT_ADDRESS to 9L) }
        verify { router.back() }
    }

    private fun create(
        mode: WalletSelectionMode? = null,
        selectedWalletId: Long? = null
    ): WalletSelectorViewModel {
        val arguments = mutableMapOf<String, Any?>(WalletSelectorFragment.TAG_ARGUMENT_KEY to TAG)
        if (mode != null) arguments[WalletSelectorFragment.WALLET_SELECTION_MODE] = mode
        if (selectedWalletId != null) arguments[WalletSelectorFragment.SELECTED_WALLET_ID] = selectedWalletId
        return WalletSelectorViewModel(repository, icons, router, total, scores, resources, SavedStateHandle(arguments))
            .also(models::add)
    }

    private fun <T> failing(message: String): Flow<T> = flow { throw IllegalStateException(message) }

    private fun ready(): TotalBalance.Ready {
        val key = ByteArray(32) { 1 }
        val meta = MetaAccount(7, emptyMap(), emptyMap(), key, CryptoType.SR25519, key,
            null, null, null, true, true, null, "Main", true)
        val amount = PortfolioBalance(BigDecimal.TEN, BigDecimal.TEN, null, null, 1, 1, 0, 0)
        val hidden = PortfolioBalance(BigDecimal.ZERO, BigDecimal.ZERO, null, null, 0, 0, 0, 0)
        return TotalBalance.Ready(meta, emptyMap(), "usd", "$", true, amount, hidden, amount)
    }

    private companion object {
        const val TAG = "coverage-tag"
    }
}
