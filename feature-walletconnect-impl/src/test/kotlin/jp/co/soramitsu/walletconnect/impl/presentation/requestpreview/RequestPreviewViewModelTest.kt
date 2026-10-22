package jp.co.soramitsu.walletconnect.impl.presentation.requestpreview

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import co.jp.soramitsu.feature_walletconnect_impl.R
import co.jp.soramitsu.walletconnect.domain.WalletConnectInteractor
import co.jp.soramitsu.walletconnect.domain.WalletConnectRouter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.fearless_utils.extensions.fromHex
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
class RequestPreviewViewModelTest {

    @get:Rule
    val instantTasks = InstantTaskExecutorRule()

    private val evmAddress = "0x8ba1f109551bd432803012645ac136ddd64dba72"
    private val shown = personalSign(id = 5, messageHex = "0x68656c6c6f")
    private val newer = personalSign(id = 9, messageHex = "0x6f74686572")

    private val interactor = mockk<WalletConnectInteractor>(relaxed = true)
    private val router = mockk<WalletConnectRouter>(relaxed = true)
    private val resourceManager = mockk<ResourceManager>()
    private val accountRepository = mockk<AccountRepository>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { resourceManager.getString(any()) } answers { "string:${firstArg<Int>()}" }
        every { interactor.getPendingListOfSessionRequests(TEST_TOPIC) } returns listOf(shown, newer)
        coEvery { interactor.getChains() } returns listOf(ethereumChain)
        val wallet = mockk<MetaAccount>(relaxed = true) {
            every { id } returns 1L
            every { name } returns "Wallet"
            every { chainAccounts } returns emptyMap()
            every { substrateAccountId } returns null
            every { ethereumAddress } returns evmAddress.fromHex()
        }
        coEvery { accountRepository.allMetaAccounts() } returns listOf(wallet)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `preview shows and signs the request it was opened for, not the newest one`() {
        val viewModel = viewModel(requestId = shown.request.id)

        viewModel.onTableItemClick(RAW_DATA_ITEM)
        viewModel.onSignClick()

        verify { router.openRawData("hello") }
        coVerify { interactor.handleSignAction(ethereumChain, TEST_TOPIC, shown, any(), any(), any()) }
        coVerify(exactly = 0) { interactor.handleSignAction(any(), any(), newer, any(), any(), any()) }
    }

    @Test
    fun `sign is refused once the request is no longer pending`() {
        val viewModel = viewModel(requestId = shown.request.id)
        every { interactor.getPendingListOfSessionRequests(TEST_TOPIC) } returns listOf(newer)

        viewModel.onSignClick()
        val dialog = viewModel.awaitErrorDialog { it.message == "string:${R.string.common_undefined_error_message}" }
        dialog.positiveClick()

        coVerify(exactly = 0) { interactor.handleSignAction(any(), any(), any(), any(), any(), any()) }
        verify { router.back() }
    }

    @Test
    fun `sign is refused when the request changed after it was shown`() {
        val viewModel = viewModel(requestId = shown.request.id)
        val changed = personalSign(id = shown.request.id, messageHex = "0x6f74686572")
        every { interactor.getPendingListOfSessionRequests(TEST_TOPIC) } returns listOf(changed, newer)

        viewModel.onSignClick()
        viewModel.awaitErrorDialog { it.message == "string:${R.string.common_undefined_error_message}" }

        coVerify(exactly = 0) { interactor.handleSignAction(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `preview opened without a request id closes instead of picking one`() {
        val viewModel = viewModel(requestId = null)

        viewModel.onSignClick()

        verify { router.back() }
        coVerify(exactly = 0) { interactor.handleSignAction(any(), any(), any(), any(), any(), any()) }
    }

    private fun personalSign(id: Long, messageHex: String) = sessionRequest(
        id = id,
        method = "personal_sign",
        params = """["$messageHex", "$evmAddress"]""",
        chainId = ethereumChain.caip2id
    )

    private fun viewModel(requestId: Long?) = RequestPreviewViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(
                RequestPreviewFragment.PAYLOAD_TOPIC_KEY to TEST_TOPIC,
                RequestPreviewFragment.PAYLOAD_REQUEST_ID_KEY to requestId
            )
        ),
        walletConnectInteractor = interactor,
        walletConnectRouter = router,
        resourceManager = resourceManager,
        accountRepository = accountRepository,
        addressIconGenerator = mockk<AddressIconGenerator>(relaxed = true)
    )

    private companion object {
        const val RAW_DATA_ITEM = 1
    }
}
