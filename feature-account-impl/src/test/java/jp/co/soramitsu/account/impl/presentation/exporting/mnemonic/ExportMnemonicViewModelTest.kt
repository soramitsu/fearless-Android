package jp.co.soramitsu.account.impl.presentation.exporting.mnemonic

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.VmTestFixtures
import jp.co.soramitsu.common.data.secrets.v1.Keypair
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.utils.ComponentHolder
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExportMnemonicViewModelTest {
    @get:Rule val instantTaskRule = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()
    private val router = mockk<AccountRouter>(relaxed = true)
    private val interactor = mockk<AccountInteractor>(relaxed = true)
    private val chainRegistry = mockk<ChainRegistry>()
    private val rootMnemonic = MnemonicCreator.fromWords(ROOT_WORDS)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(wallet: MetaAccount, chainId: String = "polkadot"): ExportMnemonicViewModel {
        coEvery { chainRegistry.getChain(chainId) } returns VmTestFixtures.chain(id = chainId)
        coEvery { interactor.getMetaAccount(7) } returns wallet
        every { interactor.getMnemonic(7) } returns flowOf(rootMnemonic)
        every { interactor.getSeedForSeedExport(7) } returns flowOf(ComponentHolder(listOf(null, null)))
        return ExportMnemonicViewModel(
            router, VmTestFixtures.resourceManager(), interactor, chainRegistry,
            SavedStateHandle(mapOf(ExportMnemonicFragment.PAYLOAD_KEY to ExportMnemonicPayload(7, chainId, false)))
        )
    }

    @Test
    fun `a root wallet shows its numbered words and confirms with exactly those words`() = runTest(dispatcher) {
        val vm = viewModel(VmTestFixtures.meta())
        assertTrue(vm.showSecurityWarningEvent.value?.getContentIfNotHandled() == Unit)
        vm.mnemonicWordsLiveData.observeForever { }
        runCurrent()

        val words = vm.mnemonicWordsLiveData.value!!
        assertEquals(12, words.size)
        assertEquals("1", words.first().numberToShow)
        assertEquals("abandon", words.first().word)
        assertEquals("about", words.last().word)

        vm.openConfirmMnemonic()
        verify { router.openConfirmMnemonicOnExport(rootMnemonic.wordList, 7) }
    }

    @Test
    fun `a chain account shows the phrase rebuilt from its own entropy`() = runTest(dispatcher) {
        val chainMnemonic = MnemonicCreator.fromWords(CHAIN_WORDS)
        val key = ByteArray(32) { 3 }
        coEvery { interactor.getChainAccountSecrets(7, "kusama") } returns
            ChainAccountSecrets(keyPair = Keypair(key, key), entropy = chainMnemonic.entropy)
        val wallet = VmTestFixtures.meta(chainAccounts = mapOf("kusama" to MetaAccount.ChainAccount(7, null, key, key, CryptoType.SR25519, "k")))
        val vm = viewModel(wallet, chainId = "kusama")
        vm.mnemonicWordsLiveData.observeForever { }
        runCurrent()

        assertEquals(CHAIN_WORDS.split(" "), vm.mnemonicWordsLiveData.value!!.map { it.word })
        vm.openConfirmMnemonic()
        verify { router.openConfirmMnemonicOnExport(chainMnemonic.wordList, 7) }
    }

    @Test
    fun `confirm does nothing before the phrase is loaded and cancel or back leave`() = runTest(dispatcher) {
        val vm = viewModel(VmTestFixtures.meta())
        vm.openConfirmMnemonic()
        vm.exportClicked()
        vm.securityWarningCancel()
        vm.back()

        verify(exactly = 0) { router.openConfirmMnemonicOnExport(any(), any()) }
        verify(exactly = 2) { router.back() }
        assertTrue(vm.showSecurityWarningEvent.value?.getContentIfNotHandled() == Unit)
    }

    private companion object {
        const val ROOT_WORDS = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        const val CHAIN_WORDS = "legal winner thank year wave sausage worth useful legal winner thank yellow"
    }
}
