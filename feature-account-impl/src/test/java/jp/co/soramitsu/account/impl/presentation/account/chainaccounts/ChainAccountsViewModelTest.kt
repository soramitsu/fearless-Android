package jp.co.soramitsu.account.impl.presentation.account.chainaccounts

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.TotalBalanceUseCase
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.account.api.presentation.exporting.ExportSource
import jp.co.soramitsu.account.impl.domain.account.details.AccountDetailsInteractor
import jp.co.soramitsu.account.impl.domain.account.details.AccountInChain
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.VmTestFixtures
import jp.co.soramitsu.account.impl.presentation.account.details.WalletAccountActionsSheet
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.data.secrets.v1.Keypair
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.list.headers.TextHeader
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.navigation.DelayedNavigation
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChainAccountsViewModelTest {
    @get:Rule val instantTaskRule = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()
    private val interactor = mockk<AccountDetailsInteractor>()
    private val walletInteractor = mockk<WalletInteractor>()
    private val router = mockk<AccountRouter>(relaxed = true)
    private val totalBalance = mockk<TotalBalanceUseCase>()
    private val chains = mockk<ChainsRepository>()
    private val externalActions = mockk<ExternalAccountActions.Presentation>(relaxed = true)

    private val polkadot = VmTestFixtures.chain(id = "polkadot", name = "Polkadot")
    private val kusama = VmTestFixtures.chain(id = "kusama", name = "Kusama")
    private val acala = VmTestFixtures.chain(id = "acala", name = "Acala")
    private val karura = VmTestFixtures.chain(id = "karura", name = "Karura")

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(balances: Flow<TotalBalance> = flowOf(TotalBalance.Unavailable)): ChainAccountsViewModel {
        every { totalBalance.observe(9) } returns balances
        every { interactor.getChainProjectionsFlow(9, WalletEcosystem.Substrate) } returns flowOf(
            mapOf(
                AccountInChain.From.META_ACCOUNT to listOf(
                    AccountInChain(polkadot, AccountInChain.Projection("1polka", ByteArray(32)), AccountInChain.From.META_ACCOUNT, "Main", true, false),
                    AccountInChain(kusama, AccountInChain.Projection("Hkusama", ByteArray(32)), AccountInChain.From.META_ACCOUNT, "Main", true, false)
                ),
                AccountInChain.From.CHAIN_ACCOUNT to listOf(
                    AccountInChain(acala, AccountInChain.Projection("22acala", ByteArray(32)), AccountInChain.From.CHAIN_ACCOUNT, "Custom", true, false)
                ),
                AccountInChain.From.ACCOUNT_WO_ADDRESS to listOf(
                    AccountInChain(karura, null, AccountInChain.From.ACCOUNT_WO_ADDRESS, null, false, markedAsNotNeed = false),
                    AccountInChain(VmTestFixtures.chain(id = "khala", name = "Khala"), null, AccountInChain.From.ACCOUNT_WO_ADDRESS, null, false, markedAsNotNeed = true)
                )
            )
        )
        return ChainAccountsViewModel(
            interactor, walletInteractor, router, mockk<AddressIconGenerator>(relaxed = true), VmTestFixtures.resourceManager(),
            totalBalance, chains, externalActions,
            SavedStateHandle(mapOf(ACCOUNT_ID_KEY to 9L, ACCOUNT_TYPE_KEY to WalletEcosystem.Substrate))
        )
    }

    private fun ChainAccountsViewModel.items() = state.value.chainProjections

    @Test
    fun `accounts are grouped under their secret source headers with placeholders for missing addresses`() = runTest(dispatcher) {
        val vm = viewModel()
        VmTestFixtures.awaitReal { vm.items().isNotEmpty() }

        val items = vm.items()
        val headers = items.filterIsInstance<TextHeader>().map { it.content }
        assertEquals(listOf("s${R.string.default_account_shared_secret}", "s${R.string.account_custom_secret}"), headers)
        val accounts = items.filterIsInstance<AccountInChainUi>()
        assertEquals(listOf("polkadot", "kusama", "acala", "karura", "khala"), accounts.map { it.chainId })
        val polka = accounts.first()
        assertEquals("1polka", polka.address)
        assertTrue(polka.enabled)
        assertNotNull(polka.accountIcon)
        val karuraUi = accounts.single { it.chainId == "karura" }
        assertEquals("s${R.string.account_no_chain_projection}", karuraUi.address)
        assertFalse(karuraUi.enabled)
        assertNotNull(karuraUi.accountIcon)
        assertNull(accounts.single { it.chainId == "khala" }.accountIcon)
        assertEquals(9, vm.state.value.walletItem!!.id)
    }

    @Test
    fun `the search keeps only chains whose name contains the query, ignoring case`() = runTest(dispatcher) {
        val vm = viewModel()
        VmTestFixtures.awaitReal { vm.items().isNotEmpty() }

        vm.onSearchInput("KA")
        VmTestFixtures.awaitReal { vm.items().filterIsInstance<AccountInChainUi>().size == 2 }
        runCurrent()

        assertEquals("KA", vm.state.value.searchQuery)
        assertEquals(listOf("polkadot", "karura"), vm.items().filterIsInstance<AccountInChainUi>().map { it.chainId })
        assertEquals(listOf("s${R.string.default_account_shared_secret}"), vm.items().filterIsInstance<TextHeader>().map { it.content })
    }

    @Test
    fun `an online account offers explorer actions while an offline one only copies`() = runTest(dispatcher) {
        coEvery { chains.getChain("polkadot") } returns polkadot
        val vm = viewModel()
        val online = ui("polkadot", isOffline = false)
        val offline = ui("taira", isOffline = true)

        vm.chainAccountOptionsClicked(offline)
        vm.chainAccountOptionsClicked(online)
        runCurrent()

        val payloads = mutableListOf<ExternalAccountActions.Payload>()
        verify(exactly = 2) { externalActions.showExternalActions(capture(payloads)) }
        val offlinePayload = payloads[0] as WalletAccountActionsSheet.Payload
        assertFalse(offlinePayload.canSwitchNode)
        assertNull(offlinePayload.chainId)
        val onlinePayload = payloads[1] as WalletAccountActionsSheet.Payload
        assertEquals("polkadot", onlinePayload.chainId)
        assertTrue(onlinePayload.canSwitchNode)
        assertEquals("addr-polkadot", onlinePayload.value)
    }

    @Test
    fun `an unsupported chain shows the update alert and update opens the store`() {
        val vm = viewModel()
        vm.chainAccountClicked(ui("polkadot", isSupported = true))
        assertNull(vm.showUnsupportedChainAlert.value)
        vm.chainAccountClicked(ui("polkadot", isSupported = false))
        assertEquals(Unit, vm.showUnsupportedChainAlert.value!!.getContentIfNotHandled())
        vm.updateAppClicked()
        assertEquals(Unit, vm.openPlayMarket.value!!.getContentIfNotHandled())
    }

    @Test
    fun `export offers the chain account's own forms or the wallet's forms`() = runTest(dispatcher) {
        val key = ByteArray(32) { 2 }
        coEvery { chains.getChain("acala") } returns acala
        coEvery { chains.getChain("polkadot") } returns polkadot
        coEvery { interactor.getMetaAccount(9) } returns VmTestFixtures.meta(
            id = 9, chainAccounts = mapOf("acala" to MetaAccount.ChainAccount(9, acala, key, key, CryptoType.SR25519, "c"))
        )
        coEvery { interactor.getChainAccountSecret(9, "acala") } returns
            ChainAccountSecrets(keyPair = Keypair(key, key), entropy = ByteArray(16))
        coEvery { walletInteractor.getExportSourceTypes("polkadot", 9) } returns mutableSetOf(ExportSource.Json)
        val vm = viewModel()

        vm.exportClicked("acala")
        runCurrent()
        assertEquals(listOf(ExportSource.Mnemonic, ExportSource.Seed, ExportSource.Json), vm.showExportSourceChooser.value!!.getContentIfNotHandled()!!.sources.toList())

        vm.exportClicked("polkadot")
        runCurrent()
        assertEquals(setOf(ExportSource.Json), vm.showExportSourceChooser.value!!.getContentIfNotHandled()!!.sources)
    }

    @Test
    fun `chosen export forms open behind the pin and navigation callbacks route`() {
        val destination = mockk<DelayedNavigation>()
        every { router.getExportSeedDestination(9, "acala") } returns destination
        every { router.getExportMnemonicDestination(9, "acala") } returns destination
        every { router.openExportJsonPasswordDestination(9, "acala") } returns destination
        val vm = viewModel()

        ExportSourceList.forEach { vm.exportTypeSelected(it, "acala") }
        vm.switchNode("acala")
        vm.onBackClick()

        verify(exactly = 3) { router.withPinCodeCheckRequired(destination, pinCodeTitleRes = R.string.account_export) }
        verify { router.openNodes("acala") }
        verify { router.back() }
    }

    @Test
    fun `a failing balance source reports the error and keeps the card`() = runTest(dispatcher) {
        val vm = viewModel(balances = flow { throw IllegalStateException("balance offline") })
        val errors = mutableListOf<String>()
        vm.errorLiveData.observeForever { it.getContentIfNotHandled()?.let(errors::add) }
        runCurrent()

        assertEquals(listOf("balance offline"), errors)
        assertEquals(R.drawable.ic_wallet, vm.state.value.walletItem!!.walletIcon)
    }

    private fun ui(chainId: String, isOffline: Boolean = false, isSupported: Boolean = true) = AccountInChainUi(
        chainId = chainId, chainName = chainId, chainIcon = "", address = "addr-$chainId", accountIcon = null,
        accountName = null, accountFrom = AccountInChain.From.META_ACCOUNT, isSupported = isSupported,
        hasAccount = true, markedAsNotNeed = false, isOffline = isOffline
    )

    private companion object {
        val ExportSourceList = listOf(ExportSource.Json, ExportSource.Seed, ExportSource.Mnemonic)
    }
}
