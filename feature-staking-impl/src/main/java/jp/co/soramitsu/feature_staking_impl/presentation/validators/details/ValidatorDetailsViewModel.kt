package jp.co.soramitsu.feature_staking_impl.presentation.validators.details

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.Browserable
import jp.co.soramitsu.common.utils.Event
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
) : BaseViewModel(), Browserable {

    private val validatorDetailsFlow = MutableStateFlow(validator)

    private val assetFlow = interactor.currentAssetFlow()
        .share()

    val validatorDetails = validatorDetailsFlow.combine(assetFlow) { validator, asset ->
        mapValidatorDetailsParcelToValidatorDetailsModel(validator, asset, iconGenerator)
    }.flowOn(Dispatchers.IO).asLiveData()

    private val _openEmailEvent = MutableLiveData<Event<String>>()
    val openEmailEvent: LiveData<Event<String>> = _openEmailEvent

    override val openBrowserEvent = MutableLiveData<Event<String>>()

    fun backClicked() {
        router.back()
    }

    fun totalStakeClicked() {

    }

    fun webClicked() {
        validator.identity?.web?.let {
            openBrowserEvent.value = Event(it)
        }
    }

    fun emailClicked() {
        validator.identity?.email?.let {
            _openEmailEvent.value = Event(it)
        }
    }

    fun twitterClicked() {
        validator.identity?.twitter?.let {
            openBrowserEvent.value = Event(it)
        }
    }
}

