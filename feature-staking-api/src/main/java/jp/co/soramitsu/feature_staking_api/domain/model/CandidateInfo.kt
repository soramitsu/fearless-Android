package jp.co.soramitsu.feature_staking_api.domain.model

import java.math.BigInteger

class CandidateInfo(
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
)

enum class CandidateInfoStatus {
    Active, Empty;

    companion object {
        fun from(key: String?) = when (key) {
            "Active" -> Active
            else -> Empty
        }
    }
}

enum class CandidateCapacity {
    Full, Partial, Empty;

    companion object {
        fun from(key: String?) = when (key) {
            "Full" -> Full
            "Partial" -> Partial
            else -> Empty
        }
    }
}

data class CollatorIdentity(val info: Info) {
    data class Info(
        val display: Display,
        val legal: String,
        val web: String,
        val riot: String,
        val email: String,
        val image: String,
        val twitter: String,
    ) {
        data class Display(val raw: String)
    }
}
