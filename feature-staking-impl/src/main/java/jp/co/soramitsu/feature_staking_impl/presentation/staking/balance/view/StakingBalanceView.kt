package jp.co.soramitsu.feature_staking_impl.presentation.staking.balance.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import jp.co.soramitsu.common.view.shape.getCutCornerDrawable
import jp.co.soramitsu.feature_staking_impl.R
import kotlinx.android.synthetic.main.view_staking_balance.view.stakingBalanceBonded
import kotlinx.android.synthetic.main.view_staking_balance.view.stakingBalanceRedeemable
import kotlinx.android.synthetic.main.view_staking_balance.view.stakingBalanceUnbonding

class StakingBalanceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : ConstraintLayout(context, attrs, defStyle) {

    val bonded: StakingBalanceItemView
        get() = stakingBalanceBonded

    val unbonding: StakingBalanceItemView
        get() = stakingBalanceUnbonding

    val redeemable: StakingBalanceItemView
        get() = stakingBalanceRedeemable

    init {
        View.inflate(context, R.layout.view_staking_balance, this)

        background = context.getCutCornerDrawable(fillColorRes = R.color.blurColor)
    }
}
