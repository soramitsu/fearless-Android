package jp.co.soramitsu.feature_staking_api.domain.model

import java.math.BigInteger

class Collator(
    val address: String,
    val bond: BigInteger,
    val delegationCount: BigInteger,
    val totalCounted: BigInteger,
    val lowestTopDelegationAmount: BigInteger,
    val highestBottomDelegationAmount: BigInteger,
    val lowestBottomDelegationAmount: BigInteger,
    val topCapacity: CandidateCapacity,
    val bottomCapacity: CandidateCapacity,
    val request: String?,
    val status: CandidateInfoStatus,
    val identity: Identity?,
    val minFromTopDelegations: BigInteger,
)
