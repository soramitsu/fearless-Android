package jp.co.soramitsu.wallet.impl.data.network.blockchain

import java.math.BigInteger
import jp.co.soramitsu.common.utils.failure
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumChainConnection
import jp.co.soramitsu.shared_utils.extensions.requireHexPrefix
import jp.co.soramitsu.wallet.impl.data.network.model.EvmTransfer
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.RawTransaction
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.utils.Numeric

class EthereumTransactionBuilder(ethereumWebSocketConnection: EthereumChainConnection) {

    private val service = ethereumWebSocketConnection.service
        ?: throw IllegalStateException("There is no connection established for chain ${ethereumWebSocketConnection.chain.name}, ${ethereumWebSocketConnection.chain.id}")
    private val web3j = ethereumWebSocketConnection.web3j
        ?: throw IllegalStateException("There is no connection established for chain ${ethereumWebSocketConnection.chain.name}, ${ethereumWebSocketConnection.chain.id}")

    private val erc20EIP1559TransferCallBuilder: (EvmTransfer) -> Transaction? = {
        Transaction(
            /* from = */ it.sender,
            /* nonce = */ it.nonce,
            /* gasPrice = */ it.gasPrice,
            /* gasLimit = */ it.gasLimit,
            /* to = */ it.chainAsset.id,
            /* value = */ BigInteger.ZERO,
            /* data = */ erc20TransferFunction(it.recipient, it.amount).encode(),
            /* chainId = */ it.chainAsset.chainId.requireHexPrefix().drop(2).toLong(),
            /* maxPriorityFeePerGas = */ it.maxPriorityFeePerGas,
            /* maxFeePerGas = */ it.maxFeePerGas
        )
    }

    private val erc20LegacyTransferCallBuilder: (EvmTransfer) -> Transaction? = {
        Transaction.createFunctionCallTransaction(
            /* from = */ it.sender,
            /* nonce = */ it.nonce,
            /* gasPrice = */ it.gasPrice,
            /* gasLimit = */ it.gasLimit,
            /* to = */ it.chainAsset.id,
            /* value = */ BigInteger.ZERO,
            /* data = */ erc20TransferFunction(it.recipient, it.amount).encode()
        )
    }

    private val erc20EIP1559RawTransferCallBuilder: (EvmTransfer) -> RawTransaction = {
        RawTransaction.createTransaction(
            /* chainId = */ it.chainAsset.chainId.requireHexPrefix().drop(2).toLong(),
            /* nonce = */ it.nonce,
            /* gasLimit = */ it.gasLimit,
            /* to = */ it.chainAsset.id,
            /* value = */ BigInteger.ZERO,
            /* data = */ erc20TransferFunction(it.recipient, it.amount).encode(),
            /* maxPriorityFeePerGas = */ it.maxPriorityFeePerGas,
            /* maxFeePerGas = */ it.maxFeePerGas
        )
    }
    private val erc20LegacyRawTransferCallBuilder: (EvmTransfer) -> RawTransaction = {
        RawTransaction.createTransaction(
            /* nonce = */ it.nonce,
            /* gasPrice = */ it.gasPrice,
            /* gasLimit = */ it.gasLimit,
            /* to = */ it.chainAsset.id,
            /* value = */ BigInteger.ZERO,
            /* data = */ erc20TransferFunction(it.recipient, it.amount).encode(),
        )
    }

    private val legacyTransactionCallBuildersByAssetType: HashMap<ChainAssetType, (EvmTransfer) -> Transaction?> =
        hashMapOf(
            ChainAssetType.Normal to {
                Transaction.createEtherTransaction(
                    /* from = */ it.sender,
                    /* nonce = */ it.nonce,
                    /* gasPrice = */ it.gasPrice,
                    /* gasLimit = */ it.gasLimit,
                    /* to = */ it.recipient,
                    /* value = */ it.amount
                )
            },
            ChainAssetType.ERC20 to erc20LegacyTransferCallBuilder,
            ChainAssetType.BEP20 to erc20LegacyTransferCallBuilder
        )

    private val eip1559TransactionCallBuildersByAssetType: HashMap<ChainAssetType, (EvmTransfer) -> Transaction?> =
        hashMapOf(
            ChainAssetType.Normal to {
                Transaction(
                    /* from = */ it.sender,
                    /* nonce = */ it.nonce,
                    /* gasPrice = */ it.gasPrice,
                    /* gasLimit = */ it.gasLimit,
                    /* to = */ it.recipient,
                    /* value = */ it.amount,
                    /* data = */ null,
                    /* chainId = */ it.chainAsset.chainId.requireHexPrefix().drop(2).toLong(),
                    /* maxPriorityFeePerGas = */ it.maxPriorityFeePerGas,
                    /* maxFeePerGas = */ it.maxFeePerGas
                )
            },
            ChainAssetType.ERC20 to erc20EIP1559TransferCallBuilder,
            ChainAssetType.BEP20 to erc20EIP1559TransferCallBuilder
        )

    private val eip1559RawTransactionCallBuildersByAssetType: HashMap<ChainAssetType, (EvmTransfer) -> RawTransaction> =
        hashMapOf(
            ChainAssetType.Normal to {
                RawTransaction.createEtherTransaction(
                    /* chainId = */ it.chainAsset.chainId.requireHexPrefix().drop(2).toLong(),
                    /* nonce = */ it.nonce,
                    /* gasLimit = */ it.gasLimit,
                    /* to = */ it.recipient,
                    /* value = */ it.amount,
                    /* maxPriorityFeePerGas = */ it.maxPriorityFeePerGas,
                    /* maxFeePerGas = */ it.maxFeePerGas
                )
            },
            ChainAssetType.ERC20 to erc20EIP1559RawTransferCallBuilder,
            ChainAssetType.BEP20 to erc20EIP1559RawTransferCallBuilder
        )

    private val legacyRawTransactionCallBuildersByAssetType: HashMap<ChainAssetType, (EvmTransfer) -> RawTransaction> =
        hashMapOf(
            ChainAssetType.Normal to {
                RawTransaction.createEtherTransaction(
                    /* nonce = */ it.nonce,
                    /* gasPrice = */ it.gasPrice,
                    /* gasLimit = */ it.gasLimit,
                    /* to = */ it.recipient,
                    /* value = */ it.amount,
                )
            },
            ChainAssetType.ERC20 to erc20LegacyRawTransferCallBuilder,
            ChainAssetType.BEP20 to erc20LegacyRawTransferCallBuilder
        )

    fun build(transfer: Transfer): Result<RawTransaction> {
        val (baseFeePerGas, maxPriorityFeePerGas) = getFees()
        val chainAsset = transfer.chainAsset

        val nonce =
            kotlin.runCatching {
                web3j.ethGetTransactionCount(transfer.sender, DefaultBlockParameterName.PENDING)
                    .send().transactionCount
            }
                .getOrElse { return Result.failure("Error ethGetTransactionCount for chain ${chainAsset.chainName}, ${chainAsset.chainId}, error: $it") }

        val evmTransfer = EvmTransfer.createFromTransfer(transfer, nonce)
        val callForEstimateGas = buildFeeEstimationCall(evmTransfer)

        val gasLimit = kotlin.runCatching {
            web3j.ethEstimateGas(callForEstimateGas).send()
                .resultOrThrow()
                .let { Numeric.decodeQuantity(it) }
        }
            .getOrElse { return Result.failure("Error ethEstimateGas for chain ${chainAsset.chainName}, ${chainAsset.chainId}, error: $it") }

        val gasPrice = runCatching {
            val response = web3j.ethGasPrice().send()
            response.gasPrice
        }.getOrNull()
            ?: throw GasServiceException("Failed to get ethGasPrice on chain ${chainAsset.chainName}. Response is null")

        return if (baseFeePerGas != null && maxPriorityFeePerGas != null) {
            // eip1559
            val maxFeePerGas = baseFeePerGas + maxPriorityFeePerGas

            eip1559RawTransactionCallBuildersByAssetType[chainAsset.type]?.invoke(
                EvmTransfer.createFromTransfer(
                    transfer = transfer,
                    nonce = nonce,
                    gasLimit = gasLimit,
                    maxPriorityFeePerGas = gasPrice,
                    maxFeePerGas = gasPrice,
                    gasPrice = gasPrice
                )
            )
        } else {
            // legacy

            legacyRawTransactionCallBuildersByAssetType[chainAsset.type]?.invoke(
                EvmTransfer.createFromTransfer(
                    transfer = transfer,
                    nonce = nonce,
                    gasLimit = gasLimit,
                    gasPrice = gasPrice
                )
            )
        }?.let { Result.success(it) } ?: Result.failure("Cannot find ")
    }

    fun buildFeeEstimationCall(transfer: Transfer): Transaction? {
        val evmTransfer = EvmTransfer(
            transfer.sender,
            recipient = transfer.recipient,
            amount = null,
            chainAsset = transfer.chainAsset
        )
        return buildFeeEstimationCall(evmTransfer)
    }

    private fun buildFeeEstimationCall(evmTransfer: EvmTransfer): Transaction? {
        return legacyTransactionCallBuildersByAssetType[evmTransfer.chainAsset.type]?.invoke(
            evmTransfer
        )
    }

    private fun getFees(): Pair<BigInteger?, BigInteger?> {
        val baseFeePerGas = runCatching {
            web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false)
                .send()
                .resultOrThrow()
                .baseFeePerGas
                .let { Numeric.decodeQuantity(it) }
        }.getOrNull()

        val maxPriorityFeePerGas = runCatching {
            service.ethMaxPriorityFeePerGas().send().maxPriorityFeePerGas
        }.getOrNull()

        return baseFeePerGas to maxPriorityFeePerGas
    }
}


fun erc20TransferFunction(recipient: String, amount: BigInteger?): Function {
    return Function(
        "transfer",
        listOf(
            Address(recipient),
            amount?.let { nonNullAmount -> Uint256(nonNullAmount) } ?: Uint256.DEFAULT),
        listOf(TypeReference.create(Bool::class.java))
    )
}

fun Function.encode(): String {
    return FunctionEncoder.encode(this)
}