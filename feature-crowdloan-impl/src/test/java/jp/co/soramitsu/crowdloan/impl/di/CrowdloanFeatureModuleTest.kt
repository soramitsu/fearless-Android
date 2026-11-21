package jp.co.soramitsu.crowdloan.impl.di

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.crowdloan.api.domain.LegacyCrowdloanEvidence
import jp.co.soramitsu.crowdloan.impl.data.repository.CrowdloanRepositoryImpl
import jp.co.soramitsu.crowdloan.impl.domain.LegacyCrowdloanRecoveryImpl
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrowdloanFeatureModuleTest {
    private val module = CrowdloanFeatureModule()
    private val chainRegistry = mockk<ChainRegistry>()

    @Test fun repositoryProviderReadsTheRelayRuntime() = runTest {
        val runtime = mockk<RuntimeSnapshot> { every { metadata.modules } returns emptyMap() }
        coEvery { chainRegistry.getRuntime(polkadotChainId) } returns runtime

        val repository = module.crowdloanRepository(mockk(), chainRegistry)

        assertTrue(repository is CrowdloanRepositoryImpl)
        assertFalse(repository.isCrowdloansAvailable(polkadotChainId))
    }

    @Test fun recoveryProviderFailsClosedWithoutTheChain() = runTest {
        coEvery { chainRegistry.getChain(polkadotChainId) } throws NoSuchElementException("not synced")

        val recovery = module.provideLegacyCrowdloanRecovery(mockk(), chainRegistry, mockk(), mockk())

        assertTrue(recovery is LegacyCrowdloanRecoveryImpl)
        assertEquals(LegacyCrowdloanEvidence.None, recovery.findEvidence(polkadotChainId, "dot"))
    }
}
