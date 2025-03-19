package jp.co.soramitsu.common.data.network.ton

import com.google.gson.annotations.SerializedName

data class TonAppManifest(
    @SerializedName("url")
    val url: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("iconUrl")
    val iconUrl: String,
    @SerializedName("termsOfUseUrl")
    val termsOfUseUrl: String?,
    @SerializedName("privacyPolicyUrl")
    val privacyPolicyUrl: String?
)
