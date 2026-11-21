package jp.co.soramitsu.account.impl.presentation.options_switch_node

import androidx.lifecycle.SavedStateHandle
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.interfaces.AssetNotNeedAccountUseCase
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.VmTestFixtures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OptionsSwitchNodeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val router = mockk<AccountRouter>(relaxed = true)
    private val interactor = mockk<AccountInteractor>()
    private val assetNotNeedAccount = mockk<AssetNotNeedAccountUseCase>(relaxed = true)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(): OptionsSwitchNodeViewModel {
        every { interactor.selectedMetaAccountFlow() } returns flowOf(VmTestFixtures.meta())
        return OptionsSwitchNodeViewModel(
            SavedStateHandle(
                mapOf(
                    OptionsSwitchNodeFragment.KEY_META_ID to 3L,
                    OptionsSwitchNodeFragment.KEY_CHAIN_ID to "kusama",
                    OptionsSwitchNodeFragment.KEY_CHAIN_NAME to "Kusama"
                )
            ),
            interactor, assetNotNeedAccount, router
        )
    }

    @Test
    fun `the screen shows the chain it was opened for once the wallet is known`() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals("Dotsama", vm.state.value.chainName)
        withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                while (vm.state.value.chainName != "Kusama") delay(10)
            }
        }
        assertEquals(OptionsSwitchNodeScreenViewState(metaId = 3, chainId = "kusama", chainName = "Kusama"), vm.state.value)
    }

    @Test
    fun `switch opens the chain's nodes and don't show again hides the chain's assets before closing`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onSwitch("kusama")
        verify { router.openNodes("kusama") }

        vm.dontShowAgainClicked("kusama", 3)
        runCurrent()
        coVerifyOrder {
            assetNotNeedAccount.markChainAssetsNotNeed(chainId = "kusama", metaId = 3)
            router.back()
        }
        vm.onBackClicked()
        verify(exactly = 2) { router.back() }
    }
}
