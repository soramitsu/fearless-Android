package jp.co.soramitsu.common.data.network.bitcoin

import jp.co.soramitsu.common.utils.BitcoinKeyDerivation

class BitcoinSendService(
    private val planner: BitcoinSendPlanner,
    private val broadcaster: BitcoinTransactionBroadcaster
) {
    constructor(client: BitcoinIndexerClient) : this(
        planner = BitcoinSendPlanner(client),
        broadcaster = BitcoinTransactionBroadcaster(client)
    )

    suspend fun prepare(request: BitcoinSendRequest): BitcoinPreparedSendTransaction {
        val plan = planner.plan(
            amountSats = request.amountSats,
            sources = request.sources,
            recipientAddress = request.recipientAddress,
            changeAddress = request.changeAddress,
            feeRateSatPerVbyte = request.feeRateSatPerVbyte,
            feeTargetBlocks = request.feeTargetBlocks,
            includeUnconfirmed = request.includeUnconfirmed,
            maxInputs = request.maxInputs,
            network = request.network,
            baseUrl = request.baseUrl
        )
        return prepareConfirmedPlan(request, plan)
    }

    /** Signs only the reviewed plan after checking that its exact inputs remain unspent. */
    suspend fun prepareConfirmedPlan(request: BitcoinSendRequest, plan: BitcoinSendPlan): BitcoinPreparedSendTransaction {
        if (request.network != plan.network || request.amountSats != plan.amountSats ||
            BitcoinIndexerRoutes.normalizeAddress(request.recipientAddress, request.network) != plan.recipientAddress ||
            request.includeUnconfirmed != plan.includeUnconfirmed) {
            throw BitcoinSendServiceException(BitcoinSendServiceException.Code.PLANNED_TRANSACTION_MISMATCH)
        }
        planner.requireInputsUnchanged(plan, request.baseUrl)
        val transaction = BitcoinTransactionBuilder.buildP2wpkhTransaction(
            mnemonic = request.mnemonic,
            passphrase = request.passphrase,
            inputs = plan.selectedUtxos,
            outputs = listOf(BitcoinPaymentOutput(plan.recipientAddress, plan.amountSats)),
            changeAddress = plan.changeAddress,
            feeSats = plan.feeSats,
            network = request.network.toKeyDerivationNetwork()
        )

        if (transaction.feeSats != plan.feeSats || transaction.changeSats != plan.changeSats) {
            throw BitcoinSendServiceException(BitcoinSendServiceException.Code.PLANNED_TRANSACTION_MISMATCH)
        }

        return BitcoinPreparedSendTransaction(
            plan = plan,
            transaction = transaction
        )
    }

    suspend fun send(request: BitcoinSendRequest): BitcoinSentTransaction {
        val prepared = prepare(request)
        return broadcastPrepared(prepared, request.network, request.baseUrl)
    }

    /** Broadcasts previously signed bytes exactly; callers retain them durably before invoking this. */
    suspend fun broadcastPrepared(
        prepared: BitcoinPreparedSendTransaction,
        network: BitcoinIndexerRoutes.Network = prepared.plan.network,
        baseUrl: String? = null
    ): BitcoinSentTransaction {
        require(network == prepared.plan.network)
        val broadcast = broadcaster.broadcast(
            txHex = prepared.transaction.txHex,
            network = network,
            baseUrl = baseUrl
        )

        if (broadcast.txid != prepared.transaction.txid) {
            throw BitcoinSendServiceException(BitcoinSendServiceException.Code.BROADCAST_TXID_MISMATCH)
        }

        return BitcoinSentTransaction(
            broadcastTxid = broadcast.txid,
            prepared = prepared
        )
    }

    private fun BitcoinIndexerRoutes.Network.toKeyDerivationNetwork(): BitcoinKeyDerivation.Network = when (this) {
        BitcoinIndexerRoutes.Network.Mainnet -> BitcoinKeyDerivation.Network.Mainnet
        BitcoinIndexerRoutes.Network.Testnet -> BitcoinKeyDerivation.Network.Testnet
    }
}

data class BitcoinSendRequest(
    val mnemonic: String,
    val amountSats: Long,
    val sources: List<BitcoinUtxoSource>,
    val recipientAddress: String,
    val passphrase: String = "",
    val changeAddress: String? = null,
    val feeRateSatPerVbyte: Double? = null,
    val feeTargetBlocks: Int = BitcoinFeeEstimator.DEFAULT_TARGET_BLOCKS,
    val includeUnconfirmed: Boolean = false,
    val maxInputs: Int = BitcoinUtxoSelector.DEFAULT_MAX_INPUTS,
    val network: BitcoinIndexerRoutes.Network = BitcoinIndexerRoutes.Network.Mainnet,
    val baseUrl: String? = null
)

data class BitcoinPreparedSendTransaction(
    val plan: BitcoinSendPlan,
    val transaction: BitcoinBuiltTransaction
)

data class BitcoinSentTransaction(
    val broadcastTxid: String,
    val prepared: BitcoinPreparedSendTransaction
)

class BitcoinSendServiceException(
    val code: Code
) : IllegalArgumentException(code.name) {
    enum class Code {
        BROADCAST_TXID_MISMATCH,
        PLANNED_TRANSACTION_MISMATCH
    }
}
