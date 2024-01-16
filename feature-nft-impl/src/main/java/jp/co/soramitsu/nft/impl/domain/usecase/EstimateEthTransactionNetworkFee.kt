package jp.co.soramitsu.nft.impl.domain.usecase

import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.core.utils.amountFromPlanks
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.nft.impl.domain.models.EIP1559CallImpl
import jp.co.soramitsu.nft.impl.domain.models.EthCall
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumWebSocketConnection
import java.math.BigDecimal
import java.math.BigInteger

@Suppress("FunctionName")
suspend fun EthereumWebSocketConnection.EstimateEthTransactionNetworkFee(
    chain: Chain,
    transfer: EthCall,
    baseFeePerGas: BigInteger
): BigDecimal {
    val eip1559Transfer = when(transfer) {
        is EthCall.SmartContractCall ->
            EIP1559CallImpl.createAsync(
                ethConnection = this,
                transfer = transfer,
                baseFeePerGas = baseFeePerGas,
                estimateGas = EstimateEthTransactionGas(
                    transfer = transfer
                )
            )

        else -> error(
            """
                Unknown transfer type.
            """.trimIndent()
        )
    }

    val networkFeeInPlanks =
        eip1559Transfer.estimateGas * (eip1559Transfer.maxPriorityFeePerGas + eip1559Transfer.baseFeePerGas)

    return chain.utilityAsset?.amountFromPlanks(networkFeeInPlanks).orZero()
}