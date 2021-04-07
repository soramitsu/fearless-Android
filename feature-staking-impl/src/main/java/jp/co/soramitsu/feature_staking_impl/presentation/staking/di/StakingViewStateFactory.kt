package jp.co.soramitsu.feature_staking_impl.presentation.staking.di

import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.feature_staking_impl.presentation.staking.NominatorViewState
import jp.co.soramitsu.feature_staking_impl.presentation.staking.ValidatorViewState
import jp.co.soramitsu.feature_staking_impl.presentation.staking.WelcomeViewState
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

class StakingViewStateFactory(
    private val stakingInteractor: StakingInteractor,
    private val setupStakingSharedState: SetupStakingSharedState,
    private val resourceManager: ResourceManager,
    private val router: StakingRouter,
    private val rewardCalculatorFactory: RewardCalculatorFactory,
) {

    fun createValidatorViewState() = ValidatorViewState

    fun createWelcomeViewState(
        currentAssetFlow: Flow<Asset>,
        accountStakingState: StakingState,
        scope: CoroutineScope,
        errorDisplayer: (String) -> Unit
    ) = WelcomeViewState(
        setupStakingSharedState,
        rewardCalculatorFactory,
        stakingInteractor,
        resourceManager,
        router,
        accountStakingState,
        currentAssetFlow,
        scope,
        errorDisplayer
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
}
