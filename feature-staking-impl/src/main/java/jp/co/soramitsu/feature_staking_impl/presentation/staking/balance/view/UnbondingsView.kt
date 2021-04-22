package jp.co.soramitsu.feature_staking_impl.presentation.staking.balance.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.common.view.shape.getCutCornerDrawable
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.presentation.staking.balance.UnbondingsAdapter
import jp.co.soramitsu.feature_staking_impl.presentation.staking.balance.model.UnbondingModel
import kotlinx.android.synthetic.main.view_unbondings.view.unbondingListContainer
import kotlinx.android.synthetic.main.view_unbondings.view.unbondingsList
import kotlinx.android.synthetic.main.view_unbondings.view.unbondingsMoreAction
import kotlinx.android.synthetic.main.view_unbondings.view.unbondingsPlaceholder

class UnbondingsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : ConstraintLayout(context, attrs, defStyle) {

    private val unbondingsAdapter = UnbondingsAdapter()

    init {
        View.inflate(context, R.layout.view_unbondings, this)

        background = context.getCutCornerDrawable(fillColorRes = R.color.blurColor)

        unbondingsList.adapter = unbondingsAdapter
    }

    fun setMoreActionClickListener(listener: OnClickListener) {
        unbondingsMoreAction.setOnClickListener(listener)
    }

    fun submitList(unbondings: List<UnbondingModel>) {
        unbondingsAdapter.submitList(unbondings)

        unbondingListContainer.setVisible(unbondings.isNotEmpty())
        unbondingsPlaceholder.setVisible(unbondings.isEmpty())
        unbondingsMoreAction.isEnabled = unbondings.isNotEmpty()
    }
}
