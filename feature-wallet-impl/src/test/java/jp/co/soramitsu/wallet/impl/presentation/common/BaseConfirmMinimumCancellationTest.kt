package jp.co.soramitsu.wallet.impl.presentation.common

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.AlertViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUseCase
import jp.co.soramitsu.wallet.api.presentation.BaseConfirmViewModel
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

/** Exercises the production default validator, without overriding isValid. */
@OptIn(ExperimentalCoroutinesApi::class)
class BaseConfirmMinimumCancellationTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    @get:Rule val main = MainCoroutineRule(UnconfinedTestDispatcher())
    private val owned = mutableListOf<BaseConfirmViewModel>()
    private val alerts = mutableListOf<AlertViewState>()
    private var operations = 0

    @After fun tearDown() { owned.forEach { it.viewModelScope.cancel() } }

    @Test fun `cancelled minimum lookup cannot become an unknown-minimum alert`() {
        val vm = create(Result.failure(CancellationException("local lookup cancelled")))
        val state = vm.viewState
        vm.onConfirm()
        assertEquals(0, operations)
        assertFalse(state.value.isLoading)
        assertTrue(alerts.isEmpty())
    }

    @Test fun `ordinary unknown minimum still blocks and explains confirmation`() {
        val vm = create(Result.failure(IllegalStateException("local minimum unavailable")))
        vm.onConfirm()
        assertEquals(0, operations)
        assertEquals(1, alerts.size)
    }

    @Test fun `known minimum allows one healthy confirmation`() {
        val vm = create(Result.success(BigInteger.ZERO))
        vm.onConfirm()
        assertEquals(1, operations)
        assertTrue(alerts.isEmpty())
    }

    private fun create(minimum: Result<BigInteger>): BaseConfirmViewModel {
        val resources = mockk<ResourceManager>(relaxed = true).also { r ->
            every { r.getString(any()) } answers { "fixture-${firstArg<Int>()}" }
        }
        val asset = mockk<Asset>(relaxed = true).also { a ->
            every { a.transferable } returns BigDecimal.ONE
            every { a.transferableInPlanks } returns BigInteger.TEN.pow(10)
            every { a.token.configuration.symbol } returns "DOT"
            every { a.token.configuration.precision } returns 10
            every { a.token.fiatRate } returns null
        }
        return object : BaseConfirmViewModel(
            walletInteractor = mockk(relaxed = true) { every { assetFlow(any(), any()) } returns emptyFlow() },
            existentialDepositUseCase = object : ExistentialDepositUseCase {
                override suspend fun invoke(chainAsset: jp.co.soramitsu.core.models.Asset) = minimum
            },
            resourceManager = resources,
            asset = asset,
            chain = mockk(relaxed = true),
            address = "public-fixture-address",
            titleRes = R.string.common_confirm,
            feeEstimator = { BigInteger.ONE },
            executeOperation = { _, _ -> operations++; Result.success("0xfixture") },
            accountNameProvider = { null },
            onOperationSuccess = {},
            errorAlertPresenter = { alerts += it }
        ) {}.also(owned::add)
    }
}
