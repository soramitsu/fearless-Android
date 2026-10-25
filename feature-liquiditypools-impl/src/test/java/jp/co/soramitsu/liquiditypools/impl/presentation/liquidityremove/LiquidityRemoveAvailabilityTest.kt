package jp.co.soramitsu.liquiditypools.impl.presentation.liquidityremove

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.feature_liquiditypools_impl.R
import jp.co.soramitsu.liquiditypools.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.liquiditypools.impl.presentation.CoroutinesStore
import jp.co.soramitsu.liquiditypools.navigation.InternalPoolsRouter
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState
import jp.co.soramitsu.wallet.impl.domain.model.FiatValuationContext
import jp.co.soramitsu.wallet.impl.domain.model.Token
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset as ChainAsset

@OptIn(ExperimentalCoroutinesApi::class)
class LiquidityRemoveAvailabilityTest {
    @Test
    fun `utility amount and fiat clear on missing balance then recover known zero`() = runTest {
        val config = mockk<ChainAsset>(relaxed = true) {
            every { id } returns "xor"
            every { chainId } returns "sora"
            every { symbol } returns "XOR"
            every { precision } returns 2
            every { isUtility } returns true
            every { priceId } returns "sora"
            every { priceProvider } returns null
        }
        val known = Asset.createEmpty(config, 7, byteArrayOf(1), minSupportedVersion = null).copy(
            freeInPlanksState = AssetAmountState.Known(BigInteger.valueOf(1000)),
            token = Token(
                config,
                AssetAmountState.Known(BigDecimal("2")),
                AssetAmountState.Known(BigDecimal.ZERO),
                FiatValuationContext("usd", FiatCurrency("usd", "$", "Dollar", "")),
                "usd",
                "sora"
            )
        )
        val values = MutableStateFlow(known)
        val wallet = mockk<WalletInteractor> {
            every { assetFlow("sora", "xor") } returns values
        }
        val chain = mockk<Chain> { every { assets } returns listOf(config) }
        val chains = mockk<ChainsRepository> { coEvery { getChain("sora") } returns chain }
        val pools = mockk<PoolsInteractor> { every { poolsChainId } returns "sora" }
        val router = mockk<InternalPoolsRouter> { every { createNavGraphRoutesFlow() } returns emptyFlow() }
        val store = mockk<CoroutinesStore> {
            every { uiScope } returns backgroundScope
            every { ioScope } returns backgroundScope
        }
        val resources = mockk<ResourceManager> {
            every { getString(R.string.portfolio_balance_unavailable) } returns "Unavailable"
        }
        val presenter = LiquidityRemovePresenter(store, router, wallet, chains, pools, mockk(), resources, mockk())
        val state = presenter.createScreenStateFlow(backgroundScope)
        runCurrent()
        assertEquals(BigDecimal.TEN.formatCrypto("XOR"), state.value.transferableAmount)
        assertEquals(BigDecimal("20").formatFiat("$"), state.value.transferableFiat)
        values.value = known.copy(freeInPlanksState = AssetAmountState.Missing)
        runCurrent()
        assertEquals("Unavailable", state.value.transferableAmount)
        assertNull(state.value.transferableFiat)
        values.value = known.copy(freeInPlanksState = AssetAmountState.Known(BigInteger.ZERO))
        runCurrent()
        assertEquals(BigDecimal.ZERO.formatCrypto("XOR"), state.value.transferableAmount)
        assertEquals(BigDecimal.ZERO.formatFiat("$"), state.value.transferableFiat)
    }
}
