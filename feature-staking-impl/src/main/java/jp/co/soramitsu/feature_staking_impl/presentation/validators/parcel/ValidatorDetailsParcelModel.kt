package jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import java.math.BigDecimal
import java.math.BigInteger

@Parcelize
class ValidatorDetailsParcelModel(
    val accountIdHex: String,
    val totalStake: BigInteger,
    val ownStake: BigInteger,
    val identity: IdentityParcelModel?,
    val nominators: List<NominatorParcelModel>,
    val apy: BigDecimal
) : Parcelable