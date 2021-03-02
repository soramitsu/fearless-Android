package jp.co.soramitsu.feature_staking_impl.presentation.validators.details

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.mappers.mapValidatorDetailsParcelToValidatorDetailsModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.details.model.ValidatorDetailsModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.ValidatorDetailsParcelModel

class ValidatorDetailsViewModel(
    private val interactor: StakingInteractor,
    private val router: StakingRouter,
    private val validator: ValidatorDetailsParcelModel
) : BaseViewModel() {

    private val _validatorDetails = MutableLiveData<ValidatorDetailsParcelModel>(validator)

    val validatorDetails: LiveData<ValidatorDetailsModel> = _validatorDetails.map {
        mapValidatorDetailsParcelToValidatorDetailsModel(it)
    }

    fun backClicked() {
        router.back()
    }
}