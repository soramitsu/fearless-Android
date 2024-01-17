package jp.co.soramitsu.wallet.impl.data.network.blockchain

import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.data.network.runtime.binding.SimpleBalanceData
import jp.co.soramitsu.common.utils.failure
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumConnectionPool
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumWebSocketConnection
import jp.co.soramitsu.shared_utils.extensions.requireHexPrefix
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.wallet.impl.data.network.model.EvmTransfer
import jp.co.soramitsu.wallet.impl.data.network.model.response.NewHeadsNotificationExtended
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.protocol.Web3j
import org.web3j.protocol.Web3jService
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.Ethereum
import org.web3j.protocol.core.Request
import org.web3j.protocol.core.Response
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.core.methods.response.EthSubscribe
import org.web3j.protocol.http.HttpService
import org.web3j.protocol.websocket.WebSocketService
import org.web3j.utils.Convert
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
        val connection = ethereumConnectionPool.get(chain.id)

        return kotlin.runCatching {
            connection?.web3j!!.fetchEthBalance(
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
            val connection = ethereumConnectionPool.get(chain.id)
                ?: return@withContext Result.failure("There is no connection created for chain ${chain.name}, ${chain.id}")

            connection.web3j
                ?: return@withContext Result.failure("There is no connection established for chain ${chain.name}, ${chain.id}")
            connection.service
                ?: return@withContext Result.failure("There is no connection established for chain ${chain.name}, ${chain.id}")

            val web3 = connection.web3j!!
            val cred = Credentials.create(privateKey)
            val nonce =
                kotlin.runCatching {
                    web3.ethGetTransactionCount(transfer.sender, DefaultBlockParameterName.PENDING)
                        .send().transactionCount
                }
                    .getOrElse { return@withContext Result.failure("Error ethGetTransactionCount for chain ${chain.name}, ${chain.id}, error: $it") }

            val amountInPlanks = Convert.toWei(transfer.amount, Convert.Unit.ETHER).toBigInteger()
            val evmTransfer = EvmTransfer.createFromTransfer(transfer, nonce)
            val transaction = createTransferCall(evmTransfer)

            val baseFee = runCatching {
                web3.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false)
                    .send()
                    .resultOrThrow()
                    .baseFeePerGas
                    .let { Numeric.decodeQuantity(it) }
            }
                .getOrElse { return@withContext Result.failure("Error ethGetBlockByNumber for chain ${chain.name}, ${chain.id}, error: $it") }

            val priorityFee = runCatching {
                connection.service!!.ethMaxPriorityFeePerGas()
                    .send()
                    .resultOrThrow()
                    .let { Numeric.decodeQuantity(it) }
            }
                .getOrElse { return@withContext Result.failure("Error ethMaxPriorityFeePerGas for chain ${chain.name}, ${chain.id}, error: $it") }

            val estimatedGas = kotlin.runCatching {
                web3.ethEstimateGas(transaction).send()
                    .resultOrThrow()
                    .let { Numeric.decodeQuantity(it) }
            }
                .getOrElse { return@withContext Result.failure("Error ethEstimateGas for chain ${chain.name}, ${chain.id}, error: $it") }

            val maxFeePerGas = baseFee + priorityFee
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
        val connection = ethereumConnectionPool.get(chain.id)
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
        val connection = ethereumConnectionPool.get(asset.chainId)
        val web3 = connection?.web3j
            ?: throw RuntimeException("There is no connection created for chain ${asset.chainId}")

        return web3.fetchEthBalance(asset, address)
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

    fun listenGas(transfer: Transfer, chain: Chain): Flow<BigInteger> {
        val connection = requireNotNull(ethereumConnectionPool.get(chain.id))
        val web3j = requireNotNull(connection.web3j)

        return connection.subscribeBaseFeePerGas()
            .map { baseFeePerGas ->
                val evmTransfer = EvmTransfer(
                    transfer.sender,
                    recipient = transfer.recipient,
                    amount = null,
                    chainAsset = transfer.chainAsset
                )
                if (baseFeePerGas == null) {
                    // use legacy transaction
                    calculateLegacyGas(web3j, evmTransfer, chain)
                } else {
                    // use EIP-1559 transaction
                    calculateEIP1559Gas(connection, evmTransfer, chain, baseFeePerGas)
                }
            }
    }

    private fun calculateLegacyGas(
        web3j: Web3j,
        transfer: EvmTransfer,
        chain: Chain
    ): BigInteger {
        val call = createTransferCall(transfer)

        val gasLimit = kotlin.runCatching {
            val response = web3j.ethEstimateGas(call).send()
            if (response.hasError()) {
                throw GasServiceException("Failed to ethEstimateGas on chain ${chain.name}: ${response.error}")
            }
            response.amountUsed
        }.getOrNull()
            ?: throw GasServiceException("Failed to ethEstimateGas on chain ${chain.name}. Response is null")

        val gasPrice = runCatching {
            val response = web3j.ethGasPrice().send()
            response.gasPrice
        }.getOrNull()
            ?: throw GasServiceException("Failed to get ethGasPrice on chain ${chain.name}. Response is null")

        return gasLimit * gasPrice
    }

    private suspend fun calculateEIP1559Gas(
        connection: EthereumWebSocketConnection,
        transfer: EvmTransfer,
        chain: Chain,
        baseFeePerGas: BigInteger
    ): BigInteger {
        val wsService = requireNotNull(connection.service)
        val web3j = requireNotNull(connection.web3j)

        val priorityFee = withContext(Dispatchers.IO) {
            wsService.ethMaxPriorityFeePerGas().send()?.maxPriorityFeePerGas
        }
            ?: throw GasServiceException("Failed to get ethMaxPriorityFeePerGas on chain ${chain.name}. Response is null")

        val call = createTransferCall(transfer)

        val gasLimit = kotlin.runCatching {
            val response = web3j.ethEstimateGas(call).send()
            if (response.hasError()) {
                throw GasServiceException("Failed to ethEstimateGas on chain ${chain.name}: ${response.error}")
            }
            response.amountUsed
        }.getOrNull()
            ?: throw GasServiceException("Failed to ethEstimateGas on chain ${chain.name}. Response is null")

        return gasLimit * (priorityFee + baseFeePerGas)
    }

    private fun createTransferCall(transfer: EvmTransfer): Transaction? {
        return transactionCallBuildersByAssetType[transfer.chainAsset.ethereumType]?.invoke(transfer)
    }

    suspend fun sendRawTransaction(
        chainId: ChainId,
        raw: RawTransaction,
        privateKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val connection = ethereumConnectionPool.get(chainId)
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
        val connection = ethereumConnectionPool.get(chainId)
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

fun EthereumWebSocketConnection.subscribeBaseFeePerGas(): Flow<BigInteger?> {
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

private val erc20TransferCallBuilder: (EvmTransfer) -> Transaction? = {
    val function = Function(
        "transfer",
        listOf(
            Address(it.recipient),
            it.amount?.let { nonNullAmount -> Uint256(nonNullAmount) } ?: Uint256.DEFAULT),
        listOf(TypeReference.create(Bool::class.java))
    )
    val txData: String = FunctionEncoder.encode(function)

    Transaction.createFunctionCallTransaction(
        it.sender,
        it.nonce,
        it.gasPrice,
        it.gasLimit,
        it.chainAsset.id,
        BigInteger.ZERO,
        txData
    )
}

private val transactionCallBuildersByAssetType: HashMap<Asset.EthereumType, (EvmTransfer) -> Transaction?> =
    hashMapOf(
        Asset.EthereumType.NORMAL to {
            Transaction.createEtherTransaction(
                it.sender,
                it.nonce,
                it.gasPrice,
                it.gasLimit,
                it.recipient,
                it.amount
            )
        },
        Asset.EthereumType.ERC20 to erc20TransferCallBuilder,
        Asset.EthereumType.BEP20 to erc20TransferCallBuilder
    )
