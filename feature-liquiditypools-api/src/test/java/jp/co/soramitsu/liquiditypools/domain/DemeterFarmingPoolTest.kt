package jp.co.soramitsu.liquiditypools.domain

import jp.co.soramitsu.wallet.impl.domain.model.Asset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.math.BigDecimal
import jp.co.soramitsu.core.models.Asset as CoreAsset

class DemeterFarmingPoolTest {

    private val base = walletAsset("xor", "XOR")
    private val pool = walletAsset("val", "VAL")
    private val reward = walletAsset("pswap", "PSWAP")

    @Test
    fun `a farmed position keeps its tokens, yield and amounts`() {
        val position = DemeterFarmingPool(base, pool, reward, apr = 12.5, amount = BigDecimal.TEN, amountReward = BigDecimal.ONE)

        assertEquals(
            listOf("XOR", "VAL", "PSWAP"),
            listOf(position.tokenBase, position.tokenTarget, position.tokenReward).map {
            it.token.configuration.symbol
        }
        )
        assertEquals(12.5, position.apr, 0.0)
        assertEquals(BigDecimal.TEN to BigDecimal.ONE, position.amount to position.amountReward)
        assertEquals(position, position.copy())
        assertNotEquals(position, position.copy(amountReward = BigDecimal.ZERO))
    }

    @Test
    fun `an available farm keeps its value locked and deposit fee`() {
        val farm = DemeterFarmingBasicPool(base, pool, reward, apr = 40.0, tvl = BigDecimal("120"), fee = 0.4)

        assertEquals(BigDecimal("120"), farm.tvl)
        assertEquals(0.4, farm.fee, 0.0)
        assertEquals(40.0, farm.apr, 0.0)
        assertEquals("PSWAP", farm.tokenReward.token.configuration.symbol)
        assertEquals(farm.copy(apr = 1.0).hashCode(), farm.copy(apr = 1.0).hashCode())
        assertEquals(pool, farm.tokenTarget)
        assertEquals(base, farm.tokenBase)
    }

    private fun walletAsset(id: String, symbol: String) = Asset.createEmpty(
        chainAsset = CoreAsset(
            id = id, name = symbol, symbol = symbol, iconUrl = "", chainId = "sora", chainName = "SORA", chainIcon = null,
            isTestNet = false, priceId = null, precision = 18, staking = CoreAsset.StakingType.UNSUPPORTED,
            purchaseProviders = null, supportStakingPool = false, isUtility = false, type = null, currencyId = "0x$id",
            existentialDeposit = null, color = null, isNative = null
        ),
        metaId = 1,
        accountId = ByteArray(32),
        minSupportedVersion = null
    )
}
