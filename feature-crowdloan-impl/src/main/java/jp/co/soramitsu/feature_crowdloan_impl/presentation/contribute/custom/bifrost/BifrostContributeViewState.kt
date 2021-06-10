package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.bifrost

import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.bifrost.BifrostContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.BonusPayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.model.CustomContributePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.referral.ReferralContributeViewState

class BifrostContributeViewState(
    interactor: BifrostContributeInteractor,
    customContributePayload: CustomContributePayload,
    resourceManager: ResourceManager,
    termsLink: String
) : ReferralContributeViewState(
    customContributePayload = customContributePayload,
    resourceManager = resourceManager,
    fearlessReferralCode = interactor.fearlessReferralCode,
    bonusPercentage = BIFROST_BONUS_MULTIPLIER,
    termsUrl = termsLink
) {

    override fun createBonusPayload(referralCode: String): BonusPayload {
        return BifrostBonusPayload(
            referralCode,
            customContributePayload.paraId,
            customContributePayload.parachainMetadata.rewardRate
        )
    }

    override suspend fun validatePayload(payload: BonusPayload) {
        // no validations
    }
}
