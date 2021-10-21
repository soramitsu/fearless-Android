package jp.co.soramitsu.feature_crowdloan_impl.data.network.api.moonbeam

import com.google.gson.annotations.SerializedName

class SignatureResponse(
    val address: String,
    val contribution: String,
    @SerializedName("previous-total-contribution")
    val previousTotalContribution: String,
    val signature: String,
    @SerializedName("time-stamp") //yyyy-mm-ddThh:mm:ss.ffffff
    val timeStamp: String,
)
