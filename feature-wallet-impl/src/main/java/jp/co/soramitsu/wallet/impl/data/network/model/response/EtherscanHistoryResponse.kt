package jp.co.soramitsu.wallet.impl.data.network.model.response

import java.math.BigInteger

data class EtherscanHistoryResponse(
    val status: String,
    val message: String,
    val result: List<EtherscanHistoryElement>
)

data class EtherscanHistoryElement(
    val blockNumber: String,
    val timeStamp: Long,
    val hash: String,
    val nonce: String,
    val blockHash: String,
    val from: String,
    val to: String,
    val contractAddress: String,
    val value: BigInteger,
    val gas: BigInteger,
    val gasPrice: BigInteger,
    val gasUsed: BigInteger,
    val isError: Int
)