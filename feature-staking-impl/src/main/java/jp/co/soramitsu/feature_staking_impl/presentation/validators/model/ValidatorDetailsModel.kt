package jp.co.soramitsu.feature_staking_impl.presentation.validators.model

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
class ValidatorDetailsModel(
    val identity: IdentityModel?,
    val nominators: List<NominatorModel>,
    val apy: String
) : Parcelable