package jp.co.soramitsu.nft.impl.domain.models

import jp.co.soramitsu.nft.impl.domain.utils.getBaseFee
import jp.co.soramitsu.nft.impl.domain.utils.getMaxPriorityFeePerGas
import jp.co.soramitsu.nft.impl.domain.utils.nonNullWeb3j
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumWebSocketConnection
import java.math.BigInteger

internal class EIP1559CallImpl<Transfer: EthCall> private constructor(
    override val transfer: Transfer,
    override val estimateGas: BigInteger,
    override val baseFeePerGas: BigInteger,
    override val maxPriorityFeePerGas: BigInteger
) : EIP1559Call<Transfer> {

    companion object {
        suspend fun <Transfer: EthCall> createAsync(
            ethConnection: EthereumWebSocketConnection,
            transfer: Transfer,
            estimateGas: BigInteger
        ) = EIP1559CallImpl(
            transfer = transfer,
            estimateGas = estimateGas,
            baseFeePerGas = ethConnection.nonNullWeb3j.getBaseFee(),
            maxPriorityFeePerGas = ethConnection.getMaxPriorityFeePerGas()
        )

        suspend fun <Transfer: EthCall> createAsync(
            ethConnection: EthereumWebSocketConnection,
            transfer: Transfer,
            baseFeePerGas: BigInteger,
            estimateGas: BigInteger
        ) = EIP1559CallImpl(
            transfer = transfer,
            estimateGas = estimateGas,
            baseFeePerGas = baseFeePerGas,
            maxPriorityFeePerGas = ethConnection.getMaxPriorityFeePerGas()
        )
    }

}

interface EIP1559Call<Transfer: EthCall> {

    val transfer: Transfer

    val estimateGas: BigInteger

    val baseFeePerGas: BigInteger

    val maxPriorityFeePerGas: BigInteger

}