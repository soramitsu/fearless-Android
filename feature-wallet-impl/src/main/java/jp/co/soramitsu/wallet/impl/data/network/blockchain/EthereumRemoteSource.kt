package jp.co.soramitsu.wallet.impl.data.network.blockchain

import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.shared_utils.extensions.requireHexPrefix
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.Request
import org.web3j.protocol.core.Response
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.http.HttpService
import org.web3j.utils.Convert
import org.web3j.utils.Numeric

class EthereumRemoteSource {

    suspend fun getTotalBalance(chainAsset: Asset, chain: Chain, accountId: AccountId): BigInteger {
        val web3 = Web3j.build(HttpService(chain.nodes.first().url))
        return web3.fetchEthBalance(chainAsset, accountId.toHexString(true))
    }

    suspend fun performTransfer(chain: Chain, transfer: Transfer, privateKey: String) = withContext(Dispatchers.IO) {
        val service = HttpService(chain.nodes.first().url)
        val web3 = Web3j.build(service)
        val cred = Credentials.create(privateKey)
        val nonce = web3.ethGetTransactionCount(transfer.sender, DefaultBlockParameterName.LATEST).send().transactionCount

        val amountInPlanks = Convert.toWei(transfer.amount, Convert.Unit.ETHER).toBigInteger()

        val transaction = if (transfer.chainAsset.isUtility) {
            Transaction.createEtherTransaction(
                transfer.sender,
                nonce,
                null,
                null,
                transfer.recipient,
                amountInPlanks
            )
        } else {
            val function =
                Function("transfer", listOf(Address(transfer.recipient), Uint256(transfer.amountInPlanks)), listOf(TypeReference.create(Bool::class.java)))

            Transaction.createFunctionCallTransaction(
                transfer.sender,
                null,
                null,
                null,
                transfer.chainAsset.id,
                BigInteger.ZERO,
                FunctionEncoder.encode(function)
            )
        }

        val baseFeeRaw = web3.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send().resultOrThrow().baseFeePerGas
        val baseFee = Numeric.decodeQuantity(baseFeeRaw)
        val priorityFee = service.ethMaxPriorityFeePerGas().send().resultOrThrow().let { Numeric.decodeQuantity(it) }

        val estimatedGas = web3.ethEstimateGas(transaction).send().resultOrThrow().let { Numeric.decodeQuantity(it) }

        val maxFeePerGas = (baseFee + priorityFee) * estimatedGas
        val chainId = chain.id.requireHexPrefix().drop(2).toLong()
        val raw = if (transfer.chainAsset.isUtility) {
            RawTransaction.createEtherTransaction(
                chainId,
                nonce,
                estimatedGas, //gasLimit
                transfer.recipient,
                amountInPlanks,
                priorityFee, //maxPriorityFeePerGas
                maxFeePerGas //maxFeePerGas
            )
        } else {
            val erc20TransferFunction = Function(
                "transfer",
                listOf(Address(transfer.recipient), Uint256(transfer.amountInPlanks)),
                listOf(TypeReference.create(Bool::class.java))
            )

            val encodedErc20Function = FunctionEncoder.encode(erc20TransferFunction)
            RawTransaction.createTransaction(
                chainId,
                nonce,
                estimatedGas, //gasLimit
                transfer.chainAsset.id,
                BigInteger.ZERO,
                encodedErc20Function,
                priorityFee, //maxPriorityFeePerGas
                maxFeePerGas //maxFeePerGas
            )
        }

        val signed = TransactionEncoder.signMessage(raw, cred)

        web3.ethSendRawTransaction(signed.toHexString(true)).send().let {
            hashCode()
            it
        }.resultOrThrow()
    }
}

fun <T> Response<T>.resultOrThrow(): T {
    if (hasError()) {

        throw EthereumRequestError(error.message)
    } else {
        return result
    }
}

class EthereumRequestError(message: String) : Exception(message)

class EthereumTransferException(message: String) : Exception(message)
