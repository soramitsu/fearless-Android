package jp.co.soramitsu.wallet.impl.data.network.model.response

import java.math.BigDecimal
import java.math.BigInteger

data class OkLinkHistoryResponse(
    val code: Int,
    val msg: String,
    val data: List<OkLinkHistoryPage>
)

data class OkLinkHistoryPage(
    val page: Int,
    val limit: Int,
    val totalPage: Int,
    val chainFullName: String,
    val chainShortName: String,
    val transactionLists: List<OkLinkHistoryItem>
)

data class OkLinkHistoryItem(
    val txId: String,
    val methodId: String,
    val blockHash: String,
    val height: BigInteger,
    val transactionTime: Long,
    val from: String,
    val to: String,
    val isFromContract: Boolean,
    val isToContract: Boolean,
    val amount: BigDecimal,
    val transactionSymbol: String,
    val txFee: BigDecimal,
    val state: String,
    val tokenId: String,
    val tokenContractAddress: String,
    val challengeStatus: String,
    val l1OriginHash: String
)