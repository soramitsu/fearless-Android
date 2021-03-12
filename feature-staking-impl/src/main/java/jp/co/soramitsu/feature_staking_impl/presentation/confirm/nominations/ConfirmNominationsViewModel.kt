package jp.co.soramitsu.feature_staking_impl.presentation.confirm.nominations

import androidx.lifecycle.liveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.StakingSharedState
import jp.co.soramitsu.feature_staking_impl.presentation.mappers.mapValidatorToValidatorDetailsParcelModel
import jp.co.soramitsu.feature_staking_impl.presentation.mappers.mapValidatorToValidatorModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.findSelectedValidator
import jp.co.soramitsu.feature_staking_impl.presentation.validators.recommended.model.ValidatorModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ConfirmNominationsViewModel(
    private val router: StakingRouter,
    private val addressIconGenerator: AddressIconGenerator,
    private val interactor: StakingInteractor,
    private val resourceManager: ResourceManager,
    private val sharedState: StakingSharedState
) : BaseViewModel() {

    val selectedValidatorsLiveData = liveData(Dispatchers.Default) {
        val nominations = sharedState.selectedValidators.first()
        val networkType = interactor.getSelectedNetworkType()

        emit(convertToModels(nominations, networkType))
    }

    val toolbarTitle = selectedValidatorsLiveData.map {
        resourceManager.getString(R.string.staking_selected_validators_mask, it.size)
    }

    fun backClicked() {
        router.back()
    }

    fun validatorInfoClicked(validatorModel: ValidatorModel) {
        viewModelScope.launch {
            sharedState.selectedValidators.findSelectedValidator(validatorModel.accountIdHex)?.let {
                router.openValidatorDetails(mapValidatorToValidatorDetailsParcelModel(it))
            }
        }
    }

    private suspend fun convertToModels(
        validators: List<Validator>,
        networkType: Node.NetworkType
    ): List<ValidatorModel> {
        return validators.map {
            mapValidatorToValidatorModel(it, addressIconGenerator, networkType)
        }
    }
}
