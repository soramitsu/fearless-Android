package jp.co.soramitsu.tonconnect.api.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class AppEntity(
    val url: String,
    val name: String,
    val iconUrl: String,
    val termsOfUseUrl: String?,
    val privacyPolicyUrl: String?
) : Parcelable
