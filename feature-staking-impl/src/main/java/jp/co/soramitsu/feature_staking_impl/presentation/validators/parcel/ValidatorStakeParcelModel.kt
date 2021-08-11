package jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel

import android.os.Parcelable
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import kotlinx.android.parcel.Parcelize
import java.math.BigDecimal
import java.math.BigInteger

sealed class ValidatorStakeParcelModel : Parcelable {

    @Parcelize
    object Inactive : ValidatorStakeParcelModel()

    @Parcelize
    class Active(
        val totalStake: BigInteger,
        val ownStake: BigInteger,
        val nominators: List<NominatorParcelModel>,
        val apy: BigDecimal,
        val isSlashed: Boolean,
        val maxNominators: Int = Int.MAX_VALUE,
        val isNominated: Boolean = false,
    ) : ValidatorStakeParcelModel() {

        fun isInLimit(address: String): Boolean {
            val indexOfCurrentAccount = nominators.sortedBy { it.value }.indexOfFirst { it.who.contentEquals(address.toAccountId()) }
            return indexOfCurrentAccount < maxNominators
        }

        fun isOversubscribed() = nominators.size > maxNominators
    }
}
