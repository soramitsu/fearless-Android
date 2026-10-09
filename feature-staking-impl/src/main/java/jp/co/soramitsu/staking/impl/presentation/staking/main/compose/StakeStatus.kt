package jp.co.soramitsu.staking.impl.presentation.staking.main.compose

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import jp.co.soramitsu.feature_staking_impl.R

internal const val MAX_STAKING_TIMER_MILLIS = 315_360_000_000L // ten years

internal fun sanitizeStakingTimerMillis(value: Long): Long =
    value.coerceIn(0L, MAX_STAKING_TIMER_MILLIS)

sealed class StakeStatus(
    @StringRes val textRes: Int,
    @ColorRes val tintRes: Int,
    val extraMessage: String?,
    val statusClickable: Boolean
) {

    class Active(eraDisplay: String) : StakeStatus(
        R.string.staking_nominator_status_active,
        R.color.green,
        eraDisplay,
        true
    )

    class PoolActive(timeLeft: Long, override val hideZeroTimer: Boolean) :
        StakeStatus(
            R.string.staking_nominator_status_active,
            R.color.green,
            "",
            true
        ),
        WithTimer {
        override val timeLeft: Long = sanitizeStakingTimerMillis(timeLeft)
    }

    class Inactive(eraDisplay: String) : StakeStatus(
        R.string.staking_nominator_status_inactive,
        R.color.red,
        eraDisplay,
        true
    )

    object PoolHasNoValidators : StakeStatus(
        R.string.staking_set_validators_message,
        R.color.warning_orange,
        null,
        false
    )

    class Waiting(
        timeLeft: Long,
        override val hideZeroTimer: Boolean = false
    ) : StakeStatus(R.string.staking_nominator_status_waiting, R.color.white_64, null, true), WithTimer {
        override val timeLeft: Long = sanitizeStakingTimerMillis(timeLeft)
    }

    class ActiveCollator(
        timeLeft: Long,
        override val hideZeroTimer: Boolean = false
    ) : StakeStatus(R.string.staking_nominator_status_active, R.color.green, "Next round", false), WithTimer {
        override val timeLeft: Long = sanitizeStakingTimerMillis(timeLeft)
    }

    class IdleCollator : StakeStatus(
        R.string.staking_collator_status_idle,
        R.color.colorGreyText,
        null,
        false
    )

    class LeavingCollator(
        timeLeft: Long,
        override val hideZeroTimer: Boolean = true
    ) : StakeStatus(
        R.string.staking_collator_status_leaving,
        R.color.red,
        "Waiting execution",
        false
    ), WithTimer {
        override val timeLeft: Long = sanitizeStakingTimerMillis(timeLeft)
    }

    object UnavailableCollator : StakeStatus(
        R.string.common_unknown,
        R.color.warning_orange,
        null,
        false
    )

    object ReadyToUnlockCollator : StakeStatus(
        R.string.staking_delegation_status_ready_to_unlock,
        R.color.red,
        null,
        false
    )

    interface WithTimer {
        val timeLeft: Long
        val hideZeroTimer: Boolean
    }
}
