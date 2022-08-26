package jp.co.soramitsu.staking.impl.presentation.staking.main.di

import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.staking.impl.domain.validations.welcome.WelcomeStakingValidationSystem
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.staking.impl.presentation.staking.main.DelegatorViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.NominatorViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.ParachainWelcomeViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.PoolMemberViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.RelaychainWelcomeViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.StakingPoolWelcomeViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.StakingViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.StashNoneViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.ValidatorViewState
import jp.co.soramitsu.staking.impl.scenarios.parachain.StakingParachainScenarioInteractor
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

class StakingViewStateFactory(
    private val stakingInteractor: StakingInteractor,
    private val setupStakingSharedState: SetupStakingSharedState,
    private val resourceManager: ResourceManager,
    private val router: StakingRouter,
    private val rewardCalculatorFactory: RewardCalculatorFactory,
    private val validationExecutor: ValidationExecutor,
    private val relayChainScenarioInteractor: StakingRelayChainScenarioInteractor,
    private val parachainScenarioInteractor: StakingParachainScenarioInteractor
) {

    fun createValidatorViewState(
        stakingState: StakingState.Stash.Validator,
        currentAssetFlow: Flow<Asset>,
        scope: CoroutineScope,
        errorDisplayer: (Throwable) -> Unit
    ) = ValidatorViewState(
        validatorState = stakingState,
        stakingInteractor = stakingInteractor,
        relayChainScenarioInteractor = relayChainScenarioInteractor,
        currentAssetFlow = currentAssetFlow,
        scope = scope,
        router = router,
        errorDisplayer = errorDisplayer,
        resourceManager = resourceManager
    )

    fun createStashNoneState(
        currentAssetFlow: Flow<Asset>,
        accountStakingState: StakingState.Stash.None,
        scope: CoroutineScope,
        errorDisplayer: (Throwable) -> Unit
    ) = StashNoneViewState(
        stashState = accountStakingState,
        currentAssetFlow = currentAssetFlow,
        stakingInteractor = stakingInteractor,
        relayChainScenarioInteractor = relayChainScenarioInteractor,
        resourceManager = resourceManager,
        scope = scope,
        router = router,
        errorDisplayer = errorDisplayer
    )

    fun createRelayChainWelcomeViewState(
        currentAssetFlow: Flow<Asset>,
        scope: CoroutineScope,
        welcomeStakingValidationSystem: WelcomeStakingValidationSystem,
        errorDisplayer: (String) -> Unit
    ) = RelaychainWelcomeViewState(
        setupStakingSharedState,
        rewardCalculatorFactory,
        resourceManager,
        router,
        currentAssetFlow,
        scope,
        errorDisplayer,
        welcomeStakingValidationSystem,
        validationExecutor
    )

    fun createParachainWelcomeViewState(
        currentAssetFlow: Flow<Asset>,
        scope: CoroutineScope,
        errorDisplayer: (String) -> Unit
    ) = ParachainWelcomeViewState(
        setupStakingSharedState,
        rewardCalculatorFactory,
        resourceManager,
        router,
        currentAssetFlow,
        scope,
        errorDisplayer,
        ValidationSystem(CompositeValidation(validations = listOf())),
        validationExecutor
    )

    fun createNominatorViewState(
        stakingState: StakingState.Stash.Nominator,
        currentAssetFlow: Flow<Asset>,
        scope: CoroutineScope,
        errorDisplayer: (Throwable) -> Unit
    ) = NominatorViewState(
        nominatorState = stakingState,
        stakingInteractor = stakingInteractor,
        relayChainScenarioInteractor = relayChainScenarioInteractor,
        currentAssetFlow = currentAssetFlow,
        scope = scope,
        router = router,
        errorDisplayer = errorDisplayer,
        resourceManager = resourceManager
    )

    fun createDelegatorViewState(
        accountStakingState: StakingState.Parachain.Delegator,
        currentAssetFlow: Flow<Asset>,
        scope: CoroutineScope,
        errorDisplayer: (Throwable) -> Unit
    ): StakingViewState {
        val welcomeViewState = createParachainWelcomeViewState(currentAssetFlow, scope) {
            errorDisplayer(Exception(it))
        }
        return DelegatorViewState(
            delegatorState = accountStakingState,
            welcomeViewState = welcomeViewState,
            currentAssetFlow = currentAssetFlow,
            stakingInteractor = stakingInteractor,
            parachainScenarioInteractor = parachainScenarioInteractor,
            resourceManager = resourceManager,
            scope = scope,
            router = router,
            errorDisplayer = errorDisplayer,
            rewardCalculatorFactory = rewardCalculatorFactory
        )
    }

    fun createPoolWelcomeViewState(
        currentAssetFlow: Flow<Asset>,
        scope: CoroutineScope,
        errorDisplayer: (String) -> Unit
    ) = StakingPoolWelcomeViewState(
        setupStakingSharedState,
        rewardCalculatorFactory,
        resourceManager,
        router,
        currentAssetFlow,
        scope,
        errorDisplayer,
        ValidationSystem(CompositeValidation(validations = listOf())),
        validationExecutor
    )

    fun createPoolMemberViewState(
        state: StakingState.Pool.Member,
        currentAssetFlow: Flow<Asset>,
        scope: CoroutineScope,
        errorDisplayer: (Throwable) -> Unit
    ): PoolMemberViewState {
        return PoolMemberViewState(state, currentAssetFlow, stakingInteractor, resourceManager, scope, router, errorDisplayer)
    }
}
