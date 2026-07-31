package jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios

import java.io.File
import java.math.BigDecimal
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import jp.co.soramitsu.staking.impl.domain.rewards.PeriodReturns
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PoolRewardCalculationTest {

    @Test
    fun `pool returns preserve every selected chain id and compound mode`() = runTest {
        val calculator = RecordingRewardCalculator()
        val chainIds = listOf(
            polkadotChainId,
            FORMER_KUSAMA_DEMO_CHAIN_ID,
            FORMER_POLKADOT_DEMO_CHAIN_ID,
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        )

        chainIds.forEachIndexed { index, chainId ->
            val amount = BigDecimal(index + 1)
            val result = calculator.calculatePoolStakingReturns(amount, PERIOD_MONTH + index, chainId)

            assertSame(calculator.result, result)
        }

        assertEquals(chainIds, calculator.requests.map { it.chainId })
        assertTrue(calculator.requests.all { it.isCompound })
        assertEquals(
            chainIds.indices.map { BigDecimal(it + 1) },
            calculator.requests.map { it.amount }
        )
        assertEquals(chainIds.indices.map { PERIOD_MONTH + it }, calculator.requests.map { it.days })
    }

    @Test
    fun `former demo ids are not aliased to Polkadot`() = runTest {
        val calculator = RecordingRewardCalculator()

        calculator.calculatePoolStakingReturns(BigDecimal.ONE, PERIOD_YEAR, FORMER_KUSAMA_DEMO_CHAIN_ID)
        calculator.calculatePoolStakingReturns(BigDecimal.TEN, PERIOD_YEAR, FORMER_POLKADOT_DEMO_CHAIN_ID)

        assertEquals(
            listOf(FORMER_KUSAMA_DEMO_CHAIN_ID, FORMER_POLKADOT_DEMO_CHAIN_ID),
            calculator.requests.map { it.chainId }
        )
        assertFalse(calculator.requests.any { it.chainId == polkadotChainId })
    }

    @Test
    fun `calculator failures propagate without a fallback chain retry`() = runTest {
        val failure = IllegalStateException("reward backend unavailable")
        val calculator = RecordingRewardCalculator(failure)
        var thrown: Throwable? = null

        try {
            calculator.calculatePoolStakingReturns(BigDecimal.ONE, PERIOD_YEAR, FORMER_KUSAMA_DEMO_CHAIN_ID)
        } catch (error: Throwable) {
            thrown = error
        }

        assertSame(failure, thrown)
        assertEquals(listOf(FORMER_KUSAMA_DEMO_CHAIN_ID), calculator.requests.map { it.chainId })
    }

    @Test
    fun `cancellation propagates without a fallback chain retry`() = runTest {
        val cancellation = CancellationException("screen left")
        val calculator = RecordingRewardCalculator(cancellation)
        var thrown: Throwable? = null

        try {
            calculator.calculatePoolStakingReturns(BigDecimal.ONE, PERIOD_YEAR, FORMER_POLKADOT_DEMO_CHAIN_ID)
        } catch (error: Throwable) {
            thrown = error
        }

        assertSame(cancellation, thrown)
        assertEquals(listOf(FORMER_POLKADOT_DEMO_CHAIN_ID), calculator.requests.map { it.chainId })
    }

    @Test
    fun `pathological inputs are forwarded without trimming clamping or substitution`() = runTest {
        val calculator = RecordingRewardCalculator()
        val requests = listOf(
            Triple("", BigDecimal("-0.000000000000000001"), -1),
            Triple(" polkadot ", BigDecimal.ZERO, 0),
            Triple("unicode-💥-\\u0000-chain", BigDecimal("999999999999999999999999.999999999999"), Int.MAX_VALUE)
        )

        requests.forEach { (chainId, amount, days) ->
            calculator.calculatePoolStakingReturns(amount, days, chainId)
        }

        assertEquals(requests.map { it.first }, calculator.requests.map { it.chainId })
        assertEquals(requests.map { it.second }, calculator.requests.map { it.amount })
        assertEquals(requests.map { it.third }, calculator.requests.map { it.days })
        assertTrue(calculator.requests.all { it.isCompound })
    }

    @Test
    fun `production pool screens contain no demo chain remapping`() {
        val sourceRoot = File("src/main/java")
        val sources = listOf(
            sourceRoot.resolve("jp/co/soramitsu/staking/impl/presentation/setup/pool/StartStakingPoolViewModel.kt"),
            sourceRoot.resolve(
                "jp/co/soramitsu/staking/impl/presentation/staking/main/scenarios/StakingPoolViewModel.kt"
            )
        )

        sources.forEach { source ->
            assertTrue("Missing production source ${source.path}", source.isFile)
            val text = source.readText()

            assertFalse("${source.name} contains the retired Kusama demo alias", text.contains(FORMER_KUSAMA_DEMO_CHAIN_ID))
            assertFalse(
                "${source.name} contains the retired Polkadot demo alias",
                text.contains(FORMER_POLKADOT_DEMO_CHAIN_ID)
            )
            assertFalse("${source.name} must not force pool rewards onto Polkadot", text.contains("polkadotChainId"))
        }
    }

    private data class Request(
        val amount: BigDecimal,
        val days: Int,
        val isCompound: Boolean,
        val chainId: ChainId
    )

    private class RecordingRewardCalculator(
        private val failure: Throwable? = null
    ) : RewardCalculator {
        val requests = mutableListOf<Request>()
        val result = PeriodReturns(BigDecimal.ONE, BigDecimal.TEN)

        override suspend fun calculateReturns(
            amount: BigDecimal,
            days: Int,
            isCompound: Boolean,
            chainId: ChainId
        ): PeriodReturns {
            requests += Request(amount, days, isCompound, chainId)
            failure?.let { throw it }

            return result
        }

        override suspend fun calculateMaxAPY(chainId: ChainId): BigDecimal = error("unused")

        override suspend fun calculateAvgAPY(): BigDecimal = error("unused")

        override suspend fun getApyFor(targetId: ByteArray): BigDecimal = error("unused")

        override suspend fun calculateReturns(
            amount: Double,
            days: Int,
            isCompound: Boolean,
            targetIdHex: String
        ): PeriodReturns = error("unused")
    }

    private companion object {
        const val FORMER_KUSAMA_DEMO_CHAIN_ID =
            "51cdb4b3101904a9d234d126656d33cd17518249819b510a03d6c90d0a019611"
        const val FORMER_POLKADOT_DEMO_CHAIN_ID =
            "4f77f65b21b1f396c1555850be6f21e2b1f36c26b94dbcbfec976901c9f08bf3"
    }
}
