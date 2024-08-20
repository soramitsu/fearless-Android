package jp.co.soramitsu.wallet.impl.data.network.model.response

import java.math.BigInteger
import jp.co.soramitsu.common.data.network.runtime.binding.BlockNumber

data class VicscanHistoryResponse(
    val data: List<VicscanHistoryItem>,
    val total: Int
)

data class VicscanHistoryItem(
    val transactionIndex: BigInteger,
    val status: String,
    val hash: String,
    val timestamp: Long,
    val blockNumber: BlockNumber,
    val from: String,
    val to: String,
    val value: BigInteger,
    val gas: BigInteger,
    val fee: BigInteger,
    val gasUsed: BigInteger,
    val gasPrice: BigInteger
)

