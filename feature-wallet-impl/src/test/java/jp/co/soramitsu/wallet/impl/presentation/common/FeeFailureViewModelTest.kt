package jp.co.soramitsu.wallet.impl.presentation.common

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import org.junit.Assert.assertFalse
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.AlertViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.CallMovedToAssetHubException
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.api.presentation.BaseConfirmViewModel
import jp.co.soramitsu.wallet.api.presentation.BaseEnterAmountViewModel
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Pool staking screens build on these view models and estimate the fee eagerly in viewModelScope. On Polkadot and
 * Kusama fearless-utils refuses NominationPools calls (CallMovedToAssetHubException); before this fix that failure,
 * like any failed fee query, was uncaught there and crashed the app.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeeFailureViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val resourceManager = mockk<ResourceManager>(relaxed = true).also { resources ->
        every { resources.getString(any()) } answers { "text-${firstArg<Int>()}" }
    }
    private val asset = mockk<Asset>(relaxed = true).also { asset ->
        every { asset.transferable } returns BigDecimal.ONE
        every { asset.transferableInPlanks } returns BigInteger.TEN.pow(10)
        every { asset.token.configuration.symbol } returns "DOT"
        every { asset.token.configuration.precision } returns 10
        every { asset.token.fiatRate } returns null
    }

    private val alerts = mutableListOf<AlertViewState>()
    private val uncaught = mutableListOf<Throwable>()
    private val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

    init {
        // viewModelScope reports uncaught failures here; on a device the same failure kills the app.
        Thread.setDefaultUncaughtExceptionHandler { _, error -> uncaught += error }
    }

    @After
    fun tearDown() {
        Thread.setDefaultUncaughtExceptionHandler(previousHandler)
    }

    @Test
    fun `enter amount explains a call that moved to Asset Hub instead of crashing`() {
        var nextSteps = 0
        var estimates = 0
        val viewModel = enterAmount(
            feeEstimator = {
                estimates++
                throw CallMovedToAssetHubException("NominationPools", "bond_extra")
            },
            onNextStep = { nextSteps++ }
        )

        viewModel.onAmountInput(BigDecimal.TEN)
        viewModel.onNextClick()

        assertEquals(emptyList<Throwable>(), uncaught)
        // Each amount is estimated again: one failure does not end the fee flow.
        assertEquals(2, estimates)
        assertEquals(0, nextSteps)
        assertEquals(movedAlertTitleAndMessage, alerts.single().let { it.title to it.message })
    }

    @Test
    fun `enter amount reports a failed fee query on next and recovers when it succeeds`() {
        var fail = true
        var nextSteps = 0
        val viewModel = enterAmount(
            feeEstimator = { if (fail) throw IOException("offline") else BigInteger.ONE },
            onNextStep = { nextSteps++ }
        )

        viewModel.onNextClick()

        assertEquals(emptyList<Throwable>(), uncaught)
        assertEquals(text(R.string.choose_amount_network_error) to text(R.string.choose_amount_error_fee), alerts.single().let { it.title to it.message })
        assertEquals(0, nextSteps)

        fail = false
        viewModel.onAmountInput(BigDecimal.ONE)
        viewModel.onNextClick()

        assertEquals(1, nextSteps)
    }

    @Test
    fun `enter amount tries a failed fee query again on next without a new amount`() {
        var fail = true
        var nextSteps = 0
        val viewModel = enterAmount(
            feeEstimator = { if (fail) throw IOException("offline") else BigInteger.ONE },
            onNextStep = { nextSteps++ }
        )

        fail = false
        // This tap still reports the failure, as the message says to try again, and starts the estimation again.
        viewModel.onNextClick()

        assertEquals(text(R.string.choose_amount_network_error), alerts.single().title)
        assertEquals(0, nextSteps)

        viewModel.onNextClick()

        assertEquals(emptyList<Throwable>(), uncaught)
        assertEquals(1, alerts.size)
        assertEquals(1, nextSteps)
    }

    @Test
    fun `enter amount does not estimate a moved call again on next`() {
        var estimates = 0
        val viewModel = enterAmount(
            feeEstimator = {
                estimates++
                throw CallMovedToAssetHubException("NominationPools", "unbond")
            },
            onNextStep = {}
        )

        viewModel.onNextClick()
        viewModel.onNextClick()

        assertEquals(1, estimates)
        assertEquals(listOf(movedAlertTitleAndMessage, movedAlertTitleAndMessage), alerts.map { it.title to it.message })
    }

    @Test
    fun `confirm explains a call that moved to Asset Hub instead of crashing or waiting for the fee`() {
        var executed = 0
        var estimates = 0
        val viewModel = confirm(
            feeEstimator = {
                estimates++
                throw CallMovedToAssetHubException("NominationPools", "join")
            },
            executeOperation = { _, _ ->
                executed++
                Result.success("0x01")
            }
        )

        viewModel.onConfirm()
        viewModel.onConfirm()

        assertEquals(emptyList<Throwable>(), uncaught)
        assertEquals(0, executed)
        // Asking again changes nothing: the call would be refused again.
        assertEquals(1, estimates)
        assertEquals(listOf(movedAlertTitleAndMessage, movedAlertTitleAndMessage), alerts.map { it.title to it.message })
    }

    @Test
    fun `confirm tries a failed fee query again and submits once the fee is known`() {
        var fail = true
        var executed = 0
        val viewModel = confirm(
            feeEstimator = { if (fail) throw IOException("offline") else BigInteger.ONE },
            executeOperation = { _, _ ->
                executed++
                Result.success("0x01")
            }
        )

        fail = false
        viewModel.onConfirm()

        assertEquals(emptyList<Throwable>(), uncaught)
        assertEquals(text(R.string.choose_amount_network_error) to text(R.string.choose_amount_error_fee), alerts.single().let { it.title to it.message })
        // Nothing is sent on a fee the user has not seen.
        assertEquals(0, executed)

        viewModel.onConfirm()

        assertEquals(1, alerts.size)
        assertEquals(1, executed)
    }

    @Test
    fun `confirm shows a refused submission in the user's language`() {
        val viewModel = confirm(
            feeEstimator = { BigInteger.ONE },
            executeOperation = { _, _ -> Result.failure(CallMovedToAssetHubException("Staking", "nominate")) }
        )

        viewModel.onConfirm()

        assertEquals(emptyList<Throwable>(), uncaught)
        assertEquals(movedAlertTitleAndMessage, alerts.single().let { it.title to it.message })
    }

    @Test
    fun `unknown balance disables amount actions and explains next`() {
        every { asset.transferable } returns null
        var nextSteps = 0
        var quickCalculations = 0
        val viewModel = BaseEnterAmountViewModel(
            balanceHintRes = R.string.common_available_format,
            asset = asset, resourceManager = resourceManager,
            feeEstimator = { BigInteger.ONE }, onNextStep = { nextSteps++ },
            quickInputsCalculator = { quickCalculations++; mapOf(1.0 to BigDecimal.TEN) },
            errorAlertPresenter = { alerts += it }
        )
        try {
            viewModel.onAmountInput(BigDecimal.TEN)
            assertFalse(viewModel.state.value.buttonState.enabled)
            viewModel.onQuickAmountInput(1.0)
            viewModel.onNextClick()
            assertEquals(0, quickCalculations)
            assertEquals(0, nextSteps)
            assertEquals(text(jp.co.soramitsu.common.R.string.portfolio_balance_unavailable), alerts.single().message)
            assertEquals(emptyList<Throwable>(), uncaught)
        } finally { viewModel.viewModelScope.cancel() }
    }

    @Test
    fun `unknown balance blocks the actual default confirmation validator`() {
        every { asset.transferableInPlanks } returns null
        var operations = 0
        val viewModel = confirm({ BigInteger.ONE }, { _, _ -> operations++; Result.success("not authorized") })
        try {
            viewModel.onConfirm()
            assertEquals(0, operations)
            assertEquals(text(jp.co.soramitsu.common.R.string.portfolio_balance_unavailable), alerts.single().message)
            assertEquals(emptyList<Throwable>(), uncaught)
        } finally { viewModel.viewModelScope.cancel() }
    }

    private val movedAlertTitleAndMessage =
        text(R.string.relay_moved_to_asset_hub_title) to text(R.string.relay_moved_to_asset_hub_message)

    private fun text(id: Int) = "text-$id"

    private fun enterAmount(feeEstimator: suspend (BigInteger) -> BigInteger, onNextStep: suspend (BigInteger) -> Unit) =
        BaseEnterAmountViewModel(
            balanceHintRes = R.string.common_available_format,
            asset = asset,
            resourceManager = resourceManager,
            feeEstimator = feeEstimator,
            onNextStep = onNextStep,
            errorAlertPresenter = { alerts += it }
        )

    private fun confirm(
        feeEstimator: suspend (BigInteger?) -> BigInteger,
        executeOperation: suspend (String, BigInteger?) -> Result<String>
    ) = object : BaseConfirmViewModel(
        walletInteractor = mockk(relaxed = true) { every { assetFlow(any(), any()) } returns emptyFlow() },
        existentialDepositUseCase = mockk { coEvery { this@mockk.invoke(any()) } returns Result.success(BigInteger.ZERO) },
        resourceManager = resourceManager,
        asset = asset,
        chain = mockk(relaxed = true),
        address = ADDRESS,
        titleRes = R.string.common_confirm,
        feeEstimator = feeEstimator,
        executeOperation = executeOperation,
        accountNameProvider = { null },
        onOperationSuccess = {},
        errorAlertPresenter = { alerts += it }
    ) {}

    private companion object {
        const val ADDRESS = "15oF4uVJwmo4TdGW7VfQxNLavjCXviqxT9S1MgbjMNHr6Sp5"
    }
}
