package jp.co.soramitsu.feature_staking_impl.presentation.validators.details.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.ColorRes
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.feature_staking_impl.R
import kotlinx.android.synthetic.main.view_collator_info.view.collatorDelegations
import kotlinx.android.synthetic.main.view_collator_info.view.collatorEffectiveAmountBonded
import kotlinx.android.synthetic.main.view_collator_info.view.collatorEstimatedReward
import kotlinx.android.synthetic.main.view_collator_info.view.collatorMinBond
import kotlinx.android.synthetic.main.view_collator_info.view.collatorSelfBonded
import kotlinx.android.synthetic.main.view_collator_info.view.collatorStatusView
import kotlinx.android.synthetic.main.view_collator_info.view.collatorTotalStakeView

class CollatorInfoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    init {
        View.inflate(context, R.layout.view_collator_info, this)

        orientation = VERTICAL
    }

    private val totalStakeFields = listOf(collatorTotalStakeView, collatorEstimatedReward)

    fun setMinBond(value: String) {
        collatorMinBond.setBody(value)
    }

    fun setSelfBonded(value: String) {
        collatorSelfBonded.setBody(value)
    }

    fun setEffectiveAmountBonded(value: String) {
        collatorEffectiveAmountBonded.setBody(value)
    }

    fun setTotalStakeValue(value: String) {
        collatorTotalStakeView.setBody(value)
    }

    fun setTotalStakeValueFiat(fiat: String?) {
        collatorTotalStakeView.setExtraOrHide(fiat)
    }

    fun setDelegationsCount(count: String) {
        collatorDelegations.setBody(count)
    }

    fun setEstimatedRewardApr(reward: String) {
        collatorEstimatedReward.setBody(reward)
    }

    fun setTotalStakeClickListener(clickListener: () -> Unit) {
        collatorTotalStakeView.setOnClickListener { clickListener() }
    }

    fun hideActiveStakeFields() {
        totalStakeFields.forEach(ValidatorInfoItemView::makeGone)
    }

    fun showActiveStakeFields() {
        totalStakeFields.forEach(ValidatorInfoItemView::makeVisible)
    }

    fun setStatus(statusText: String, @ColorRes statusColorRes: Int) {
        collatorStatusView.setBodyOrHide(statusText)
        collatorStatusView.setBodyIconResource(R.drawable.ic_status_indicator, statusColorRes)
    }
}
