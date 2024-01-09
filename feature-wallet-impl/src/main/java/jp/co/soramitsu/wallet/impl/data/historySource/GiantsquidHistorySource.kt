package jp.co.soramitsu.wallet.impl.data.historySource

import android.os.Build
import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.wallet.impl.data.network.model.request.GiantsquidHistoryRequest
import jp.co.soramitsu.wallet.impl.data.network.subquery.OperationsHistoryApi
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Locale

class GiantsquidHistorySource(
    private val walletOperationsApi: OperationsHistoryApi,
    private val url: String
) : HistorySource {

    private val giantsquidDateFormat by lazy {
        SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSX",
            Locale.getDefault()
        )
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
        val page = 0
        val response = walletOperationsApi.getGiantsquidOperationsHistory(
            url = url,
            GiantsquidHistoryRequest(
                accountAddress = accountAddress,
                pageSize,
                pageSize * page
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
                        amount = transfer.transfer.amount.toBigIntegerOrNull().orZero(),
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
                        amount = reward.amount.toBigIntegerOrNull().orZero(),
                        isReward = true,
                        era = reward.era.orZero().toInt(),
                        validator = reward.validatorId
                    )
                )
            }
        }.orEmpty()

        if (TransactionFilter.EXTRINSIC in filters) {
            // todo complete history parse
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
            }
            response.data.bonds?.map { bond ->
                Operation(
                    id = bond.id,
                    address = accountAddress,
                    time = parseTimeToMillis(bond.timestamp),
                    chainAsset = chainAsset,
                    type = Operation.Type.Extrinsic(
                        hash = bond.extrinsicHash.orEmpty(),
                        module = "bond",
                        call = bond.amount,
                        fee = BigInteger.ZERO,
                        status = Operation.Status.fromSuccess(bond.success == true)
                    )
                )
            }
        }

        val operations = transfers + rewards
        return CursorPage(null, operations)
    }

    private fun parseTimeToMillis(timestamp: String): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Instant.parse(timestamp).toEpochMilli()
        } else {
            try {
                giantsquidDateFormat.parse(timestamp)?.time ?: 0
            } catch (e: Exception) {
                0
            }
        }
    }
}
