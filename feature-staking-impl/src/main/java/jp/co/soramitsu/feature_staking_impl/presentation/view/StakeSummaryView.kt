package jp.co.soramitsu.feature_staking_impl.presentation.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
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

    sealed class Status(@StringRes val textRes: Int, @ColorRes val tintRes: Int, val extraMessage: String?) {

        class Active(eraDisplay: String) : Status(R.string.staking_nominator_status_active, R.color.green, eraDisplay)

        class Inactive(eraDisplay: String) : Status(R.string.staking_nominator_status_inactive, R.color.red, eraDisplay)

        class Waiting(override val timeLeft: Long, override val hideZeroTimer: Boolean = false) : Status(R.string.staking_nominator_status_waiting, R.color.white_64, null), WithTimer

        class ActiveCollator(override val timeLeft: Long, override val hideZeroTimer: Boolean = false) : Status(R.string.staking_nominator_status_active, R.color.green, "Next round"), WithTimer

        class IdleCollator : Status(R.string.staking_collator_status_idle, R.color.colorGreyText, null)

        class LeavingCollator(override val timeLeft: Long, override val hideZeroTimer: Boolean = true) : Status(R.string.staking_collator_status_leaving, R.color.red, "Waiting execution"), WithTimer

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
    }

    fun hideLoading() {
        binding.stakeTotalStakedView.hideLoading()
        binding.stakeTotalRewardsView.hideLoading()
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

    fun setTotalRewards(inTokens: String) {
        binding.stakeTotalRewardsView.setBody(inTokens)
    }

    fun showTotalRewardsFiat() {
        binding.stakeTotalRewardsView.showWholeExtraBlock()
    }

    fun hideTotalRewardsFiat() {
        binding.stakeTotalRewardsView.makeExtraBlockInvisible()
    }

    fun setTotalRewardsFiat(totalRewards: String) {
        binding.stakeTotalRewardsView.setExtraBlockValueText(totalRewards)
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
