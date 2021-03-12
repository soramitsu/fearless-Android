package jp.co.soramitsu.feature_staking_api.domain.model

import jp.co.soramitsu.fearless_utils.runtime.AccountId
import java.math.BigInteger

class Nominations(
    val targets: List<AccountId>,
    val submittedInEra: BigInteger,
    val suppressed: Boolean
)