package jp.co.soramitsu.account.impl.presentation.exporting.json.confirm

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.VmTestFixtures
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.moonriverChainId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
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
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ExportJsonConfirmViewModelTest {
    @get:Rule val instantTaskRule = InstantTaskExecutorRule()
    @get:Rule val folder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val router = mockk<AccountRouter>(relaxed = true)
    private val interactor = mockk<AccountInteractor>(relaxed = true)
    private val chainRegistry = mockk<ChainRegistry>()
    private val wallet = VmTestFixtures.meta()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(chain: Chain, substrateJson: String? = SUBSTRATE_JSON, ethereumJson: String? = ETHEREUM_JSON): ExportJsonConfirmViewModel {
        coEvery { chainRegistry.getChain(chain.id) } returns chain
        coEvery { interactor.getMetaAccount(7) } returns wallet
        every { interactor.getMnemonic(7) } returns emptyFlow()
        every { interactor.getSeedForSeedExport(7) } returns emptyFlow()
        coEvery { interactor.createFileInTempStorageAndRetrieveAsset(any()) } answers {
            Result.success(File(folder.root, firstArg<String>()))
        }
        val payload = ExportJsonConfirmPayload(7, chain.id, substrateJson, ethereumJson, false)
        return ExportJsonConfirmViewModel(
            router, VmTestFixtures.resourceManager(), interactor, chainRegistry,
            SavedStateHandle(mapOf(ExportJsonConfirmFragment.PAYLOAD_KEY to payload))
        ).also { vm ->
            vm.chainLiveData.observeForever { }
            vm.accountLiveData.observeForever { }
        }
    }

    @Test
    fun `the substrate json is shared as a file named after the chain address and removed when sharing ends`() = runTest(dispatcher) {
        val chain = VmTestFixtures.chain(id = "polkadot")
        val vm = viewModel(chain)
        runCurrent()

        vm.onExportByFile(isEthereum = false)
        runCurrent()

        coVerify { interactor.updateWalletBackedUp(7) }
        val file = vm.shareEvent.value!!.getContentIfNotHandled()!!
        assertEquals("${wallet.address(chain)}.json", file.name)
        assertEquals(SUBSTRATE_JSON, file.readText())

        vm.shareCompleted()
        assertFalse(file.exists())
        verify { router.finishExportFlow() }
    }

    @Test
    fun `the ethereum json of a substrate chain is named after the wallet's moonriver address`() = runTest(dispatcher) {
        val moonriver = VmTestFixtures.chain(id = moonriverChainId, isEthereumBased = true)
        coEvery { chainRegistry.getChain(moonriverChainId) } returns moonriver
        val vm = viewModel(VmTestFixtures.chain(id = "polkadot"))
        runCurrent()

        vm.onExportByFile(isEthereum = true)
        runCurrent()

        val file = vm.shareEvent.value!!.getContentIfNotHandled()!!
        assertEquals("${wallet.address(moonriver)}.json", file.name)
        assertTrue(file.name.startsWith("0x"))
        assertEquals(ETHEREUM_JSON, file.readText())
    }

    @Test
    fun `a second share replaces the previous temporary file and clearing the screen deletes the last one`() = runTest(dispatcher) {
        val chain = VmTestFixtures.chain(id = "moonbeam", isEthereumBased = true)
        val vm = viewModel(chain)
        runCurrent()
        vm.exportEthereumJsonAsFile()
        runCurrent()
        val first = vm.shareEvent.value!!.getContentIfNotHandled()!!
        coEvery { interactor.createFileInTempStorageAndRetrieveAsset(any()) } returns Result.success(File(folder.root, "second.json"))
        vm.exportEthereumJsonAsFile()
        runCurrent()
        val second = vm.shareEvent.value!!.getContentIfNotHandled()!!

        assertFalse(first.exists())
        assertTrue(second.exists())
        val onCleared = vm.javaClass.getDeclaredMethod("onCleared").apply { isAccessible = true }
        onCleared.invoke(vm)
        assertFalse(second.exists())
    }

    @Test
    fun `a temporary storage failure is shown and nothing is shared`() = runTest(dispatcher) {
        val vm = viewModel(VmTestFixtures.chain(id = "polkadot"))
        coEvery { interactor.createFileInTempStorageAndRetrieveAsset(any()) } returns Result.failure(IllegalStateException("disk full"))
        val errors = mutableListOf<String>()
        vm.errorLiveData.observeForever { it.getContentIfNotHandled()?.let(errors::add) }
        runCurrent()

        vm.exportSubstrateAsFile()
        runCurrent()

        assertNull(vm.shareEvent.value)
        assertEquals(listOf("disk full"), errors)
    }

    @Test
    fun `exporting as text emits the chosen json and still marks the backup`() = runTest(dispatcher) {
        val vm = viewModel(VmTestFixtures.chain(id = "polkadot"))
        vm.onExportByText(isEthereum = false)
        assertEquals(SUBSTRATE_JSON, vm.exportEvent.value!!.getContentIfNotHandled())
        vm.onExportByText(isEthereum = true)
        assertEquals(ETHEREUM_JSON, vm.exportEvent.value!!.getContentIfNotHandled())
        runCurrent()
        coVerify(exactly = 2) { interactor.updateWalletBackedUp(7) }
    }

    @Test
    fun `a missing json exports nothing`() = runTest(dispatcher) {
        val vm = viewModel(VmTestFixtures.chain(id = "polkadot"), substrateJson = null, ethereumJson = null)
        runCurrent()
        vm.onExportByText(isEthereum = false)
        vm.exportSubstrateAsFile()
        vm.exportEthereumJsonAsFile()
        runCurrent()

        assertNull(vm.exportEvent.value)
        assertNull(vm.shareEvent.value)
        coVerify(exactly = 0) { interactor.createFileInTempStorageAndRetrieveAsset(any()) }
    }

    @Test
    fun `confirm buttons ask for the import type and change password goes back`() = runTest(dispatcher) {
        val vm = viewModel(VmTestFixtures.chain(id = "polkadot"))
        assertEquals(SUBSTRATE_JSON, vm.substrateJson)
        assertEquals(ETHEREUM_JSON, vm.ethereumJson)
        vm.confirmSubstrateClicked()
        assertEquals(false, vm.showJsonImportTypeEvent.value!!.getContentIfNotHandled())
        vm.confirmEthereumClicked()
        assertEquals(true, vm.showJsonImportTypeEvent.value!!.getContentIfNotHandled())
        vm.changePasswordClicked()
        verify { router.back() }
    }

    private companion object {
        const val SUBSTRATE_JSON = "{\"encoded\":\"substrate\"}"
        const val ETHEREUM_JSON = "{\"encoded\":\"ethereum\"}"
    }
}
