package jp.co.soramitsu.walletconnect.impl.presentation.sessionrequest

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import co.jp.soramitsu.walletconnect.domain.WalletConnectInteractor
import co.jp.soramitsu.walletconnect.domain.WalletConnectRouter
import com.reown.walletkit.client.Wallet
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.interfaces.TotalBalanceUseCase
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.walletconnect.impl.presentation.summary.WalletConnectRequestDecoder
import jp.co.soramitsu.walletconnect.impl.presentation.summary.WalletConnectRequestSummaries
import jp.co.soramitsu.walletconnect.impl.presentation.summary.WalletConnectSummaryPresenter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Actual Android ViewModel test draft; run with generated resources and the real module graph. */
@OptIn(ExperimentalCoroutinesApi::class)
class WalletConnectRequestLookupFailureTest {
    @get:Rule val instantTask = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val interactor = mockk<WalletConnectInteractor>()
    private val router = mockk<WalletConnectRouter>(relaxed = true)
    private val repository = mockk<AccountRepository>()
    private val total = mockk<TotalBalanceUseCase>()
    private val resources = mockk<ResourceManager>()
    private val icons = mockk<AddressIconGenerator>()
    private val request = mockk<Wallet.Model.SessionRequest>(relaxed = true)
    private val viewModels = mutableListOf<WalletConnectSignMessageViewModel>()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        every { interactor.getPendingListOfSessionRequests("topic") } returns listOf(request)
        every { request.request.method } returns "unsupported-fixture-method"
        every { resources.getString(any()) } answers { "text-${firstArg<Int>()}" }
        every { resources.getQuantityString(any(), any(), *anyVararg()) } returns "hidden"
    }

    @After
    fun tearDown() {
        viewModels.forEach { it.coroutineContext.cancel() }
        Dispatchers.resetMain()
    }

    @Test
    fun accountLookupFailurePublishesUnavailableAndError() = runTest(dispatcher) {
        coEvery { repository.allMetaAccounts() } throws IllegalStateException("controlled account lookup")
        val vm = create()
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { vm.state.first { it.wallet.balanceCaption != null } }
        }
        assertNull(vm.state.value.wallet.balance)
        assertEquals("", vm.state.value.wallet.title)
        assertNotNull(vm.state.value.wallet.balanceCaption)
        assertEquals("controlled account lookup", vm.errorLiveData.value?.peekContent())
        coVerify(exactly = 0) { total.invoke(any()) }
    }

    @Test
    fun chainLookupFailurePublishesUnavailableAndError() = runTest(dispatcher) {
        coEvery { repository.allMetaAccounts() } returns emptyList()
        coEvery { interactor.getChains() } throws IllegalStateException("controlled chain lookup")
        val vm = create()
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { vm.state.first { it.wallet.balanceCaption != null } }
        }
        assertNull(vm.state.value.wallet.balance)
        assertNotNull(vm.state.value.wallet.balanceCaption)
        assertEquals("controlled chain lookup", vm.errorLiveData.value?.peekContent())
    }

    @Test
    fun lookupCancellationDoesNotBecomeAnOrdinaryError() = runTest(dispatcher) {
        coEvery { repository.allMetaAccounts() } throws CancellationException("controlled cancellation")
        val vm = create()
        val collectors = vm.coroutineContext[Job]!!.children.toList()
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { collectors.forEach { it.join() } }
        }
        assertNull(vm.state.value.wallet.balance)
        assertNull(vm.errorLiveData.value)
    }

    @Test
    fun unknownRequestWalletPublishesUnavailableAndUnsupportedDialog() = runTest(dispatcher) {
        coEvery { repository.allMetaAccounts() } returns emptyList()
        coEvery { interactor.getChains() } returns emptyList()
        val vm = create()
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { vm.state.first { it.wallet.balanceCaption != null } }
        }
        assertNull(vm.state.value.wallet.balance)
        assertNotNull(vm.state.value.wallet.balanceCaption)
        assertNotNull(vm.errorDialogStateLiveData.value)
    }
    private fun create() = WalletConnectSignMessageViewModel(
        SavedStateHandle(mapOf(WalletConnectSignMessageFragment.SESSION_REQUEST_TOPIC_KEY to "topic")),
        total, interactor, router, resources, repository, icons,
        WalletConnectRequestSummaries(WalletConnectRequestDecoder { null }, WalletConnectSummaryPresenter(resources))
    ).also(viewModels::add)
}
