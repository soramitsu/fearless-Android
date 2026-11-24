package jp.co.soramitsu.polkaswap.impl.di

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.polkaswap.api.data.PolkaswapRepository
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.impl.data.PolkaswapRepositoryImpl
import jp.co.soramitsu.polkaswap.impl.domain.PolkaswapInteractorImpl
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class PolkaswapFeatureModuleTest {
    private val module = PolkaswapFeatureModule()

    @Test fun repositoryProviderBuildsTheGuardedRepository() = runTest {
        val toggles = mockk<ProductFeatureToggleStore> { every { polkaswapMutationsEnabled } returns false }

        val repository = module.providePolkaswapRepositoryImpl(
            mockk(), mockk(), mockk(), mockk(), mockk(), toggles, mockk(), mockk()
        )

        assertTrue(repository is PolkaswapRepositoryImpl)
        val refused = repository.swap(
            soraMainChainId, 0, "0x01", "0x02", BigInteger.TEN, BigInteger.ONE, "Disabled", emptyList(),
            jp.co.soramitsu.polkaswap.api.models.WithDesired.INPUT
        )
        assertEquals("Polkaswap actions are temporarily disabled", refused.exceptionOrNull()?.message)
    }

    @Test fun interactorProviderUsesTheGivenRepositoryAndPreferences() = runTest {
        val repository = mockk<PolkaswapRepository> {
            coEvery { getAvailableDexes(soraMainChainId) } returns listOf(BigInteger.ONE)
        }
        val preferences = mockk<Preferences> {
            every { getBoolean(PolkaswapInteractor.HAS_READ_DISCLAIMER_KEY, false) } returns false
        }

        val interactor = module.providePolkaswapInteractor(mockk(), mockk(), mockk(), repository, preferences, mockk())

        assertTrue(interactor is PolkaswapInteractorImpl)
        assertEquals(listOf(BigInteger.ONE), interactor.getAvailableDexes())
        assertFalse(interactor.hasReadDisclaimer)
        coVerify { repository.getAvailableDexes(soraMainChainId) }
    }
}
