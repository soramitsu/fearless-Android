package jp.co.soramitsu.staking.impl.presentation.validators.compose

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.common.utils.lazyAsync
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.domain.recommendations.ValidatorRecommendatorFactory
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.RecommendationSettingsProviderFactory
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.pools.compose.SelectListItemViewState
import jp.co.soramitsu.staking.impl.presentation.pools.compose.SelectableListItemState
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

@HiltViewModel
class SelectRecommendedValidatorsViewModel @Inject constructor(
    private val router: StakingRouter,
    private val validatorRecommendatorFactory: ValidatorRecommendatorFactory,
    private val recommendationSettingsProviderFactory: RecommendationSettingsProviderFactory,
    private val resourceManager: ResourceManager
) : BaseViewModel() {

    private val recommendedSettings by lazyAsync {
        recommendationSettingsProviderFactory.createRelayChain(router.currentStackEntryLifecycle).defaultSettings()
    }

    private val recommendedValidators = flow {
        val validatorRecommendator = validatorRecommendatorFactory.create(router.currentStackEntryLifecycle)
        val validators = validatorRecommendator.recommendations(recommendedSettings())

        emit(validators)
    }.inBackground().share()

    private val viewState = recommendedValidators.map { validators ->
        val items = validators.map { SelectableListItemState(
            id = it.accountIdHex,
            title = it.identity?.display ?: it.accountIdHex,
            subtitle = it.electedInfo?.totalStake
        ) }
        val listState = SelectListItemViewState<Int>()
        SelectValidatorsScreenViewState(
            toolbarTitle = resourceManager.getString(R.string.staking_select_suggested),

        )
    }
}
