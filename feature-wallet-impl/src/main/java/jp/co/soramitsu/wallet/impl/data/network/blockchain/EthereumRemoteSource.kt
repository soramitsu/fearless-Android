package jp.co.soramitsu.wallet.impl.data.network.blockchain

import java.math.BigInteger
import jp.co.soramitsu.common.utils.failure
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumChainConnection
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumConnectionPool
import jp.co.soramitsu.fearless_utils.extensions.requireHexPrefix
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.wallet.impl.data.network.model.response.NewHeadsNotificationExtended
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.web3j.abi.DefaultFunctionReturnDecoder
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Int256
import org.web3j.abi.datatypes.generated.Uint80
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.protocol.Web3jService
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.Ethereum
import org.web3j.protocol.core.Request
import org.web3j.protocol.core.Response
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.core.methods.response.EthSubscribe
import org.web3j.protocol.http.HttpService
import org.web3j.protocol.websocket.WebSocketService
import org.web3j.utils.Numeric

class EthereumRemoteSource(private val ethereumConnectionPool: EthereumConnectionPool) {
    suspend fun getTotalBalance(
        chainAsset: Asset,
        chain: Chain,
        accountId: AccountId
    ): Result<BigInteger> {
        val connection = ethereumConnectionPool.await(chain.id)

        return kotlin.runCatching {
            connection.web3j!!.fetchEthBalance(
                chainAsset,
                accountId.toHexString(true)
            )
        }
    }

    suspend fun performTransfer(
        chain: Chain,
        transfer: Transfer,
        privateKey: String
    ): Result<String> =
        withContext(Dispatchers.IO) {
            val connection = ethereumConnectionPool.await(chain.id)
                ?: return@withContext Result.failure("There is no connection created for chain ${chain.name}, ${chain.id}")

            val web3 = connection.web3j ?: return@withContext Result.failure("There is no connection established for chain ${chain.name}, ${chain.id}")
            val cred = Credentials.create(privateKey)

            val builder = EthereumTransactionBuilder(connection)
            val rawTransaction =
                builder.build(transfer).onFailure { return@withContext Result.failure(it) }
                    .requireValue()

            val evmChainId = transfer.chainAsset.chainId.requireHexPrefix().drop(2).toLong()
            val signed = signEvmTransfer(rawTransaction, evmChainId, cred)

            val transactionHash = kotlin.runCatching {
                web3.ethSendRawTransaction(signed.toHexString(true)).send().resultOrThrow()
            }
                .getOrElse { return@withContext Result.failure("Error ethSendRawTransaction for chain ${chain.name}, ${chain.id}, error: ${it.message ?: it}") }

            return@withContext Result.success(transactionHash)
        }

    suspend fun fetchEthBalance(
        asset: Asset,
        address: String
    ): BigInteger {
        val connection = ethereumConnectionPool.await(asset.chainId)
        val web3 = connection?.web3j
            ?: throw RuntimeException("There is no connection created for chain ${asset.chainId}")

        return web3.fetchEthBalance(asset, address)
    }

    suspend fun fetchPriceFeed(
        chainId: ChainId,
        receiverAddress: String
    ): BigInteger? {
        val connection = ethereumConnectionPool.await(chainId)

        val web3 = connection?.web3j
            ?: throw RuntimeException("There is no connection created for chain ${chainId}")

        return web3.fetchPriceFeed(receiverAddress)
    }

    private suspend fun Ethereum.fetchEthBalance(asset: Asset, address: String): BigInteger {
        return if (asset.isUtility) {
            withContext(Dispatchers.IO) {
                ethGetBalance(
                    address,
                    DefaultBlockParameterName.LATEST
                ).send().balance
            }
        } else {
            val erc20GetBalanceFunction = Function(
                "balanceOf",
                listOf(Address(address)),
                emptyList()
            )

            val erc20BalanceWei = withContext(Dispatchers.IO) {
                ethCall(
                    Transaction.createEthCallTransaction(
                        null,
                        asset.id,
                        FunctionEncoder.encode(erc20GetBalanceFunction)
                    ),
                    DefaultBlockParameterName.LATEST
                ).send().value
            }

            Numeric.decodeQuantity(erc20BalanceWei)
        }
    }

    private suspend fun Ethereum.fetchPriceFeed(address: String): BigInteger? {
        val outParameters = listOf(
            Uint80::class.java,
            Int256::class.java,
            Int256::class.java,
            Int256::class.java,
            Uint80::class.java
        ).map {
            TypeReference.create(it)
        }

        val latestRoundDataFunction = Function(
            "latestRoundData",
            emptyList(),
            outParameters
        )

        val latestRoundData = withContext(Dispatchers.IO) {
            kotlin.runCatching {
                ethCall(
                    Transaction.createEthCallTransaction(
                        null,
                        Address(address).value,
                        latestRoundDataFunction.encode()
                    ),
                    DefaultBlockParameterName.LATEST
                ).send().value
            }.getOrNull()
        }

        val decodeResult = DefaultFunctionReturnDecoder.decode(
            latestRoundData,
            latestRoundDataFunction.outputParameters
        )

        return decodeResult[1].value as? BigInteger
    }

    fun listenGas(transfer: Transfer, chain: Chain): Flow<BigInteger> {
        val connection = requireNotNull(ethereumConnectionPool.getOrNull(chain.id))
        val web3j = requireNotNull(connection.web3j)
        val wsService = requireNotNull(connection.service)
        val transactionBuilder = EthereumTransactionBuilder(connection)

        return connection.subscribeBaseFeePerGas()
            .map { baseFeePerGas ->
                // The gas of the transfer as reviewed. While the entered amount is above the balance
                // the node refuses that estimate; show the amount-free one then, as before: the amount
                // check reports the shortfall, and the send is estimated again with its amount. A request
                // that fails is not a refusal: the fee fails and is retried instead of showing less.
                val gasLimit = try {
                    web3j.estimateGasOrRefusal(transactionBuilder.buildFeeEstimationCall(transfer))
                } catch (failure: Exception) {
                    throw GasServiceException("Failed to ethEstimateGas on chain ${chain.name}: ${failure.message}")
                }
                    ?: web3j.estimateGasOrNull(transactionBuilder.buildAmountFreeFeeEstimationCall(transfer))
                    ?: throw GasServiceException("Failed to ethEstimateGas on chain ${chain.name}. Response is null")

                val priorityFee = withContext(Dispatchers.IO) {
                    runCatching {
                        wsService.ethMaxPriorityFeePerGas().send()?.maxPriorityFeePerGas
                    }.getOrNull()
                }

                if (baseFeePerGas != null && priorityFee != null) {
                    // use EIP-1559 transaction
                    gasLimit * (priorityFee + baseFeePerGas)
                } else {
                    // use legacy transaction

                    val gasPrice = runCatching {
                        val response = web3j.ethGasPrice().send()
                        response.gasPrice
                    }.getOrNull()
                        ?: throw GasServiceException("Failed to get ethGasPrice on chain ${chain.name}. Response is null")

                    gasLimit * gasPrice
                }
            }
            .flowOn(Dispatchers.IO)
    }

    /** The gas the node estimates for [call], or null when it refuses or cannot be reached. */
    private fun Ethereum.estimateGasOrNull(call: Transaction?): BigInteger? = runCatching {
        estimateGasOrRefusal(call)
    }.getOrNull()

    /** The gas the node estimates for [call], or null when the node answers with an error. A failed request throws. */
    private fun Ethereum.estimateGasOrRefusal(call: Transaction?): BigInteger? {
        val response = ethEstimateGas(requireNotNull(call) { "There is no fee estimation call for this transfer" }).send()
        return if (response.hasError()) null else response.amountUsed
    }

    suspend fun sendRawTransaction(
        chainId: ChainId,
        raw: RawTransaction,
        privateKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val connection = ethereumConnectionPool.await(chainId)
            ?: return@withContext Result.failure("There is no connection created for chain with id = $chainId")
        val web3 = connection.web3j
            ?: return@withContext Result.failure("There is no connection established for chain with id = $chainId")

        val transactionHash = kotlin.runCatching {
            val signedTransaction = signRawTransaction(chainId, raw, privateKey).getOrThrow()
            web3.ethSendRawTransaction(signedTransaction).send().resultOrThrow()
        }
            .getOrElse { return@withContext Result.failure("Error ethSendRawTransaction for chain with id = $chainId, error: ${it.message ?: it}") }

        return@withContext Result.success(transactionHash)
    }

    suspend fun signRawTransaction(
        chainId: ChainId,
        raw: RawTransaction,
        privateKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val connection = ethereumConnectionPool.await(chainId)
            ?: return@withContext Result.failure("There is no connection created for chain with id = $chainId")

        val web3 = connection.web3j
            ?: return@withContext Result.failure("There is no connection established for chain with id = $chainId")
        connection.service
            ?: return@withContext Result.failure("There is no connection established for chain with id = $chainId")

        val cred = Credentials.create(privateKey)

        val senderAddress = cred.address

        val nonce = raw.nonce ?: kotlin.runCatching {
            web3.ethGetTransactionCount(senderAddress, DefaultBlockParameterName.PENDING)
                .send().transactionCount
        }
            .getOrElse { return@withContext Result.failure("Error ethGetTransactionCount for chain with id = $chainId, error: $it") }

        val gasPrice = raw.gasPrice ?: run {
            val baseFeePerGas = runCatching {
                web3.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false)
                    .send()
                    .resultOrThrow()
                    .baseFeePerGas
                    .let { Numeric.decodeQuantity(it) }
            }
                .getOrElse { return@withContext Result.failure("Error ethGetBlockByNumber for chain with id = $chainId, error: $it") }

            val maxPriorityFeePerGas = runCatching {
                connection.service!!.ethMaxPriorityFeePerGas()
                    .send()
                    .resultOrThrow()
                    .let { Numeric.decodeQuantity(it) }
            }
                .getOrElse { return@withContext Result.failure("Error ethMaxPriorityFeePerGas for chain with id = $chainId, error: $it") }

            baseFeePerGas + maxPriorityFeePerGas
        }

        val gasLimit = raw.gasLimit ?: kotlin.runCatching {
            val txForGasLimitEstimate = Transaction.createFunctionCallTransaction(
                senderAddress,
                nonce,
                null,
                null,
                raw.to,
                raw.value,
                raw.data
            )

            web3.ethEstimateGas(txForGasLimitEstimate).send()
                .resultOrThrow()
                .let { Numeric.decodeQuantity(it) }
        }
            .getOrElse { return@withContext Result.failure("Error ethEstimateGas for chain with id = $chainId, error: $it") }

        val actualRawTransaction = RawTransaction.createTransaction(
            nonce,
            gasPrice,
            gasLimit,
            raw.to,
            raw.value,
            raw.data.orEmpty()
        )

        val signed = TransactionEncoder.signMessage(actualRawTransaction, chainId.toLong(), cred)
        return@withContext Result.success(signed.toHexString(true))
    }
}

/**
 * Signs a wallet transfer so that it is valid only on [chainId]. EIP-1559 transactions carry the
 * id in their payload; legacy ones, built when a node does not report EIP-1559 fees, get EIP-155
 * replay protection instead of a chain-less signature another EVM network would also accept.
 */
internal fun signEvmTransfer(rawTransaction: RawTransaction, chainId: Long, credentials: Credentials): ByteArray =
    TransactionEncoder.signMessage(rawTransaction, chainId, credentials)

class GasServiceException(message: String) : RuntimeException(message)

fun <T> Response<T>.resultOrThrow(): T {
    if (hasError()) {
        throw EthereumRequestError(error.message)
    } else {
        return result
    }
}

class EthereumRequestError(message: String) : Exception(message)

fun Web3jService.ethMaxPriorityFeePerGas(): Request<Any, MaxPriorityFeePerGas> {
    return Request<Any, MaxPriorityFeePerGas>(
        "eth_maxPriorityFeePerGas",
        emptyList(),
        this,
        MaxPriorityFeePerGas::class.java
    )
}

class MaxPriorityFeePerGas : Response<String?>() {
    val maxPriorityFeePerGas: BigInteger
        get() = Numeric.decodeQuantity(result)
}

fun EthereumChainConnection.subscribeBaseFeePerGas(): Flow<BigInteger?> {
    val wsService = requireNotNull(service)
    val web3j = requireNotNull(web3j)

    return when (wsService) {
        is WebSocketService -> {
            wsService.subscribeNewHeads().map { it.params.result?.baseFeePerGas }
                .onStart {
                    val baseFeePerGas =
                        web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false)
                            .send()
                            .block
                            .baseFeePerGas
                    emit(baseFeePerGas)
                }
        }

        is HttpService -> {
            flow {
                while (true) {
                    val block =
                        web3j.ethGetBlockByNumber(DefaultBlockParameterName.PENDING, false)
                            .send()
                            .block
                    val baseFeePerGas = block.baseFeePerGas
                    emit(baseFeePerGas)
                    delay(3000)
                    yield()
                }
            }
        }

        else -> {
            throw IllegalStateException("Can't use this node to listen gas fee")
        }
    }.map { runCatching { Numeric.decodeQuantity(it) }.getOrNull() }
}

fun WebSocketService.subscribeNewHeads(): Flow<NewHeadsNotificationExtended> {
    return subscribe(
        Request(
            "eth_subscribe", listOf("newHeads"),
            this,
            EthSubscribe::class.java
        ),
        "eth_unsubscribe",
        NewHeadsNotificationExtended::class.java
    ).asFlow()
}