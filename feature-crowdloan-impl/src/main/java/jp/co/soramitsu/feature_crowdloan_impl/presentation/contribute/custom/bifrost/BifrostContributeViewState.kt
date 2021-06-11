package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.bifrost

import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.bifrost.BifrostContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.model.CustomContributePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.referral.ReferralCodePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.referral.ReferralContributeViewState

class BifrostContributeViewState(
    interactor: BifrostContributeInteractor,
    customContributePayload: CustomContributePayload,
    resourceManager: ResourceManager,
    termsLink: String,
    private val bifrostInteractor: BifrostContributeInteractor
) : ReferralContributeViewState(
    customContributePayload = customContributePayload,
    resourceManager = resourceManager,
    fearlessReferralCode = interactor.fearlessReferralCode,
    bonusPercentage = BIFROST_BONUS_MULTIPLIER,
    termsUrl = termsLink
) {

    override fun createBonusPayload(referralCode: String): ReferralCodePayload {
        return BifrostBonusPayload(
            referralCode,
            customContributePayload.paraId,
            customContributePayload.parachainMetadata.rewardRate
        )
    }

    override suspend fun validatePayload(payload: ReferralCodePayload) {
        if (bifrostInteractor.isCodeValid(payload.referralCode).not()) {
            throw IllegalArgumentException(resourceManager.getString(R.string.referral_code_is_invalid))
        }
    }
}
