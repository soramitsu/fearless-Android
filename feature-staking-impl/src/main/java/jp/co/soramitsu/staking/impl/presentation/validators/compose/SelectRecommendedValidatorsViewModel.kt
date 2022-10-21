package jp.co.soramitsu.staking.impl.presentation.validators.compose

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.theme.black1
import jp.co.soramitsu.common.compose.theme.greenText
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatAsPercentage
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.common.utils.lazyAsync
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.impl.domain.recommendations.ValidatorRecommendatorFactory
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.RecommendationSettingsProviderFactory
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.presentation.pools.compose.SelectListItemViewState
import jp.co.soramitsu.staking.impl.presentation.pools.compose.SelectableListItemState
import jp.co.soramitsu.wallet.api.presentation.formatters.tokenAmountFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

@HiltViewModel
class SelectRecommendedValidatorsViewModel @Inject constructor(
    private val router: StakingRouter,
    private val validatorRecommendatorFactory: ValidatorRecommendatorFactory,
    private val recommendationSettingsProviderFactory: RecommendationSettingsProviderFactory,
    private val resourceManager: ResourceManager,
    private val stakingPoolSharedStateProvider: StakingPoolSharedStateProvider
) : BaseViewModel() {

    private val asset: Asset
    private val chain: Chain

    init {
        val mainState = stakingPoolSharedStateProvider.requireMainState
        asset = mainState.requireAsset
        chain = mainState.requireChain
    }

    private val recommendedSettings by lazyAsync {
        recommendationSettingsProviderFactory.createRelayChain(router.currentStackEntryLifecycle).defaultSettings()
    }

    private val recommendedValidators = flow {
        val validatorRecommendator = validatorRecommendatorFactory.create(router.currentStackEntryLifecycle)
        val validators = validatorRecommendator.recommendations(recommendedSettings())

        emit(validators)
    }.inBackground().share()

    private val viewState = recommendedValidators.map { validators ->
        val items = validators.map {
            val totalStake = it.electedInfo?.totalStake.orZero().tokenAmountFromPlanks(asset)

            val apyText = buildAnnotatedString {
                withStyle(style = SpanStyle(color = black1)) {
                    append("${resourceManager.getString(R.string.staking_only_apy)} ")
                }
                withStyle(style = SpanStyle(color = greenText)) {
                    append(it.electedInfo?.apy.orZero().formatAsPercentage())
                }
            }
            SelectableListItemState(
                id = it.accountIdHex,
                title = it.identity?.display ?: it.accountIdHex,
                subtitle = resourceManager.getString(R.string.staking_validator_total_stake_token, totalStake),
                caption = apyText
            )
        }
        val listState = SelectListItemViewState<String>(items,)
        SelectValidatorsScreenViewState(
            toolbarTitle = resourceManager.getString(R.string.staking_select_suggested),

            )
    }
}
