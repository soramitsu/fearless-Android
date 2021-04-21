package jp.co.soramitsu.feature_staking_impl.presentation.staking.balance

import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.ManageStakingValidationSystem
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.staking.balance.model.StakingBalanceModel
import jp.co.soramitsu.feature_wallet_api.presentation.model.mapAmountToAmountModel
import kotlinx.coroutines.flow.map

class StakingBalanceViewModel(
    private val router: StakingRouter,
    private val addressIconGenerator: AddressIconGenerator,
    private val defaultActionValidationSystem: ManageStakingValidationSystem,
    private val unbondValidationSystem: ManageStakingValidationSystem,
    private val validationExecutor: ValidationExecutor,
    private val resourceManager: ResourceManager,
    private val interactor: StakingInteractor,
) : BaseViewModel(), Validatable by validationExecutor {

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
}
