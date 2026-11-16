package jp.co.soramitsu.liquiditypools.impl.presentation.liquidityremove

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.liquiditypools.domain.DemeterFarmingPool
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

/**
 * Bug 33: when part of a position is farmed in Demeter, the withdrawable target amount was rounded with the
 * base token's precision (18) instead of the target's, so a 6-decimal target showed and carried an amount
 * with more decimals than the token has.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiquidityRemoveFarmedTargetPrecisionTest {

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

    // 10 XOR and 20.000001 USDX pooled for 4 LP tokens.
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
    private val resources = mockk<ResourceManager>(relaxed = true)

    @Test
    fun shouldRoundTheWithdrawableTargetToTheTargetPrecision_whenAQuarterOfThePositionIsFarmed() = runTest {
        // 1 of the 4 LP tokens is deposited in a Demeter farm, so 75 % of the position can be withdrawn.
        val farming = farmedPositions(BigDecimal.ONE)
        val (presenter, state) = openRemoveLiquidity(farming)

        presenter.onRemoveTargetAmountChange(BigDecimal("999"))
        settle()

        // 75 % of 20.000001 USDX is 15.00000075, which USDX (6 decimals) rounds to 15.000001.
        assertAmount("15.000001", state.value.targetAmountInputViewState.tokenAmount)
        assertAmount("7.5", state.value.baseAmountInputViewState.tokenAmount)
    }

    @Test
    fun shouldOfferTheWholeTarget_whenNothingIsFarmed() = runTest {
        val (presenter, state) = openRemoveLiquidity(farmedPositions(amount = null))

        presenter.onRemoveTargetAmountChange(BigDecimal("999"))
        settle()

        assertAmount("20.000001", state.value.targetAmountInputViewState.tokenAmount)
        assertAmount("10", state.value.baseAmountInputViewState.tokenAmount)
    }

    private fun farmedPositions(amount: BigDecimal?) = mockk<DemeterFarmingInteractor> {
        coEvery { getFarmedPools(soraMainChainId) } returns listOfNotNull(
            amount?.let {
                DemeterFarmingPool(
                    tokenBase = walletAsset(xor, BigDecimal("2")),
                    tokenTarget = walletAsset(usdx, BigDecimal.ONE),
                    tokenReward = walletAsset(xor, BigDecimal("2")),
                    apr = 10.0,
                    amount = it,
                    amountReward = BigDecimal.ZERO
                )
            }
        )
    }

    private fun TestScope.openRemoveLiquidity(
        farming: DemeterFarmingInteractor
    ): Pair<LiquidityRemovePresenter, StateFlow<LiquidityRemoveState>> {
        val store = mockk<CoroutinesStore> {
            every { uiScope } returns backgroundScope
            every { ioScope } returns backgroundScope
        }
        val presenter = LiquidityRemovePresenter(
            store,
            router,
            wallet,
            chains,
            pools,
            farming,
            resources,
            ValidateRemoveLiquidityUseCase()
        )
        val state = presenter.createScreenStateFlow(backgroundScope)
        routes.tryEmit(LiquidityPoolsNavGraphRoute.LiquidityRemoveScreen(ids))
        settle()
        return presenter to state
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
