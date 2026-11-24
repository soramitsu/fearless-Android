package jp.co.soramitsu.staking.impl.di

import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.staking.api.data.StakingSharedState
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class StakingUpdatersModuleTest {

    private val module = StakingUpdatersModule()

    private inline fun <reified T : Any> m(): T = mockk(relaxed = true)

    private fun assertType(expected: String, value: Any) = assertEquals(expected, value::class.simpleName)

    @Test
    fun everyUpdaterIsBuiltFromItsDependencies() {
        assertType("AccountStakingScope", module.provideAccountStakingScope(m(), m(), m()))
        assertType("BlockNumberUpdater", module.provideBlockNumberUpdater(m(), m(), m()))
        assertType("ActiveEraUpdater", module.provideActiveEraUpdater(m(), m(), m()))
        assertType("ValidatorExposureUpdater", module.provideElectedValidatorsUpdater(m(), m(), m(), m()))
        assertType("NominatorsUpdater", module.provideElectedNominatorsUpdater(m(), m(), m(), m()))
        assertType("TotalIssuanceUpdater", module.provideTotalInsuranceUpdater(m(), m(), m()))
        assertType("CurrentEraUpdater", module.provideCurrentEraUpdater(m(), m(), m()))
        assertType("StakingLedgerUpdater", module.provideStakingLedgerUpdater(m(), m(), m(), m(), m(), m(), m()))
        assertType("ValidatorPrefsUpdater", module.provideAccountValidatorPrefsUpdater(m(), m(), m(), m()))
        assertType("AccountNominationsUpdater", module.provideAccountNominationsUpdater(m(), m(), m(), m()))
        assertType("AccountRewardDestinationUpdater", module.provideAccountRewardDestinationUpdater(m(), m(), m(), m()))
        assertType("HistoryDepthUpdater", module.provideHistoryDepthUpdater(m(), m(), m()))
        assertType("HistoricalUpdateMediator", module.provideHistoricalMediator(m(), m(), m(), m(), m()))
        assertType("AccountControllerBalanceUpdater", module.provideAccountControllerBalanceUpdater(m(), m(), m(), m()))
        assertType("MinBondUpdater", module.provideMinBondUpdater(m(), m(), m()))
        assertType("MaxNominatorsUpdater", module.provideMaxNominatorsUpdater(m(), m(), m()))
        assertType("CounterForNominatorsUpdater", module.provideCounterForNominatorsUpdater(m(), m(), m()))
        assertType("DelegatorStateUpdater", module.provideDelegatorStateUpdater(m(), m(), m(), m()))
    }

    @Test
    fun updateSystemRunsOnTheStakingChain() {
        val shared = m<StakingSharedState>()
        every { shared.assetWithChain } returns emptyFlow()
        val system = module.provideStakingUpdaterSystem(
            m(), m(), m(), m(), m(), m(), m(), m(), m(), m(), m(), m(), m(), m(), m(), m(), m(), m(), shared
        )
        assertType("SingleChainUpdateSystem", system)
    }
}
