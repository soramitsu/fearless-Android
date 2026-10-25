package jp.co.soramitsu.common.data.network.bitcoin

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class BitcoinEsploraStats(
    @SerializedName("funded_txo_count")
    val fundedTxoCount: Int,
    @SerializedName("funded_txo_sum")
    val fundedTxoSum: Long,
    @SerializedName("spent_txo_count")
    val spentTxoCount: Int,
    @SerializedName("spent_txo_sum")
    val spentTxoSum: Long,
    @SerializedName("tx_count")
    val txCount: Int
)

data class BitcoinEsploraAddress(
    @SerializedName("address")
    val address: String,
    @SerializedName("chain_stats")
    val chainStats: BitcoinEsploraStats,
    @SerializedName("mempool_stats")
    val mempoolStats: BitcoinEsploraStats
) {
    val confirmedSats: Long
        get() = chainStats.fundedTxoSum - chainStats.spentTxoSum

    val mempoolSats: Long
        get() = mempoolStats.fundedTxoSum - mempoolStats.spentTxoSum

    val totalSats: Long
        get() = confirmedSats + mempoolSats
}

data class BitcoinEsploraTxStatus(
    @SerializedName("confirmed")
    val confirmed: Boolean,
    @SerializedName("block_hash")
    val blockHash: String? = null,
    @SerializedName("block_height")
    val blockHeight: Long? = null,
    @SerializedName("block_time")
    val blockTime: Long? = null
)

data class BitcoinEsploraUtxo(
    @SerializedName("txid")
    val txid: String,
    @SerializedName("vout")
    val vout: Long,
    @SerializedName("value")
    val value: Long,
    @SerializedName("status")
    val status: BitcoinEsploraTxStatus
)

data class BitcoinEsploraTransaction(
    @SerializedName("txid")
    val txid: String,
    @SerializedName("status")
    val status: BitcoinEsploraTxStatus,
    @SerializedName("fee")
    val fee: Long? = null,
    @SerializedName("weight")
    val weight: Long? = null,
    @SerializedName("size")
    val size: Long? = null,
    @SerializedName("version")
    val version: Long? = null,
    @SerializedName("locktime")
    val locktime: Long? = null,
    @SerializedName("vin")
    val vin: List<BitcoinEsploraTransactionInput>? = null,
    @SerializedName("vout")
    val vout: List<BitcoinEsploraTransactionOutput>? = null
)

data class BitcoinEsploraTransactionInput(
    @SerializedName("prevout")
    val prevout: BitcoinEsploraTransactionOutput? = null
)

data class BitcoinEsploraTransactionOutput(
    @SerializedName("scriptpubkey_address")
    val scriptPubKeyAddress: String? = null,
    @SerializedName("value")
    val value: JsonElement? = null
) {
    fun valueSatsOrNull(): Long? {
        val primitive = value?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: return null
        if (!primitive.isNumber) {
            return null
        }

        return primitive.asString.takeIf { SATOSHI.matches(it) }?.toLongOrNull()
    }

    private companion object {
        val SATOSHI = Regex("^\\d+$")
    }
}
