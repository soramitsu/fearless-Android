package jp.co.soramitsu.account.impl.presentation.profile

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.interfaces.NomisScoreInteractor
import jp.co.soramitsu.account.api.domain.interfaces.TotalBalanceUseCase
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.NomisScoreData
import jp.co.soramitsu.account.api.domain.model.PortfolioBalance
import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.account.impl.domain.account.details.AccountDetailsInteractor
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.VmTestFixtures
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.compose.component.SettingsItemAction
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.common.domain.GetAvailableFiatCurrencies
import jp.co.soramitsu.common.domain.SelectedFiat
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.core.model.Language
import jp.co.soramitsu.feature_account_impl.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    @get:Rule val instantTaskRule = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()
    private val interactor = mockk<AccountInteractor>()
    private val details = mockk<AccountDetailsInteractor>()
    private val nomis = mockk<NomisScoreInteractor>(relaxed = true)
    private val router = mockk<AccountRouter>(relaxed = true)
    private val totalBalance = mockk<TotalBalanceUseCase>()
    private val fiatCurrencies = mockk<GetAvailableFiatCurrencies>()
    private val selectedFiat = mockk<SelectedFiat>(relaxed = true)
    private val scores = MutableStateFlow<NomisScoreData?>(null)
    private val usd = FiatCurrency("usd", "$", "US Dollar", "")
    private val eur = FiatCurrency("eur", "€", "Euro", "")

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(balances: Flow<TotalBalance>, hasChainsWithNoAccount: Boolean = false): ProfileViewModel {
        every { totalBalance.observe(any()) } returns balances
        every { details.hasChainsWithNoAccount() } returns flowOf(hasChainsWithNoAccount)
        coEvery { interactor.getSelectedLanguage() } returns Language("en")
        every { selectedFiat.get() } returns "usd"
        every { selectedFiat.flow() } returns flowOf("eur")
        every { nomis.observeNomisMultichainScoreEnabled() } returns flowOf(false)
        every { nomis.observeCurrentAccountScore() } returns scores
        return ProfileViewModel(
            interactor, details, nomis, router, mockk<AddressIconGenerator>(relaxed = true),
            mockk<ExternalAccountActions.Presentation>(relaxed = true), totalBalance, fiatCurrencies, selectedFiat,
            VmTestFixtures.resourceManager()
        )
    }

    // One ecosystem keeps the wallet icon on the generator's synchronous path.
    private fun substrateOnly() = VmTestFixtures.meta(ethereumAddress = null, ethereumPublicKey = null)

    private fun ready(meta: MetaAccount = substrateOnly()): TotalBalance.Ready {
        val partition = PortfolioBalance(BigDecimal.TEN, BigDecimal.TEN, null, null, 1, 1, 0, 0)
        return TotalBalance.Ready(meta, emptyMap(), "usd", "$", true, partition, partition, partition)
    }

    private fun tonOnly() = VmTestFixtures.meta(
        substratePublicKey = null, substrateAccountId = null, ethereumAddress = null, ethereumPublicKey = null,
        tonPublicKey = ByteArray(32) { 6 }
    )

    @Test
    fun `settings rows follow language, currency, nomis toggle and chains without accounts`() = runTest(dispatcher) {
        val vm = viewModel(flowOf(TotalBalance.AwaitingSelection), hasChainsWithNoAccount = true)
        assertEquals("usd", vm.state.value.currency)
        runCurrent()

        val state = vm.state.value
        assertEquals("EUR", state.currency)
        assertEquals(java.util.Locale.ENGLISH.displayLanguage.replaceFirstChar { it.uppercase() }, state.language)
        assertFalse(state.nomisChecked)
        assertEquals(SettingsItemAction.TransitionWithIcon(R.drawable.ic_status_warning_16), state.walletsItemAction)
        assertEquals(0, state.walletState.id)

        val plain = viewModel(flowOf(TotalBalance.AwaitingSelection), hasChainsWithNoAccount = false)
        runCurrent()
        assertEquals(SettingsItemAction.Transition, plain.state.value.walletsItemAction)
    }

    @Test
    fun `the selected wallet card shows its name and only its own nomis score`() = runTest(dispatcher) {
        val vm = viewModel(flowOf(ready()))
        runCurrent()
        assertEquals(7, vm.state.value.walletState.id)
        assertEquals("Main wallet", vm.state.value.walletState.title)
        assertTrue(WalletEcosystem.Substrate in vm.state.value.walletState.supportedEcosystems)
        assertNull(vm.state.value.walletState.score)

        scores.value = score(metaId = 7, score = 83); runCurrent()
        assertEquals(83, vm.state.value.walletState.score)

        scores.value = score(metaId = 8, score = 12); runCurrent()
        assertNull(vm.state.value.walletState.score)
    }

    @Test
    fun `a loading wallet keeps its id but no options or score navigation`() = runTest(dispatcher) {
        val vm = viewModel(flowOf(TotalBalance.Loading(7, "usd")))
        runCurrent()
        assertEquals(7, vm.state.value.walletState.id)

        vm.onWalletOptionsClick(vm.state.value.walletState)
        vm.onScoreClick(vm.state.value.walletState)
        vm.onWalletConnectClick()
        vm.onTonConnectClick()

        verify(exactly = 0) { router.openAccountDetails(any()) }
        verify(exactly = 0) { router.openScoreDetailsScreen(any()) }
        verify(exactly = 0) { router.openConnectionsScreen() }
        verify(exactly = 0) { router.openOptionsAddAccount(any(), any()) }
    }

    @Test
    fun `options and score open only for the card of the selected wallet`() = runTest(dispatcher) {
        val vm = viewModel(flowOf(ready()))
        runCurrent()
        val card = vm.state.value.walletState

        vm.onWalletOptionsClick(card)
        vm.onScoreClick(card)
        vm.onWalletOptionsClick(card.copy(id = 99))
        vm.onScoreClick(card.copy(id = 99))

        verify(exactly = 1) { router.openAccountDetails(7) }
        verify(exactly = 1) { router.openScoreDetailsScreen(7) }
    }

    @Test
    fun `wallet connect needs a substrate or ethereum account, ton connect a ton account`() = runTest(dispatcher) {
        val substrate = viewModel(flowOf(ready()))
        runCurrent()
        substrate.onWalletConnectClick()
        substrate.onTonConnectClick()
        verify { router.openConnectionsScreen() }
        verify { router.openOptionsAddAccount(7, WalletEcosystem.Ton) }

        val ton = viewModel(flowOf(ready(tonOnly())))
        runCurrent()
        ton.onWalletConnectClick()
        ton.onTonConnectClick()
        verify { router.openOptionsAddAccount(7, WalletEcosystem.Substrate) }
        verify { router.openTonConnectionsScreen() }
    }

    @Test
    fun `the currency chooser preselects the current currency and a choice is stored`() = runTest(dispatcher) {
        coEvery { fiatCurrencies.invoke() } returns listOf(eur, usd)
        val vm = viewModel(flowOf(TotalBalance.AwaitingSelection))
        vm.currencyClicked()
        runCurrent()

        val payload = vm.showFiatChooser.value!!.getContentIfNotHandled()!!
        assertEquals(listOf(eur, usd), payload.data.toList())
        assertEquals(usd, payload.selected)

        vm.onFiatSelected(eur)
        runCurrent()
        verify { selectedFiat.set("eur") }
    }

    @Test
    fun `no currencies means no chooser`() = runTest(dispatcher) {
        coEvery { fiatCurrencies.invoke() } returns emptyList()
        val vm = viewModel(flowOf(TotalBalance.AwaitingSelection))
        vm.currencyClicked()
        runCurrent()
        assertNull(vm.showFiatChooser.value)
    }

    @Test
    fun `the nomis row toggles the multichain score setting`() = runTest(dispatcher) {
        every { nomis.nomisMultichainScoreEnabled } returns true
        val vm = viewModel(flowOf(TotalBalance.AwaitingSelection))
        vm.onNomisMultichainScoreContainerClick()
        verify { nomis.nomisMultichainScoreEnabled = false }
    }

    @Test
    fun `settings rows open their screens`() = runTest(dispatcher) {
        val vm = viewModel(flowOf(TotalBalance.AwaitingSelection))
        vm.recoveryClicked()
        vm.aboutClicked()
        vm.walletsClicked()
        vm.languagesClicked()
        vm.appearanceClicked()
        vm.changePinCodeClicked()
        vm.polkaswapDisclaimerClicked()
        vm.onManageAssetsClick()

        verify {
            router.openRecoveryCenter()
            router.openAboutScreen()
            router.openSelectWallet()
            router.openLanguages()
            router.openAppearance()
            router.openChangePinCode()
            router.openPolkaswapDisclaimerFromProfile()
            router.openManageAssets()
        }
    }

    private fun score(metaId: Long, score: Int) = NomisScoreData(
        metaId, score, 0, BigDecimal.ONE, BigDecimal.ONE, 1, 1, 0, 1.0, 1.0, 1.0, null
    )
}
