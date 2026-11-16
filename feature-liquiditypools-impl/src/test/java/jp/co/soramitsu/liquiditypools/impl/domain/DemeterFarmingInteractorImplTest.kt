package jp.co.soramitsu.liquiditypools.impl.domain

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import jp.co.soramitsu.liquiditypools.data.DemeterFarmingRepository
import jp.co.soramitsu.liquiditypools.domain.DemeterFarmingBasicPool
import jp.co.soramitsu.liquiditypools.domain.DemeterFarmingPool
import jp.co.soramitsu.liquiditypools.domain.DemeterMutationAction
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.pswap
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.valToken
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.walletAsset
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.xor
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class DemeterFarmingInteractorImplTest {

    private val repository = mockk<DemeterFarmingRepository>(relaxed = true)
    private val interactor = DemeterFarmingInteractorImpl(repository)
    private val basic = DemeterFarmingBasicPool(walletAsset(xor), walletAsset(valToken), walletAsset(pswap), 12.0, BigDecimal.TEN, 0.4)
    private val position = DemeterFarmingPool(walletAsset(xor), walletAsset(valToken), walletAsset(pswap), 12.0, BigDecimal.ONE, BigDecimal.ONE)

    @Test
    fun `reads come from the Demeter repository of the requested chain`() = runBlocking {
        coEvery { repository.getFarmedPools(soraMainChainId) } returns listOf(position)
        coEvery { repository.getAvailablePools(soraMainChainId) } returns listOf(basic)
        coEvery { repository.mutationCapabilityReason(soraMainChainId, DemeterMutationAction.Claim) } returns "claim off"

        assertEquals(listOf(position), interactor.getFarmedPools(soraMainChainId))
        assertEquals(listOf(basic), interactor.getAvailablePools(soraMainChainId))
        assertEquals("claim off", interactor.mutationCapabilityReason(soraMainChainId, DemeterMutationAction.Claim))
        interactor.refresh(soraMainChainId)
        coVerify { repository.refresh(soraMainChainId) }
    }

    @Test
    fun `mutations return the repository results`() = runBlocking {
        coEvery { repository.deposit(soraMainChainId, basic, BigDecimal.ONE) } returns Result.success("0x1")
        coEvery { repository.withdraw(soraMainChainId, position, BigDecimal.ONE) } returns Result.success("0x2")
        coEvery { repository.claimRewards(soraMainChainId, position) } returns Result.failure(IllegalStateException("none"))

        assertEquals("0x1", interactor.deposit(soraMainChainId, basic, BigDecimal.ONE).getOrThrow())
        assertEquals("0x2", interactor.withdraw(soraMainChainId, position, BigDecimal.ONE).getOrThrow())
        assertEquals("none", interactor.claimRewards(soraMainChainId, position).exceptionOrNull()?.message)
    }
}
