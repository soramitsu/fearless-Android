package jp.co.soramitsu.nft.impl.domain.utils

import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumWebSocketConnection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.reactive.asFlow
import org.web3j.protocol.Web3j
import org.web3j.protocol.Web3jService
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.Request
import org.web3j.protocol.core.Response
import org.web3j.protocol.core.methods.response.EthSubscribe
import org.web3j.protocol.websocket.events.Notification
import org.web3j.utils.Numeric
import java.math.BigInteger

val EthereumWebSocketConnection.nonNullWeb3j: Web3j
    get() = web3j ?: error(
        """
            Established connection to web3 contains errors.
        """.trimIndent()
    )

val EthereumWebSocketConnection.nonNullWeb3jService: Web3jService
    get() = service ?: error(
        """
            Could not have establish subscription to web3jService.
        """.trimIndent()
    )

suspend fun Web3j.getNonce(address: String): BigInteger {
    return ethGetTransactionCount(address, DefaultBlockParameterName.PENDING)
        .sendAsync().await()
        .transactionCount
}

suspend fun EthereumWebSocketConnection.getMaxPriorityFeePerGas(): BigInteger {
    return Request<Any, MaxPriorityFeePerGas>(
        "eth_maxPriorityFeePerGas",
        emptyList(),
        nonNullWeb3jService,
        MaxPriorityFeePerGas::class.java
    ).sendAsync().await().maxPriorityFeePerGas
}

class MaxPriorityFeePerGas : Response<String?>() {
    val maxPriorityFeePerGas: BigInteger
        get() = Numeric.decodeQuantity(result)
}

fun EthereumWebSocketConnection.subscribeNewHeads(): Flow<NewHeadsNotificationExtended> {
    return nonNullWeb3jService.subscribe(
        Request(
            /* method */ "eth_subscribe",
            /* params */ listOf("newHeads"),
            /* web3jSocket */ service,
            /* type */ EthSubscribe::class.java
        ),
        "eth_unsubscribe",
        NewHeadsNotificationExtended::class.java
    ).asFlow()
}

class NewHeadsNotificationExtended :
    Notification<NewHeadExtended?>()

class NewHeadExtended {
    var difficulty: String? = null
    var extraData: String? = null
    var gasLimit: String? = null
    var gasUsed: String? = null
    var hash: String? = null
    var logsBloom: String? = null
    var miner: String? = null
    var nonce: String? = null
    var number: String? = null
    var parentHash: String? = null
    var receiptRoot: String? = null
    var sha3Uncles: String? = null
    var stateRoot: String? = null
    var timestamp: String? = null
    var transactionRoot: String? = null
    var baseFeePerGas: String? = null
}