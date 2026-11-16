package jp.co.soramitsu.staking.impl.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.math.BigInteger
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.core.rpc.FinalizedExtrinsicReader
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.fearless_utils.runtime.metadata.ExtrinsicMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.staking.impl.presentation.common.EditPoolFlowState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pool nomination, payout, bond-extra and edit transactions: the real API forms each call on a builder over a runtime
 * without Asset Hub staking, and a mocked extrinsic service runs the form and answers with a fee or a hash.
 */
class StakingPoolApiCoverageTest {

    private class Recorded(val call: String, val arguments: Map<String, Any?>)

    private val chain = StakingLaneFixtures.chain()
    private val account = ByteArray(32) { 3 }
    private val other = ByteArray(32) { 4 }
    private val root = ByteArray(32) { 5 }
    private val nominator = ByteArray(32) { 6 }
    private val address = chain.addressOf(account)

    private val recorded = mutableListOf<Recorded>()
    private val runtime = RuntimeSnapshot(
        mockk(relaxed = true),
        RuntimeMetadata(BigInteger.ONE, emptyMap(), ExtrinsicMetadata(BigInteger.valueOf(4), emptyList()))
    )
    private val builder = mockk<ExtrinsicBuilder>()
    private val service = mockk<ExtrinsicService>()
    private val sharedState = mockk<StakingSharedState>()
    private val registry = mockk<ChainRegistry>()
    private val form = slot<suspend ExtrinsicBuilder.() -> Unit>()
    private val api = StakingPoolApi(
        service,
        sharedState,
        registry,
        PoolJoinPreferencesFixture().journal(),
        mockk<FinalizedExtrinsicReader>()
    )

    init {
        every { builder.runtime } returns runtime
        every { builder.call(any<String>(), any<String>(), any()) } answers {
            recorded += Recorded(secondArg(), thirdArg())
            builder
        }
        coEvery { sharedState.chain() } returns chain
        coEvery { service.estimateFee(any(), any<ByteArray>(), any(), any(), any(), capture(form)) } coAnswers {
            form.captured.invoke(builder)
            FEE
        }
        coEvery { service.submitExtrinsic(any(), any(), any(), any(), any(), capture(form)) } coAnswers {
            form.captured.invoke(builder)
            Result.success(HASH)
        }
    }

    private fun editState(name: String?, newRoot: ByteArray?, newNominator: ByteArray?, newToggler: ByteArray?) =
        EditPoolFlowState(
            poolId = POOL,
            initialPoolName = "Old",
            depositor = account,
            initialRoot = root,
            initialNominator = nominator,
            initialStateToggler = nominator,
            newPoolName = name,
            newRoot = newRoot,
            newNominator = newNominator,
            newStateToggler = newToggler
        )

    private fun role(name: String) = recorded.last().arguments[name] as DictEnum.Entry<*>

    @Test
    fun `pool nomination names the pool and its validators`() = runTest {
        assertEquals(FEE, api.estimateNominatePoolFee(address, POOL, account, other))
        assertEquals("nominate", recorded.last().call)
        assertEquals(POOL, recorded.last().arguments["pool_id"])
        assertEquals(2, (recorded.last().arguments["validators"] as List<*>).size)

        assertEquals(HASH, api.nominatePool(POOL, address, other).getOrThrow())
        assertSame(other, (recorded.last().arguments["validators"] as List<*>).single())
        coVerify(exactly = 1) { service.submitExtrinsic(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `payout claim has no arguments`() = runTest {
        assertEquals(FEE, api.estimateClaimPayoutFee(address))
        assertEquals("claim_payout", recorded.last().call)
        assertTrue(recorded.last().arguments.isEmpty())

        assertEquals(HASH, api.claimPayout(address).getOrThrow())
        assertEquals(2, recorded.count { it.call == "claim_payout" })
    }

    @Test
    fun `bond extra spends free balance`() = runTest {
        assertEquals(FEE, api.estimateBondExtraFee(address, BigInteger.TEN))
        assertEquals("bond_extra", recorded.last().call)
        assertEquals("FreeBalance", role("extra").name)
        assertEquals(BigInteger.TEN, role("extra").value)

        assertEquals(HASH, api.bondExtra(address, BigInteger.ONE).getOrThrow())
        assertEquals(BigInteger.ONE, role("extra").value)
    }

    @Test
    fun `old runtimes rename the pool and update state toggler roles`() = runTest {
        coEvery { registry.getRemoteRuntimeVersion(chain.id) } returns 9380
        val state = editState("Renamed", other, nominator, null)

        assertEquals(FEE, api.estimateEditPool(state, address))

        assertEquals(listOf("set_metadata", "update_roles"), recorded.map { it.call })
        assertArrayEquals("Renamed".encodeToByteArray(), recorded.first().arguments["metadata"] as ByteArray)
        assertEquals("Set", role("new_root").name)
        assertSame(other, role("new_root").value)
        assertEquals("Noop", role("new_nominator").name)
        assertEquals("Noop", role("new_state_toggler").name)
        assertFalse(recorded.last().arguments.containsKey("new_bouncer"))

        recorded.clear()
        assertEquals(HASH, api.editPool(state, address).getOrThrow())
        assertEquals(listOf("set_metadata", "update_roles"), recorded.map { it.call })
        assertTrue(recorded.last().arguments.containsKey("new_state_toggler"))
    }

    @Test
    fun `current runtimes update bouncer roles`() = runTest {
        coEvery { registry.getRemoteRuntimeVersion(chain.id) } returns 9400
        val state = editState(null, root, other, other)

        assertEquals(FEE, api.estimateEditPool(state, address))

        assertEquals(listOf("update_roles"), recorded.map { it.call })
        assertEquals(POOL, recorded.last().arguments["pool_id"])
        assertEquals("Noop", role("new_root").name)
        assertEquals("Set", role("new_nominator").name)
        assertEquals("Set", role("new_bouncer").name)
        assertFalse(recorded.last().arguments.containsKey("new_state_toggler"))

        recorded.clear()
        assertEquals(HASH, api.editPool(state.copy(newPoolName = "New"), address).getOrThrow())
        assertEquals(listOf("set_metadata", "update_roles"), recorded.map { it.call })
    }

    @Test
    fun `unchanged pool edit adds no calls`() = runTest {
        val unchanged = editState(null, root, nominator, nominator)

        coEvery { registry.getRemoteRuntimeVersion(chain.id) } returns 9400
        assertEquals(FEE, api.estimateEditPool(unchanged, address))
        assertEquals(HASH, api.editPool(unchanged, address).getOrThrow())
        coEvery { registry.getRemoteRuntimeVersion(chain.id) } returns 9000
        assertEquals(FEE, api.estimateEditPool(unchanged, address))
        assertEquals(HASH, api.editPool(unchanged, address).getOrThrow())

        assertTrue(recorded.isEmpty())
    }

    private companion object {
        val POOL: BigInteger = BigInteger.valueOf(7)
        val FEE: BigInteger = BigInteger.valueOf(15_000)
        const val HASH = "0xpool"
    }
}
