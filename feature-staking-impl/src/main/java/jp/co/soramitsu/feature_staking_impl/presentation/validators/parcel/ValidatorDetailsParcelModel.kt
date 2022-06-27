package jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
class ValidatorDetailsParcelModel(
    val accountIdHex: String,
    val stake: ValidatorStakeParcelModel,
    val identity: IdentityParcelModel?,
) : Parcelable

@Parcelize
class CollatorDetailsParcelModel(
    val accountIdHex: String,
    val statusText: String,
    val statusColor: Int,
    val stake: ValidatorStakeParcelModel,
    val identity: IdentityParcelModel?,
) : Parcelable
