package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.acala

import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.acala.AcalaContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.ApplyActionState
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.model.CustomContributePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.referral.ReferralCodePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.referral.ReferralContributeViewState
import kotlinx.coroutines.flow.combine

class AcalaContributeViewState(
    private val interactor: AcalaContributeInteractor,
    customContributePayload: CustomContributePayload,
    resourceManager: ResourceManager
) : ReferralContributeViewState(
    customContributePayload = customContributePayload,
    resourceManager = resourceManager,
) {

    override val applyActionState = enteredReferralCodeFlow.combine(emailValidationFlow) { referral, emailValid ->
        when {
            referral.isEmpty() -> ApplyActionState.Unavailable(reason = resourceManager.getString(R.string.crowdloan_enter_referral))
            emailValid.not() -> ApplyActionState.Unavailable(reason = resourceManager.getString(R.string.crowdloan_empty_email_title))
            else -> ApplyActionState.Available
        }
    }

    override fun createBonusPayload(referralCode: String, email: String?): ReferralCodePayload {
        return AcalaBonusPayload(referralCode, customContributePayload.parachainMetadata.rewardRate, email)
    }

    override suspend fun validatePayload(payload: ReferralCodePayload) {
        val isReferralValid = interactor.isReferralValid(payload.referralCode)

        if (!isReferralValid) throw IllegalArgumentException(resourceManager.getString(R.string.crowdloan_referral_code_invalid))
    }
}
