package jp.co.soramitsu.staking.impl.presentation.confirm.nominations

import androidx.lifecycle.liveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
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
import jp.co.soramitsu.staking.impl.presentation.validators.findSelectedValidator
import jp.co.soramitsu.wallet.impl.domain.TokenUseCase
import jp.co.soramitsu.wallet.impl.domain.model.Token
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
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
                interactor.validatorDetailsCache.update { prev ->
                    prev + (it.accountIdHex to mapValidatorToValidatorDetailsParcelModel(it))
                }
                router.openValidatorDetails(it.accountIdHex)
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
