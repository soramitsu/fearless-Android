package jp.co.soramitsu.common.data.network.iroha

import com.google.gson.JsonParseException
import com.google.gson.TypeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

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
    val hasMore: Boolean?,
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
    val hasMore: Boolean?,
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
    val aliasBinding: Map<String, Any?>? = null,
    @SerializedName("spec")
    val spec: IrohaAssetDefinitionSpec? = null
)

data class IrohaAssetDefinitionSpec(
    @SerializedName("scale")
    @field:JsonAdapter(CanonicalIrohaScaleAdapter::class)
    val scale: Int? = null
)

class CanonicalIrohaScaleAdapter : TypeAdapter<Int?>() {
    override fun read(reader: JsonReader): Int? {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return null
        }
        if (reader.peek() != JsonToken.NUMBER) {
            throw JsonParseException("Iroha asset scale must be a canonical JSON integer")
        }
        val literal = reader.nextString()
        return literal.takeIf(CANONICAL_SCALE::matches)?.toIntOrNull()
            ?: throw JsonParseException("Iroha asset scale is outside the canonical integer domain")
    }

    override fun write(writer: JsonWriter, value: Int?) {
        if (value == null) writer.nullValue() else writer.value(value)
    }

    private companion object {
        val CANONICAL_SCALE = Regex("^(0|[1-9][0-9]*)$")
    }
}

data class IrohaToriiFanoutStatus(
    val attempted: Int,
    val succeeded: Int,
    val failed: Int,
    val denied: Int,
    val unavailable: Int,
    val notFound: Int
) {
    val isValid: Boolean
        get() {
            if (
                attempted <= 0 ||
                succeeded < 0 ||
                failed < 0 ||
                denied < 0 ||
                unavailable < 0 ||
                notFound < 0 ||
                succeeded > attempted ||
                failed != attempted - succeeded ||
                denied > failed ||
                unavailable > failed - denied ||
                notFound > failed - denied - unavailable
            ) {
                return false
            }

            return true
        }

    val isComplete: Boolean
        get() = isValid && succeeded == attempted && failed == 0 &&
            denied == 0 && unavailable == 0 && notFound == 0
}

class IrohaToriiDegradedException(
    val fanout: IrohaToriiFanoutStatus
) : IllegalStateException(
    "Torii fanout is incomplete: ${fanout.succeeded}/${fanout.attempted} routes succeeded " +
        "(${fanout.failed} failed, ${fanout.denied} denied, ${fanout.unavailable} unavailable, " +
        "${fanout.notFound} not found)"
)

class IrohaToriiResponseException(message: String) : IllegalStateException(message)

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
    val kind: IrohaPipelineTransactionStatusKind,
    @SerializedName("block_height")
    val blockHeight: Long? = null,
    @SerializedName("rejection_reason")
    val rejectionReason: Any? = null
)

enum class IrohaPipelineTransactionStatusKind {
    @SerializedName("Queued")
    Queued,

    @SerializedName("Approved")
    Approved,

    @SerializedName("Committed")
    Committed,

    @SerializedName("Applied")
    Applied,

    @SerializedName("Rejected")
    Rejected,

    @SerializedName("Expired")
    Expired
}

data class IrohaSubmitAndWaitOutcome(
    val hash: String,
    val transactionHash: String,
    val receiptHash: String,
    val finalHash: String,
    val terminalKind: IrohaPipelineTransactionStatusKind,
    val terminalStatuses: List<IrohaPipelineTransactionStatusKind>,
    val attempts: Long,
    val elapsedMillis: Long,
    val rejectionReason: Any? = null
)

enum class IrohaSubmitAndWaitErrorCode {
    REJECTED,
    EXPIRED,
    TIMEOUT,
    RPC_ERROR,
    INVALID_RESPONSE
}

class IrohaSubmitAndWaitException(
    val code: IrohaSubmitAndWaitErrorCode,
    message: String,
    val details: Any? = null
) : IllegalStateException(message)

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
    val jsonrpc: String? = null,
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
