package jp.co.soramitsu.wallet.impl.data.network.blockchain

import android.util.Log
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.data.network.runtime.binding.SimpleBalanceData
import jp.co.soramitsu.common.utils.failure
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumConnectionPool
import jp.co.soramitsu.shared_utils.extensions.requireHexPrefix
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.shared_utils.runtime.AccountId
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
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.protocol.Web3jService
import org.web3j.protocol.core.BatchRequest
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.Ethereum
import org.web3j.protocol.core.Request
import org.web3j.protocol.core.Response
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.core.methods.response.EthCall
import org.web3j.protocol.core.methods.response.EthGetBalance
import org.web3j.protocol.core.methods.response.EthSubscribe
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
                    web3.ethGetTransactionCount(transfer.sender, DefaultBlockParameterName.LATEST)
                        .send().transactionCount
                }
                    .getOrElse { return@withContext Result.failure("Error ethGetTransactionCount for chain ${chain.name}, ${chain.id}, error: $it") }

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
                    Function(
                        "transfer",
                        listOf(Address(transfer.recipient), Uint256(transfer.amountInPlanks)),
                        listOf(TypeReference.create(Bool::class.java))
                    )

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

    suspend fun fetchEthBalances(
        chain: Chain,
        accounts: List<MetaAccount>
    ): List<Triple<Response<*>, String, MetaAccount>> {
        return withContext(Dispatchers.Default) {
            val connection = ethereumConnectionPool.get(chain.id)
            val service = connection?.service
                ?: throw RuntimeException("There is no connection created for chain ${chain.id}")

            val batch = BatchRequest(service)
            val requestsWithMetadata: MutableList<Triple<Long, String, MetaAccount>> =
                mutableListOf()
            accounts.forEach { metaAccount ->
                val address = metaAccount.address(chain) ?: return@forEach
                chain.assets.forEach { asset ->
                    kotlin.runCatching {
                        val request = service.getBalanceRequest(asset, address)
                        requestsWithMetadata.add(Triple(request.id, asset.id, metaAccount))
                        batch.add(request)
                    }.getOrNull() ?: return@forEach
                }
            }
            val response = kotlin.runCatching { batch.send() }.getOrNull() ?: return@withContext emptyList()
            response.responses.mapNotNull {
                val metadata =
                    requestsWithMetadata.firstOrNull { request -> request.first == it.id }
                        ?: return@mapNotNull null
                Triple(it, metadata.second, metadata.third)
            }
        }
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

    private fun Web3jService.getBalanceRequest(
        asset: Asset,
        address: String
    ): Request<*, *> {
        return if (asset.isUtility) {
            Request(
                "eth_getBalance",
                listOf(address, DefaultBlockParameterName.LATEST),
                this,
                EthGetBalance::class.java
            )
        } else {
            val erc20GetBalanceFunction = Function(
                "balanceOf",
                listOf(Address(address)),
                emptyList()
            )

            Request(
                "eth_call",
                listOf(
                    Transaction.createEthCallTransaction(
                        null,
                        asset.id,
                        FunctionEncoder.encode(erc20GetBalanceFunction)
                    ), DefaultBlockParameterName.LATEST
                ),
                this,
                EthCall::class.java
            )
        }
    }


    fun listenGas(transfer: Transfer, chain: Chain): Flow<BigInteger> {
        val connection = requireNotNull(ethereumConnectionPool.get(chain.id))
        val wsService = requireNotNull(connection.service)
        val web3j = requireNotNull(connection.web3j)

        return wsService.subscribeNewHeads()
            .map { it.params.result?.baseFeePerGas }
            .onStart {
                val block = web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false)
                    .send()
                    .block
                    .baseFeePerGas
                emit(block)
            }
            .map { baseFeePerGas ->

                val priorityFee = withContext(Dispatchers.IO) {
                    wsService.ethMaxPriorityFeePerGas().send()?.maxPriorityFeePerGas
                }
                    ?: throw GasServiceException("Failed to get ethMaxPriorityFeePerGas on chain ${chain.name}. Response is null")

                val baseFee = Numeric.decodeQuantity(baseFeePerGas)
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
                    val function = Function(
                        "transfer",
                        listOf(Address(transfer.recipient), Uint256.DEFAULT),
                        emptyList()
                    )
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
                    val response = web3j.ethEstimateGas(call).send()
                    if (response.hasError()) {
                        Log.d("&&&", "ethEstimateGas ${response.error.code} ${response.error.message}")
                        throw GasServiceException("Failed to ethEstimateGas on chain ${chain.name}: ${response.error}")
                    }
                    response.amountUsed
                }.getOrNull()
                    ?: throw GasServiceException("Failed to ethEstimateGas on chain ${chain.name}. Response is null")

                gasLimit * (priorityFee + baseFee)
            }
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

class EthereumTransferException(message: String) : Exception(message)


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

fun Web3jService.subscribeNewHeads(): Flow<NewHeadsNotificationExtended> {
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