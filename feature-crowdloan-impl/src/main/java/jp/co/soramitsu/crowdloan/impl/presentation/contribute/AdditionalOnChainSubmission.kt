package jp.co.soramitsu.crowdloan.impl.presentation.contribute

import java.math.BigDecimal
import jp.co.soramitsu.crowdloan.impl.di.customCrowdloan.CustomContributeManager
import jp.co.soramitsu.crowdloan.impl.domain.contribute.AdditionalOnChainSubmission
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.BonusPayload

fun additionalOnChainSubmission(
    bonusPayload: BonusPayload,
    customFlow: String,
    amount: BigDecimal,
    contributeManager: CustomContributeManager
): AdditionalOnChainSubmission {
    val submitter = contributeManager.getSubmitter(customFlow)

    return {
        submitter.submitOnChain(bonusPayload, amount, this)
    }
}
