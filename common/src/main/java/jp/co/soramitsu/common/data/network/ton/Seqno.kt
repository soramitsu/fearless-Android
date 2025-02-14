package jp.co.soramitsu.common.data.network.ton

import com.google.gson.annotations.SerializedName

data class Seqno (
    val seqno: Int
)

data class RawTime(
    val time: Int
)

data class PublicKeyResponse(
    @SerializedName("public_key")
    val publicKey: String
)