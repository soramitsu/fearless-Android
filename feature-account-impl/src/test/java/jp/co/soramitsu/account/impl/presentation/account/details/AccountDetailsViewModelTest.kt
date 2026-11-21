package jp.co.soramitsu.account.impl.presentation.account.details

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
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.VmTestFixtures
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.data.secrets.v1.Keypair
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountDetailsViewModelTest {
    @get:Rule val instantTaskRule = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()
    private val interactor = mockk<AccountDetailsInteractor>()
    private val walletInteractor = mockk<WalletInteractor>()
    private val router = mockk<AccountRouter>(relaxed = true)
    private val totalBalance = mockk<TotalBalanceUseCase>()
    private val chains = mockk<ChainsRepository>()
    private val externalActions = mockk<ExternalAccountActions.Presentation>(relaxed = true)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        balances: Flow<TotalBalance> = flowOf(TotalBalance.Unavailable),
        summary: List<Pair<WalletEcosystem, Int>> = listOf(WalletEcosystem.Substrate to 2, WalletEcosystem.Ethereum to 5, WalletEcosystem.Ton to 0)
    ): AccountDetailsViewModel {
        every { totalBalance.observe(9) } returns balances
        every { interactor.getChainAccountsSummaryFlow(9) } returns flowOf(summary)
        return AccountDetailsViewModel(
            interactor, walletInteractor, router, mockk<AddressIconGenerator>(relaxed = true), VmTestFixtures.resourceManager(),
            totalBalance, chains, externalActions, SavedStateHandle(mapOf(ACCOUNT_ID_KEY to 9L))
        )
    }

    @Test
    fun `connected accounts are titled per ecosystem and sorted by how many chains they cover`() = runTest(dispatcher) {
        val vm = viewModel(summary = WalletEcosystem.entries.mapIndexed { index, type -> type to index })
        runCurrent()

        val info = vm.state.value.connectedAccountsInfo
        assertEquals(WalletEcosystem.entries.reversed(), info.map { it.accountType })
        val titles = info.associate { it.accountType to it.title }
        assertEquals("s${R.string.connected_accounts_substrate_title}", titles[WalletEcosystem.Substrate])
        assertEquals("s${R.string.connected_accounts_ethereum_title}", titles[WalletEcosystem.Ethereum])
        assertEquals("s${R.string.connected_accounts_ton_title}", titles[WalletEcosystem.Ton])
        assertEquals("s${R.string.connected_accounts_bitcoin_title}", titles[WalletEcosystem.Bitcoin])
        assertEquals("s${R.string.connected_accounts_solana_title}", titles[WalletEcosystem.Solana])
        assertEquals("s${R.string.connected_accounts_iroha_title}", titles[WalletEcosystem.Iroha])
    }

    @Test
    fun `an unavailable balance still shows the wallet card with the default icon`() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()

        val card = vm.state.value.walletItem!!
        assertEquals(9, card.id)
        assertEquals("", card.title)
        assertEquals(R.drawable.ic_wallet, card.walletIcon)
    }

    @Test
    fun `a failing balance source shows the card as unavailable and reports the failure`() = runTest(dispatcher) {
        val vm = viewModel(balances = flow { throw IllegalStateException("balance offline") })
        val errors = mutableListOf<String>()
        vm.errorLiveData.observeForever { it.getContentIfNotHandled()?.let(errors::add) }
        runCurrent()

        assertEquals(listOf("balance offline"), errors)
        assertEquals(R.drawable.ic_wallet, vm.state.value.walletItem!!.walletIcon)
    }

    @Test
    fun `ecosystem options open the accounts list when chains are connected, otherwise adding an account`() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()

        vm.accountsItemOptionsClicked(WalletEcosystem.Ethereum)
        vm.accountsItemOptionsClicked(WalletEcosystem.Ton)
        vm.accountsItemOptionsClicked(WalletEcosystem.Bitcoin)
        runCurrent()

        verify { router.openEcosystemAccountsOptions(9, WalletEcosystem.Ethereum) }
        verify { router.openOptionsAddAccount(9, WalletEcosystem.Ton) }
        verify { router.openOptionsAddAccount(9, WalletEcosystem.Bitcoin) }
    }

    @Test
    fun `a chain account offers only the export forms its own secrets allow`() = runTest(dispatcher) {
        val chain = VmTestFixtures.chain(id = "kusama")
        val key = ByteArray(32) { 1 }
        coEvery { chains.getChain("kusama") } returns chain
        coEvery { interactor.getMetaAccount(9) } returns VmTestFixtures.meta(
            id = 9,
            chainAccounts = mapOf("kusama" to MetaAccount.ChainAccount(9, chain, key, key, CryptoType.SR25519, "k"))
        )
        coEvery { interactor.getChainAccountSecret(9, "kusama") } returns ChainAccountSecrets(keyPair = Keypair(key, key), seed = ByteArray(32))
        val vm = viewModel()
        runCurrent()

        vm.exportClicked("kusama")
        runCurrent()

        val payload = vm.showExportSourceChooser.value!!.getContentIfNotHandled()!!
        assertEquals("kusama", payload.chainId)
        assertEquals(listOf(ExportSource.Seed, ExportSource.Json), payload.sources.toList())
    }

    @Test
    fun `a root account asks the wallet which export forms exist`() = runTest(dispatcher) {
        coEvery { chains.getChain("polkadot") } returns VmTestFixtures.chain(id = "polkadot")
        coEvery { interactor.getMetaAccount(9) } returns VmTestFixtures.meta(id = 9)
        coEvery { walletInteractor.getExportSourceTypes("polkadot", 9) } returns mutableSetOf(ExportSource.Mnemonic, ExportSource.Json)
        val vm = viewModel()
        vm.exportClicked("polkadot")
        runCurrent()

        assertEquals(setOf(ExportSource.Mnemonic, ExportSource.Json), vm.showExportSourceChooser.value!!.getContentIfNotHandled()!!.sources)
    }

    @Test
    fun `each chosen export form opens its screen behind the pin`() {
        val json = mockk<DelayedNavigation>()
        val seed = mockk<DelayedNavigation>()
        val mnemonic = mockk<DelayedNavigation>()
        every { router.openExportJsonPasswordDestination(9, "polkadot") } returns json
        every { router.getExportSeedDestination(9, "polkadot") } returns seed
        every { router.getExportMnemonicDestination(9, "polkadot") } returns mnemonic
        val vm = viewModel()

        vm.exportTypeSelected(ExportSource.Json, "polkadot")
        vm.exportTypeSelected(ExportSource.Seed, "polkadot")
        vm.exportTypeSelected(ExportSource.Mnemonic, "polkadot")

        listOf(json, seed, mnemonic).forEach { destination ->
            verify { router.withPinCodeCheckRequired(destination, pinCodeTitleRes = R.string.account_export) }
        }
    }

    @Test
    fun `wallet options, back and node switching navigate`() {
        val vm = viewModel()
        vm.walletOptionsClicked(WalletItemViewState(id = 9, title = "w", isSelected = false, walletIcon = 0))
        vm.onBackClick()
        vm.switchNode("kusama")

        verify { router.openOptionsWallet(9, false) }
        verify { router.back() }
        verify { router.openNodes("kusama") }
    }
}
