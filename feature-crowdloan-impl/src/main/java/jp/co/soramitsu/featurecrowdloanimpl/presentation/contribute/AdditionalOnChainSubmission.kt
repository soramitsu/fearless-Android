package jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute

import java.math.BigDecimal
import jp.co.soramitsu.featurecrowdloanimpl.di.customCrowdloan.CustomContributeManager
import jp.co.soramitsu.featurecrowdloanimpl.domain.contribute.AdditionalOnChainSubmission
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.BonusPayload

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
