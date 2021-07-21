package jp.co.soramitsu.feature_staking_impl.presentation.validators.change.recommended

import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.common.utils.lazyAsync
import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.ValidatorRecommendatorFactory
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationSettingsProviderFactory
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingProcess.ReadyToSubmit
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingProcess.ReadyToSubmit.SelectionMethod
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.feature_staking_impl.presentation.mappers.mapValidatorToValidatorDetailsParcelModel
import jp.co.soramitsu.feature_staking_impl.presentation.mappers.mapValidatorToValidatorModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.ValidatorModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.setRecommendedValidators
import jp.co.soramitsu.feature_wallet_api.domain.TokenUseCase
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class RecommendedValidatorsViewModel(
    private val router: StakingRouter,
    private val validatorRecommendatorFactory: ValidatorRecommendatorFactory,
    private val recommendationSettingsProviderFactory: RecommendationSettingsProviderFactory,
    private val addressIconGenerator: AddressIconGenerator,
    private val interactor: StakingInteractor,
    private val resourceManager: ResourceManager,
    private val sharedStateSetup: SetupStakingSharedState,
    private val tokenUseCase: TokenUseCase,
) : BaseViewModel() {

    private val recommendedSettings by lazyAsync {
        recommendationSettingsProviderFactory.create(router.currentStackEntryLifecycle).defaultSettings()
    }

    private val recommendedValidators = flow {
        val validatorRecommendator = validatorRecommendatorFactory.create(router.currentStackEntryLifecycle)
        val validators = validatorRecommendator.recommendations(recommendedSettings())

        emit(validators)
    }.inBackground().share()

    val recommendedValidatorModels = recommendedValidators.map {
        convertToModels(it, tokenUseCase.currentToken())
    }.inBackground().share()

    val selectedTitle = recommendedValidators.map {
        val maxValidators = interactor.maxValidatorsPerNominator()

        resourceManager.getString(R.string.staking_selected_validators_format, it.size, maxValidators)
    }.inBackground().share()

    fun backClicked() {
        retractRecommended()

        router.back()
    }

    fun validatorInfoClicked(validatorModel: ValidatorModel) {
        router.openValidatorDetails(mapValidatorToValidatorDetailsParcelModel(validatorModel.validator))
    }

    fun nextClicked() {
        viewModelScope.launch {
            sharedStateSetup.setRecommendedValidators(recommendedValidators.first())

            router.openConfirmStaking()
        }
    }

    private suspend fun convertToModels(
        validators: List<Validator>,
        token: Token
    ): List<ValidatorModel> {
        return validators.map {
            mapValidatorToValidatorModel(it, addressIconGenerator, token)
        }
    }

    private fun retractRecommended() = sharedStateSetup.mutate {
        if (it is ReadyToSubmit && it.payload.selectionMethod == SelectionMethod.RECOMMENDED) {
            it.previous()
        } else {
            it
        }
    }
}
