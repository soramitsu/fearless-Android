package jp.co.soramitsu.common.data.network.ton

import com.google.gson.annotations.SerializedName

data class EmulateMessageToWalletRequestParamsInner (

    @SerializedName("address")
    val address: String,

    @SerializedName("balance")
    val balance: Long? = null

)

data class EmulateMessageToWalletRequest (

    @SerializedName("boc")
    val boc: String,

    /* additional per account configuration */
    @SerializedName("params")
    val params: List<EmulateMessageToWalletRequestParamsInner>? = null,

    @SerializedName("safe_mode")
    val safeMode: Boolean? = null

)