package jp.co.soramitsu.featurestakingimpl.presentation.validators.change.recommended

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.common.utils.lazyAsync
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.featurestakingapi.domain.model.Validator
import jp.co.soramitsu.featurestakingimpl.domain.StakingInteractor
import jp.co.soramitsu.featurestakingimpl.domain.getSelectedChain
import jp.co.soramitsu.featurestakingimpl.domain.recommendations.ValidatorRecommendatorFactory
import jp.co.soramitsu.featurestakingimpl.domain.recommendations.settings.RecommendationSettingsProviderFactory
import jp.co.soramitsu.featurestakingimpl.presentation.StakingRouter
import jp.co.soramitsu.featurestakingimpl.presentation.common.SetupStakingProcess.ReadyToSubmit
import jp.co.soramitsu.featurestakingimpl.presentation.common.SetupStakingProcess.ReadyToSubmit.SelectionMethod
import jp.co.soramitsu.featurestakingimpl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.featurestakingimpl.presentation.mappers.mapValidatorToValidatorDetailsParcelModel
import jp.co.soramitsu.featurestakingimpl.presentation.mappers.mapValidatorToValidatorModel
import jp.co.soramitsu.featurestakingimpl.presentation.validators.change.ValidatorModel
import jp.co.soramitsu.featurestakingimpl.presentation.validators.change.setRecommendedValidators
import jp.co.soramitsu.featurestakingimpl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.featurewalletapi.domain.TokenUseCase
import jp.co.soramitsu.featurewalletapi.domain.model.Token
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Named

@HiltViewModel
class RecommendedValidatorsViewModel @Inject constructor(
    private val router: StakingRouter,
    private val validatorRecommendatorFactory: ValidatorRecommendatorFactory,
    private val recommendationSettingsProviderFactory: RecommendationSettingsProviderFactory,
    private val addressIconGenerator: AddressIconGenerator,
    private val interactor: StakingInteractor,
    private val stakingRelayChainScenarioInteractor: StakingRelayChainScenarioInteractor,
    private val resourceManager: ResourceManager,
    private val sharedStateSetup: SetupStakingSharedState,
    @Named("StakingTokenUseCase") private val tokenUseCase: TokenUseCase
) : BaseViewModel() {

    private val recommendedSettings by lazyAsync {
        recommendationSettingsProviderFactory.createRelayChain(router.currentStackEntryLifecycle).defaultSettings()
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
        val maxValidators = stakingRelayChainScenarioInteractor.maxValidatorsPerNominator()

        resourceManager.getString(R.string.staking_custom_header_validators_title, it.size, maxValidators)
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
        val chain = interactor.getSelectedChain()

        return validators.map {
            mapValidatorToValidatorModel(chain, it, addressIconGenerator, token)
        }
    }

    private fun retractRecommended() = sharedStateSetup.mutate {
        if (it is ReadyToSubmit<*> && it.payload.selectionMethod == SelectionMethod.RECOMMENDED) {
            it.previous()
        } else {
            it
        }
    }
}
