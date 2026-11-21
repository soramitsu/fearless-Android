package jp.co.soramitsu.staking.impl.di

import io.mockk.mockk
import jp.co.soramitsu.staking.api.data.StakingSharedState
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class StakingFeatureModuleTest {

    private val module = StakingFeatureModule()

    private inline fun <reified T : Any> m(): T = mockk(relaxed = true)

    private fun assertType(expected: String, value: Any) = assertEquals(expected, value::class.simpleName)

    @Test
    fun tokenUseCaseIsTheStakingSharedState() {
        val shared = m<StakingSharedState>()
        assertSame(shared, module.provideTokenUseCase(shared))
    }

    @Test
    fun sharedStateAndStoriesAreBuiltFromTheirDependencies() {
        assertType("FeeLoaderProvider", module.provideFeeLoaderMixin(m(), m()))
        assertType("StakingSharedState", module.provideStakingSharedState(m(), m(), m(), m(), m()))
        assertType("StakingStoriesDataSourceImpl", module.provideRelayChainStories())
        assertType("ParachainStakingStoriesDataSourceImpl", module.provideParachainStories())
        assertType("SetupStakingSharedState", module.provideSetupStakingSharedState())
        assertType("StakingPoolSharedStateProvider", module.provideStakingPoolSharedStateProvider())
        assertType("SettingsStorage", module.provideSettingsStorage())
        assertNotSame(module.provideSettingsStorage(), module.provideSettingsStorage())
    }

    @Test
    fun repositoriesAreBuiltFromStorageSources() {
        assertType("SubqueryStakingRewardsDataSource", module.provideStakingRewardsSubqueryDataSource(m(), m(), m(), m()))
        assertType("StakingParachainScenarioRepository", module.provideParachainStakingRepository(m(), m()))
        assertType("StakingRelayChainScenarioRepository", module.provideRelayChainStakingRepository(m(), m(), m(), m(), m(), m()))
        assertType("StakingRepositoryImpl", module.provideStakingRepository(m(), m()))
        assertType("IdentityRepositoryImpl", module.provideIdentityRepository(m(), m()))
        assertType("StakingConstantsRepository", module.provideStakingConstantsRepository(m()))
        assertType("StakingRewardsRepository", module.provideStakingRewardsRepository(m()))
        assertType("SubQueryValidatorSetFetcher", module.provideValidatorSetFetcher(m(), m(), m(), m()))
        assertType("SubQueryDelegationHistoryFetcher", module.provideDelegationHistoryFetcher(m(), m()))
        assertType("PayoutRepository", module.providePayoutRepository(m(), m(), m(), m(), m()))
        assertType("StakingPoolDataSource", module.provideStakingPoolDataSource(m(), m(), m(), m(), m()))
        assertType("StakingPoolApi", module.provideStakingPoolApi(m(), m(), m(), m(), m()))
    }

    @Test
    fun interactorsAreBuiltFromTheirDependencies() {
        assertType("StakingInteractor", module.provideStakingInteractor(m(), m(), m(), m(), m(), m(), m(), m()))
        assertType(
            "StakingParachainScenarioInteractor",
            module.provideParachainScenarioInteractor(m(), m(), m(), m(), m(), m(), m(), m(), m(), m())
        )
        assertType(
            "StakingRelayChainScenarioInteractor",
            module.provideRelayChainScenarioInteractor(m(), m(), m(), m(), m(), m(), m(), m(), m(), m(), m(), m(), m(), m())
        )
        assertType("EraTimeCalculatorFactory", module.provideEraTimeCalculatorFactory(m()))
        assertType("AlertsInteractor", module.provideAlertsInteractor(m(), m(), m(), m(), m()))
        assertType("RewardCalculatorFactory", module.provideRewardCalculatorFactory(m(), m(), m(), m(), m()))
        assertType("ValidatorRecommendatorFactory", module.provideValidatorRecommendatorFactory(m(), m(), m()))
        assertType("CollatorRecommendatorFactory", module.provideCollatorRecommendatorFactory(m(), m(), m()))
        assertType("ValidatorProvider", module.provideValidatorProvider(m(), m(), m(), m()))
        assertType("CollatorProvider", module.provideCollatorProvider(m(), m(), m(), m(), m()))
        assertType("RecommendationSettingsProviderFactory", module.provideRecommendationSettingsProviderFactory(m(), m(), m()))
        assertType("SetupStakingInteractor", module.provideSetupStakingInteractor(m(), m()))
        assertType("RewardDestinationProvider", module.provideRewardDestinationChooserMixin(m(), m(), m(), m(), m(), m(), m(), m()))
        assertType("PayoutInteractor", module.providePayoutInteractor(m(), m()))
        assertType("BondMoreInteractor", module.provideBondMoreInteractor(m(), m()))
        assertType("UnbondInteractor", module.provideUnbondInteractor(m()))
        assertType("RedeemInteractor", module.provideRedeemInteractor(m()))
        assertType("RebondInteractor", module.provideRebondInteractor(m(), m()))
        assertType("ControllerInteractor", module.provideControllerInteractor(m(), m(), m()))
        assertType("CurrentValidatorsInteractor", module.provideCurrentValidatorsInteractor(m(), m(), m()))
        assertType("ChangeRewardDestinationInteractor", module.provideChangeRewardDestinationInteractor(m()))
        assertType(
            "SearchCustomBlockProducerInteractor",
            module.provideSearchCustomBlockProducerInteractor(m(), m(), m(), m(), m(), m())
        )
        assertType("StakingPoolInteractor", module.provideStakingPoolInteractor(m(), m(), m(), m(), m(), m(), m(), m(), m()))
        assertType("GetIdentitiesUseCase", module.provideIdentitiesUseCase(m()))
        assertType("SoraStakingRewardsScenario", module.soraTokensRateUseCase(m(), m(), m()))
    }

    @Test
    fun stakingApiIsARetrofitProxyOfTheSubqueryInterface() {
        val api = module.provideStakingApi(OkHttpClient())
        assertEquals(true, java.lang.reflect.Proxy.isProxyClass(api.javaClass))
        assertEquals(
            "jp.co.soramitsu.staking.impl.data.network.subquery.StakingApi",
            api.javaClass.interfaces.single().name
        )
    }
}
