package jp.co.soramitsu.staking.impl.presentation.staking.balance.view

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView
import jp.co.soramitsu.common.utils.dp
import jp.co.soramitsu.common.utils.updatePadding
import jp.co.soramitsu.common.view.shape.getCutCornerDrawable
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.ViewStakingBalanceActionsBinding

class StakingBalanceActions
@JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    private val binding: ViewStakingBalanceActionsBinding

    init {
        orientation = HORIZONTAL

        inflate(context, R.layout.view_staking_balance_actions, this)
        binding = ViewStakingBalanceActionsBinding.bind(this)

        background = context.getCutCornerDrawable(R.color.blurColor)

        updatePadding(top = 4.dp(context), bottom = 4.dp(context))
    }

    val bondMore: TextView
        get() = binding.stakingBalanceActionsBondMore

    val unbond: TextView
        get() = binding.stakingBalanceActionsUnbond

    val redeem: TextView
        get() = binding.stakingBalanceActionsRedeem
}
