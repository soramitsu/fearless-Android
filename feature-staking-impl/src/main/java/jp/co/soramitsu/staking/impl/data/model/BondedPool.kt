package jp.co.soramitsu.staking.impl.data.model

import jp.co.soramitsu.shared_utils.runtime.AccountId
import java.math.BigInteger

data class BondedPool(
    val points: BigInteger,
    val state: BondedPoolState,
    val memberCounter: BigInteger,
    val depositor: AccountId,
    val root: AccountId?,
    val nominator: AccountId?,
    val bouncer: AccountId?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BondedPool

        if (points != other.points) return false
        if (state != other.state) return false
        if (memberCounter != other.memberCounter) return false
        if (!depositor.contentEquals(other.depositor)) return false
        if (!root.contentEquals(other.root)) return false
        if (!nominator.contentEquals(other.nominator)) return false
        if (!bouncer.contentEquals(other.bouncer)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = points.hashCode()
        result = 31 * result + state.hashCode()
        result = 31 * result + memberCounter.hashCode()
        result = 31 * result + depositor.contentHashCode()
        result = 31 * result + root.contentHashCode()
        result = 31 * result + nominator.contentHashCode()
        result = 31 * result + bouncer.contentHashCode()
        return result
    }
}

enum class BondedPoolState {
    Open, Blocked, Destroying;

    companion object {
        fun from(value: String) = when (value) {
            "Open" -> Open
            "Blocked" -> Blocked
            "Destroying" -> Destroying
            else -> error("Nomination pool state cannot be parsed")
        }
    }
}
