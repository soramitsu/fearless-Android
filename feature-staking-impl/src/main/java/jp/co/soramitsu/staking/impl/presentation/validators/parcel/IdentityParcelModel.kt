package jp.co.soramitsu.staking.impl.presentation.validators.parcel

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

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
