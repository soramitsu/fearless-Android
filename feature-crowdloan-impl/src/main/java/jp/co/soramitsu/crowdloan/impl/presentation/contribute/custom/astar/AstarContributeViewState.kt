package jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.astar

import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.crowdloan.impl.data.network.api.parachain.FLOW_BONUS_RATE
import jp.co.soramitsu.crowdloan.impl.domain.contribute.custom.astar.AstarContributeInteractor
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.ApplyActionState
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.model.CustomContributePayload
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.referral.ReferralCodePayload
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.referral.ReferralContributeViewState
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.select.parcel.getAsBigDecimal
import kotlinx.coroutines.flow.map

class AstarContributeViewState(
    private val interactor: AstarContributeInteractor,
    customContributePayload: CustomContributePayload,
    resourceManager: ResourceManager
) : ReferralContributeViewState(
    customContributePayload = customContributePayload,
    resourceManager = resourceManager
) {

    override val applyActionState = enteredReferralCodeFlow.map { referral ->
        when {
            referral.isEmpty() -> ApplyActionState.Unavailable(reason = resourceManager.getString(R.string.crowdloan_enter_referral_address))
            else -> ApplyActionState.Available
        }
    }

    override fun createBonusPayload(referralCode: String, email: String?, agreeReceiveEmail: Boolean?): ReferralCodePayload = AstarBonusPayload(
        referralCode,
        customContributePayload.chainId,
        customContributePayload.paraId,
        customContributePayload.parachainMetadata.rewardRate,
        customContributePayload.parachainMetadata.flow?.data?.getAsBigDecimal(FLOW_BONUS_RATE)
    )

    override suspend fun validatePayload(payload: ReferralCodePayload) {
        require(payload is AstarBonusPayload)

        val isReferralValid = interactor.isReferralValid(payload.referralCode, payload.chainId)

        if (!isReferralValid) throw IllegalArgumentException(resourceManager.getString(R.string.crowdloan_astar_referral_code_invalid))
    }
}
