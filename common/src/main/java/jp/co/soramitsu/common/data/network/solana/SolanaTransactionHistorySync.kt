package jp.co.soramitsu.common.data.network.solana

import jp.co.soramitsu.common.model.UniversalWalletEcosystem
import jp.co.soramitsu.common.model.UniversalWalletIndexedOperationType
import jp.co.soramitsu.common.model.UniversalWalletIndexedTransaction
import jp.co.soramitsu.common.model.UniversalWalletIndexedTransactionDirection
import jp.co.soramitsu.common.model.UniversalWalletIndexedTransactionStatus
import jp.co.soramitsu.common.model.UniversalWalletIndexerPageInfo
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import java.lang.Math.multiplyExact
import java.math.BigInteger

class SolanaTransactionHistorySync(
    private val client: SolanaIndexerClient
) {
    suspend fun history(
        wallet: String,
        assetId: String = UniversalWalletRegistry.solanaMainnet.nativeAsset.id,
        isNative: Boolean = true,
        network: UniversalWalletRegistry.SolanaNetwork = UniversalWalletRegistry.solanaMainnet,
        baseUrl: String? = null,
        before: String? = null,
        limit: Int = SolanaIndexerRoutes.DEFAULT_LIMIT
    ): SolanaTransactionHistoryPage {
        val resolvedBaseUrl = try {
            SolanaIndexerRoutes.normalizeBaseUrl(baseUrl ?: network.indexerBaseUrl)
        } catch (_: SolanaIndexerRoutes.SolanaIndexerRouteException) {
            throw SolanaTransactionHistoryException(SolanaTransactionHistoryException.Code.INVALID_INPUT)
        }
        validateInputs(wallet, assetId, isNative, network, resolvedBaseUrl, before, limit)

        client.verifyServiceInfo(resolvedBaseUrl)
        val response = client.transactions(
            wallet = wallet,
            baseUrl = resolvedBaseUrl,
            before = before,
            limit = limit
        )
        if (response.wallet != wallet) {
            throw SolanaTransactionHistoryException(SolanaTransactionHistoryException.Code.WALLET_MISMATCH)
        }
        if (response.syncedAt <= 0) {
            throw SolanaTransactionHistoryException(SolanaTransactionHistoryException.Code.INVALID_PAGE)
        }

        val transactions = response.transactions.mapNotNull {
            normalizeTransaction(
                transaction = it,
                wallet = wallet,
                assetId = assetId,
                isNative = isNative,
                network = network,
                syncedAtMillis = response.syncedAt
            )
        }
        val nextCursor = response.nextBefore?.takeIf { isBase58Signature(it) }
        val pageInfo = UniversalWalletIndexerPageInfo(
            nextCursor = nextCursor,
            limit = response.limit,
            total = response.total,
            syncedAtMillis = response.syncedAt
        )
        if (pageInfo.validationErrors().isNotEmpty()) {
            throw SolanaTransactionHistoryException(SolanaTransactionHistoryException.Code.INVALID_PAGE)
        }

        return SolanaTransactionHistoryPage(
            wallet = wallet,
            networkId = network.id,
            chainId = network.chainId,
            assetId = assetId,
            isNative = isNative,
            transactions = transactions,
            pageInfo = pageInfo
        )
    }

    private fun validateInputs(
        wallet: String,
        assetId: String,
        isNative: Boolean,
        network: UniversalWalletRegistry.SolanaNetwork,
        baseUrl: String,
        before: String?,
        limit: Int
    ) {
        try {
            SolanaIndexerRoutes.transactionsUrl(wallet, baseUrl, before, limit)
            if (isNative) {
                if (assetId != network.nativeAsset.id) {
                    throw SolanaTransactionHistoryException(SolanaTransactionHistoryException.Code.INVALID_INPUT)
                }
            } else {
                SolanaIndexerRoutes.tokenMetadataUrl(assetId, baseUrl)
            }
        } catch (error: SolanaTransactionHistoryException) {
            throw error
        } catch (_: SolanaIndexerRoutes.SolanaIndexerRouteException) {
            throw SolanaTransactionHistoryException(SolanaTransactionHistoryException.Code.INVALID_INPUT)
        }
    }

    private fun normalizeTransaction(
        transaction: SolanaWalletTransactionRecord,
        wallet: String,
        assetId: String,
        isNative: Boolean,
        network: UniversalWalletRegistry.SolanaNetwork,
        syncedAtMillis: Long
    ): UniversalWalletIndexedTransaction? {
        if (!isBase58Signature(transaction.signature) || transaction.slot < 0) {
            return null
        }
        val amount = transaction.amountDelta(assetId, isNative) ?: return null
        if (amount == BigInteger.ZERO) {
            return null
        }
        val fee = unsignedIntegerOrNull(transaction.feeLamports ?: "0") ?: return null
        val timestampMillis = try {
            multiplyExact(transaction.timestamp, 1000L)
        } catch (_: ArithmeticException) {
            return null
        }
        if (timestampMillis <= 0) {
            return null
        }
        val status = when (transaction.status) {
            "success" -> UniversalWalletIndexedTransactionStatus.Confirmed
            "failed" -> UniversalWalletIndexedTransactionStatus.Failed
            else -> return null
        }
        val direction = if (amount.signum() < 0) {
            UniversalWalletIndexedTransactionDirection.Outgoing
        } else {
            UniversalWalletIndexedTransactionDirection.Incoming
        }
        val indexed = UniversalWalletIndexedTransaction(
            accountId = network.id,
            ecosystem = UniversalWalletEcosystem.Solana.id,
            chainId = network.chainId,
            transactionId = transaction.signature,
            status = status,
            direction = direction,
            operationType = if (transaction.solswapRoute == null) {
                UniversalWalletIndexedOperationType.Transfer
            } else {
                UniversalWalletIndexedOperationType.Swap
            },
            timestampMillis = timestampMillis,
            amount = amount.abs().toString(),
            assetId = assetId,
            feeAmount = fee.toString(),
            feeAssetId = network.nativeAsset.id,
            blockNumber = transaction.slot.toString(),
            cursor = transaction.signature,
            syncedAtMillis = syncedAtMillis
        )

        return indexed.takeIf { it.validationErrors().isEmpty() }
    }

    private fun SolanaWalletTransactionRecord.amountDelta(
        assetId: String,
        isNative: Boolean
    ): BigInteger? {
        if (isNative) {
            return signedIntegerOrNull(nativeBalanceChangeLamports)
        }

        var total: BigInteger? = null
        tokenBalanceChanges.forEach { change ->
            if (change.mint == assetId) {
                val delta = signedIntegerOrNull(change.amountDelta) ?: return@forEach
                total = (total ?: BigInteger.ZERO) + delta
            }
        }
        return total
    }

    companion object {
        private val SIGNED_INTEGER = Regex("^-?(0|[1-9][0-9]*)$")
        private val UNSIGNED_INTEGER = Regex("^(0|[1-9][0-9]*)$")
        private val BASE58_SIGNATURE = Regex("^[1-9A-HJ-NP-Za-km-z]{64,128}$")

        private fun signedIntegerOrNull(value: String?): BigInteger? {
            return value?.takeIf { SIGNED_INTEGER.matches(it) }?.let { BigInteger(it) }
        }

        private fun unsignedIntegerOrNull(value: String): BigInteger? {
            return value.takeIf { UNSIGNED_INTEGER.matches(it) }?.let { BigInteger(it) }
        }

        private fun isBase58Signature(value: String): Boolean {
            return BASE58_SIGNATURE.matches(value)
        }
    }
}

data class SolanaTransactionHistoryPage(
    val wallet: String,
    val networkId: String,
    val chainId: String,
    val assetId: String,
    val isNative: Boolean,
    val transactions: List<UniversalWalletIndexedTransaction>,
    val pageInfo: UniversalWalletIndexerPageInfo
)

class SolanaTransactionHistoryException(
    val code: Code
) : IllegalArgumentException(code.name) {
    enum class Code {
        INVALID_INPUT,
        WALLET_MISMATCH,
        INVALID_PAGE
    }
}
