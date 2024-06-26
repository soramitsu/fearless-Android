package jp.co.soramitsu.nft.impl.domain.usecase.eth

import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.core.utils.amountFromPlanks
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.nft.impl.domain.models.transfer.EIP1559CallImpl
import jp.co.soramitsu.nft.impl.domain.models.transfer.EthCall
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumChainConnection
import java.math.BigDecimal
import java.math.BigInteger

suspend fun EthereumChainConnection.estimateEthTransactionNetworkFee(
    call: EthCall,
    baseFeePerGas: BigInteger
): BigDecimal {
    val eip1559Transfer = if (call is EthCall.SmartContractCall) {
        val estimateGas = estimateEthTransactionGas(call = call)

        EIP1559CallImpl.createAsync(
            ethConnection = this,
            call = call,
            baseFeePerGas = baseFeePerGas,
            estimateGas = estimateGas
        )
    } else {
        error(
            """
                    Unknown transfer type.
                """.trimIndent()
        )
    }

    val networkFeeInPlanks =
        eip1559Transfer.estimateGas * (eip1559Transfer.maxPriorityFeePerGas + eip1559Transfer.baseFeePerGas)

    return chain.utilityAsset?.amountFromPlanks(networkFeeInPlanks).orZero()
}
