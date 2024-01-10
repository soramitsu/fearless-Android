package jp.co.soramitsu.nft.impl.domain.usecase

import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.core.utils.amountFromPlanks
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.nft.impl.domain.models.NFTTransferParams
import jp.co.soramitsu.nft.impl.domain.utils.getMaxPriorityFeePerGas
import jp.co.soramitsu.nft.impl.domain.utils.nonNullWeb3j
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumWebSocketConnection
import org.web3j.utils.Numeric
import java.math.BigDecimal
import javax.inject.Inject

class EstimateNFTTransactionNetworkFeeUseCase @Inject constructor(
    private val nftTransferFunctionAdapter: NFTTransferFunctionAdapter,
    private val estimateEthTransactionGasLimitUseCase: EstimateEthTransactionGasLimitUseCase
) {

    suspend operator fun invoke(
        socketConnection: EthereumWebSocketConnection,
        chain: Chain,
        params: NFTTransferParams,
        nonce: String? = null,
        baseFeePerGas: String?
    ): BigDecimal {
        val nonceAsNumeric = nonce?.let {
            Numeric.decodeQuantity(it)
        } ?: error(
            """
                Nonce provided is null.
            """.trimIndent()
        )

        val baseFeePerGasAsNumeric = baseFeePerGas?.let {
            Numeric.decodeQuantity(it)
        } ?: error(
            """
                BaseFeePerGas is null.
            """.trimIndent()
        )

        val priorityFee = socketConnection.getMaxPriorityFeePerGas()

        val estimateGas = estimateEthTransactionGasLimitUseCase(
            web3j = socketConnection.nonNullWeb3j,
            fromAddress = params.sender,
            toAddress = params.receiver,
            nonce = nonceAsNumeric,
            data = nftTransferFunctionAdapter(params)
        )

        val networkFeeInPlanks = estimateGas * (priorityFee + baseFeePerGasAsNumeric)

        return chain.utilityAsset?.amountFromPlanks(networkFeeInPlanks).orZero()
    }

}