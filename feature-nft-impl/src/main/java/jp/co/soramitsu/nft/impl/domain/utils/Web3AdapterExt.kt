package jp.co.soramitsu.nft.impl.domain.utils

import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumChainConnection
import kotlinx.coroutines.future.await
import org.web3j.protocol.Web3j
import org.web3j.protocol.Web3jService
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.Request
import org.web3j.protocol.core.Response
import org.web3j.utils.Numeric
import java.math.BigInteger

val EthereumChainConnection.nonNullWeb3j: Web3j
    get() = web3j ?: error(
        """
            Established connection to web3 contains errors.
        """.trimIndent()
    )

val EthereumChainConnection.nonNullWeb3jService: Web3jService
    get() = service ?: error(
        """
            Could not have establish subscription to web3jService.
        """.trimIndent()
    )

inline fun <T, K> Response<T>.map(crossinline transform: (T) -> K): K {
    if (error != null) {
        error(
            """
                Could not fetch web3 response due to "${error.message}"
            """.trimIndent()
        )
    }

    return try {
        transform.invoke(result)
    } catch (t: Throwable) {
        error(
            """
                Could not transform web3 response due to ${t.message}
            """.trimIndent()
        )
    }
}

suspend fun Web3j.getNonce(address: String): BigInteger {
    val response =  ethGetTransactionCount(address, DefaultBlockParameterName.PENDING).sendAsync().await()

    return response.map { Numeric.decodeQuantity(it) }
}

suspend fun EthereumChainConnection.getMaxPriorityFeePerGas(): BigInteger {
    val response = Request<Any, MaxPriorityFeePerGas>(
        "eth_maxPriorityFeePerGas",
        emptyList(),
        nonNullWeb3jService,
        MaxPriorityFeePerGas::class.java
    ).sendAsync().await()

    return response.map { Numeric.decodeQuantity(it) }
}

@Suppress("MemberNameEqualsClassName")
class MaxPriorityFeePerGas : Response<String?>() {
    val maxPriorityFeePerGas: BigInteger
        get() = Numeric.decodeQuantity(result)
}

suspend fun Web3j.getBaseFee(): BigInteger {
    val response = ethGetBlockByNumber(
        DefaultBlockParameterName.PENDING,
        false
    ).sendAsync().await()

    return response.map { Numeric.decodeQuantity(it.baseFeePerGas) }
}