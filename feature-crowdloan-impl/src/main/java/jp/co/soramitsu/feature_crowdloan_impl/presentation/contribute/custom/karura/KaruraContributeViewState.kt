package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.karura

import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.karura.KaruraContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.model.CustomContributePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.referral.ReferralCodePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.referral.ReferralContributeViewState

class KaruraContributeViewState(
    private val interactor: KaruraContributeInteractor,
    customContributePayload: CustomContributePayload,
    resourceManager: ResourceManager
) : ReferralContributeViewState(
    customContributePayload = customContributePayload,
    resourceManager = resourceManager,
    fearlessReferralCode = interactor.fearlessReferralCode,
    bonusPercentage = KARURA_BONUS_MULTIPLIER
) {

    override fun createBonusPayload(referralCode: String): ReferralCodePayload {
        return KaruraBonusPayload(referralCode, customContributePayload.parachainMetadata.rewardRate)
    }

    override suspend fun validatePayload(payload: ReferralCodePayload) {
        val isReferralValid = interactor.isReferralValid(payload.referralCode)

        if (!isReferralValid) throw IllegalArgumentException(resourceManager.getString(R.string.crowdloan_referral_code_invalid))
    }
}
