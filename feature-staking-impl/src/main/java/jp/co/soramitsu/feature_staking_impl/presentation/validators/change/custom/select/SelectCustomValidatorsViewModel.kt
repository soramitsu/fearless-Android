package jp.co.soramitsu.feature_staking_impl.presentation.validators.change.custom.select

import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.toggle
import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.ValidatorRecommendatorFactory
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationSettingsProviderFactory
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.sortings.APYSorting
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.sortings.OwnStakeSorting
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.sortings.TotalStakeSorting
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.mappers.mapValidatorToValidatorDetailsParcelModel
import jp.co.soramitsu.feature_staking_impl.presentation.mappers.mapValidatorToValidatorModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.custom.select.model.ContinueButtonState
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.recommended.model.ValidatorModel
import jp.co.soramitsu.feature_wallet_api.domain.TokenUseCase
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class SelectCustomValidatorsViewModel(
    private val router: StakingRouter,
    private val validatorRecommendatorFactory: ValidatorRecommendatorFactory,
    private val recommendationSettingsProviderFactory: RecommendationSettingsProviderFactory,
    private val addressIconGenerator: AddressIconGenerator,
    private val interactor: StakingInteractor,
    private val resourceManager: ResourceManager,
    private val tokenUseCase: TokenUseCase,
) : BaseViewModel() {

    private val validatorRecommendator by lazy {
        async { validatorRecommendatorFactory.create(router.currentStackEntryLifecycle) }
    }

    private val recommendationSettingsFlow = flow {
        emitAll(recommendationSettingsProviderFactory.get().observeRecommendationSettings())
    }.share()

    private val shownValidators = recommendationSettingsFlow.map {
        recommendator().recommendations(it)
    }.share()

    private val tokenFlow = tokenUseCase.currentTokenFlow()
        .inBackground()
        .share()

    private val selectedValidators = MutableStateFlow(emptySet<Validator>())

    private val maxSelectedValidators = interactor.maxValidatorsPerNominator()

    val validatorModelsFlow = combine(
        shownValidators,
        selectedValidators,
        tokenFlow,
        ::convertToModels
    ).inBackground().share()

    val selectedTitle = shownValidators.map {
        resourceManager.getString(R.string.staking_shown_validators_format, it.size, recommendator().availableValidators.size)
    }.inBackground().share()

    val buttonState = selectedValidators.map {
        if (it.isEmpty()) {
            ContinueButtonState(
                enabled = false,
                text = resourceManager.getString(R.string.staking_select_validators_with_max, maxSelectedValidators)
            )
        } else {
            ContinueButtonState(
                enabled = true,
                text = resourceManager.getString(R.string.staking_show_selected, it.size, maxSelectedValidators)
            )
        }
    }

    val scoringHeader = recommendationSettingsFlow.map {
        when (it.sorting) {
            APYSorting -> resourceManager.getString(R.string.staking_rewards_apy)
            TotalStakeSorting -> resourceManager.getString(R.string.staking_sorting_header_total_stake)
            OwnStakeSorting -> resourceManager.getString(R.string.staking_sorting_header_own_stake)
            else -> throw IllegalArgumentException("Unknown sorting: ${it.sorting}")
        }
    }.inBackground().share()

    fun backClicked() {
        router.back()
    }

    fun validatorInfoClicked(validatorModel: ValidatorModel) {
        router.openValidatorDetails(mapValidatorToValidatorDetailsParcelModel(validatorModel.validator))
    }

    fun validatorClicked(validatorModel: ValidatorModel) {
        mutateSelected {
            it.toggle(validatorModel.validator)
        }
    }

    private suspend fun convertToModels(
        validators: List<Validator>,
        selectedValidators: Set<Validator>,
        token: Token
    ): List<ValidatorModel> {
        return validators.map {
            mapValidatorToValidatorModel(
                validator = it,
                iconGenerator = addressIconGenerator,
                token = token,
                isChecked = it in selectedValidators,
                sorting = recommendationSettingsFlow.first().sorting
            )
        }
    }

    private suspend fun recommendator() = validatorRecommendator.await()

    private fun mutateSelected(mutation: (Set<Validator>) -> Set<Validator>) {
        selectedValidators.value = mutation(selectedValidators.value)
    }
}
