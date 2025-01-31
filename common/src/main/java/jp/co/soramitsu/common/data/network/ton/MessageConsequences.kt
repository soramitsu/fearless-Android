package jp.co.soramitsu.common.data.network.ton

import com.google.gson.annotations.SerializedName

data class MessageConsequences (

    @SerializedName("trace")
    val trace: Trace,

    @SerializedName("risk")
    val risk: Risk,

    @SerializedName("event")
    val event: AccountEvent

)

val MessageConsequences.totalFees: Long
    get() = trace.transaction.totalFees

data class Trace (

    @SerializedName("transaction")
    val transaction: Transaction,

    @SerializedName("interfaces")
    val interfaces: kotlin.collections.List<kotlin.String>,

    @SerializedName("children")
    val children: kotlin.collections.List<Trace>? = null,

    @SerializedName("emulated")
    val emulated: kotlin.Boolean? = null

)

data class Risk (

    /* transfer all the remaining balance of the wallet. */
    @SerializedName("transfer_all_remaining_balance")
    val transferAllRemainingBalance: kotlin.Boolean,

    @SerializedName("ton")
    val ton: kotlin.Long,

    @SerializedName("jettons")
    val jettons: kotlin.collections.List<JettonQuantity>,


)

data class JettonQuantity (

    @SerializedName("quantity")
    val quantity: kotlin.String,

    @SerializedName("wallet_address")
    val walletAddress: AccountAddress,

    @SerializedName("jetton")
    val jetton: JettonPreview

)