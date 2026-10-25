package jp.co.soramitsu.wallet.impl.presentation.balance.detail.claimreward

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import java.math.BigInteger
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.base.models.ErrorDialogState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.CallMovedToAssetHubException
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * The claim screen loads its fee and locked amount eagerly in viewModelScope. Before this fix a failed lookup, for
 * example the CallMovedToAssetHubException that fearless-utils throws for Vesting.vest on Polkadot and Kusama, was
 * uncaught there and crashed the app.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClaimRewardsViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val router = mockk<WalletRouter>(relaxed = true)
    private val chain = mockk<Chain>(relaxed = true).also { every { it.assets } returns emptyList() }
    // Lookups wait until the test observes the dialogs, so every dialog the screen shows is counted.
    private val observing = CompletableDeferred<Unit>()
    private var fee: (call: Int) -> BigInteger = { BigInteger.ONE }
    private var lockedAmount: () -> BigInteger = { BigInteger.TEN }
    private var feeCalls = 0
    private val interactor = mockk<WalletInteractor>(relaxed = true).also { interactor ->
        coEvery { interactor.getChain(CHAIN_ID) } returns chain
        every { interactor.assetFlow(any(), any()) } returns emptyFlow()
        coEvery { interactor.estimateClaimRewardsFee(CHAIN_ID) } coAnswers {
            observing.await()
            fee(++feeCalls)
        }
        coEvery { interactor.getVestingLockedAmount(CHAIN_ID) } coAnswers {
            observing.await()
            lockedAmount()
        }
    }
    private val resourceManager = mockk<ResourceManager>(relaxed = true).also { resources ->
        every { resources.getString(any()) } answers { "text-${firstArg<Int>()}" }
    }

    private val uncaught = mutableListOf<Throwable>()
    private val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
    private val viewModelStore = ViewModelStore()

    init {
        // viewModelScope reports uncaught failures here; on a device the same failure kills the app.
        Thread.setDefaultUncaughtExceptionHandler { _, error -> uncaught += error }
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        Thread.setDefaultUncaughtExceptionHandler(previousHandler)
    }

    @Test
    fun `vesting moved to Asset Hub explains why and closes instead of crashing`() {
        fee = { throw CallMovedToAssetHubException("Vesting", "vest") }

        val dialog = viewModel().singleDialog()

        assertEquals(emptyList<Throwable>(), uncaught)
        assertEquals(text(R.string.relay_moved_to_asset_hub_title), dialog.title)
        assertEquals(text(R.string.relay_moved_to_asset_hub_message), dialog.message)
        assertEquals(text(R.string.common_close), dialog.positiveButtonText)
        assertNull(dialog.negativeButtonText)

        dialog.positiveClick()
        verify { router.back() }
    }

    @Test
    fun `a failed fee lookup offers retry and loads again`() {
        fee = { call -> if (call == 1) throw IOException("timeout") else BigInteger.ONE }

        val viewModel = viewModel()
        val dialog = viewModel.singleDialog()

        assertEquals(emptyList<Throwable>(), uncaught)
        assertEquals(text(R.string.choose_amount_network_error), dialog.title)
        assertEquals(text(R.string.choose_amount_error_fee), dialog.message)
        assertEquals(text(R.string.common_retry), dialog.positiveButtonText)

        dialog.positiveClick()
        coVerify(exactly = 2) { interactor.estimateClaimRewardsFee(CHAIN_ID) }
        coVerify(exactly = 2) { interactor.getVestingLockedAmount(CHAIN_ID) }
        assertEquals(1, viewModel.dialogs.size)

        dialog.negativeClick()
        verify { router.back() }
    }

    @Test
    fun `fee and locked amount failing together show one dialog`() {
        fee = { throw IOException("offline") }
        lockedAmount = { throw IOException("offline") }

        val dialog = viewModel().singleDialog()

        assertEquals(emptyList<Throwable>(), uncaught)
        assertEquals(text(R.string.common_retry), dialog.positiveButtonText)
    }

    @Test
    fun `unknown current balance cannot invoke claim even through direct callback`() {
        val config = mockk<jp.co.soramitsu.core.models.Asset>(relaxed = true) {
            every { id } returns "native"
            every { chainId } returns CHAIN_ID
            every { isUtility } returns true
            every { symbol } returns "DOT"
            every { precision } returns 10
            every { color } returns null
        }
        every { chain.assets } returns listOf(config)
        val unknown = Asset.createEmpty(config, 1, ByteArray(32), minSupportedVersion = null)
        every { interactor.assetFlow(CHAIN_ID, "native") } returns flowOf(unknown)
        val vm = viewModel()
        vm.onNextClick()
        coVerify(exactly = 0) { interactor.claimRewards(any()) }
        assertEquals(false, vm.state.value.buttonState.enabled)
        assertEquals(text(R.string.portfolio_balance_unavailable), vm.state.value.transferableInfoItem?.value)
        assertEquals(emptyList<Throwable>(), uncaught)
    }

    private val ClaimRewardsViewModel.dialogs: List<ErrorDialogState>
        get() = dialogsByViewModel.getValue(this)

    private val dialogsByViewModel = mutableMapOf<ClaimRewardsViewModel, MutableList<ErrorDialogState>>()

    private fun ClaimRewardsViewModel.singleDialog(): ErrorDialogState = dialogs.single()

    private fun text(id: Int) = "text-$id"

    private fun viewModel(): ClaimRewardsViewModel {
        val dialogs = mutableListOf<ErrorDialogState>()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ClaimRewardsViewModel(
                interactor = interactor,
                router = router,
                savedStateHandle = SavedStateHandle(mapOf(ClaimRewardsFragment.KEY_CHAIN_ID to CHAIN_ID)),
                resourceManager = resourceManager
            ).also { viewModel ->
                viewModel.errorDialogStateLiveData.observeForever { dialogs += it.peekContent() }
            } as T
        }

        return ViewModelProvider(viewModelStore, factory)[ClaimRewardsViewModel::class.java].also {
            dialogsByViewModel[it] = dialogs
            observing.complete(Unit)
        }
    }

    private companion object {
        const val CHAIN_ID = "91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3"
    }
}
