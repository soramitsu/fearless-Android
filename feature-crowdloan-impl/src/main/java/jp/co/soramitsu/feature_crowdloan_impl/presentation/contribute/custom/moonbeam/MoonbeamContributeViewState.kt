package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.moonbeam

import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.moonbeam.MoonbeamContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.acala.AcalaBonusPayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.model.CustomContributePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.referral.ReferralCodePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.referral.ReferralContributeViewState

class MoonbeamContributeViewState(
    private val interactor: MoonbeamContributeInteractor,
    customContributePayload: CustomContributePayload,
    resourceManager: ResourceManager
) : ReferralContributeViewState(
    customContributePayload = customContributePayload,
    resourceManager = resourceManager,
    fearlessReferralCode = interactor.fearlessReferralCode,
    bonusPercentage = MOONBEAM_BONUS_MULTIPLIER
) {

    override fun createBonusPayload(referralCode: String): ReferralCodePayload {
        return AcalaBonusPayload(referralCode, customContributePayload.parachainMetadata.rewardRate)
    }

    override suspend fun validatePayload(payload: ReferralCodePayload) {
    }
}
