@file:OptIn(ExperimentalTime::class)

package jp.co.soramitsu.feature_staking_impl.presentation.validators.change.custom.search

import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.presentation.map
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.common.utils.toggle
import jp.co.soramitsu.common.utils.withLoadingSingle
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.ValidatorRecommendatorFactory
import jp.co.soramitsu.feature_staking_impl.domain.validators.current.search.SearchCustomValidatorsInteractor
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.feature_staking_impl.presentation.mappers.mapValidatorToValidatorDetailsParcelModel
import jp.co.soramitsu.feature_staking_impl.presentation.mappers.mapValidatorToValidatorModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.ValidatorModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.setCustomValidators
import jp.co.soramitsu.feature_wallet_api.domain.TokenUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime

sealed class SearchValidatorsState {
    object NoInput : SearchValidatorsState()

    object Loading : SearchValidatorsState()

    object NoResults : SearchValidatorsState()

    class Success(val validators: List<ValidatorModel>, val headerTitle: String) : SearchValidatorsState()
}

class SearchCustomValidatorsViewModel(
    private val router: StakingRouter,
    private val addressIconGenerator: AddressIconGenerator,
    private val interactor: SearchCustomValidatorsInteractor,
    private val resourceManager: ResourceManager,
    private val sharedStateSetup: SetupStakingSharedState,
    private val validatorRecommendatorFactory: ValidatorRecommendatorFactory,
    tokenUseCase: TokenUseCase,
) : BaseViewModel() {

    private val confirmSetupState = sharedStateSetup.setupStakingProcess
        .filterIsInstance<SetupStakingProcess.ReadyToSubmit>()
        .share()

    private val selectedValidators = confirmSetupState
        .map { it.payload.validators.toSet() }
        .inBackground()
        .share()

    private val currentTokenFlow = tokenUseCase.currentTokenFlow()
        .share()

    val enteredQuery = MutableStateFlow("")

    private val allElectedValidators by lazy {
        async { validatorRecommendatorFactory.create(router.currentStackEntryLifecycle).availableValidators.toSet() }
    }

    private val foundValidatorsState = enteredQuery
        .withLoadingSingle {
            if (it.isNotEmpty()) {
                interactor.searchValidator(it, allElectedValidators() + selectedValidators.first())
            } else {
                null
            }
        }
        .inBackground()
        .share()

    private val selectedValidatorModelsState = combine(
        selectedValidators,
        foundValidatorsState,
        currentTokenFlow
    ) { selectedValidators, foundValidatorsState, token ->
        foundValidatorsState.map { validators ->
            validators?.map { validator ->
                mapValidatorToValidatorModel(
                    validator = validator,
                    iconGenerator = addressIconGenerator,
                    token = token,
                    isChecked = validator in selectedValidators
                )
            }
        }
    }
        .inBackground()
        .share()

    val screenState = selectedValidatorModelsState.map { validatorsState ->
        when {
            validatorsState is LoadingState.Loading -> SearchValidatorsState.Loading
            validatorsState is LoadingState.Loaded && validatorsState.data == null -> SearchValidatorsState.NoInput

            validatorsState is LoadingState.Loaded && validatorsState.data.isNullOrEmpty().not() -> {
                val validators = validatorsState.data!!

                SearchValidatorsState.Success(
                    validators = validators,
                    headerTitle = resourceManager.getString(R.string.common_search_results_number, validators.size)
                )
            }

            else -> SearchValidatorsState.NoResults
        }
    }.share()

    fun validatorClicked(validatorModel: ValidatorModel) {
        if (validatorModel.validator.prefs!!.blocked) {
            showError(resourceManager.getString(R.string.staking_custom_blocked_warning))
            return
        }

        launch {
            val newSelected = selectedValidators.first().toggle(validatorModel.validator)

            sharedStateSetup.setCustomValidators(newSelected.toList())
        }
    }

    fun backClicked() {
        router.back()
    }

    fun doneClicked() {
        router.back()
    }

    fun validatorInfoClicked(validatorModel: ValidatorModel) {
        router.openValidatorDetails(mapValidatorToValidatorDetailsParcelModel(validatorModel.validator))
    }
}
