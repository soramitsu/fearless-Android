package jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
class IdentityParcelModel(
    val display: String?,
    val legal: String?,
    val web: String?,
    val riot: String?,
    val email: String?,
    val pgpFingerprint: String?,
    val image: String?,
    val twitter: String?
) : Parcelable