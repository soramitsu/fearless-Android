package jp.co.soramitsu.feature_wallet_impl.data.network.model.response

import com.google.gson.annotations.SerializedName

class SubscanResponse<T>(
    val code: Int,
    @SerializedName("data")
    val content: T?,
    @SerializedName("generated_at")
    val generatedAt: Long,
    val message: String
) {
    companion object {
        fun <T> createEmptyResponse() = SubscanResponse<T>(
            code = 200,
            content = null,
            generatedAt = System.currentTimeMillis(),
            message = ""
        )
    }
}