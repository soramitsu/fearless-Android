package jp.co.soramitsu.staking.impl.data.repository

import java.math.BigInteger
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.fearless_utils.runtime.metadata.ExtrinsicMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Module
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.kusamaChainId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.givenBlocking

class StakingConstantsRepositoryTest {

    private val chainRegistry = mock<ChainRegistry>()
    private val repository = StakingConstantsRepository(chainRegistry)

    @Before
    fun setUp() {
        val stakingModule = Module(
            name = "Staking",
            storage = null,
            calls = null,
            events = null,
            constants = emptyMap(),
            errors = emptyMap(),
            index = BigInteger.ZERO
        )
        val runtime = RuntimeSnapshot(
            typeRegistry = mock<TypeRegistry>(),
            metadata = RuntimeMetadata(
                runtimeVersion = BigInteger.ZERO,
                modules = mapOf(stakingModule.name to stakingModule),
                extrinsic = ExtrinsicMetadata(
                    version = BigInteger.ZERO,
                    signedExtensions = emptyList()
                )
            )
        )
        givenBlocking { chainRegistry.getRuntime(any()) }.willReturn(runtime)
    }

    @Test
    fun `unknown relay chain without MaxNominations uses conservative limit`() = runTest {
        assertEquals(16, repository.maxValidatorsPerNominator("unknown-chain"))
    }

    @Test
    fun `Kusama without MaxNominations keeps its established limit`() = runTest {
        assertEquals(24, repository.maxValidatorsPerNominator(kusamaChainId))
    }
}
