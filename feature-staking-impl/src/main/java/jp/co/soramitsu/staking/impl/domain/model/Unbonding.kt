package jp.co.soramitsu.staking.impl.domain.model

import android.os.Build
import java.math.BigInteger
import java.util.Calendar
import java.util.TimeZone
import jp.co.soramitsu.common.data.network.subquery.StakingHistoryRemote
import jp.co.soramitsu.common.data.network.subquery.SubsquidRewardResponse
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.staking.api.domain.model.DelegationAction
import jp.co.soramitsu.staking.api.domain.model.DelegationScheduledRequest
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Locale

data class Unbonding(
    val amount: BigInteger,
    val timeLeft: Long,
    val calculatedAt: Long,
    val type: DelegationAction?
)

fun DelegationScheduledRequest.toUnbonding(timeLeft: Long) = Unbonding(
    amount = this.actionValue,
    timeLeft = timeLeft,
    calculatedAt = System.currentTimeMillis(),
    type = action
)

fun StakingHistoryRemote.HistoryElement.toUnbonding(): Unbonding {
    val timeLeft = this.timestamp?.toLongOrNull()?.let {
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).timeInMillis / 1000 - it
    } ?: 0

    return Unbonding(
        amount = this.amount?.toBigInteger().orZero(),
        timeLeft = timeLeft,
        calculatedAt = System.currentTimeMillis(),
        type = DelegationAction.byId(type?.toInt())
    )
}

fun SubsquidRewardResponse.Reward.toUnbonding(): Unbonding {
    val timeLeft = timestamp?.let {
        parseTimeToMillis(it).let { parsedMillis ->
            (Calendar.getInstance(TimeZone.getTimeZone("UTC")).timeInMillis - parsedMillis) / 1000
        }
    } ?: 0

    return Unbonding(
        amount = amount.orZero(),
        timeLeft = timeLeft,
        calculatedAt = System.currentTimeMillis(),
        type = DelegationAction.REWARD
    )
}

private fun parseTimeToMillis(timestamp: String): Long {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Instant.parse(timestamp).toEpochMilli()
    } else {
        try {
            val subsquidDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSX", Locale.getDefault())
            subsquidDateFormat.parse(timestamp)?.time ?: 0
        } catch (e: Exception) {
            0
        }
    }
}
