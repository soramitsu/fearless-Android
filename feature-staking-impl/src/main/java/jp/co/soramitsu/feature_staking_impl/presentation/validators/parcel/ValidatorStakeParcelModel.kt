package jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel

import android.os.Parcelable
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.feature_staking_api.domain.model.CandidateInfoStatus
import kotlinx.android.parcel.Parcelize

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
        val isOversubscribed: Boolean,
        val nominatorInfo: NominatorInfo? = null
    ) : ValidatorStakeParcelModel() {

        @Parcelize
        class NominatorInfo(val willBeRewarded: Boolean) : Parcelable
    }
}

@Parcelize
class CollatorStakeParcelModel(
    val status: CandidateInfoStatus,
    val selfBonded: BigInteger,
    val delegations: Int,
    val totalStake: BigInteger,
    val minBond: BigInteger,
    val estimatedRewards: BigDecimal?,
) : Parcelable
