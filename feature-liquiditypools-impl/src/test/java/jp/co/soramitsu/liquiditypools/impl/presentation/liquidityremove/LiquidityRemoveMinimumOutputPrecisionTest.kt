package jp.co.soramitsu.liquiditypools.impl.presentation.liquidityremove

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.liquiditypools.domain.LiquidityMutationAction
import jp.co.soramitsu.liquiditypools.domain.interfaces.DemeterFarmingInteractor
import jp.co.soramitsu.liquiditypools.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.liquiditypools.domain.model.BasicPoolData
import jp.co.soramitsu.liquiditypools.domain.model.CommonPoolData
import jp.co.soramitsu.liquiditypools.domain.model.UserPoolData
import jp.co.soramitsu.liquiditypools.impl.PoolsPrecisionFixtures.USDX_ID
import jp.co.soramitsu.liquiditypools.impl.PoolsPrecisionFixtures.XOR_ID
import jp.co.soramitsu.liquiditypools.impl.PoolsPrecisionFixtures.token
import jp.co.soramitsu.liquiditypools.impl.PoolsPrecisionFixtures.usdx
import jp.co.soramitsu.liquiditypools.impl.PoolsPrecisionFixtures.walletAsset
import jp.co.soramitsu.liquiditypools.impl.PoolsPrecisionFixtures.xor
import jp.co.soramitsu.liquiditypools.impl.data.exactPositivePlanks
import jp.co.soramitsu.liquiditypools.impl.presentation.CoroutinesStore
import jp.co.soramitsu.liquiditypools.impl.usecase.ValidateRemoveLiquidityUseCase
import jp.co.soramitsu.liquiditypools.navigation.InternalPoolsRouter
import jp.co.soramitsu.liquiditypools.navigation.LiquidityPoolsNavGraphRoute
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * Bug row 54: the remove-liquidity minimum outputs (amount × 0.995) carried more decimals than their assets, and the
 * repository converts each minimum to planks with `exactPositivePlanks`, which refuses a value finer than the asset's
 * precision, so most withdrawals failed before signing. Each minimum is rounded down to its asset's decimals: a lower
 * minimum is the safe side of a slippage bound, by less than one plank.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiquidityRemoveMinimumOutputPrecisionTest {

    private val ids = StringPair(XOR_ID, USDX_ID)
    private val routes = MutableSharedFlow<LiquidityPoolsNavGraphRoute>(replay = 1)
    private val router = mockk<InternalPoolsRouter>(relaxed = true) {
        every { createNavGraphRoutesFlow() } returns routes
    }
    private val chain = mockk<Chain>(relaxed = true) {
        every { id } returns soraMainChainId
        every { assets } returns listOf(xor, usdx)
    }
    private val chains = mockk<ChainsRepository> { coEvery { getChain(soraMainChainId) } returns chain }

    // 10 XOR and 20.000001 USDX (6 decimals) pooled for 4 LP tokens.
    private val pool = CommonPoolData(
        basic = BasicPoolData(
            baseToken = xor,
            targetToken = usdx,
            baseReserves = BigDecimal("1000"),
            targetReserves = BigDecimal("2000.0001"),
            totalIssuance = BigDecimal("400"),
            reserveAccount = "cnReserveAccount"
        ),
        user = UserPoolData(
            basePooled = BigDecimal("10"),
            targetPooled = BigDecimal("20.000001"),
            poolShare = 1.0,
            poolProvidersBalance = BigDecimal("4")
        )
    )
    private val pools = mockk<PoolsInteractor> {
        every { poolsChainId } returns soraMainChainId
        coEvery { getPoolData(XOR_ID, USDX_ID) } returns flowOf(pool)
        coEvery { calcRemoveLiquidityNetworkFee(any(), any()) } returns BigDecimal("0.007")
        coEvery { mutationCapabilityReason(LiquidityMutationAction.Remove) } returns null
    }
    private val wallet = mockk<WalletInteractor> {
        coEvery { getToken(xor) } returns token(xor, BigDecimal("2"))
        coEvery { getToken(usdx) } returns token(usdx, BigDecimal.ONE)
        every { assetFlow(soraMainChainId, "xor") } returns flowOf(walletAsset(xor, BigDecimal("2")))
    }
    private val demeter = mockk<DemeterFarmingInteractor> { coEvery { getFarmedPools(soraMainChainId) } returns null }

    @Test
    fun shouldRoundTheTargetMinimumDownToSixDecimals_whenTheWholePositionIsWithdrawn() = runTest {
        val review = review { onRemoveTargetAmountChange(BigDecimal("999")) }

        // The repository converts each minimum with exactPositivePlanks before signing; it refused 19.900000995 USDX.
        assertEquals(BigInteger.valueOf(19_900_000), usdx.exactPositivePlanks(review.secondAmountMin, "Minimum target output"))
        assertEquals(BigInteger("9950000000000000000"), xor.exactPositivePlanks(review.firstAmountMin, "Minimum base output"))
        // 20.000001 × 0.995 = 19.900000995 USDX; USDX has 6 decimals.
        assertAmount("20.000001", review.amountTarget)
        assertAmount("19.9", review.secondAmountMin)
        assertAmount("9.95", review.firstAmountMin)
    }

    @Test
    fun shouldRoundBothMinimumsDown_whenTheBaseAmountIsDerivedFromTheTarget() = runTest {
        val review = review { onRemoveTargetAmountChange(BigDecimal("15.000001")) }

        // The base amount follows from the target by ratio, with up to all 18 of XOR's decimals:
        // 7.50000012499999375 × 0.995 = 7.46250012437499378125 XOR, and 15.000001 × 0.995 = 14.925000995 USDX.
        assertAmount("7.50000012499999375", review.amountBase)
        xor.exactPositivePlanks(review.firstAmountMin, "Minimum base output")
        usdx.exactPositivePlanks(review.secondAmountMin, "Minimum target output")
        assertMinimumIsRoundedDown(review.amountBase, xor, review.firstAmountMin)
        assertMinimumIsRoundedDown(review.amountTarget, usdx, review.secondAmountMin)
        assertAmount("7.462500124374993781", review.firstAmountMin)
        assertAmount("14.925", review.secondAmountMin)
    }

    private data class Review(
        val amountBase: BigDecimal,
        val amountTarget: BigDecimal,
        val firstAmountMin: BigDecimal,
        val secondAmountMin: BigDecimal
    )

    private fun TestScope.review(input: LiquidityRemovePresenter.() -> Unit): Review {
        val store = mockk<CoroutinesStore> {
            every { uiScope } returns backgroundScope
            every { ioScope } returns backgroundScope
        }
        val presenter = LiquidityRemovePresenter(
            store, router, wallet, chains, pools, demeter, mockk<ResourceManager>(relaxed = true),
            ValidateRemoveLiquidityUseCase()
        )
        presenter.createScreenStateFlow(backgroundScope)
        routes.tryEmit(LiquidityPoolsNavGraphRoute.LiquidityRemoveScreen(ids))
        settle()
        presenter.input()
        settle()
        presenter.onRemoveReviewClick()
        settle()

        val base = slot<BigDecimal>()
        val target = slot<BigDecimal>()
        val firstMin = slot<BigDecimal>()
        val secondMin = slot<BigDecimal>()
        verify {
            router.openRemoveLiquidityConfirmScreen(ids, capture(base), capture(target), capture(firstMin), capture(secondMin), any())
        }
        return Review(base.captured, target.captured, firstMin.captured, secondMin.captured)
    }

    // The minimum is amount × 0.995 rounded down to the asset's decimals, and never above that product.
    private fun assertMinimumIsRoundedDown(
        amount: BigDecimal,
        asset: Asset,
        minimum: BigDecimal
    ) {
        val exact = amount.multiply(BigDecimal("0.995"))
        assertAmount(exact.setScale(asset.precision, RoundingMode.DOWN).toPlainString(), minimum)
        assertTrue("minimum $minimum has more than ${asset.precision} decimals", minimum.stripTrailingZeros().scale() <= asset.precision)
        assertTrue(minimum <= exact)
    }

    private fun assertAmount(expected: String, actual: BigDecimal) {
        assertEquals("expected $expected but was ${actual.toPlainString()}", 0, BigDecimal(expected).compareTo(actual))
    }

    // Runs the presenter's work, debounces included; backgroundScope work does not keep advanceUntilIdle going.
    private fun TestScope.settle() {
        advanceTimeBy(SETTLE_MILLIS)
        runCurrent()
    }

    private companion object {
        const val SETTLE_MILLIS = 10_000L
    }
}
