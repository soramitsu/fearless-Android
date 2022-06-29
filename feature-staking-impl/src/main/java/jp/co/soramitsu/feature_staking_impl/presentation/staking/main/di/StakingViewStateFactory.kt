package jp.co.soramitsu.feature_staking_impl.presentation.staking.main.di

import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.feature_staking_impl.domain.validations.welcome.WelcomeStakingValidationSystem
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.CollatorViewState
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.DelegatorViewState
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.NominatorViewState
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.ParachainWelcomeViewState
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.RelaychainWelcomeViewState
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.StakingViewState
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.StashNoneViewState
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.ValidatorViewState
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

class StakingViewStateFactory(
    private val stakingInteractor: StakingInteractor,
    private val setupStakingSharedState: SetupStakingSharedState,
    private val resourceManager: ResourceManager,
    private val router: StakingRouter,
    private val rewardCalculatorFactory: RewardCalculatorFactory,
    private val welcomeStakingValidationSystem: WelcomeStakingValidationSystem,
    private val validationExecutor: ValidationExecutor
) {

    fun createValidatorViewState(
        stakingState: StakingState.Stash.Validator,
        currentAssetFlow: Flow<Asset>,
        scope: CoroutineScope,
        errorDisplayer: (Throwable) -> Unit
    ) = ValidatorViewState(
        validatorState = stakingState,
        stakingInteractor = stakingInteractor,
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
        resourceManager = resourceManager,
        scope = scope,
        router = router,
        errorDisplayer = errorDisplayer
    )

    fun createWelcomeViewState(
        currentAssetFlow: Flow<Asset>,
        scope: CoroutineScope,
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
        welcomeStakingValidationSystem,
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
        currentAssetFlow = currentAssetFlow,
        scope = scope,
        router = router,
        errorDisplayer = errorDisplayer,
        resourceManager = resourceManager
    )

    fun createCollatorViewState(): StakingViewState {
        return CollatorViewState
    }

    fun createDelegatorViewState(accountStakingState: StakingState.Parachain.Delegator): StakingViewState {
        return DelegatorViewState(
            accountStakingState.chain,
            accountStakingState.accountId,
            accountStakingState.delegations,
            accountStakingState.totalDelegatedAmount
        )
    }
}
