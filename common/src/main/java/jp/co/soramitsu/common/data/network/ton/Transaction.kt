package jp.co.soramitsu.common.data.network.ton

import com.google.gson.annotations.SerializedName
import java.math.BigInteger

data class Transaction (

    @SerializedName("hash")
    val hash: kotlin.String? = null,

    @SerializedName("lt")
    val lt: kotlin.Long,

    @SerializedName("account")
    val account: AccountAddress,

    @SerializedName("success")
    val success: kotlin.Boolean,

    @SerializedName("utime")
    val utime: kotlin.Long,

    @SerializedName("orig_status")
    val origStatus: AccountStatus,

    @SerializedName("end_status")
    val endStatus: AccountStatus,

    @SerializedName("total_fees")
    val totalFees: BigInteger,

    @SerializedName("end_balance")
    val endBalance: kotlin.Long,

    @SerializedName("transaction_type")
    val transactionType: String,

    @SerializedName("state_update_old")
    val stateUpdateOld: kotlin.String,

    @SerializedName("state_update_new")
    val stateUpdateNew: kotlin.String,

    @SerializedName("out_msgs")
    val outMsgs: kotlin.collections.List<Message>,

    @SerializedName("block")
    val block: kotlin.String,

    @SerializedName("aborted")
    val aborted: kotlin.Boolean,

    @SerializedName("destroyed")
    val destroyed: kotlin.Boolean,

    /* hex encoded boc with raw transaction */
    @SerializedName("raw")
    val raw: kotlin.String? = null,

    @SerializedName("in_msg")
    val inMsg: Message? = null,

    @SerializedName("prev_trans_hash")
    val prevTransHash: kotlin.String? = null,

    @SerializedName("prev_trans_lt")
    val prevTransLt: kotlin.Long? = null,

    @SerializedName("compute_phase")
    val computePhase: ComputePhase? = null,

    @SerializedName("storage_phase")
    val storagePhase: StoragePhase? = null,

    @SerializedName("credit_phase")
    val creditPhase: CreditPhase? = null,

    @SerializedName("action_phase")
    val actionPhase: ActionPhase? = null,

    @SerializedName("bounce_phase")
    val bouncePhase: String? = null

)

data class Message (

    @SerializedName("msg_type")
    val msgType: String,

    @SerializedName("created_lt")
    val createdLt: kotlin.Long,

    @SerializedName("ihr_disabled")
    val ihrDisabled: kotlin.Boolean,

    @SerializedName("bounce")
    val bounce: kotlin.Boolean,

    @SerializedName("bounced")
    val bounced: kotlin.Boolean,

    @SerializedName("value")
    val `value`: kotlin.Long,

    @SerializedName("fwd_fee")
    val fwdFee: kotlin.Long,

    @SerializedName("ihr_fee")
    val ihrFee: kotlin.Long,

    @SerializedName("import_fee")
    val importFee: kotlin.Long,

    @SerializedName("created_at")
    val createdAt: kotlin.Long,

    @SerializedName("hash")
    val hash: kotlin.String? = null,

    @SerializedName("destination")
    val destination: AccountAddress? = null,

    @SerializedName("source")
    val source: AccountAddress? = null,

    @SerializedName("op_code")
    val opCode: kotlin.String? = null,

    @SerializedName("init")
    val `init`: StateInit? = null,

    /* hex-encoded BoC with raw message body */
    @SerializedName("raw_body")
    val rawBody: kotlin.String? = null,

    @SerializedName("decoded_op_name")
    val decodedOpName: kotlin.String? = null,

    @SerializedName("decoded_body")
    val decodedBody: kotlin.Any? = null

)

data class StateInit (

    @SerializedName("boc")
    val boc: kotlin.String,

    @SerializedName("interfaces")
    val interfaces: kotlin.collections.List<kotlin.String>? = null

)

data class ComputePhase (

    @SerializedName("skipped")
    val skipped: kotlin.Boolean,

    @SerializedName("skip_reason")
    val skipReason: String? = null,

    @SerializedName("success")
    val success: kotlin.Boolean? = null,

    @SerializedName("gas_fees")
    val gasFees: kotlin.Long? = null,

    @SerializedName("gas_used")
    val gasUsed: kotlin.Long? = null,

    @SerializedName("vm_steps")
    val vmSteps: kotlin.Int? = null,

    @SerializedName("exit_code")
    val exitCode: kotlin.Int? = null,

    @SerializedName("exit_code_description")
    val exitCodeDescription: kotlin.String? = null

)

data class StoragePhase (

    @SerializedName("fees_collected")
    val feesCollected: kotlin.Long,

    @SerializedName("status_change")
    val statusChange: String,

    @SerializedName("fees_due")
    val feesDue: kotlin.Long? = null

)

data class CreditPhase (

    @SerializedName("fees_collected")
    val feesCollected: kotlin.Long,

    @SerializedName("credit")
    val credit: kotlin.Long

)


data class ActionPhase (

    @SerializedName("success")
    val success: kotlin.Boolean,

    @SerializedName("result_code")
    val resultCode: kotlin.Int,

    @SerializedName("total_actions")
    val totalActions: kotlin.Int,

    @SerializedName("skipped_actions")
    val skippedActions: kotlin.Int,

    @SerializedName("fwd_fees")
    val fwdFees: kotlin.Long,

    @SerializedName("total_fees")
    val totalFees: kotlin.Long,

    @SerializedName("result_code_description")
    val resultCodeDescription: kotlin.String? = null

)

