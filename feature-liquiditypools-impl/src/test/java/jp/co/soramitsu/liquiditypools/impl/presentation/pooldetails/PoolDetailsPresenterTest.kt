package jp.co.soramitsu.liquiditypools.impl.presentation.pooldetails

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.formatPercent
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.liquiditypools.data.PoolDataDto
import jp.co.soramitsu.liquiditypools.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.liquiditypools.domain.model.BasicPoolData
import jp.co.soramitsu.liquiditypools.domain.model.CommonPoolData
import jp.co.soramitsu.liquiditypools.domain.model.UserPoolData
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.VAL_ID
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.XOR_ID
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.token
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.valToken
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.xor
import jp.co.soramitsu.liquiditypools.impl.presentation.CoroutinesStore
import jp.co.soramitsu.liquiditypools.navigation.InternalPoolsRouter
import jp.co.soramitsu.liquiditypools.navigation.LiquidityPoolsNavGraphRoute
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

@OptIn(ExperimentalCoroutinesApi::class)
class PoolDetailsPresenterTest {

    private val ids = StringPair(XOR_ID, VAL_ID)
    private val routes = MutableSharedFlow<LiquidityPoolsNavGraphRoute>(replay = 1)
    private val router = mockk<InternalPoolsRouter>(relaxed = true) {
        every { createNavGraphRoutesFlow() } returns routes
    }
    private val pool = CommonPoolData(
        BasicPoolData(xor, valToken, BigDecimal("1000"), BigDecimal("2000"), BigDecimal("300"), "reserve"),
        UserPoolData(BigDecimal("10"), BigDecimal("20"), 1.0, BigDecimal("3"))
    )
    private val pools = mockk<PoolsInteractor> {
        every { poolsChainId } returns soraMainChainId
        coEvery { getPoolData(XOR_ID, VAL_ID) } returns flowOf(pool)
        coEvery { getSbApy("reserve") } returns 18.0
    }
    private val wallet = mockk<WalletInteractor> { coEvery { getToken(xor) } returns token(xor) }
    private val chain = mockk<Chain>(relaxed = true) {
        every { id } returns soraMainChainId
        every { ecosystem } returns Ecosystem.Substrate
        every { addressPrefix } returns 69
        every { externalApi } returns null
        every { assets } returns listOf(xor, valToken)
    }
    private val meta = mockk<MetaAccount>(relaxed = true) { every { substrateAccountId } returns ACCOUNT }
    private val accounts = mockk<AccountInteractor> {
        coEvery { getChain(soraMainChainId) } returns chain
        coEvery { selectedMetaAccount() } returns meta
    }

    private fun TestScope.presenter(): PoolDetailsPresenter {
        val store = mockk<CoroutinesStore> {
            every { uiScope } returns backgroundScope
            every { ioScope } returns backgroundScope
        }
        return PoolDetailsPresenter(store, router, wallet, pools, accounts)
    }

    @Test
    fun `details show the pooled amounts, their value, TVL and yield`() = runTest {
        val presenter = presenter()
        val state = presenter.createScreenStateFlow(backgroundScope)

        routes.emit(LiquidityPoolsNavGraphRoute.PoolDetailsScreen(ids))
        runCurrent()

        val value = state.value
        assertEquals(xor, value.assetBase)
        assertEquals(valToken, value.assetTarget)
        assertEquals(BigDecimal("10").formatCrypto("XOR"), value.pooledBaseAmount)
        assertEquals(BigDecimal("20").formatFiat("$"), value.pooledBaseFiat)
        assertEquals(BigDecimal("20").formatCrypto("VAL"), value.pooledTargetAmount)
        assertEquals(BigDecimal("4000").formatFiat("$"), value.tvl)
        assertEquals("${BigDecimal("18.0").formatPercent()}%", value.apy)
    }

    @Test
    fun `supply and remove open the screens for the shown pool`() = runTest {
        val presenter = presenter()
        routes.emit(LiquidityPoolsNavGraphRoute.PoolDetailsScreen(ids))
        runCurrent()

        presenter.onSupplyLiquidityClick()
        presenter.onRemoveLiquidityClick()
        presenter.onDetailItemClick(1)
        runCurrent()

        verify { router.openAddLiquidityScreen(ids) }
        verify { router.openRemoveLiquidityScreen(ids) }
        verify { router.openInfoScreen(1) }
    }

    @Test
    fun `actions before a pool is shown do nothing`() = runTest {
        val presenter = presenter()

        presenter.onSupplyLiquidityClick()
        presenter.onRemoveLiquidityClick()
        runCurrent()

        verify(exactly = 0) { router.openAddLiquidityScreen(any()) }
        verify(exactly = 0) { router.openRemoveLiquidityScreen(any()) }
    }

    @Test
    fun `requested details resolve chain assets and the account position`() = runTest {
        val dto = PoolDataDto(XOR_ID, VAL_ID, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, "reserve")
        coEvery { pools.getUserPoolData(soraMainChainId, ADDRESS, XOR_ID, any()) } answers {
            if (arg<ByteArray>(3).contentEquals(VAL_ID.fromHex())) dto else null
        }
        val presenter = presenter()

        val details = presenter.requestPoolDetails(StringPair("xor", "val"))

        assertEquals(xor, details?.assetBase)
        assertEquals(valToken, details?.assetTarget)
        assertNull(details?.tvl)
        val missing = runCatching { presenter.requestPoolDetails(StringPair("xor", "dai")) }.exceptionOrNull()
        assertEquals("No currency for Asset null", missing?.message)
    }

    private companion object {
        val ACCOUNT = ByteArray(32) { 7 }
        val ADDRESS = ACCOUNT.toAddress(69.toShort())
    }
}
