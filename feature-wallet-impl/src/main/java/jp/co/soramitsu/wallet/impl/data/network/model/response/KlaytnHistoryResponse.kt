package jp.co.soramitsu.wallet.impl.data.network.model.response

import java.math.BigInteger
import jp.co.soramitsu.common.data.network.runtime.binding.BlockNumber

data class KlaytnHistoryItem(
    val createdAt: Long,
    val txHash: String,
    val blockNumber: BlockNumber,
    val fromAddress: String,
    val toAddress: String,
    val amount: BigInteger,
    val txFee: BigInteger,
    val gasLimit: BigInteger,
    val gasUsed: BigInteger,
    val gasPrice: BigInteger,
    val txStatus: Int,
)

data class KlaytnHistoryResponse(
    val success: Boolean,
    val result: List<KlaytnHistoryItem>,
    val page: Int,
    val total: Int
)
