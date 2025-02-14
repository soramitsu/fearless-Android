package jp.co.soramitsu.common.data.network.ton

import com.google.gson.annotations.SerializedName

data class JettonTransferPayloadRemote (

    /* hex-encoded BoC */
    @SerializedName("custom_payload")
    val customPayload: String? = null,

    /* hex-encoded BoC */
    @SerializedName("state_init")
    val stateInit: String? = null
)

