package jp.co.soramitsu.feature_staking_impl.presentation.confirm.nominations

import androidx.lifecycle.liveData
import androidx.lifecycle.map
import jp.co.soramitsu.common.account.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.data.mappers.mapValidatorToValidatorModel
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.StakingSharedState
import jp.co.soramitsu.feature_staking_impl.presentation.validators.model.ValidatorModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first

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
        // TODO
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