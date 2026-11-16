package jp.co.soramitsu.staking.impl.domain.staking.bond

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.math.BigInteger
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class BondMoreInteractorCoverageTest {

    private val chain = StakingLaneFixtures.chain()
    private val accountId = ByteArray(32) { 4 }
    private val address = chain.addressOf(accountId)
    private val extrinsics = mockk<ExtrinsicService>()
    private val shared = mockk<StakingSharedState>()
    private val captured = slot<suspend ExtrinsicBuilder.() -> Unit>()
    private val builder = mockk<ExtrinsicBuilder>()
    private val formedOn = mutableListOf<ExtrinsicBuilder>()
    private val form: suspend ExtrinsicBuilder.() -> Unit = { formedOn += this }
    private val interactor = BondMoreInteractor(extrinsics, shared)

    init {
        coEvery { shared.chain() } returns chain
    }

    @Test
    fun `fee is estimated for the account on the selected chain`() = runTest {
        coEvery { extrinsics.estimateFee(any(), any<ByteArray>(), any(), any(), any(), capture(captured)) } returns BigInteger.TEN

        assertEquals(BigInteger.TEN, interactor.estimateFee(address, form))
        captured.captured.invoke(builder)

        assertEquals(listOf(builder), formedOn)
        coVerify { extrinsics.estimateFee(chain, match<ByteArray> { it.contentEquals(accountId) }, any(), any(), any(), any()) }
    }

    @Test
    fun `bonding more submits the given call for the account`() = runTest {
        coEvery { extrinsics.submitExtrinsic(any(), any(), any(), any(), any(), capture(captured)) } returns Result.success("0xhash")

        assertEquals("0xhash", interactor.bondMore(address, form).getOrThrow())
        captured.captured.invoke(builder)

        assertEquals(listOf(builder), formedOn)
        coVerify { extrinsics.submitExtrinsic(chain, match { it.contentEquals(accountId) }, any(), any(), any(), any()) }
    }

    @Test
    fun `a failed bond submission is returned unchanged`() = runTest {
        val failure = IllegalStateException("node down")
        coEvery { extrinsics.submitExtrinsic(any(), any(), any(), any(), any(), any()) } returns Result.failure(failure)

        assertSame(failure, interactor.bondMore(address, form).exceptionOrNull())
    }
}
