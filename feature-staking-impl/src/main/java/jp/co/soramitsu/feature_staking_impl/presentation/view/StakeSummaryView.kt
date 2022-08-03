package jp.co.soramitsu.feature_staking_impl.presentation.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import jp.co.soramitsu.common.utils.setCompoundDrawableTint
import jp.co.soramitsu.common.utils.setTextColorRes
import jp.co.soramitsu.common.view.shape.addRipple
import jp.co.soramitsu.common.view.shape.getCutCornerDrawable
import jp.co.soramitsu.common.view.startTimer
import jp.co.soramitsu.common.view.stopTimer
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.ViewStakeSummaryBinding

class StakeSummaryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayout(context, attrs, defStyle) {

    sealed class Status(
        @StringRes val textRes: Int,
        @ColorRes val tintRes: Int,
        val extraMessage: String?,
        val statusClickable: Boolean
    ) {

        class Active(eraDisplay: String) : Status(R.string.staking_nominator_status_active, R.color.green, eraDisplay, true)

        class Inactive(eraDisplay: String) : Status(R.string.staking_nominator_status_inactive, R.color.red, eraDisplay, true)

        class Waiting(
            override val timeLeft: Long,
            override val hideZeroTimer: Boolean = false
        ) : Status(R.string.staking_nominator_status_waiting, R.color.white_64, null, true), WithTimer

        class ActiveCollator(
            override val timeLeft: Long,
            override val hideZeroTimer: Boolean = false
        ) : Status(R.string.staking_nominator_status_active, R.color.green, "Next round", false), WithTimer

        class IdleCollator : Status(R.string.staking_collator_status_idle, R.color.colorGreyText, null, false)

        class LeavingCollator(
            override val timeLeft: Long,
            override val hideZeroTimer: Boolean = true
        ) : Status(R.string.staking_collator_status_leaving, R.color.red, "Waiting execution", false), WithTimer

        object ReadyToUnlockCollator : Status(R.string.staking_delegation_status_ready_to_unlock, R.color.red, null, false)

        interface WithTimer {
            val timeLeft: Long
            val extraMessage: String?
            val hideZeroTimer: Boolean
        }
    }

    private val binding: ViewStakeSummaryBinding

    init {
        inflate(context, R.layout.view_stake_summary, this)
        binding = ViewStakeSummaryBinding.bind(this)

        orientation = VERTICAL

        with(context) {
            background = addRipple(getCutCornerDrawable(R.color.blurColor))
        }
    }

    fun setElectionStatus(status: Status) {
        with(binding.stakeSummaryStatus) {
            setCompoundDrawableTint(status.tintRes)
            setTextColorRes(status.tintRes)
            setText(status.textRes)
        }

        if (status is Status.WithTimer) {
            binding.stakeSummaryStatusHelper.startTimer(millis = status.timeLeft, extraMessage = status.extraMessage, hideZeroTimer = status.hideZeroTimer)
        } else {
            binding.stakeSummaryStatusHelper.stopTimer()
            binding.stakeSummaryStatusHelper.text = status.extraMessage
        }
        binding.imageView2.isVisible = status.statusClickable
    }

    fun hideLoading() {
        binding.stakeTotalStakedView.hideLoading()
        binding.stakeRewardsAprView.hideLoading()
    }

    fun setTotalStaked(inTokens: String) {
        binding.stakeTotalStakedView.setBody(inTokens)
    }

    fun showTotalStakedFiat() {
        binding.stakeTotalStakedView.showWholeExtraBlock()
    }

    fun hideTotalStakeFiat() {
        binding.stakeTotalStakedView.makeExtraBlockInvisible()
    }

    fun setTotalStakedFiat(totalStake: String) {
        binding.stakeTotalStakedView.setExtraBlockValueText(totalStake)
    }

    fun setRewardsApr(value: String) {
        binding.stakeRewardsAprView.setBody(value)
    }

    fun showRewardsAprFiat() {
        binding.stakeRewardsAprView.showWholeExtraBlock()
    }

    fun hideRewardsAprFiat() {
        binding.stakeRewardsAprView.makeExtraBlockInvisible()
    }

    fun setRewardsAprFiat(totalRewards: String) {
        binding.stakeRewardsAprView.setExtraBlockValueText(totalRewards)
    }

    fun setStatusClickListener(listener: OnClickListener) {
        binding.statusTapZone.setOnClickListener(listener)
    }

    fun setStakeInfoClickListener(listener: OnClickListener) {
        setOnClickListener(listener)
    }

    fun setTitle(title: String) {
        binding.stakeSummaryTitle.text = title
    }

    val moreActions: View
        get() = binding.stakeMoreActions
}
