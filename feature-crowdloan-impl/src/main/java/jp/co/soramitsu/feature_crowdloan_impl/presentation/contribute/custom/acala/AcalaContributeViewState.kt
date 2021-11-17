package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.acala

import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.parachain.FLOW_API_URL
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.acala.AcalaContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.ApplyActionState
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.model.CustomContributePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.referral.ReferralCodePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.referral.ReferralContributeViewState
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.parcel.getString
import kotlinx.coroutines.flow.combine

class AcalaContributeViewState(
    private val interactor: AcalaContributeInteractor,
    customContributePayload: CustomContributePayload,
    resourceManager: ResourceManager
) : ReferralContributeViewState(
    customContributePayload = customContributePayload,
    resourceManager = resourceManager,
) {

    init {
        (previousPayload() as? AcalaBonusPayload)?.let {
            it.email?.let { email ->
                enteredEmailFlow.value = email
            }
            emailAgreedFlow.value = it.agreeReceiveEmail == true
        }
    }

    override val applyActionState = enteredReferralCodeFlow.combine(emailValidationFlow) { referral, emailValid ->
        when {
            referral.isEmpty() -> ApplyActionState.Unavailable(reason = resourceManager.getString(R.string.crowdloan_enter_referral))
            emailValid.not() -> ApplyActionState.Unavailable(reason = resourceManager.getString(R.string.crowdloan_empty_email_title))
            else -> ApplyActionState.Available
        }
    }

    override fun createBonusPayload(referralCode: String, email: String?, agreeReceiveEmail: Boolean?): ReferralCodePayload {
        return AcalaBonusPayload(
            referralCode = referralCode,
            rewardRate = customContributePayload.parachainMetadata.rewardRate,
            email = email,
            agreeReceiveEmail = agreeReceiveEmail,
            contributionType = null,
            parachainId = customContributePayload.paraId,
            baseUrl = customContributePayload.parachainMetadata.flow?.data?.getString(FLOW_API_URL)!!
        )
    }

    override suspend fun validatePayload(payload: ReferralCodePayload) {
        val apiUrl = customContributePayload.parachainMetadata.flow?.data?.getString(FLOW_API_URL)!!
        val isReferralValid = interactor.isReferralValid(payload.referralCode, apiUrl)

        if (!isReferralValid) throw IllegalArgumentException(resourceManager.getString(R.string.crowdloan_acala_referral_code_invalid))
    }
}
