package jp.co.soramitsu.common.data.network.iroha

import com.google.gson.annotations.SerializedName

data class IrohaPageMetadata(
    @SerializedName("has_more")
    val hasMore: Boolean,
    @SerializedName("count_mode")
    val countMode: String,
    @SerializedName("total")
    val total: Long? = null,
    @SerializedName("indexed_height")
    val indexedHeight: Long? = null,
    @SerializedName("indexed_block_hash")
    val indexedBlockHash: String? = null,
    @SerializedName("query_source")
    val querySource: String? = null
)

data class IrohaAccountListResponse(
    @SerializedName("items")
    val items: List<IrohaAccountListItem> = emptyList(),
    @SerializedName("has_more")
    val hasMore: Boolean,
    @SerializedName("count_mode")
    val countMode: String,
    @SerializedName("total")
    val total: Long? = null
)

data class IrohaAccountListItem(
    @SerializedName("id")
    val id: String,
    @SerializedName("primary_alias")
    val primaryAlias: String? = null,
    @SerializedName("primary_alias_name")
    val primaryAliasName: String? = null,
    @SerializedName("primary_alias_dataspace")
    val primaryAliasDataspace: String? = null,
    @SerializedName("primary_alias_domain")
    val primaryAliasDomain: String? = null,
    @SerializedName("has_primary_alias")
    val hasPrimaryAlias: Boolean? = null
)

data class IrohaAccountAssetListResponse(
    @SerializedName("items")
    val items: List<IrohaAccountAssetListItem> = emptyList(),
    @SerializedName("has_more")
    val hasMore: Boolean,
    @SerializedName("count_mode")
    val countMode: String,
    @SerializedName("total")
    val total: Long? = null
)

data class IrohaAccountAssetListItem(
    @SerializedName("account_id")
    val accountId: String? = null,
    @SerializedName("asset")
    val asset: String,
    @SerializedName("asset_id")
    val assetId: String? = null,
    @SerializedName("asset_name")
    val assetName: String? = null,
    @SerializedName("asset_alias")
    val assetAlias: String? = null,
    @SerializedName("quantity")
    val quantity: String,
    @SerializedName("scope")
    val scope: String? = null
)

data class IrohaAssetDefinitionListResponse(
    @SerializedName("items")
    val items: List<IrohaAssetDefinitionListItem> = emptyList(),
    @SerializedName("has_more")
    val hasMore: Boolean,
    @SerializedName("count_mode")
    val countMode: String,
    @SerializedName("total")
    val total: Long? = null
)

data class IrohaAssetDefinitionListItem(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("alias")
    val alias: String? = null,
    @SerializedName("owned_by")
    val ownedBy: String? = null,
    @SerializedName("metadata")
    val metadata: Map<String, Any?>? = null,
    @SerializedName("alias_binding")
    val aliasBinding: Map<String, Any?>? = null
)

data class IrohaTransactionSubmissionReceipt(
    @SerializedName("payload")
    val payload: IrohaTransactionSubmissionPayload,
    @SerializedName("signature")
    val signature: Any? = null
)

data class IrohaTransactionSubmissionPayload(
    @SerializedName("tx_hash")
    val txHash: String,
    @SerializedName("entrypoint_hash")
    val entrypointHash: String,
    @SerializedName("signed_transaction_hash")
    val signedTransactionHash: String? = null,
    @SerializedName("submitted_at_ms")
    val submittedAtMs: Long,
    @SerializedName("submitted_at_height")
    val submittedAtHeight: Long,
    @SerializedName("signer")
    val signer: Any? = null
)

data class IrohaPipelineTransactionStatusResponse(
    @SerializedName("hash")
    val hash: String,
    @SerializedName("status")
    val status: IrohaPipelineTransactionStatus,
    @SerializedName("scope")
    val scope: String,
    @SerializedName("resolved_from")
    val resolvedFrom: String
)

data class IrohaPipelineTransactionStatus(
    @SerializedName("kind")
    val kind: String,
    @SerializedName("block_height")
    val blockHeight: Long? = null,
    @SerializedName("rejection_reason")
    val rejectionReason: Any? = null
)

data class IrohaErrorEnvelope(
    @SerializedName("code")
    val code: String,
    @SerializedName("message")
    val message: String,
    @SerializedName("details")
    val details: Any? = null
)

data class IrohaMcpJsonRpcRequest(
    @SerializedName("jsonrpc")
    val jsonrpc: String = "2.0",
    @SerializedName("id")
    val id: String,
    @SerializedName("method")
    val method: String,
    @SerializedName("params")
    val params: Map<String, Any?>? = null
)

data class IrohaMcpJsonRpcResponse(
    @SerializedName("jsonrpc")
    val jsonrpc: String = "2.0",
    @SerializedName("id")
    val id: String? = null,
    @SerializedName("result")
    val result: Any? = null,
    @SerializedName("error")
    val error: IrohaMcpJsonRpcError? = null
)

data class IrohaMcpJsonRpcError(
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: Any? = null
)
