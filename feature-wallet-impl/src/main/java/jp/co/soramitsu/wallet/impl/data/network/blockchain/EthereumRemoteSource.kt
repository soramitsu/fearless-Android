package jp.co.soramitsu.wallet.impl.data.network.blockchain

import android.util.Log
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.data.network.runtime.binding.SimpleBalanceData
import jp.co.soramitsu.common.utils.failure
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumConnectionPool
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumChainConnection
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.wallet.impl.data.network.model.response.NewHeadsNotificationExtended
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
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
    companion object {
        private const val ETHEREUM_BALANCES_UPDATE_DELAY = 30_000L
    }

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

            val signed = TransactionEncoder.signMessage(rawTransaction, cred)

            val transactionHash = kotlin.runCatching {
                web3.ethSendRawTransaction(signed.toHexString(true)).send().resultOrThrow()
            }
                .getOrElse { return@withContext Result.failure("Error ethSendRawTransaction for chain ${chain.name}, ${chain.id}, error: ${it.message ?: it}") }

            return@withContext Result.success(transactionHash)
        }


    private val ethereumBalancesSubscriptionJob: MutableMap<ChainId, Job?> = mutableMapOf()
    suspend fun subscribeEthereumBalance(
        chain: Chain,
        account: MetaAccount
    ): Result<Flow<Result<Pair<Asset, SimpleBalanceData>>>> {
        val connection = ethereumConnectionPool.await(chain.id)
        val web3 = connection?.web3j
            ?: return Result.failure("There is no connection created for chain ${chain.name}, ${chain.id}")

        ethereumBalancesSubscriptionJob[chain.id]?.cancel()
        ethereumBalancesSubscriptionJob[chain.id] = Job()
        val flow = withContext(Dispatchers.Default + ethereumBalancesSubscriptionJob[chain.id]!!) {
            flow {

                while (ethereumBalancesSubscriptionJob[chain.id]?.isActive == true) {
                    val address = account.address(chain)
                    if (address == null) {
                        emit(Result.failure("Can't find address for chain ${chain.name} : ${chain.id}, metaAccount: ${account.name}"))
                        continue
                    }

                    chain.assets.forEach { asset ->
                        val balance =
                            kotlin.runCatching { web3.fetchEthBalance(asset, address) }.getOrElse {
                                emit(Result.failure("Can't fetchEthBalance for ${asset.name}, ${chain.name}, address: $address"))
                                return@forEach
                            }
                        emit(Result.success(asset to SimpleBalanceData(balance)))
                    }
                    delay(ETHEREUM_BALANCES_UPDATE_DELAY)
                }
            }
        }
        return Result.success(flow)
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
                val call = transactionBuilder.buildFeeEstimationCall(transfer)

                val gasLimit = kotlin.runCatching {
                    val response = web3j.ethEstimateGas(call).send()
                    if (response.hasError()) {
                        throw GasServiceException("Failed to ethEstimateGas on chain ${chain.name}: ${response.error}")
                    }
                    response.amountUsed
                }.getOrNull()
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