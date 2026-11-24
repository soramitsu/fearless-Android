package jp.co.soramitsu.liquiditypools.impl.di

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.liquiditypools.data.DemeterFarmingRepository
import jp.co.soramitsu.liquiditypools.data.PoolsRepository
import jp.co.soramitsu.liquiditypools.domain.DemeterMutationAction
import jp.co.soramitsu.liquiditypools.impl.data.DemeterFarmingRepositoryImpl
import jp.co.soramitsu.liquiditypools.impl.data.PoolsRepositoryImpl
import jp.co.soramitsu.liquiditypools.impl.domain.DemeterFarmingInteractorImpl
import jp.co.soramitsu.liquiditypools.impl.domain.PoolsInteractorImpl
import jp.co.soramitsu.liquiditypools.impl.navigation.InternalPoolsRouterImpl
import jp.co.soramitsu.liquiditypools.navigation.LiquidityPoolsNavGraphRoute
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PoolsModuleTest {

    private val module = PoolsModule()

    @Test
    fun `pools bindings use the SORA pools repository`() {
        val repository = module.providePoolsRepositoryImpl(mockk(), mockk(), mockk(), mockk(), mockk(), mockk(), mockk())
        val poolsRepository = mockk<PoolsRepository> { every { poolsChainId } returns "pools-chain" }

        val interactor = module.providesPoolInteractor(poolsRepository, mockk(), mockk(), mockk())

        assertTrue(repository is PoolsRepositoryImpl)
        assertEquals(soraMainChainId, repository.poolsChainId)
        assertTrue(interactor is PoolsInteractorImpl)
        assertEquals("pools-chain", interactor.poolsChainId)
    }

    @Test
    fun `Demeter bindings delegate to the provided repository`() = runBlocking {
        val repository = mockk<DemeterFarmingRepository> {
            coEvery { mutationCapabilityReason(soraMainChainId, DemeterMutationAction.Deposit) } returns "paused"
        }

        val interactor = module.provideDemeterFarmingInteractor(repository)
        val concrete = module.provideDemeterFarmingRepository(mockk(), mockk(), mockk(), mockk(), mockk(), mockk(), mockk(), mockk())

        assertTrue(interactor is DemeterFarmingInteractorImpl)
        assertEquals("paused", interactor.mutationCapabilityReason(soraMainChainId, DemeterMutationAction.Deposit))
        assertTrue(concrete is DemeterFarmingRepositoryImpl)
    }

    @Test
    fun `internal router keeps the flow's own back stack`() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val router = module.provideInternalLiquidityPoolsRouter(mockk(), mockk())

            router.openAllPoolsScreen()
            router.openPoolListScreen(isUserPools = true)

            assertTrue(router is InternalPoolsRouterImpl)
            assertEquals(true, router.destination(LiquidityPoolsNavGraphRoute.ListPoolsScreen::class.java)?.isUserPools)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
