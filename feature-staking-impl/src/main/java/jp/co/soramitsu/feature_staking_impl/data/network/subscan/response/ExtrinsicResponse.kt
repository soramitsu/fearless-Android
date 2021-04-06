package jp.co.soramitsu.feature_staking_impl.data.network.subscan.response

import com.google.gson.annotations.SerializedName
import jp.co.soramitsu.common.data.network.subscan.CollectionContent

class ExtrinsicHistory(
    override val count: Int,
    @SerializedName("extrinsics")
    override val items: List<ExtrinsicRemote>?
) : CollectionContent<ExtrinsicRemote>

class ExtrinsicRemote(
    @SerializedName("block_timestamp")
    val blockTimestamp: Int,
    @SerializedName("block_num")
    val blockNum: Int,
    @SerializedName("extrinsic_index")
    val extrinsicIndex: String,
    @SerializedName("call_module_function")
    val callModuleFunction: String,
    @SerializedName("call_module")
    val callModule: String,
    val params: String,
    @SerializedName("account_id")
    val accountId: String,
    @SerializedName("account_index")
    val accountIndex: String,
    val signature: String,
    val nonce: Int,
    @SerializedName("extrinsic_hash")
    val extrinsicHash: String,
    val success: Boolean,
    val fee: String,
    @SerializedName("from_hex")
    val fromHex: String,
    val finalized: Boolean,
    @SerializedName("account_display")
    val accountDisplay: AccountDisplay
) {

    class AccountDisplay(
        val address: String,
        val display: String,
        val judgements: Any,
        @SerializedName("parent_display")
        val parentDisplay: String,
        val parent: Any?,
        @SerializedName("account_index")
        val accountIndex: String,
        val identity: Boolean
    )
}
