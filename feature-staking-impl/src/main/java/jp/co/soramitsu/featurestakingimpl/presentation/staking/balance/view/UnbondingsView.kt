package jp.co.soramitsu.featurestakingimpl.presentation.staking.balance.view

import android.content.Context
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.common.view.shape.getCutCornerDrawable
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.ViewUnbondingsBinding
import jp.co.soramitsu.featurestakingimpl.presentation.staking.balance.UnbondingsAdapter
import jp.co.soramitsu.featurestakingimpl.presentation.staking.balance.model.UnbondingModel

class UnbondingsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : ConstraintLayout(context, attrs, defStyle) {

    private val unbondingsAdapter = UnbondingsAdapter()

    private val binding: ViewUnbondingsBinding

    init {
        inflate(context, R.layout.view_unbondings, this)
        binding = ViewUnbondingsBinding.bind(this)

        background = context.getCutCornerDrawable(fillColorRes = R.color.blurColor)

        binding.unbondingsList.adapter = unbondingsAdapter
    }

    val unbondingsMoreAction = binding.unbondingsMoreAction
    val title = binding.unbondingsTitle

    fun setMoreActionClickListener(listener: OnClickListener) {
        binding.unbondingsMoreAction.setOnClickListener(listener)
    }

    fun submitList(unbondings: List<UnbondingModel>) {
        unbondingsAdapter.submitList(unbondings)

        binding.unbondingListContainer.setVisible(unbondings.isNotEmpty())
        binding.unbondingsPlaceholder.setVisible(unbondings.isEmpty())
    }
}
