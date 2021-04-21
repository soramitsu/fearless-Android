package jp.co.soramitsu.feature_staking_impl.presentation.staking.balance.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import jp.co.soramitsu.common.utils.dp
import jp.co.soramitsu.common.utils.updatePadding
import jp.co.soramitsu.common.view.shape.getCutCornerDrawable
import jp.co.soramitsu.feature_staking_impl.R
import kotlinx.android.synthetic.main.view_staking_balance_actions.view.stakingBalanceActionsBondMore
import kotlinx.android.synthetic.main.view_staking_balance_actions.view.stakingBalanceActionsRedeem
import kotlinx.android.synthetic.main.view_staking_balance_actions.view.stakingBalanceActionsUnbond

class StakingBalanceActions
@JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    init {
        orientation = HORIZONTAL

        View.inflate(context, R.layout.view_staking_balance_actions, this)

        background = context.getCutCornerDrawable(R.color.blurColor)

        updatePadding(top = 4.dp(context), bottom = 4.dp(context))
    }

    val bondMore: TextView
        get() = stakingBalanceActionsBondMore

    val unbond: TextView
        get() = stakingBalanceActionsUnbond

    val redeem: TextView
        get() = stakingBalanceActionsRedeem
}
