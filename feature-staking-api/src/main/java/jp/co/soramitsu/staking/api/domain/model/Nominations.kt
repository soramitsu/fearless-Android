package jp.co.soramitsu.staking.api.domain.model

import jp.co.soramitsu.shared_utils.runtime.AccountId

class Nominations(
    val targets: List<AccountId>,
    val submittedInEra: EraIndex,
    val suppressed: Boolean
)
