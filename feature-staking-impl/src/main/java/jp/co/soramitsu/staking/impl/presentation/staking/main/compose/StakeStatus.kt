package jp.co.soramitsu.staking.impl.presentation.staking.main.compose

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import jp.co.soramitsu.feature_staking_impl.R

sealed class StakeStatus(
    @StringRes val textRes: Int,
    @ColorRes val tintRes: Int,
    val extraMessage: String?,
    val statusClickable: Boolean
) {

    class Active(eraDisplay: String) : StakeStatus(
        R.string.staking_nominator_status_active,
        R.color.green, eraDisplay, true)

    class PoolActive(override val timeLeft: Long, override val hideZeroTimer: Boolean) : StakeStatus(
        R.string.staking_nominator_status_active,
        R.color.green, "", true), WithTimer

    class Inactive(eraDisplay: String) : StakeStatus(
        R.string.staking_nominator_status_inactive,
        R.color.red, eraDisplay, true)

    class Waiting(
        override val timeLeft: Long,
        override val hideZeroTimer: Boolean = false
    ) : StakeStatus(R.string.staking_nominator_status_waiting, R.color.white_64, null, true), WithTimer

    class ActiveCollator(
        override val timeLeft: Long,
        override val hideZeroTimer: Boolean = false
    ) : StakeStatus(R.string.staking_nominator_status_active, R.color.green, "Next round", false), WithTimer

    class IdleCollator : StakeStatus(
        R.string.staking_collator_status_idle,
        R.color.colorGreyText, null, false)

    class LeavingCollator(
        override val timeLeft: Long,
        override val hideZeroTimer: Boolean = true
    ) : StakeStatus(
        R.string.staking_collator_status_leaving,
        R.color.red, "Waiting execution", false), WithTimer

    object ReadyToUnlockCollator : StakeStatus(
        R.string.staking_delegation_status_ready_to_unlock,
        R.color.red, null, false)

    interface WithTimer {
        val timeLeft: Long
        val hideZeroTimer: Boolean
    }
}
