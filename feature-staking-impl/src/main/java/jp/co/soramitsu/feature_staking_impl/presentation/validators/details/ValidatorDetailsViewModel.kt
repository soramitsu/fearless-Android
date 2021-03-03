package jp.co.soramitsu.feature_staking_impl.presentation.validators.details

import jp.co.soramitsu.common.account.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.mappers.mapValidatorDetailsParcelToValidatorDetailsModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.ValidatorDetailsParcelModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

class ValidatorDetailsViewModel(
    private val interactor: StakingInteractor,
    private val router: StakingRouter,
    private val validator: ValidatorDetailsParcelModel,
    private val iconGenerator: AddressIconGenerator
) : BaseViewModel() {

    private val validatorDetailsFlow = MutableStateFlow(validator)

    private val assetFlow = interactor.getCurrentAsset()
        .share()

    val validatorDetails = validatorDetailsFlow.combine(assetFlow) { validator, asset ->

        mapValidatorDetailsParcelToValidatorDetailsModel(validator, asset)
    }.flowOn(Dispatchers.IO).asLiveData()

    fun backClicked() {
        router.back()
    }
}