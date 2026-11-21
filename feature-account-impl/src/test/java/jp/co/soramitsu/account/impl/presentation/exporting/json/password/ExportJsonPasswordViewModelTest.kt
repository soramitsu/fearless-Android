package jp.co.soramitsu.account.impl.presentation.exporting.json.password

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.VmTestFixtures
import jp.co.soramitsu.account.impl.presentation.exporting.json.confirm.ExportJsonConfirmPayload
import jp.co.soramitsu.common.utils.ComponentHolder
import jp.co.soramitsu.common.view.ButtonState
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
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

@OptIn(ExperimentalCoroutinesApi::class)
class ExportJsonPasswordViewModelTest {
    @get:Rule val instantTaskRule = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()
    private val router = mockk<AccountRouter>(relaxed = true)
    private val interactor = mockk<AccountInteractor>(relaxed = true)
    private val chainRegistry = mockk<ChainRegistry>()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        chain: Chain,
        isExportWallet: Boolean = false,
        wallet: MetaAccount = VmTestFixtures.meta(name = WALLET_NAME),
        catalog: List<Chain> = emptyList()
    ): ExportJsonPasswordViewModel {
        coEvery { chainRegistry.getChain(chain.id) } returns chain
        every { chainRegistry.currentChains } returns MutableStateFlow(catalog)
        coEvery { interactor.getMetaAccount(7) } returns wallet
        every { interactor.getMnemonic(7) } returns emptyFlow()
        every { interactor.getSeedForSeedExport(7) } returns emptyFlow()
        every { interactor.getDerivationPathForExport(7) } returns flowOf(ComponentHolder(listOf(null, null)))
        return ExportJsonPasswordViewModel(
            router, interactor, chainRegistry, VmTestFixtures.resourceManager(),
            SavedStateHandle(mapOf(ExportJsonPasswordFragment.PAYLOAD_KEY to ExportJsonPasswordPayload(7, chain.id, isExportWallet)))
        )
    }

    private fun ExportJsonPasswordViewModel.observeAll() {
        nextButtonState.observeForever { }
        showPasswordRuleErrorLiveData.observeForever { }
        showDoNotMatchingErrorLiveData.observeForever { }
        chainLiveData.observeForever { }
    }

    private fun ExportJsonPasswordViewModel.type(password: String, confirmation: String = password) {
        passwordLiveData.value = password
        passwordConfirmationLiveData.value = confirmation
    }

    @Test
    fun `next unlocks only for a strong matching password that is not the wallet name`() = runTest(dispatcher) {
        val vm = viewModel(VmTestFixtures.chain())
        assertTrue(vm.showSecurityWarningEvent.value?.getContentIfNotHandled() == Unit)
        vm.observeAll()
        runCurrent()

        vm.type("short1A!")
        assertEquals(true, vm.showPasswordRuleErrorLiveData.value)
        assertEquals(ButtonState.DISABLED, vm.nextButtonState.value)

        vm.type(STRONG, "Correct-Horse-8")
        assertEquals(false, vm.showPasswordRuleErrorLiveData.value)
        assertEquals(true, vm.showDoNotMatchingErrorLiveData.value)
        assertEquals(ButtonState.DISABLED, vm.nextButtonState.value)

        vm.type(WALLET_NAME)
        assertEquals(true, vm.showPasswordRuleErrorLiveData.value)
        assertEquals(ButtonState.DISABLED, vm.nextButtonState.value)

        vm.type(STRONG)
        assertEquals(false, vm.showDoNotMatchingErrorLiveData.value)
        assertEquals(ButtonState.NORMAL, vm.nextButtonState.value)
    }

    @Test
    fun `a substrate chain export opens the confirmation with only the substrate json`() = runTest(dispatcher) {
        val chain = VmTestFixtures.chain(id = "kusama")
        coEvery { interactor.generateRestoreJson(7, "kusama", STRONG) } returns Result.success("{substrate}")
        val vm = viewModel(chain)
        vm.observeAll()
        runCurrent()
        vm.type(STRONG)

        vm.nextClicked()
        assertEquals(ButtonState.PROGRESS, vm.nextButtonState.value)
        runCurrent()

        val payload = slot<ExportJsonConfirmPayload>()
        verify { router.openExportJsonConfirm(capture(payload)) }
        assertEquals("{substrate}", payload.captured.substrateJson)
        assertNull(payload.captured.ethereumJson)
        assertEquals(7, payload.captured.metaId)
        assertEquals("kusama", payload.captured.chainId)
    }

    @Test
    fun `an ethereum chain export opens the confirmation with only the ethereum json`() = runTest(dispatcher) {
        val chain = VmTestFixtures.chain(id = "moonbeam", isEthereumBased = true)
        coEvery { interactor.generateRestoreJson(7, "moonbeam", STRONG) } returns Result.success("{ethereum}")
        val vm = viewModel(chain)
        vm.observeAll()
        runCurrent()
        vm.type(STRONG)
        vm.nextClicked()
        runCurrent()

        val payload = slot<ExportJsonConfirmPayload>()
        verify { router.openExportJsonConfirm(capture(payload)) }
        assertNull(payload.captured.substrateJson)
        assertEquals("{ethereum}", payload.captured.ethereumJson)
    }

    @Test
    fun `a whole wallet export uses polkadot and the first supported mainnet ethereum chain without its own account`() = runTest(dispatcher) {
        val sepolia = VmTestFixtures.chain(id = "sepolia", name = "Sepolia", isEthereumBased = true, isTestNet = true)
        val moonbeam = VmTestFixtures.chain(id = "moonbeam", name = "Moonbeam", isEthereumBased = true)
        val ethereum = VmTestFixtures.chain(id = "ethereum", name = "Ethereum", isEthereumBased = true)
        val custom = VmTestFixtures.chain(id = "custom", name = "Arbitrum", isEthereumBased = true)
        val key = ByteArray(20) { 1 }
        val wallet = VmTestFixtures.meta(
            name = WALLET_NAME,
            chainAccounts = mapOf("custom" to MetaAccount.ChainAccount(7, custom, key, key, CryptoType.ECDSA, "own"))
        )
        coEvery { interactor.generateRestoreJson(7, polkadotChainId, STRONG) } returns Result.success("{dot}")
        coEvery { interactor.generateRestoreJson(7, "ethereum", STRONG) } returns Result.success("{eth}")
        val vm = viewModel(
            VmTestFixtures.chain(id = "polkadot-ui"), isExportWallet = true, wallet = wallet,
            catalog = listOf(sepolia, moonbeam, ethereum, custom, VmTestFixtures.chain(id = "kusama"))
        )
        vm.observeAll()
        runCurrent()
        vm.type(STRONG)
        vm.nextClicked()
        runCurrent()

        val payload = slot<ExportJsonConfirmPayload>()
        verify { router.openExportJsonConfirm(capture(payload)) }
        assertEquals("{dot}", payload.captured.substrateJson)
        assertEquals("{eth}", payload.captured.ethereumJson)
        assertTrue(payload.captured.isExportWallet)
        assertTrue(vm.isExportWallet)
        coVerify(exactly = 0) { interactor.generateRestoreJson(7, "custom", any()) }
    }

    @Test
    fun `a whole wallet export without any ethereum chain still exports the substrate json`() = runTest(dispatcher) {
        coEvery { interactor.generateRestoreJson(7, polkadotChainId, STRONG) } returns Result.success("{dot}")
        val vm = viewModel(VmTestFixtures.chain(), isExportWallet = true)
        vm.observeAll()
        runCurrent()
        vm.type(STRONG)
        vm.nextClicked()
        runCurrent()

        val payload = slot<ExportJsonConfirmPayload>()
        verify { router.openExportJsonConfirm(capture(payload)) }
        assertEquals("{dot}", payload.captured.substrateJson)
        assertNull(payload.captured.ethereumJson)
    }

    @Test
    fun `a failed json generation stops the progress and opens nothing`() = runTest(dispatcher) {
        coEvery { interactor.generateRestoreJson(any(), any(), any()) } returns Result.failure(IllegalStateException("locked"))
        val vm = viewModel(VmTestFixtures.chain())
        vm.observeAll()
        runCurrent()
        vm.type(STRONG)
        vm.nextClicked()
        runCurrent()

        verify(exactly = 0) { router.openExportJsonConfirm(any()) }
        assertEquals(ButtonState.NORMAL, vm.nextButtonState.value)
    }

    @Test
    fun `next refuses a weak password even when reached directly, and progress can be reset`() = runTest(dispatcher) {
        val vm = viewModel(VmTestFixtures.chain())
        vm.observeAll()
        vm.nextClicked()
        runCurrent()
        vm.type("weak")
        vm.nextClicked()
        runCurrent()

        coVerify(exactly = 0) { interactor.generateRestoreJson(any(), any(), any()) }
        vm.resetProgress()
        assertEquals(ButtonState.DISABLED, vm.nextButtonState.value)
        vm.securityWarningCancel()
        vm.back()
        verify(exactly = 2) { router.back() }
    }

    @Test
    fun `an unreadable wallet still loads the rule with no forbidden values`() = runTest(dispatcher) {
        val vm = viewModel(VmTestFixtures.chain())
        coEvery { interactor.getMetaAccount(7) } throws IllegalStateException("gone")
        vm.observeAll()
        runCurrent()
        vm.type(STRONG)

        assertEquals(ButtonState.NORMAL, vm.nextButtonState.value)
    }

    private companion object {
        const val STRONG = "Correct-Horse-9"
        const val WALLET_NAME = "My long wallet name 2024"
    }
}
