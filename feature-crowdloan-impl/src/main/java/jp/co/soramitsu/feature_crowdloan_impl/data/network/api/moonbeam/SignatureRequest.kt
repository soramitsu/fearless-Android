package jp.co.soramitsu.feature_crowdloan_impl.data.network.api.moonbeam

import com.google.gson.annotations.SerializedName

class SignatureRequest(
    val address: String,
    val contribution: String,
    @SerializedName("previous-total-contribution")
    val previousTotalContribution: String
)
