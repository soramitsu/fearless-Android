package jp.co.soramitsu.feature_staking_impl.presentation.validators.recommended

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.account.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.mixin.api.Browserable
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.ValidatorRecommendatorFactory
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationSettings
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationSettingsProviderFactory
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.StakingSharedState
import jp.co.soramitsu.feature_staking_impl.presentation.mappers.mapValidatorToValidatorDetailsParcelModel
import jp.co.soramitsu.feature_staking_impl.presentation.mappers.mapValidatorToValidatorModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.findSelectedValidator
import jp.co.soramitsu.feature_staking_impl.presentation.validators.recommended.model.ValidatorModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class RecommendedValidatorsViewModel(
    private val router: StakingRouter,
    private val validatorRecommendatorFactory: ValidatorRecommendatorFactory,
    private val recommendationSettingsProviderFactory: RecommendationSettingsProviderFactory,
    private val addressIconGenerator: AddressIconGenerator,
    private val appLinksProvider: AppLinksProvider,
    private val interactor: StakingInteractor,
    private val sharedState: StakingSharedState
) : BaseViewModel(), Browserable {

    override val openBrowserEvent = MutableLiveData<Event<String>>()

    private val recommendedValidators = flow {
        val validatorRecommendator = validatorRecommendatorFactory.create()
        val validators = validatorRecommendator.recommendations(recommendedSettings())

        emit(validators)
    }.flowOn(Dispatchers.Default).share()

    val recommendedValidatorModels = recommendedValidators.map {
        val networkType = interactor.getSelectedNetworkType()

        convertToModels(it, networkType)
    }.flowOn(Dispatchers.Default).asLiveData()

    fun backClicked() {
        router.back()
    }

    fun validatorInfoClicked(validatorModel: ValidatorModel) {
        viewModelScope.launch {
            recommendedValidators.findSelectedValidator(validatorModel.accountIdHex)?.let {
                router.openValidatorDetails(mapValidatorToValidatorDetailsParcelModel(it))
            }
        }
    }

    fun nextClicked() {
        viewModelScope.launch {
            sharedState.selectedValidators.emit(recommendedValidators.first())

            router.openConfirmStaking()
        }
    }

    fun learnMoreClicked() {
        openBrowserEvent.value = Event(appLinksProvider.nominatorLearnMore)
    }

    private suspend fun convertToModels(
        validators: List<Validator>,
        networkType: Node.NetworkType
    ): List<ValidatorModel> {
        return validators.map {
            mapValidatorToValidatorModel(it, addressIconGenerator, networkType)
        }
    }

    private suspend fun recommendedSettings(): RecommendationSettings {
        return recommendationSettingsProviderFactory.get().defaultSettings()
    }
}