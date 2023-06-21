package jp.co.soramitsu.staking.impl.presentation.validators.change.custom.review

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Named
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.view.ButtonState
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.api.domain.model.Validator
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.getSelectedChain
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.staking.impl.presentation.mappers.mapValidatorToValidatorDetailsParcelModel
import jp.co.soramitsu.staking.impl.presentation.mappers.mapValidatorToValidatorModel
import jp.co.soramitsu.staking.impl.presentation.validators.change.ValidatorModel
import jp.co.soramitsu.staking.impl.presentation.validators.change.custom.review.model.ValidatorsSelectionState
import jp.co.soramitsu.staking.impl.presentation.validators.change.setCustomValidators
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.wallet.impl.domain.TokenUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltViewModel
class ReviewCustomValidatorsViewModel @Inject constructor(
    private val router: StakingRouter,
    private val addressIconGenerator: AddressIconGenerator,
    private val interactor: StakingInteractor,
    stakingRelayChainScenarioInteractor: StakingRelayChainScenarioInteractor,
    private val resourceManager: ResourceManager,
    private val sharedStateSetup: SetupStakingSharedState,
    @Named("StakingTokenUseCase") tokenUseCase: TokenUseCase
) : BaseViewModel() {

    private val confirmSetupState = sharedStateSetup.setupStakingProcess
        .filterIsInstance<SetupStakingProcess.ReadyToSubmit<Validator>>()
        .share()

    private val selectedValidators = confirmSetupState
        .map { it.payload.blockProducers }
        .share()

    private val currentTokenFlow = tokenUseCase.currentTokenFlow()
        .share()

    private val maxValidatorsPerNominatorFlow = flowOf {
        stakingRelayChainScenarioInteractor.maxValidatorsPerNominator()
    }.share()

    val isInEditMode = MutableStateFlow(false)

    val selectionStateFlow = combine(
        selectedValidators,
        maxValidatorsPerNominatorFlow,
        isInEditMode
    ) { validators, maxValidatorsPerNominator, isInEditMode ->
        val isOverflow = validators.size > maxValidatorsPerNominator

        ValidatorsSelectionState(
            selectedHeaderText = resourceManager.getString(R.string.staking_selected_validators_count, validators.size, maxValidatorsPerNominator),
            isOverflow = isOverflow,
            nextButtonText = if (isOverflow) {
                resourceManager.getString(R.string.staking_custom_proceed_button_disabled_title, maxValidatorsPerNominator)
            } else {
                resourceManager.getString(R.string.common_continue)
            },
            buttonState = if (isOverflow || isInEditMode) ButtonState.DISABLED else ButtonState.NORMAL
        )
    }

    val selectedValidatorModels = combine(
        selectedValidators,
        currentTokenFlow
    ) { validators, token ->
        validators.map { validator ->
            val chain = interactor.getSelectedChain()

            mapValidatorToValidatorModel(chain, validator, addressIconGenerator, token)
        }
    }
        .inBackground()
        .share()

    fun deleteClicked(validatorModel: ValidatorModel) {
        launch {
            val validators = selectedValidators.first()

            val withoutRemoved = validators - validatorModel.validator

            sharedStateSetup.setCustomValidators(withoutRemoved)

            if (withoutRemoved.isEmpty()) {
                router.back()
            }
        }
    }

    fun backClicked() {
        router.back()
    }

    fun validatorInfoClicked(validatorModel: ValidatorModel) {
        router.openValidatorDetails(mapValidatorToValidatorDetailsParcelModel(validatorModel.validator))
    }

    fun nextClicked() {
        router.openConfirmStaking()
    }
}
