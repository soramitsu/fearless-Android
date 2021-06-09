package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute

import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.CustomContributeManager
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.AdditionalOnChainSubmission
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.BonusPayload
import java.math.BigDecimal

fun additionalSubmission(
    bonusPayload: BonusPayload,
    customFlow: String,
    amount: BigDecimal,
    contributeManager: CustomContributeManager
) : AdditionalOnChainSubmission {
    val submitter = contributeManager.getSubmitter(customFlow)

    return {
        submitter.addOnChainSubmission(bonusPayload, amount, this)
    }
}
