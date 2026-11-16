package jp.co.soramitsu.staking.impl.domain.setup

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.fearless_utils.extensions.toHexString
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
import jp.co.soramitsu.staking.api.domain.model.Collator
import jp.co.soramitsu.staking.api.domain.model.RewardDestination
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupStakingInteractorCoverageTest {

    private class Recorded(val module: String, val call: String, val arguments: Map<String, Any?>)

    private val recorded = mutableListOf<Recorded>()
    private val controllerId = ByteArray(32) { 5 }
    private val validatorId = ByteArray(32) { 6 }
    private val collatorId = ByteArray(20) { 7 }
    private val extrinsics = mockk<ExtrinsicService>()
    private val shared = mockk<StakingSharedState>()
    private val form = slot<suspend ExtrinsicBuilder.() -> Unit>()
    private val interactor = SetupStakingInteractor(extrinsics, shared)

    private fun select(asset: Asset): Chain {
        val chain = StakingLaneFixtures.chain(asset)
        every { shared.assetWithChain } returns flowOf(SingleAssetSharedState.AssetWithChain(chain, asset))
        coEvery { extrinsics.estimateFee(any(), any<ByteArray>(), any(), any(), any(), capture(form)) } returns BigInteger.TEN
        coEvery { extrinsics.submitExtrinsic(any(), any(), any(), any(), any(), capture(form)) } returns Result.success("0xhash")
        return chain
    }

    private fun builder(): ExtrinsicBuilder {
        val bond = MetadataFunction("bond", listOf(FunctionArgument("value", null), FunctionArgument("payee", null)), emptyList(), 0 to 0)
        val staking = Module("Staking", null, mapOf("bond" to bond), null, emptyMap(), emptyMap(), BigInteger.ZERO)
        val metadata = RuntimeMetadata(BigInteger.ONE, mapOf("Staking" to staking), ExtrinsicMetadata(BigInteger.valueOf(4), emptyList()))
        val builder = mockk<ExtrinsicBuilder>()
        every { builder.runtime } returns RuntimeSnapshot(mockk(relaxed = true), metadata)
        every { builder.call(any<String>(), any<String>(), any()) } answers {
            recorded += Recorded(firstArg(), secondArg(), thirdArg())
            builder
        }
        return builder
    }

    private suspend fun buildCaptured() {
        form.captured.invoke(builder())
    }

    private fun collator(): Collator {
        val collator = mockk<Collator>()
        every { collator.address } returns collatorId.toHexString(true)
        every { collator.delegationCount } returns BigInteger.valueOf(4)
        return collator
    }

    @Test
    fun `maximum setup fee bonds a large amount to a payout account and nominates sixteen validators`() = runTest {
        val chain = select(StakingLaneFixtures.chainAsset())
        val controller = chain.addressOf(controllerId)

        assertEquals(BigInteger.TEN, interactor.estimateMaxSetupStakingFee(controller))
        buildCaptured()

        assertEquals(listOf("bond", "nominate"), recorded.map { it.call })
        assertEquals(BigInteger.TEN.pow(19), recorded[0].arguments["value"])
        assertEquals("Account", (recorded[0].arguments["payee"] as DictEnum.Entry<*>).name)
        assertEquals(16, (recorded[1].arguments["targets"] as List<*>).size)
        coVerify { extrinsics.estimateFee(chain, match<ByteArray> { it.contentEquals(controllerId) }, any(), any(), any(), any()) }
    }

    @Test
    fun `setup fee without a bond only nominates the chosen validators`() = runTest {
        val chain = select(StakingLaneFixtures.chainAsset())

        val fee = interactor.calculateSetupStakingFee(chain.addressOf(controllerId), listOf(validatorId.toHexString(true)), null)
        buildCaptured()

        assertEquals(BigInteger.TEN, fee)
        assertEquals(listOf("nominate"), recorded.map { it.call })
        assertEquals(1, (recorded.single().arguments["targets"] as List<*>).size)
    }

    @Test
    fun `relay setup submits the bond and nomination`() = runTest {
        val chain = select(StakingLaneFixtures.chainAsset())

        val result = interactor.setupStaking(
            chain.addressOf(controllerId),
            listOf(validatorId.toHexString(true)),
            BondPayload(BigDecimal("2"), RewardDestination.Restake)
        )
        buildCaptured()

        assertEquals("0xhash", result.getOrThrow().getOrThrow())
        assertEquals(listOf("bond", "nominate"), recorded.map { it.call })
        assertEquals(BigInteger.valueOf(2) * BigInteger.TEN.pow(10), recorded[0].arguments["value"])
        assertEquals("Staked", (recorded[0].arguments["payee"] as DictEnum.Entry<*>).name)
    }

    @Test
    fun `sora setup bonds with the raw controller id and nominates raw validator ids`() = runTest {
        val chain = select(StakingLaneFixtures.chainAsset(chainId = soraMainChainId, symbol = "XOR", precision = 18))

        val result = interactor.setupStaking(
            chain.addressOf(controllerId),
            listOf(validatorId.toHexString(true)),
            BondPayload(BigDecimal.ONE, RewardDestination.Payout(controllerId))
        )
        buildCaptured()

        assertTrue(result.isSuccess)
        assertEquals(listOf("bond", "nominate"), recorded.map { it.call })
        assertArrayEquals(controllerId, recorded[0].arguments["controller"] as ByteArray)
        assertEquals(BigInteger.TEN.pow(18), recorded[0].arguments["value"])
        val targets = recorded[1].arguments["targets"] as List<*>
        assertArrayEquals(validatorId, targets.single() as ByteArray)
    }

    @Test
    fun `sora nomination without a bond skips the bond call`() = runTest {
        val chain = select(StakingLaneFixtures.chainAsset(chainId = soraMainChainId, symbol = "XOR", precision = 18))

        interactor.calculateSetupStakingFee(chain.addressOf(controllerId), listOf(validatorId.toHexString(true)), null)
        buildCaptured()

        assertEquals(listOf("nominate"), recorded.map { it.call })
    }

    @Test
    fun `relay setup reports a failed submission`() = runTest {
        val chain = select(StakingLaneFixtures.chainAsset())
        coEvery { extrinsics.submitExtrinsic(any(), any(), any(), any(), any(), any()) } throws IllegalStateException("node down")

        val result = interactor.setupStaking(chain.addressOf(controllerId), emptyList(), null)

        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `parachain fee estimate delegates a placeholder amount to a placeholder collator`() = runTest {
        val asset = StakingLaneFixtures.chainAsset(chainId = "parachain", symbol = "GLMR", precision = 18, staking = Asset.StakingType.PARACHAIN)
        val chain = select(asset)

        assertEquals(BigInteger.TEN, interactor.estimateParachainFee(chain.addressOf(controllerId)))
        buildCaptured()

        val delegate = recorded.single()
        assertEquals("ParachainStaking", delegate.module)
        assertEquals("delegate", delegate.call)
        assertArrayEquals(ByteArray(20), delegate.arguments["candidate"] as ByteArray)
        assertEquals(BigInteger.TEN.pow(27), delegate.arguments["amount"])
        assertEquals(BigInteger.ZERO, delegate.arguments["delegation_count"])
    }

    @Test
    fun `final parachain fee and submission delegate to the selected collator`() = runTest {
        val asset = StakingLaneFixtures.chainAsset(chainId = "parachain", symbol = "GLMR", precision = 18, staking = Asset.StakingType.PARACHAIN)
        val chain = select(asset)
        val address = chain.addressOf(controllerId)

        assertEquals(BigInteger.TEN, interactor.estimateFinalParachainFee(address, collator(), BigInteger.valueOf(5), 3))
        buildCaptured()
        val result = interactor.setupStaking(collator(), BigInteger.valueOf(6), 2, address)
        buildCaptured()

        assertEquals("0xhash", result.getOrThrow().getOrThrow())
        assertEquals(2, recorded.size)
        assertArrayEquals(collatorId, recorded[0].arguments["candidate"] as ByteArray)
        assertEquals(BigInteger.valueOf(5), recorded[0].arguments["amount"])
        assertEquals(BigInteger.valueOf(4), recorded[0].arguments["candidate_delegation_count"])
        assertEquals(BigInteger.valueOf(3), recorded[0].arguments["delegation_count"])
        assertEquals(BigInteger.valueOf(6), recorded[1].arguments["amount"])
        assertEquals(BigInteger.valueOf(2), recorded[1].arguments["delegation_count"])
    }
}
