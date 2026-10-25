package jp.co.soramitsu.wallet.impl.data.historySource

import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.data.network.model.request.GiantsquidHistoryRequest
import jp.co.soramitsu.wallet.impl.data.network.subquery.OperationsHistoryApi
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.Locale

class GiantsquidHistorySource(
    private val walletOperationsApi: OperationsHistoryApi,
    private val url: String
) : HistorySource {

    private val canonicalOperationOrder = compareByDescending<Operation> { it.time }
        .thenBy(Operation::id)
        .thenBy { it.canonicalTypeRank() }
        .thenBy { it.canonicalTypeKey() }

    private val giantsquidDateFormat by lazy {
        SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
            Locale.ROOT
        ).apply {
            isLenient = false
        }
    }

    override suspend fun getOperations(
        pageSize: Int,
        cursor: String?,
        filters: Set<TransactionFilter>,
        accountId: AccountId,
        chain: Chain,
        chainAsset: Asset,
        accountAddress: String
    ): CursorPage<Operation> {
        if (pageSize !in 1..MAX_PAGE_SIZE || filters.isEmpty()) return CursorPage(null, emptyList())

        val offset = if (cursor == null) {
            0
        } else {
            cursor.toIntOrNull()?.takeIf { it >= 0 } ?: return CursorPage(null, emptyList())
        }
        val response = walletOperationsApi.getGiantsquidOperationsHistory(
            url = url,
            GiantsquidHistoryRequest(
                accountAddress = accountAddress,
                limit = pageSize,
                offset = offset,
                filters = filters
            )
        )

        val transfers = filters.firstOrNull { it == TransactionFilter.TRANSFER }?.let {
            response.data.transfers?.map { transfer ->
                Operation(
                    id = transfer.id,
                    address = accountAddress,
                    time = parseTimeToMillis(transfer.transfer.timestamp),
                    chainAsset = chainAsset,
                    type = Operation.Type.Transfer(
                        hash = transfer.transfer.extrinsicHash,
                        myAddress = accountAddress,
                        amount = transfer.transfer.amount.toGiantsquidAmountOrNull().orZero(),
                        receiver = transfer.transfer.to?.id.orEmpty(),
                        sender = transfer.transfer.from?.id.orEmpty(),
                        status = Operation.Status.fromSuccess(transfer.transfer.success),
                        fee = BigInteger.ZERO
                    )
                )
            }
        }.orEmpty()

        val rewards = filters.firstOrNull { it == TransactionFilter.REWARD }?.let {
            response.data.rewards?.map { reward ->
                Operation(
                    id = reward.id,
                    address = accountAddress,
                    time = parseTimeToMillis(reward.timestamp),
                    chainAsset = chainAsset,
                    type = Operation.Type.Reward(
                        amount = reward.amount.toGiantsquidAmountOrNull().orZero(),
                        isReward = true,
                        era = reward.era.orZero().toInt(),
                        validator = reward.validatorId
                    )
                )
            }
        }.orEmpty()

        val slashes = if (TransactionFilter.EXTRINSIC in filters) {
            response.data.slashes?.map { slash ->
                Operation(
                    id = slash.id,
                    address = accountAddress,
                    time = parseTimeToMillis(slash.timestamp),
                    chainAsset = chainAsset,
                    type = Operation.Type.Extrinsic(
                        hash = "",
                        module = "slash",
                        call = "",
                        fee = BigInteger.ZERO,
                        status = Operation.Status.COMPLETED
                    )
                )
            }.orEmpty()
        } else {
            emptyList()
        }

        val bonds = if (TransactionFilter.EXTRINSIC in filters) {
            response.data.bonds?.map { bond ->
                Operation(
                    id = bond.id,
                    address = accountAddress,
                    time = parseTimeToMillis(bond.timestamp),
                    chainAsset = chainAsset,
                    type = Operation.Type.Extrinsic(
                        hash = bond.extrinsicHash.orEmpty(),
                        module = canonicalBondModule(bond.type),
                        call = bond.amount.toGiantsquidAmountOrNull()?.toString().orEmpty(),
                        fee = BigInteger.ZERO,
                        status = Operation.Status.fromSuccess(bond.success == true)
                    )
                )
            }.orEmpty()
        } else {
            emptyList()
        }

        val operations = (transfers + rewards + slashes + bonds)
            .asSequence()
            .filter { it.id.isNotBlank() }
            .sortedWith(canonicalOperationOrder)
            .distinctBy(Operation::id)
            .toList()

        val selectedPageSizes = listOfNotNull(
            response.data.transfers?.size?.takeIf { TransactionFilter.TRANSFER in filters },
            response.data.rewards?.size?.takeIf { TransactionFilter.REWARD in filters },
            response.data.bonds?.size?.takeIf { TransactionFilter.EXTRINSIC in filters },
            response.data.slashes?.size?.takeIf { TransactionFilter.EXTRINSIC in filters }
        )
        val nextCursor = if (
            selectedPageSizes.any { it >= pageSize } &&
            offset <= Int.MAX_VALUE - pageSize
        ) {
            (offset + pageSize).toString()
        } else {
            null
        }

        return CursorPage(nextCursor, operations)
    }

    private fun parseTimeToMillis(timestamp: String): Long {
        val parts = GIANTSQUID_TIMESTAMP.matchEntire(timestamp)?.destructured ?: return 0
        val (dateTime, fraction, timeZone) = parts
        val milliseconds = fraction.padEnd(TIMESTAMP_MILLISECONDS_DIGITS, '0')
            .take(TIMESTAMP_MILLISECONDS_DIGITS)
        val rfc822TimeZone = if (timeZone == "Z") "+0000" else timeZone.replace(":", "")
        val normalizedTimestamp = "$dateTime.$milliseconds$rfc822TimeZone"

        return runCatching {
            synchronized(giantsquidDateFormat) {
                giantsquidDateFormat.parse(normalizedTimestamp)?.time ?: 0
            }
        }.getOrDefault(0)
    }

    private fun canonicalBondModule(type: String?): String {
        if (type == null || type.length > MAX_BOND_TYPE_LENGTH) return "bond"

        return if (type.filter(Char::isLetterOrDigit).lowercase(Locale.ROOT) == "bondandnominate") {
            "bondAndNominate"
        } else {
            "bond"
        }
    }

    private fun String.toGiantsquidAmountOrNull(): BigInteger? {
        if (isEmpty() || length > MAX_AMOUNT_DIGITS || any { it !in '0'..'9' }) return null

        return toBigIntegerOrNull()
    }

    private fun Operation.canonicalTypeRank(): Int {
        return when (type) {
            is Operation.Type.Transfer -> 0
            is Operation.Type.Reward -> 1
            is Operation.Type.Extrinsic -> 2
            is Operation.Type.Swap -> SWAP_TYPE_RANK
        }
    }

    private fun Operation.canonicalTypeKey(): String {
        return when (val operationType = type) {
            is Operation.Type.Transfer -> listOf(
                operationType.hash.orEmpty(),
                operationType.sender,
                operationType.receiver,
                operationType.amount.toString(),
                operationType.status.name,
                operationType.fee?.toString().orEmpty()
            ).joinToString("\u0000")

            is Operation.Type.Reward -> listOf(
                operationType.amount.toString(),
                operationType.era.toString(),
                operationType.validator.orEmpty()
            ).joinToString("\u0000")

            is Operation.Type.Extrinsic -> listOf(
                operationType.hash,
                operationType.module,
                operationType.call,
                operationType.status.name,
                operationType.fee.toString()
            ).joinToString("\u0000")

            is Operation.Type.Swap -> listOf(
                operationType.hash,
                operationType.module,
                operationType.baseAssetAmount.toString(),
                operationType.targetAssetAmount?.toString().orEmpty(),
                operationType.status.name
            ).joinToString("\u0000")
        }
    }

    private companion object {
        const val MAX_AMOUNT_DIGITS = 256
        const val MAX_BOND_TYPE_LENGTH = 64
        const val MAX_PAGE_SIZE = 100
        const val TIMESTAMP_MILLISECONDS_DIGITS = 3
        const val SWAP_TYPE_RANK = 3

        val GIANTSQUID_TIMESTAMP = Regex(
            """^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?(Z|[+-]\d{2}:\d{2})$"""
        )
    }
}
