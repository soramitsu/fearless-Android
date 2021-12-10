package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.interlay

import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.parachain.FLOW_BONUS_RATE
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.interlay.InterlayContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.ApplyActionState
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.model.CustomContributePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.referral.ReferralCodePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.referral.ReferralContributeViewState
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.parcel.getAsBigDecimal
import kotlinx.coroutines.flow.map

class InterlayContributeViewState(
    private val interactor: InterlayContributeInteractor,
    customContributePayload: CustomContributePayload,
    resourceManager: ResourceManager
) : ReferralContributeViewState(
    customContributePayload = customContributePayload,
    resourceManager = resourceManager,
) {

    override val applyActionState = enteredReferralCodeFlow.map { referral ->
        when {
            referral.isEmpty() -> ApplyActionState.Unavailable(reason = resourceManager.getString(R.string.crowdloan_enter_referral_address))
            else -> ApplyActionState.Available
        }
    }

    override fun createBonusPayload(referralCode: String, email: String?, agreeReceiveEmail: Boolean?) =
        InterlayBonusPayload(
            referralCode = referralCode,
            parachainId = customContributePayload.paraId,
            rewardRate = customContributePayload.parachainMetadata.rewardRate,
            bonusRate = customContributePayload.parachainMetadata.flow?.data?.getAsBigDecimal(FLOW_BONUS_RATE)
        )

    override suspend fun validatePayload(payload: ReferralCodePayload) {
        val isReferralValid = interactor.isReferralValid(payload.referralCode)

        if (!isReferralValid) throw IllegalArgumentException(resourceManager.getString(R.string.crowdloan_astar_referral_code_invalid))
    }
}
