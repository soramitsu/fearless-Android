package jp.co.soramitsu.nft.impl.domain.models

import jp.co.soramitsu.nft.impl.domain.utils.getBaseFee
import jp.co.soramitsu.nft.impl.domain.utils.getMaxPriorityFeePerGas
import jp.co.soramitsu.nft.impl.domain.utils.nonNullWeb3j
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumWebSocketConnection
import jp.co.soramitsu.shared_utils.extensions.requireHexPrefix
import java.math.BigInteger

internal class EIP1559CallImpl<Call : EthCall> private constructor(
    override val chainId: Long,
    override val call: Call,
    override val estimateGas: BigInteger,
    override val baseFeePerGas: BigInteger,
    override val maxPriorityFeePerGas: BigInteger
) : EIP1559Call<Call> {

    companion object {
        suspend fun <Call : EthCall> createAsync(
            ethConnection: EthereumWebSocketConnection,
            call: Call,
            estimateGas: BigInteger
        ) = EIP1559CallImpl(
            chainId = ethConnection.chain.id.requireHexPrefix().drop(2).toLong(),
            call = call,
            estimateGas = estimateGas,
            baseFeePerGas = ethConnection.nonNullWeb3j.getBaseFee(),
            maxPriorityFeePerGas = ethConnection.getMaxPriorityFeePerGas()
        )

        suspend fun <Call : EthCall> createAsync(
            ethConnection: EthereumWebSocketConnection,
            call: Call,
            baseFeePerGas: BigInteger,
            estimateGas: BigInteger
        ) = EIP1559CallImpl(
            chainId = ethConnection.chain.id.requireHexPrefix().drop(2).toLong(),
            call = call,
            estimateGas = estimateGas,
            baseFeePerGas = baseFeePerGas,
            maxPriorityFeePerGas = ethConnection.getMaxPriorityFeePerGas()
        )
    }
}

interface EIP1559Call<Call : EthCall> {

    val chainId: Long

    val call: Call

    val estimateGas: BigInteger

    val baseFeePerGas: BigInteger

    val maxPriorityFeePerGas: BigInteger
}
