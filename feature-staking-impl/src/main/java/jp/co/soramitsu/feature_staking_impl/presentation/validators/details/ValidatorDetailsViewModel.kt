package jp.co.soramitsu.feature_staking_impl.presentation.validators.details

import jp.co.soramitsu.common.account.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.mappers.mapValidatorDetailsParcelToValidatorDetailsModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.details.model.ValidatorDetailsModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.ValidatorDetailsParcelModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class ValidatorDetailsViewModel(
    private val interactor: StakingInteractor,
    private val router: StakingRouter,
    private val validator: ValidatorDetailsParcelModel,
    private val iconGenerator: AddressIconGenerator
) : BaseViewModel() {

    private val validatorDetailsFlow = MutableStateFlow(validator)

    val validatorDetails = validatorDetailsFlow.map {
        val networkType = interactor.getSelectedNetworkType()

        transferValidatorModel(it, networkType)
    }.flowOn(Dispatchers.IO).asLiveData()

    fun backClicked() {
        router.back()
    }

    private fun transferValidatorModel(validator: ValidatorDetailsParcelModel, networkType: Node.NetworkType): ValidatorDetailsModel {
        return mapValidatorDetailsParcelToValidatorDetailsModel(validator, networkType)
    }
}