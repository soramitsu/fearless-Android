package jp.co.soramitsu.liquiditypools.impl.presentation.allpools

import androidx.compose.ui.unit.dp
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.common.compose.theme.greenText
import jp.co.soramitsu.common.compose.theme.white50
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.liquiditypools.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.liquiditypools.domain.model.BasicPoolData
import jp.co.soramitsu.liquiditypools.domain.model.CommonPoolData
import jp.co.soramitsu.liquiditypools.domain.model.UserPoolData
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.coreAsset
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.token
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.xor
import jp.co.soramitsu.liquiditypools.impl.presentation.CoroutinesStore
import jp.co.soramitsu.liquiditypools.navigation.InternalPoolsRouter
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class AllPoolsPresenterTest {

    private val router = mockk<InternalPoolsRouter>(relaxed = true)
    private val chain = mockk<Chain>(relaxed = true) {
        every { id } returns soraMainChainId
        every { ecosystem } returns Ecosystem.Substrate
        every { addressPrefix } returns 69
        every { externalApi } returns null
    }
    private val meta = mockk<MetaAccount>(relaxed = true) { every { substrateAccountId } returns ACCOUNT }
    private val accounts = mockk<AccountInteractor> { coEvery { selectedMetaAccount() } returns meta }
    private val chains = mockk<ChainsRepository> { coEvery { getChain(soraMainChainId) } returns chain }
    private val wallet = mockk<WalletInteractor> { coEvery { getToken(any()) } answers { token(firstArg()) } }
    private val cache = MutableSharedFlow<List<CommonPoolData>>(replay = 1)
    private val pools = mockk<PoolsInteractor>(relaxed = true) {
        every { poolsChainId } returns soraMainChainId
        every { subscribePoolsCacheOfAccount(ADDRESS) } returns cache
        coEvery { getSbApy(any()) } answers { if (firstArg<String>() == "r-none") null else 12.5 }
    }

    private fun TestScope.presenter(): Pair<AllPoolsPresenter, StateFlow<AllPoolsState>> {
        val store = mockk<CoroutinesStore> { every { uiScope } returns backgroundScope }
        val presenter = AllPoolsPresenter(store, router, wallet, chains, pools, accounts)
        return presenter to presenter.createScreenStateFlow(backgroundScope)
    }

    @Test
    fun `account positions come first by pooled value and other pools by liquidity`() = runTest {
        val (presenter, state) = presenter()
        presenter.onWindowHeightChange(800.dp)
        presenter.onHeaderHeightChange(40.dp)

        cache.emit(listOf(pool("aaa", 100, null), pool("bbb", 500, null), pool("val", 50, "10"), pool("pswap", 50, "30")))
        runCurrent()

        val value = state.value
        assertFalse(value.isLoading)
        assertEquals(listOf("XOR-PSWAP", "XOR-VAL"), value.userPools.map { it.text1 })
        assertEquals(listOf("XOR-BBB", "XOR-AAA"), value.allPools.map { it.text1 })
        assertEquals(greenText, value.userPools.first().text2Color)
        assertEquals(white50, value.allPools.first().text2Color)
        assertEquals(
            "${BigDecimal("30").formatCrypto("XOR")} - ${BigDecimal("60").formatCrypto("PSWAP")}",
            value.userPools.first().text2
        )
        val apy = "%s%%".format(BigDecimal("12.5").formatCrypto())
        assertEquals(apy, (value.userPools.first().apy as LoadingState.Loaded).data)
        assertEquals("", (value.allPools.single { it.text1 == "XOR-AAA" }.apy as LoadingState.Loaded).data)
        assertFalse(value.hasExtraUserPools || value.hasExtraAllPools)
        coVerify { pools.syncPools() }
    }

    @Test
    fun `a short window shows part of each group and offers more`() = runTest {
        val (presenter, state) = presenter()
        presenter.onWindowHeightChange(400.dp)
        presenter.onHeaderHeightChange(40.dp)

        cache.emit((1..4).map { pool("u$it", 10, "$it") } + (1..6).map { pool("p$it", it * 10, null) })
        runCurrent()

        // (400 - 40 - 32 - 56) dp leaves room for four 56 dp rows: two of each group.
        assertEquals(2, state.value.userPools.size)
        assertEquals(2, state.value.allPools.size)
        assertTrue(state.value.hasExtraUserPools)
        assertTrue(state.value.hasExtraAllPools)
    }

    @Test
    fun `pool callbacks open details, lists and refresh the cache`() = runTest {
        val (presenter, _) = presenter()
        runCurrent()

        presenter.onPoolClicked(StringPair("a", "b"))
        presenter.onMoreClick(true)
        presenter.onRefresh()
        runCurrent()

        verify { router.openDetailsPoolScreen(StringPair("a", "b")) }
        verify { router.openPoolListScreen(true) }
        coVerify(exactly = 2) { pools.syncPools() }
    }

    @Test
    fun `without a SORA address the pools stay loading`() = runTest {
        every { meta.substrateAccountId } returns null
        val (_, state) = presenter()

        runCurrent()

        assertTrue(state.value.isLoading)
        verify(exactly = 0) { pools.subscribePoolsCacheOfAccount(any()) }
    }

    private fun pool(
        symbol: String,
        reserves: Int,
        basePooled: String?
    ): CommonPoolData {
        val target = coreAsset(symbol, symbol.uppercase(), "0x$symbol")
        return CommonPoolData(
            basic = BasicPoolData(
                xor, target, BigDecimal(reserves), BigDecimal(reserves), BigDecimal.TEN,
                if (symbol == "aaa") "r-none" else "r-$symbol"
            ),
            user = basePooled?.let { UserPoolData(BigDecimal(it), BigDecimal(it) * BigDecimal(2), 1.0, BigDecimal.ONE) }
        )
    }

    private companion object {
        val ACCOUNT = ByteArray(32) { 7 }
        val ADDRESS = ACCOUNT.toAddress(69.toShort())
    }
}
