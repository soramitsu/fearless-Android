package jp.co.soramitsu.wallet.impl.presentation.send.setup

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.base.models.ErrorDialogState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUseCase
import jp.co.soramitsu.wallet.impl.domain.SendPausedException
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.send.SendSharedState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset as ChainAsset

/**
 * Entry points without their own veto check (QR scan, search, repeat, switching the asset inside
 * Send) reach the send screen, where the transfer service refuses the fee lookup for a vetoed
 * network. The screen must explain the pause and offer Receive, not a generic fee error.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SendSetupPausedSendTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val payload = AssetPayload(CHAIN_ID, ASSET_ID)
    private val sharedState = SendSharedState()

    private val chainAsset = mockk<ChainAsset>(relaxed = true).also { asset ->
        every { asset.chainId } returns CHAIN_ID
        every { asset.id } returns ASSET_ID
    }
    private val chain = mockk<Chain>(relaxed = true).also { chain ->
        every { chain.id } returns CHAIN_ID
        every { chain.name } returns "Network"
        every { chain.ecosystem } returns Ecosystem.Ethereum
        every { chain.externalApi } returns null
    }
    private val asset = mockk<Asset>(relaxed = true).also { asset ->
        every { asset.token.configuration } returns chainAsset
    }
    private val metaAccount = mockk<MetaAccount>(relaxed = true).also { account ->
        every { account.ethereumAddress } returns ByteArray(20) { 1 }
    }

    private var feeFailure: Throwable = SendPausedException(PAUSED_TITLE, PAUSED_MESSAGE)
    private val walletInteractor = mockk<WalletInteractor>(relaxed = true).also { interactor ->
        coEvery { interactor.getChain(CHAIN_ID) } returns chain
        coEvery { interactor.getCurrentAsset(CHAIN_ID, ASSET_ID) } returns asset
        coEvery { interactor.getSelectedMetaAccount() } returns metaAccount
        coEvery { interactor.observeTransferFee(any()) } answers { flow { throw feeFailure } }
        // A relaxed PhishingModel cannot return its Compose colour (a value class).
        coEvery { interactor.getPhishingInfo(any()) } returns null
    }
    private val router = mockk<WalletRouter>(relaxed = true)
    private val resourceManager = mockk<ResourceManager>(relaxed = true).also { resources ->
        every { resources.getString(R.string.fee_calculation_error) } returns FEE_ERROR
        every { resources.getString(R.string.common_action_receive) } returns RECEIVE
    }

    private val viewModelStore = ViewModelStore()
    private val viewModelJobs = mutableListOf<Job>()

    // Cancel the view model and wait for its Dispatchers.Default work to finish before the rule
    // resets Dispatchers.Main; a late resumption would otherwise fail the next coroutine test.
    @After
    fun tearDown() {
        viewModelStore.clear()
        runBlocking { withTimeout(AWAIT_MILLIS) { viewModelJobs.joinAll() } }
    }

    @Test
    fun `vetoed network shows the paused sheet with Receive instead of a fee error`() {
        val viewModel = viewModel()

        val dialog = viewModel.awaitDialog()
        assertEquals(PAUSED_TITLE, dialog.title)
        assertEquals(PAUSED_MESSAGE, dialog.message)
        assertEquals(RECEIVE, dialog.positiveButtonText)
        assertNull(viewModel.errorLiveData.value)

        dialog.positiveClick()
        verifyOrder {
            router.back()
            router.openReceive(payload)
        }
    }

    @Test
    fun `closing the paused sheet leaves the send screen`() {
        val dialog = viewModel().awaitDialog()

        dialog.negativeClick()

        verify { router.back() }
        verify(exactly = 0) { router.openReceive(any()) }
    }

    @Test
    fun `other fee failures keep the generic fee error`() {
        feeFailure = IllegalStateException("node unavailable")
        val viewModel = viewModel()

        val error = awaitValue { viewModel.errorLiveData.value?.peekContent() }
        assertEquals(FEE_ERROR, error)
        assertNull(viewModel.errorDialogStateLiveData.value)
    }

    @Test
    fun `changing exact network identity clears amount before replacement asset loads`() {
        val viewModel = viewModel()
        viewModel.awaitDialog()
        viewModel.onAmountInput(java.math.BigDecimal("42"))
        awaitValue { viewModel.state.value.amountInputState.tokenAmount.takeIf { it == java.math.BigDecimal("42") } }
        val deferred = kotlinx.coroutines.CompletableDeferred<Asset>()
        coEvery { walletInteractor.getCurrentAsset("another-network", ASSET_ID) } coAnswers { deferred.await() }
        sharedState.update("another-network", ASSET_ID)
        awaitValue { viewModel.state.value.amountInputState.tokenAmount.takeIf { it == java.math.BigDecimal.ZERO } }
        assertEquals(java.math.BigDecimal.ZERO, viewModel.state.value.amountInputState.tokenAmount)
        deferred.complete(asset)
    }

    @Test
    fun `native Bitcoin send cannot enter an unsupported cross chain flow`() {
        every { chain.id } returns "bitcoin-mainnet"
        val viewModel = viewModel()
        awaitValue { viewModel.state.value.takeIf { it.nativeReview } }
        assertEquals(false, viewModel.state.value.crossChainAvailable)
        viewModel.onCrossChainClick()
        verify(exactly = 0) { router.openCrossChainSend(any()) }
    }

    @Test
    fun `native Solana send cannot enter an unsupported cross chain flow`() {
        every { chain.id } returns "solana-mainnet"
        val viewModel = viewModel()
        awaitValue { viewModel.state.value.takeIf { it.nativeReview } }
        assertEquals(false, viewModel.state.value.crossChainAvailable)
        viewModel.onCrossChainClick()
        verify(exactly = 0) { router.openCrossChainSend(any()) }
    }

    @Test
    fun `loaded eligible legacy network still opens its exact cross chain asset`() {
        val viewModel = viewModel()
        viewModel.awaitDialog()
        assertEquals(true, viewModel.state.value.crossChainAvailable)
        viewModel.onCrossChainClick()
        verify(exactly = 1) { router.openCrossChainSend(payload) }
    }

    private fun viewModel(): SendSetupViewModel {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = createViewModel() as T
        }
        return ViewModelProvider(viewModelStore, factory)[SendSetupViewModel::class.java]
            .also { viewModelJobs += it.viewModelScope.coroutineContext.job }
    }

    private fun createViewModel() = SendSetupViewModel(
        sharedState = sharedState,
        savedStateHandle = SavedStateHandle(mapOf(SendSetupFragment.KEY_PAYLOAD to payload)),
        resourceManager = resourceManager,
        walletInteractor = walletInteractor,
        nomisScoreInteractor = mockk(relaxed = true),
        polkaswapInteractor = mockk(relaxed = true),
        // A relaxed mock cannot build the Result the use case returns.
        existentialDepositUseCase = object : ExistentialDepositUseCase {
            override suspend fun invoke(chainAsset: ChainAsset) = Result.success(BigInteger.ZERO)
        },
        walletConstants = mockk(relaxed = true),
        router = router,
        clipboardManager = mockk(relaxed = true),
        addressIconGenerator = mockk(relaxed = true),
        currentAccountAddress = mockk(relaxed = true),
        validateTransferUseCase = mockk(relaxed = true),
        quickInputsUseCase = mockk(relaxed = true)
    )

    private fun SendSetupViewModel.awaitDialog(): ErrorDialogState =
        awaitValue { errorDialogStateLiveData.value?.peekContent() }

    // The fee lookup runs on Dispatchers.Default, which the main-dispatcher rule does not replace.
    private fun <T : Any> awaitValue(read: () -> T?): T {
        val deadline = System.currentTimeMillis() + AWAIT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            read()?.let { return it }
            Thread.sleep(10)
        }
        return requireNotNull(read()) { "Nothing was shown within $AWAIT_MILLIS ms" }
    }

    private companion object {
        const val CHAIN_ID = "send-paused-test-chain"
        const val ASSET_ID = "native"
        const val PAUSED_TITLE = "Sending paused"
        const val PAUSED_MESSAGE = "Sending on Network is paused right now."
        const val FEE_ERROR = "Fee calculation error"
        const val RECEIVE = "Receive"
        const val AWAIT_MILLIS = 10_000L
    }
}
