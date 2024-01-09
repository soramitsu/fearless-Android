package jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.interlay

import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.crowdloan.impl.domain.contribute.custom.interlay.InterlayContributeInteractor
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.model.CustomContributePayload
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.referral.ReferralCodePayload
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.referral.ReferralContributeViewState
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.shared_utils.extensions.requireHexPrefix

class InterlayContributeViewState(
    private val interactor: InterlayContributeInteractor,
    customContributePayload: CustomContributePayload,
    resourceManager: ResourceManager
) : ReferralContributeViewState(
    customContributePayload = customContributePayload,
    resourceManager = resourceManager
) {

    override fun createBonusPayload(referralCode: String, email: String?, agreeReceiveEmail: Boolean?) =
        InterlayBonusPayload(
            referralCode = referralCode.requireHexPrefix(),
            parachainId = customContributePayload.paraId
        )

    override suspend fun validatePayload(payload: ReferralCodePayload) {
        val isReferralValid = interactor.isReferralValid(payload.referralCode)

        if (!isReferralValid) throw IllegalArgumentException(resourceManager.getString(R.string.crowdloan_referral_code_invalid))
    }
}
