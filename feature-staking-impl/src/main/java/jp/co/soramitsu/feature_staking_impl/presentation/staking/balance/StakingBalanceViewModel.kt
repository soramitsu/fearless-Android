package jp.co.soramitsu.feature_staking_impl.presentation.staking.balance

import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.DefaultFailure
import jp.co.soramitsu.common.mixin.api.RetryPayload
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.common.validation.unwrap
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.ManageStakingValidationSystem
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.staking.balance.model.StakingBalanceModel
import jp.co.soramitsu.feature_wallet_api.presentation.model.mapAmountToAmountModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class StakingBalanceViewModel(
    private val router: StakingRouter,
    private val addressIconGenerator: AddressIconGenerator,
    private val defaultActionValidationSystem: ManageStakingValidationSystem,
    private val unbondValidationSystem: ManageStakingValidationSystem,
    private val resourceManager: ResourceManager,
    private val interactor: StakingInteractor
) : BaseViewModel(), Validatable {

    override val validationFailureEvent = MutableLiveData<Event<DefaultFailure>>()

    override val retryEvent = MutableLiveData<Event<RetryPayload>>()

    private val assetFlow = interactor.currentAssetFlow()
        .share()

    val stakingBalanceModelLiveData = assetFlow.map { asset ->
        StakingBalanceModel(
            bonded = mapAmountToAmountModel(asset.bonded, asset),
            unbonding = mapAmountToAmountModel(asset.unbonding, asset),
            redeemable = mapAmountToAmountModel(asset.redeemable, asset)
        )
    }
        .inBackground()
        .asLiveData()



    fun backClicked() {
        router.back()
    }

    override fun validationWarningConfirmed() {
        // pass - no warning validations
    }
}
