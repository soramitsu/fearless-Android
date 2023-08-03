package jp.co.soramitsu.wallet.impl.data.repository

import java.math.BigInteger
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.wallet.impl.data.network.blockchain.ethMaxPriorityFeePerGas
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.withContext
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.core.methods.response.EthBlock
import org.web3j.protocol.websocket.WebSocketService
import org.web3j.utils.Numeric

class EthGasService {

    private val wsServicePool: MutableMap<ChainId, WebSocketService> = mutableMapOf()
    private val blockSubscriptions: MutableMap<ChainId, Flow<EthBlock>> = mutableMapOf()

    fun listenGas(transfer: Transfer, chain: Chain): Flow<BigInteger> {
        val wsService = wsServicePool.getOrPut(chain.id) {
            // todo replace url with WS url from chain
            WebSocketService("wss://mainnet.infura.io/ws/v3/550f1aa1e8a147f8b43184507686569b", false).apply { connect() }
        }
        val web3j = Web3j.build(wsService)
        return blockSubscriptions.getOrPut(chain.id) {
            web3j.blockFlowable(false).asFlow()
                .onStart {
                    val block = web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send()
                    emit(block)
                }
        }.map { block ->
            val priorityFee = withContext(Dispatchers.IO) {
                kotlin.runCatching { wsService.ethMaxPriorityFeePerGas().send()?.maxPriorityFeePerGas }.getOrNull().orZero()
            }
            val baseFee = Numeric.decodeQuantity(block.block.baseFeePerGas)

            val call = if (transfer.chainAsset.isUtility) {
                Transaction.createEtherTransaction(
                    transfer.sender,
                    null,
                    null,
                    null,
                    transfer.recipient,
                    null
                )
            } else {
                val function = Function("transfer", listOf(Address(transfer.recipient), Uint256(null)), emptyList())
                val txData: String = FunctionEncoder.encode(function)

                Transaction.createFunctionCallTransaction(
                    transfer.sender,
                    null,
                    null,
                    null,
                    transfer.chainAsset.id,
                    BigInteger.ZERO,
                    txData
                )
            }
            val gasLimit = kotlin.runCatching {
                web3j.ethEstimateGas(call).send().amountUsed
            }.getOrNull().orZero()
            gasLimit * (priorityFee + baseFee)
        }
    }
}
