package jp.co.soramitsu.common.data.network.ton

import com.google.gson.annotations.SerializedName

data class TonAccountData (
    @SerializedName("address")
    val address: String,

    @SerializedName("balance")
    val balance: Long,

    /* unix timestamp */
    @SerializedName("last_activity")
    val lastActivity: Long,

    @SerializedName("status")
    val status: AccountStatus,

    @SerializedName("get_methods")
    val getMethods: List<String>,

    @SerializedName("is_wallet")
    val isWallet: Boolean,

    /* {'USD': 1, 'IDR': 1000} */
    @SerializedName("currencies_balance")
    val currenciesBalance: Map<String, Any>? = null,

    @SerializedName("interfaces")
    val interfaces: List<String>? = null,

    @SerializedName("name")
    val name: String? = null,

    @SerializedName("is_scam")
    val isScam: Boolean? = null,

    @SerializedName("icon")
    val icon: String? = null,

    @SerializedName("memo_required")
    val memoRequired: Boolean? = null,

    @SerializedName("is_suspended")
    val isSuspended: Boolean? = null
)

enum class AccountStatus(val value: String) {

    @SerializedName("nonexist")
    nonexist("nonexist"),

    @SerializedName("uninit")
    uninit("uninit"),

    @SerializedName("active")
    active("active"),

    @SerializedName("frozen")
    frozen("frozen")
}