package jp.co.soramitsu.staking.api.domain.model

import android.os.Parcelable
import java.math.BigInteger
import kotlinx.parcelize.Parcelize

@Parcelize
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
    val status: CandidateInfoStatus
) : Parcelable

sealed class CandidateInfoStatus : Parcelable {

    companion object {
        fun from(key: String?) = when (key) {
            "Active" -> ACTIVE
            "Leaving" -> LEAVING(null)
            "Idle" -> IDLE
            else -> EMPTY
        }
    }

    @Parcelize
    object ACTIVE : CandidateInfoStatus()

    @Parcelize
    object EMPTY : CandidateInfoStatus()

    @Parcelize
    class LEAVING(val leavingBlock: Long?) : CandidateInfoStatus()

    @Parcelize
    object IDLE : CandidateInfoStatus()
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
        val twitter: String
    ) {
        data class Display(val raw: String)
    }
}
