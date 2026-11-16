package jp.co.soramitsu.staking.impl.domain.staking.controller

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.math.BigInteger
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.core.models.MultiAddress
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.fearless_utils.runtime.metadata.ExtrinsicMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.FunctionArgument
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.MetadataFunction
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Module
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerInteractorCoverageTest {

    private class Recorded(val call: String, val arguments: Map<String, Any?>)

    private val recorded = mutableListOf<Recorded>()
    private val stashId = ByteArray(32) { 1 }
    private val controllerId = ByteArray(32) { 2 }
    private val selectedChain = StakingLaneFixtures.chain()
    private val extrinsics = mockk<ExtrinsicService>()
    private val shared = mockk<StakingSharedState>()
    private val staking = mockk<StakingInteractor>()
    private val form = slot<suspend ExtrinsicBuilder.() -> Unit>()
    private val interactor = ControllerInteractor(extrinsics, shared, staking)

    init {
        every { shared.assetWithChain } returns flowOf(SingleAssetSharedState.AssetWithChain(selectedChain, selectedChain.assets.single()))
        coEvery { extrinsics.estimateFee(any(), any<ByteArray>(), any(), any(), any(), capture(form)) } returns BigInteger.TEN
        coEvery { extrinsics.submitExtrinsic(any(), any(), any(), any(), any(), capture(form)) } returns Result.success("0xhash")
    }

    /** Metadata whose `Staking.set_controller` takes the given arguments, or has no calls at all for null. */
    private fun metadata(vararg setControllerArguments: String, withCalls: Boolean = true): RuntimeMetadata {
        val call = MetadataFunction("set_controller", setControllerArguments.map { FunctionArgument(it, null) }, emptyList(), 0 to 0)
        val calls = if (withCalls) mapOf("set_controller" to call) else null
        val module = Module("Staking", null, calls, null, emptyMap(), emptyMap(), BigInteger.ZERO)
        return RuntimeMetadata(BigInteger.ONE, mapOf("Staking" to module), ExtrinsicMetadata(BigInteger.valueOf(4), emptyList()))
    }

    private fun builder(): ExtrinsicBuilder {
        val builder = mockk<ExtrinsicBuilder>()
        every { builder.runtime } returns RuntimeSnapshot(mockk(relaxed = true), metadata())
        every { builder.call(any<String>(), any<String>(), any()) } answers {
            recorded += Recorded(secondArg(), thirdArg())
            builder
        }
        return builder
    }

    private fun chain(chainId: String): Chain {
        val chain = StakingLaneFixtures.chain(StakingLaneFixtures.chainAsset(chainId = chainId))
        coEvery { staking.getChain(chainId) } returns chain
        return chain
    }

    @Test
    fun `controller is deprecated when set_controller takes no arguments`() = runTest {
        coEvery { staking.getChainMetadata(selectedChain.id) } returns metadata()
        coEvery { staking.getChainMetadata("legacy") } returns metadata("controller")
        coEvery { staking.getChainMetadata("no-calls") } returns metadata(withCalls = false)

        assertTrue(interactor.isControllerFeatureDeprecated(null))
        assertTrue(interactor.isControllerFeatureDeprecated(""))
        assertFalse(interactor.isControllerFeatureDeprecated("legacy"))
        assertFalse(interactor.isControllerFeatureDeprecated("no-calls"))
        coVerify(exactly = 2) { staking.getChainMetadata(selectedChain.id) }
    }

    @Test
    fun `deprecated controller fee uses the argument-free call on the selected chain`() = runTest {
        coEvery { staking.getChainMetadata(selectedChain.id) } returns metadata()

        val fee = interactor.estimateFee(selectedChain.addressOf(stashId), selectedChain.addressOf(controllerId))
        form.captured.invoke(builder())

        assertEquals(BigInteger.TEN, fee)
        assertEquals("set_controller", recorded.single().call)
        assertTrue(recorded.single().arguments.isEmpty())
        coVerify { extrinsics.estimateFee(selectedChain, match<ByteArray> { it.contentEquals(stashId) }, any(), any(), any(), any()) }
    }

    @Test
    fun `legacy controller fee on a given chain passes the controller address`() = runTest {
        val chain = chain("relay")
        coEvery { staking.getChainMetadata("relay") } returns metadata("controller")

        interactor.estimateFee(chain.addressOf(stashId), chain.addressOf(controllerId), "relay")
        form.captured.invoke(builder())

        val controller = recorded.single().arguments["controller"] as DictEnum.Entry<*>
        assertEquals(MultiAddress.TYPE_ID, controller.name)
        assertArrayEquals(controllerId, controller.value as ByteArray)
    }

    @Test
    fun `sora controller fee passes the raw controller id`() = runTest {
        val chain = chain(soraMainChainId)
        coEvery { staking.getChainMetadata(soraMainChainId) } returns metadata("controller")

        interactor.estimateFee(chain.addressOf(stashId), chain.addressOf(controllerId), soraMainChainId)
        form.captured.invoke(builder())

        assertArrayEquals(controllerId, recorded.single().arguments["controller"] as ByteArray)
    }

    @Test
    fun `setting the controller submits on the selected or given chain`() = runTest {
        coEvery { staking.getChainMetadata(selectedChain.id) } returns metadata()
        val result = interactor.setController(selectedChain.addressOf(stashId), selectedChain.addressOf(controllerId))
        form.captured.invoke(builder())
        assertEquals("0xhash", result.getOrThrow())
        assertTrue(recorded.last().arguments.isEmpty())

        val relay = chain("relay")
        coEvery { staking.getChainMetadata("relay") } returns metadata("controller")
        interactor.setController(relay.addressOf(stashId), relay.addressOf(controllerId), "relay")
        form.captured.invoke(builder())
        assertEquals(MultiAddress.TYPE_ID, (recorded.last().arguments["controller"] as DictEnum.Entry<*>).name)

        val sora = chain(soraMainChainId)
        coEvery { staking.getChainMetadata(soraMainChainId) } returns metadata("controller")
        interactor.setController(sora.addressOf(stashId), sora.addressOf(controllerId), soraMainChainId)
        form.captured.invoke(builder())
        assertArrayEquals(controllerId, recorded.last().arguments["controller"] as ByteArray)
        assertEquals(3, recorded.size)
    }
}
