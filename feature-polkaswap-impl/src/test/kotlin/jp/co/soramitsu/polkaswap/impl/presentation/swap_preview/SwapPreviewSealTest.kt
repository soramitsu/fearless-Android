package jp.co.soramitsu.polkaswap.impl.presentation.swap_preview

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.feedback.TransferSeal
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.api.presentation.PolkaswapRouter
import jp.co.soramitsu.polkaswap.api.presentation.models.SwapDetailsParcelModel
import jp.co.soramitsu.polkaswap.api.presentation.models.SwapDetailsViewState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Which real event of a swap lights which stage of the seal (brand kit SPEC v4 part 3). */
@OptIn(ExperimentalCoroutinesApi::class)
class SwapPreviewSealTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    @get:Rule val main = MainCoroutineRule(UnconfinedTestDispatcher())

    private val router = mockk<PolkaswapRouter>(relaxed = true)
    private val interactor = mockk<PolkaswapInteractor>()
    private val answer = CompletableDeferred<Result<String>>()
    private lateinit var vm: SwapPreviewViewModel

    @Before fun setUp() {
        coEvery { interactor.swap(any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers { answer.await() }
        val handle = SavedStateHandle(mapOf(
            SwapPreviewFragment.KEY_SWAP_DETAILS to mockk<SwapDetailsViewState>(relaxed = true),
            SwapPreviewFragment.KEY_SWAP_DETAILS_PARCEL to mockk<SwapDetailsParcelModel>(relaxed = true)
        ))
        TransferSeal.ready(1) // a seal left by an earlier operation
        vm = SwapPreviewViewModel(router, interactor, handle)
    }

    @After fun tearDown() {
        vm.viewModelScope.cancel()
        TransferSeal.clear()
    }

    @Test fun `opening the preview clears an earlier seal`() {
        assertNull(TransferSeal.record.value.readyAtMs)
    }

    @Test fun `confirm lights ready while the swap is submitted, and acceptance lights the rest at once`() {
        vm.onConfirmClick()
        eventually { TransferSeal.record.value.stage == TransferSeal.Stage.Ready }
        assertTrue(vm.state.value.isLoading)
        answer.complete(Result.success("0xhash"))
        // The swap runs on a background dispatcher: wait for its answer to come back.
        eventually { TransferSeal.record.value.stage == TransferSeal.Stage.Accepted }
        val record = TransferSeal.record.value
        assertEquals(TransferSeal.Stage.Accepted, record.stage)
        // Signed and sent cannot be told apart from acceptance here: they are lit by the same event.
        assertEquals(record.acceptedAtMs, record.signedAtMs)
        assertEquals(record.acceptedAtMs, record.sentAtMs)
        // The result is not delayed: the success screen opens in the same step.
        eventually { runCatching { verify(exactly = 1) { router.openOperationSuccess("0xhash", any()) } }.isSuccess }
        assertNotNull(TransferSeal.consumeRecentAccepted(TransferSeal.nowMs()))
    }

    @Test fun `a refused swap breaks the seal beside the app's own error`() {
        vm.onConfirmClick()
        answer.complete(Result.failure(IllegalStateException("refused")))
        eventually { !vm.state.value.isLoading }
        assertEquals(TransferSeal.Stage.Failed, TransferSeal.record.value.stage)
        verify(exactly = 0) { router.openOperationSuccess(any(), any()) }
        coVerify(exactly = 1) { interactor.swap(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    private fun eventually(condition: () -> Boolean) {
        val until = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < until) { "condition not met in 5 s" }
            Thread.sleep(5)
        }
    }
}
