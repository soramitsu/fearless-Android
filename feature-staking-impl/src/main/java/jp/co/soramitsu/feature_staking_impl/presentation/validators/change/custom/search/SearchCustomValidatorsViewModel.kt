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
import jp.co.soramitsu.common.utils.withLoading
import jp.co.soramitsu.common.utils.withLoadingSingle
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.ValidatorRecommendatorFactory
import jp.co.soramitsu.feature_staking_impl.domain.validators.current.search.SearchCustomValidatorsInteractor
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.feature_staking_impl.presentation.mappers.mapValidatorToValidatorDetailsParcelModel
import jp.co.soramitsu.feature_staking_impl.presentation.mappers.mapValidatorToValidatorModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.ValidatorModel
import jp.co.soramitsu.feature_wallet_api.domain.TokenUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime
import kotlin.time.milliseconds

sealed class SearchValidatorsState {
    object NoInput : SearchValidatorsState()

    object Loading : SearchValidatorsState()

    object NoResults: SearchValidatorsState()

    class Success(val validators: List<ValidatorModel>) : SearchValidatorsState()
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
        .filterIsInstance<SetupStakingProcess.Confirm>()
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
                SearchValidatorsState.Success(validatorsState.data!!)
            }
            else -> SearchValidatorsState.NoResults
        }
    }.share()

    fun validatorClicked(validatorModel: ValidatorModel) {
        launch {
            val newSelected = selectedValidators.first().toggle(validatorModel.validator)

            sharedStateSetup.set(confirmSetupState.first().changeValidators(newSelected.toList()))
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
