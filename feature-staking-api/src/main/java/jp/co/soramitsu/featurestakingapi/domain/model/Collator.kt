package jp.co.soramitsu.featurestakingapi.domain.model

import java.math.BigDecimal
import java.math.BigInteger

class Collator(
    override val address: String,
    val bond: BigInteger, // collator's own stake in sorting
    val delegationCount: BigInteger, // delegations in sorting
    val totalCounted: BigInteger, // effective amount bonded in sorting
    val lowestTopDelegationAmount: BigInteger, // minimum bond in sorting
    val highestBottomDelegationAmount: BigInteger,
    val lowestBottomDelegationAmount: BigInteger,
    val topCapacity: CandidateCapacity,
    val bottomCapacity: CandidateCapacity,
    val request: String?,
    val status: CandidateInfoStatus,
    val identity: Identity?,
    val apy: BigDecimal?
) : WithAddress
