package jp.co.soramitsu.walletconnect.impl.presentation.sessionrequest

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import co.jp.soramitsu.walletconnect.domain.WalletConnectInteractor
import co.jp.soramitsu.walletconnect.domain.WalletConnectRouter
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.interfaces.TotalBalanceUseCase
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.walletconnect.impl.presentation.TEST_TOPIC
import jp.co.soramitsu.walletconnect.impl.presentation.awaitErrorDialog
import jp.co.soramitsu.walletconnect.impl.presentation.caip2id
import jp.co.soramitsu.walletconnect.impl.presentation.ethereumChain
import jp.co.soramitsu.walletconnect.impl.presentation.sessionRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WalletConnectSignMessageViewModelTest {

    @get:Rule
    val instantTasks = InstantTaskExecutorRule()

    private val evmAddress = "0x8ba1f109551bd432803012645ac136ddd64dba72"

    private val interactor = mockk<WalletConnectInteractor>(relaxed = true)
    private val router = mockk<WalletConnectRouter>(relaxed = true)
    private val resourceManager = mockk<ResourceManager>(relaxed = true)
    private val accountRepository = mockk<AccountRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { interactor.getChains() } returns listOf(ethereumChain)
        coEvery { accountRepository.allMetaAccounts() } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `malformed request shows an error and closing it answers the dApp`() {
        val malformed = sessionRequest(id = 7, method = "personal_sign", params = """["0xzz", "$evmAddress"]""", chainId = ethereumChain.caip2id)
        every { interactor.getPendingListOfSessionRequests(TEST_TOPIC) } returns listOf(malformed)

        val dialog = viewModel().awaitErrorDialog()
        dialog.positiveClick()

        verify { interactor.rejectSessionRequest(TEST_TOPIC, 7, any(), any()) }
    }

    @Test
    fun `preview opens for exactly the request this screen shows`() {
        val older = personalSign(id = 5)
        val shown = personalSign(id = 9)
        every { interactor.getPendingListOfSessionRequests(TEST_TOPIC) } returns listOf(older, shown)

        viewModel().onPreviewClick()

        verify { router.openRequestPreview(TEST_TOPIC, 9) }
    }

    @Test
    fun `closing does not answer another request once the shown one is gone`() {
        val shown = personalSign(id = 9)
        every { interactor.getPendingListOfSessionRequests(TEST_TOPIC) } returns listOf(shown)
        val viewModel = viewModel()
        every { interactor.getPendingListOfSessionRequests(TEST_TOPIC) } returns listOf(personalSign(id = 12))

        viewModel.onClose()

        verify { router.back() }
        verify(exactly = 0) { interactor.rejectSessionRequest(any(), any(), any(), any()) }
    }

    private fun personalSign(id: Long) = sessionRequest(
        id = id,
        method = "personal_sign",
        params = """["0x68656c6c6f", "$evmAddress"]""",
        chainId = ethereumChain.caip2id
    )

    private fun viewModel() = WalletConnectSignMessageViewModel(
        savedStateHandle = SavedStateHandle(mapOf(WalletConnectSignMessageFragment.SESSION_REQUEST_TOPIC_KEY to TEST_TOPIC)),
        getTotalBalance = mockk<TotalBalanceUseCase>(relaxed = true),
        walletConnectInteractor = interactor,
        walletConnectRouter = router,
        resourceManager = resourceManager,
        accountRepository = accountRepository,
        addressIconGenerator = mockk<AddressIconGenerator>(relaxed = true)
    )
}
