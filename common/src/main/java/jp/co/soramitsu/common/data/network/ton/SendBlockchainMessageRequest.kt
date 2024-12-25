package jp.co.soramitsu.common.data.network.ton

import com.google.gson.annotations.SerializedName

data class SendBlockchainMessageRequest (

    @SerializedName("boc")
    val boc: String? = null,

    @SerializedName("batch")
    val batch: List<String>? = null

)