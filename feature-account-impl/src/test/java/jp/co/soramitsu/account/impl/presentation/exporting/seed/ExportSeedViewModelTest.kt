package jp.co.soramitsu.account.impl.presentation.exporting.seed

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.presentation.exporting.ExportSource
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.VmTestFixtures
import jp.co.soramitsu.common.data.secrets.v1.Keypair
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.utils.ComponentHolder
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
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
class ExportSeedViewModelTest {
    @get:Rule val instantTaskRule = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()
    private val router = mockk<AccountRouter>(relaxed = true)
    private val interactor = mockk<AccountInteractor>(relaxed = true)
    private val chainRegistry = mockk<ChainRegistry>()
    private val clipboard = mockk<ClipboardManager>(relaxed = true)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(chain: Chain, wallet: MetaAccount, isExportWallet: Boolean = false): ExportSeedViewModel {
        coEvery { chainRegistry.getChain(chain.id) } returns chain
        coEvery { interactor.getMetaAccount(7) } returns wallet
        every { interactor.getMnemonic(7) } returns emptyFlow()
        every { interactor.getSeedForSeedExport(7) } returns flowOf(ComponentHolder(listOf(ROOT_SEED, ROOT_ETH_KEY)))
        every { interactor.getDerivationPathForExport(7) } returns flowOf(ComponentHolder(listOf("//root", "/eth")))
        every { interactor.getDerivationPathForTonExport(7) } returns flowOf(ComponentHolder(listOf("//ton", null)))
        return ExportSeedViewModel(
            router, VmTestFixtures.resourceManager(), interactor, chainRegistry, clipboard,
            SavedStateHandle(mapOf(ExportSeedFragment.PAYLOAD_KEY to ExportSeedPayload(7, chain.id, isExportWallet)))
        )
    }

    private fun chainWallet(chain: Chain, secrets: Boolean = true): MetaAccount {
        val key = ByteArray(32) { 5 }
        if (secrets) {
            coEvery { interactor.getChainAccountSecrets(7, chain.id) } returns ChainAccountSecrets(
                keyPair = Keypair(publicKey = key, privateKey = CHAIN_PRIVATE_KEY),
                seed = CHAIN_SEED,
                derivationPath = "//chain"
            )
        }
        return VmTestFixtures.meta(chainAccounts = mapOf(chain.id to MetaAccount.ChainAccount(7, chain, key, key, CryptoType.SR25519, "c")))
    }

    @Test
    fun `opening the export warns first and marks the wallet backed up`() = runTest(dispatcher) {
        val vm = viewModel(VmTestFixtures.chain(), VmTestFixtures.meta(isBackedUp = false))
        runCurrent()

        assertEquals(ExportSource.Seed, vm.exportSource)
        assertTrue(vm.showSecurityWarningEvent.value?.getContentIfNotHandled() == Unit)
        coVerify { interactor.updateWalletBackedUp(7) }

        vm.exportClicked()
        assertTrue(vm.showSecurityWarningEvent.value?.getContentIfNotHandled() == Unit)
        vm.securityWarningCancel()
        vm.back()
        verify(exactly = 2) { router.back() }
    }

    @Test
    fun `a root wallet copies its substrate seed and ethereum key as secrets`() = runTest(dispatcher) {
        val vm = viewModel(VmTestFixtures.chain(), VmTestFixtures.meta())
        val messages = mutableListOf<String>()
        vm.messageLiveData.observeForever { it.getContentIfNotHandled()?.let(messages::add) }
        vm.isEthereum.observeForever { }
        vm.exportSeedLiveData.observeForever { }
        vm.cryptoTypeLiveData.observeForever { }
        runCurrent()

        assertEquals(CryptoType.SR25519, vm.cryptoTypeLiveData.value)
        vm.substrateSeedClicked()
        vm.ethereumSeedClicked()

        verify { clipboard.addSecretToClipboard(ROOT_SEED, any()) }
        verify { clipboard.addSecretToClipboard(ROOT_ETH_KEY, any()) }
        verify(exactly = 0) { clipboard.addToClipboard(any(), any()) }
        assertEquals(List(2) { "s${R.string.common_copied}" }, messages)
    }

    @Test
    fun `a substrate chain account exports only its own seed`() = runTest(dispatcher) {
        val chain = VmTestFixtures.chain(id = "kusama")
        val vm = viewModel(chain, chainWallet(chain))
        vm.isEthereum.observeForever { }
        vm.exportSeedLiveData.observeForever { }
        vm.derivationPathLiveData.observeForever { }
        runCurrent()

        val holder = vm.exportSeedLiveData.value!!
        assertEquals("0x" + "0a".repeat(32), holder.component1<String?>())
        assertNull(holder.component2<String?>())
        assertEquals("//chain", vm.derivationPathLiveData.value!!.component1<String?>())
        assertNull(vm.derivationPathLiveData.value!!.component2<String?>())

        vm.ethereumSeedClicked()
        verify(exactly = 0) { clipboard.addSecretToClipboard(any(), any()) }
        vm.substrateSeedClicked()
        verify { clipboard.addSecretToClipboard("0x" + "0a".repeat(32), any()) }
    }

    @Test
    fun `an ethereum chain account exports its private key, never the seed`() = runTest(dispatcher) {
        val chain = VmTestFixtures.chain(id = "moonbeam", isEthereumBased = true)
        val vm = viewModel(chain, chainWallet(chain))
        vm.isEthereum.observeForever { }
        vm.exportSeedLiveData.observeForever { }
        vm.derivationPathLiveData.observeForever { }
        runCurrent()

        val holder = vm.exportSeedLiveData.value!!
        assertNull(holder.component1<String?>())
        assertEquals("0x" + "0b".repeat(32), holder.component2<String?>())
        assertNull(vm.derivationPathLiveData.value!!.component1<String?>())
        assertEquals("//chain", vm.derivationPathLiveData.value!!.component2<String?>())

        vm.substrateSeedClicked()
        verify(exactly = 0) { clipboard.addSecretToClipboard(any(), any()) }
        vm.ethereumSeedClicked()
        verify { clipboard.addSecretToClipboard("0x" + "0b".repeat(32), any()) }
    }

    @Test
    fun `a chain account without stored secrets exports nothing`() = runTest(dispatcher) {
        val chain = VmTestFixtures.chain(id = "acala")
        coEvery { interactor.getChainAccountSecrets(7, chain.id) } returns null
        val vm = viewModel(chain, chainWallet(chain, secrets = false))
        vm.isEthereum.observeForever { }
        vm.exportSeedLiveData.observeForever { }
        runCurrent()

        assertNull(vm.exportSeedLiveData.value!!.component1<String?>())
        vm.substrateSeedClicked()
        verify(exactly = 0) { clipboard.addSecretToClipboard(any(), any()) }
    }

    @Test
    fun `root derivation paths are split by the chain's ecosystem unless the whole wallet is exported`() = runTest(dispatcher) {
        val substrate = viewModel(VmTestFixtures.chain(), VmTestFixtures.meta())
        substrate.derivationPathLiveData.observeForever { }
        runCurrent()
        assertEquals("//root", substrate.derivationPathLiveData.value!!.component1<String?>())
        assertNull(substrate.derivationPathLiveData.value!!.component2<String?>())

        val ethereum = viewModel(VmTestFixtures.chain(id = "eth", isEthereumBased = true), VmTestFixtures.meta())
        ethereum.derivationPathLiveData.observeForever { }
        runCurrent()
        assertNull(ethereum.derivationPathLiveData.value!!.component1<String?>())
        assertEquals("/eth", ethereum.derivationPathLiveData.value!!.component2<String?>())

        val wallet = viewModel(VmTestFixtures.chain(), VmTestFixtures.meta(), isExportWallet = true)
        wallet.derivationPathLiveData.observeForever { }
        runCurrent()
        assertEquals("//root", wallet.derivationPathLiveData.value!!.component1<String?>())
        assertEquals("/eth", wallet.derivationPathLiveData.value!!.component2<String?>())
        assertTrue(wallet.isExportFromWallet)
    }

    @Test
    fun `a ton account exported alone uses the ton derivation path`() = runTest(dispatcher) {
        val ton = VmTestFixtures.chain(id = "ton", ecosystem = Ecosystem.Ton)
        val vm = viewModel(ton, VmTestFixtures.meta(tonPublicKey = ByteArray(32) { 4 }))
        vm.derivationPathLiveData.observeForever { }
        runCurrent()

        assertEquals("//ton", vm.derivationPathLiveData.value!!.component1<String?>())
        assertFalse(vm.isExportFromWallet)
    }

    private companion object {
        const val ROOT_SEED = "0xroot-seed"
        const val ROOT_ETH_KEY = "0xroot-eth-private-key"
        val CHAIN_SEED = ByteArray(32) { 10 }
        val CHAIN_PRIVATE_KEY = ByteArray(32) { 11 }
    }
}
