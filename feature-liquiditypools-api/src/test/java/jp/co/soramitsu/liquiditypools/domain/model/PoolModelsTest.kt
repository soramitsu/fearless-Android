package jp.co.soramitsu.liquiditypools.domain.model

import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.liquiditypools.data.LiquidityData
import jp.co.soramitsu.liquiditypools.data.PoolDataDto
import jp.co.soramitsu.liquiditypools.domain.DemeterMutationAction
import jp.co.soramitsu.liquiditypools.domain.LiquidityMutationAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

class PoolModelsTest {

    private val xor = asset("xor", "XOR", "0x0200000000000000000000000000000000000000000000000000000000000000")
    private val valToken = asset("val", "VAL", "0x0200040000000000000000000000000000000000000000000000000000000000")
    private val pool = BasicPoolData(xor, valToken, BigDecimal("150"), BigDecimal("300"), BigDecimal("10"), "reserve")

    @Test
    fun `TVL counts both sides at the base token price`() {
        assertEquals(0, BigDecimal("600").compareTo(pool.getTvl(BigDecimal("2"))))
        assertNull(pool.getTvl(null))
    }

    @Test
    fun `search matches either token by symbol or currency id, ignoring case`() {
        assertTrue(pool.isFilterMatch("va"))
        assertTrue(pool.isFilterMatch("XoR"))
        assertTrue(pool.isFilterMatch("0x020004"))
        assertTrue(pool.isFilterMatch(""))
        assertFalse(pool.isFilterMatch("pswap"))
        assertFalse(pool.copy(targetToken = null, baseToken = asset("x", "DAI", null)).isFilterMatch("xor"))
        assertTrue(pool.copy(targetToken = null).isFilterMatch("0x0200000"))
    }

    @Test
    fun `pool data carries the position next to the pool`() {
        val user = UserPoolData(BigDecimal.ONE, BigDecimal.TEN, 2.5, BigDecimal("0.3"))
        val common = CommonPoolData(pool, user)

        assertEquals(CommonUserPoolData(pool, user), CommonUserPoolData(common.basic, common.user!!))
        assertEquals(2.5, common.copy(user = user.copy(poolShare = 2.5)).user!!.poolShare, 0.0)
        assertNull(CommonPoolData(pool, null).user)
    }

    @Test
    fun `network and liquidity values default to empty`() {
        val dto = PoolDataDto("0x02", "0x03", BigInteger.ONE, BigInteger.TWO, BigInteger.TEN, BigInteger.ZERO, "reserve")
        val liquidity = LiquidityData()

        assertEquals("reserve", dto.copy(poolProvidersBalance = BigInteger.ONE).reservesAccount)
        assertEquals(BigDecimal.ZERO, liquidity.firstReserves)
        assertEquals(BigDecimal.ZERO, liquidity.secondPooled)
        assertNull(liquidity.sbApy)
        assertEquals(1.5, liquidity.copy(sbApy = 1.5).sbApy!!, 0.0)
        assertEquals(listOf("Add", "Remove"), LiquidityMutationAction.entries.map { it.name })
        assertEquals(listOf("Deposit", "Withdraw", "Claim"), DemeterMutationAction.entries.map { it.name })
    }

    private fun asset(id: String, symbol: String, currencyId: String?) = Asset(
        id = id, name = symbol, symbol = symbol, iconUrl = "", chainId = "sora", chainName = "SORA", chainIcon = null,
        isTestNet = false, priceId = null, precision = 18, staking = Asset.StakingType.UNSUPPORTED, purchaseProviders = null,
        supportStakingPool = false, isUtility = false, type = null, currencyId = currencyId, existentialDeposit = null,
        color = null, isNative = null
    )
}
