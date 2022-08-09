package jp.co.soramitsu.featurestakingimpl.presentation.validators.details.view

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.annotation.ColorRes
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.ViewCollatorInfoBinding

class CollatorInfoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    private val binding: ViewCollatorInfoBinding

    init {
        inflate(context, R.layout.view_collator_info, this)
        binding = ViewCollatorInfoBinding.bind(this)

        orientation = VERTICAL
    }

    private val totalStakeFields = listOf(binding.collatorTotalStakeView, binding.collatorEstimatedReward)

    fun setMinBond(value: String) {
        binding.collatorMinBond.setBody(value)
    }

    fun setSelfBonded(value: String) {
        binding.collatorSelfBonded.setBody(value)
    }

    fun setEffectiveAmountBonded(value: String) {
        binding.collatorEffectiveAmountBonded.setBody(value)
    }

    fun setTotalStakeValue(value: String?) {
        binding.collatorTotalStakeView.setBodyOrHide(value)
    }

    fun setTotalStakeValueFiat(fiat: String?) {
        binding.collatorTotalStakeView.setExtraOrHide(fiat)
    }

    fun setDelegationsCount(count: String) {
        binding.collatorDelegations.setBody(count)
    }

    fun setEstimatedRewardApr(reward: String) {
        binding.collatorEstimatedReward.setBody(reward)
    }

    fun setTotalStakeClickListener(clickListener: () -> Unit) {
        binding.collatorTotalStakeView.setOnClickListener { clickListener() }
    }

    fun hideActiveStakeFields() {
        totalStakeFields.forEach(ValidatorInfoItemView::makeGone)
    }

    fun showActiveStakeFields() {
        totalStakeFields.forEach(ValidatorInfoItemView::makeVisible)
    }

    fun setStatus(statusText: String, @ColorRes statusColorRes: Int) {
        binding.collatorStatusView.setBodyOrHide(statusText)
        binding.collatorStatusView.setBodyIconResource(R.drawable.ic_status_indicator, statusColorRes)
    }
}
