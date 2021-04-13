package jp.co.soramitsu.feature_staking_impl.presentation.payouts.detail

import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.feature_account_api.presenatation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.payouts.model.PendingPayoutParcelable
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import kotlinx.coroutines.flow.map

class PayoutDetailsViewModel(
    private val interactor: StakingInteractor,
    private val router: StakingRouter,
    private val payout: PendingPayoutParcelable,
    private val addressModelGenerator: AddressIconGenerator,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    private val resourceManager: ResourceManager,
) : BaseViewModel(), ExternalAccountActions.Presentation by externalAccountActions {

    val payoutDetails = interactor.currentAssetFlow()
        .map(::mapPayoutParcelableToPayoutDetailsModel)
        .inBackground()
        .asLiveData()

    fun backClicked() {
        router.back()
    }

    fun validatorExternalActionClicked() {
        val payload = ExternalAccountActions.Payload.fromAddress(payout.validatorInfo.address)

        externalAccountActions.showExternalActions(payload)
    }

    private suspend fun mapPayoutParcelableToPayoutDetailsModel(asset: Asset): PayoutDetailsModel {
        val tokenType = asset.token.type
        val rewardAmount = asset.token.amountFromPlanks(payout.amountInPlanks)

        val addressModel = with(payout.validatorInfo) {
            addressModelGenerator.createAddressModel(address, AddressIconGenerator.SIZE_SMALL, identityName)
        }

        return PayoutDetailsModel(
            validatorAddressModel = addressModel,
            createdAt = payout.createdAt,
            eraDisplay = resourceManager.getString(R.string.staking_era_index_no_prefix, payout.era.toLong()),
            reward = rewardAmount.formatTokenAmount(tokenType, precision = 7),
            rewardFiat = asset.token.fiatAmount(rewardAmount)?.formatAsCurrency()
        )
    }
}
