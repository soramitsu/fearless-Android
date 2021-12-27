package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.bifrost

import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.bifrost.BifrostContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.model.CustomContributePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.referral.ReferralCodePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.referral.ReferralContributeViewState

class BifrostContributeViewState(
    private val bifrostInteractor: BifrostContributeInteractor,
    customContributePayload: CustomContributePayload,
    resourceManager: ResourceManager,
) : ReferralContributeViewState(
    customContributePayload = customContributePayload,
    resourceManager = resourceManager,
) {

    override fun createBonusPayload(referralCode: String, email: String?, agreeReceiveEmail: Boolean?): ReferralCodePayload {
        return BifrostBonusPayload(
            referralCode,
            customContributePayload.paraId,
            customContributePayload.parachainMetadata.rewardRate
        )
    }

    override suspend fun validatePayload(payload: ReferralCodePayload) {
        if (bifrostInteractor.isCodeValid(payload.referralCode).not()) {
            throw IllegalArgumentException(resourceManager.getString(R.string.crowdloan_referral_code_invalid))
        }
    }
}
