package jp.co.soramitsu.staking.impl.scenarios.parachain

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.api.domain.api.IdentityRepository
import jp.co.soramitsu.staking.impl.data.network.subquery.SubQueryDelegationHistoryFetcher
import jp.co.soramitsu.staking.impl.data.repository.StakingConstantsRepository
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import java.math.BigDecimal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.verifyNoInteractions

class StakingParachainScenarioInteractorTest {

    private val stakingInteractor = mock<StakingInteractor>()
    private val accountRepository = mock<AccountRepository>()
    private val scenarioRepository = mock<StakingParachainScenarioRepository>()

    private val interactor = StakingParachainScenarioInteractor(
        stakingInteractor = stakingInteractor,
        accountRepository = accountRepository,
        stakingConstantsRepository = mock<StakingConstantsRepository>(),
        stakingParachainScenarioRepository = scenarioRepository,
        identityRepositoryImpl = mock<IdentityRepository>(),
        stakingSharedState = mock<StakingSharedState>(),
        iconGenerator = mock<AddressIconGenerator>(),
        resourceManager = mock<ResourceManager>(),
        delegationHistoryFetcher = mock<SubQueryDelegationHistoryFetcher>(),
        walletRepository = mock<WalletRepository>()
    )

    @Test
    fun `missing collator returns empty balance flow without touching repositories`() = runTest {
        val result = interactor.getStakingBalanceFlow(collatorId = null).firstOrNull()

        assertNull(result)
        verifyNoInteractions(stakingInteractor, accountRepository, scenarioRepository)
    }

    @Test
    fun `missing collator returns empty unbondings without touching repositories`() = runTest {
        val current = interactor.currentUnbondingsFlow(collatorAddress = null).first()
        val rebonding = interactor.getRebondingUnbondings(collatorAddress = null)

        assertEquals(emptyList<Any>(), current)
        assertEquals(emptyList<Any>(), rebonding)
        verifyNoInteractions(stakingInteractor, accountRepository, scenarioRepository)
    }

    @Test
    fun `missing collator has no available unstake amount`() = runTest {
        val result = interactor.getUnstakeAvailableAmount(
            asset = mock<Asset>(),
            collatorId = null
        )

        assertEquals(BigDecimal.ZERO, result)
        verifyNoInteractions(stakingInteractor, accountRepository, scenarioRepository)
    }
}
