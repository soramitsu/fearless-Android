package jp.co.soramitsu.feature_crowdloan_impl.data.network.api.moonbeam

import com.google.gson.annotations.SerializedName

class RemarkStoreResponse(
    val address: String,
    @SerializedName("signed-message")
    val signedMessage: String,
    val remark: String
)
