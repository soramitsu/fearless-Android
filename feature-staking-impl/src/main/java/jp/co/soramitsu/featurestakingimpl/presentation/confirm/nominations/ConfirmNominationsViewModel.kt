package jp.co.soramitsu.featurestakingimpl.presentation.confirm.nominations

import androidx.lifecycle.liveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.featurestakingapi.domain.model.Validator
import jp.co.soramitsu.featurestakingimpl.domain.StakingInteractor
import jp.co.soramitsu.featurestakingimpl.domain.getSelectedChain
import jp.co.soramitsu.featurestakingimpl.presentation.StakingRouter
import jp.co.soramitsu.featurestakingimpl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.featurestakingimpl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.featurestakingimpl.presentation.mappers.mapValidatorToValidatorDetailsParcelModel
import jp.co.soramitsu.featurestakingimpl.presentation.mappers.mapValidatorToValidatorModel
import jp.co.soramitsu.featurestakingimpl.presentation.validators.change.ValidatorModel
import jp.co.soramitsu.featurestakingimpl.presentation.validators.findSelectedValidator
import jp.co.soramitsu.featurewalletapi.domain.TokenUseCase
import jp.co.soramitsu.featurewalletapi.domain.model.Token
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class ConfirmNominationsViewModel @Inject constructor(
    private val router: StakingRouter,
    private val addressIconGenerator: AddressIconGenerator,
    private val resourceManager: ResourceManager,
    private val sharedStateSetup: SetupStakingSharedState,
    private val interactor: StakingInteractor,
    @Named("StakingTokenUseCase") private val tokenUseCase: TokenUseCase
) : BaseViewModel() {

    private val currentSetupStakingProcess = sharedStateSetup.get<SetupStakingProcess.ReadyToSubmit<Validator>>()

    private val validators = currentSetupStakingProcess.payload.blockProducers

    val selectedValidatorsLiveData = liveData(Dispatchers.Default) {
        emit(convertToModels(validators, tokenUseCase.currentToken()))
    }

    val toolbarTitle = selectedValidatorsLiveData.map {
        resourceManager.getString(R.string.staking_selected_validators_mask, it.size)
    }

    fun backClicked() {
        router.back()
    }

    fun validatorInfoClicked(validatorModel: ValidatorModel) {
        viewModelScope.launch {
            validators.findSelectedValidator(validatorModel.accountIdHex)?.let {
                router.openValidatorDetails(mapValidatorToValidatorDetailsParcelModel(it))
            }
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
}
