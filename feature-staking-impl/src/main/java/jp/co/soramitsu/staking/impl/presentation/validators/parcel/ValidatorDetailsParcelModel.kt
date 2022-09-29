package jp.co.soramitsu.staking.impl.presentation.validators.parcel

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
class ValidatorDetailsParcelModel(
    val accountIdHex: String,
    val stake: ValidatorStakeParcelModel,
    val identity: IdentityParcelModel?
) : Parcelable

@Parcelize
class CollatorDetailsParcelModel(
    val accountIdHex: String,
    val stake: CollatorStakeParcelModel,
    val identity: IdentityParcelModel?,
    val request: String
) : Parcelable
