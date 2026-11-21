package jp.co.soramitsu.staking.impl.domain.staking.redeem

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.math.BigInteger
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RedeemInteractorCoverageTest {

    private val chain = StakingLaneFixtures.chain()
    private val controllerId = ByteArray(32) { 2 }
    private val stash = StakingState.Stash.None(chain, ByteArray(32) { 1 }, controllerId, ByteArray(32) { 3 })
    private val extrinsics = mockk<ExtrinsicService>()
    private val captured = slot<suspend ExtrinsicBuilder.() -> Unit>()
    private val builder = mockk<ExtrinsicBuilder>()
    private val formedOn = mutableListOf<ExtrinsicBuilder>()
    private val form: suspend ExtrinsicBuilder.() -> Unit = { formedOn += this }
    private val interactor = RedeemInteractor(extrinsics)

    @Test
    fun `fee is estimated by the controller on the stash chain with the given call`() = runTest {
        coEvery { extrinsics.estimateFee(any(), any<ByteArray>(), any(), any(), any(), capture(captured)) } returns BigInteger.TEN

        assertEquals(BigInteger.TEN, interactor.estimateFee(stash, form))
        captured.captured.invoke(builder)

        assertEquals(listOf(builder), formedOn)
        coVerify { extrinsics.estimateFee(chain, match<ByteArray> { it.contentEquals(controllerId) }, any(), any(), any(), any()) }
    }

    @Test
    fun `redeeming everything that is locked kills the stash`() = runTest {
        coEvery { extrinsics.submitExtrinsic(any(), any(), any(), any(), any(), capture(captured)) } returns Result.success("0xhash")
        val planks = AssetAmountState.Known(StakingLaneFixtures.planks(3))
        val asset = StakingLaneFixtures.asset().copy(redeemableInPlanksState = planks, miscFrozenInPlanksState = planks)

        val consequences = interactor.redeem(stash, asset, form).getOrThrow()
        captured.captured.invoke(builder)

        assertTrue(consequences.willKillStash)
        assertEquals("0xhash", consequences.hash)
        assertEquals(listOf(builder), formedOn)
    }

    @Test
    fun `redeeming part of the locked balance keeps the stash`() = runTest {
        coEvery { extrinsics.submitExtrinsic(any(), any(), any(), any(), any(), any()) } returns Result.success("0xhash")
        val asset = StakingLaneFixtures.asset().copy(miscFrozenInPlanksState = AssetAmountState.Known(StakingLaneFixtures.planks(20)))

        assertFalse(interactor.redeem(stash, asset, form).getOrThrow().willKillStash)
    }

    @Test
    fun `a failed submission is returned as a failure`() = runTest {
        val failure = IllegalStateException("node down")
        coEvery { extrinsics.submitExtrinsic(any(), any(), any(), any(), any(), any()) } returns Result.failure(failure)

        assertSame(failure, interactor.redeem(stash, StakingLaneFixtures.asset(), form).exceptionOrNull())
    }
}
