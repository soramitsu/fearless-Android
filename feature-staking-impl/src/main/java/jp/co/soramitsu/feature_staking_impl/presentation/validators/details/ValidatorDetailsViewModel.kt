package jp.co.soramitsu.feature_staking_impl.presentation.validators.details

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.validators.model.ValidatorDetailsModel

class ValidatorDetailsViewModel(
    private val interactor: StakingInteractor,
    private val router: StakingRouter,
    private val validator: ValidatorDetailsModel
) : BaseViewModel() {

    private val _validatorDetails = MutableLiveData<ValidatorDetailsModel>(validator)

    val validatorDetails: LiveData<ValidatorDetailsModel> = _validatorDetails
}