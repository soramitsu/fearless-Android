package jp.co.soramitsu.staking.impl.presentation.staking.balance.view

import android.content.Context
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import jp.co.soramitsu.common.view.shape.getCutCornerDrawable
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.ViewStakingBalanceBinding

class StakingBalanceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : ConstraintLayout(context, attrs, defStyle) {

    private val binding: ViewStakingBalanceBinding

    val bonded: StakingBalanceItemView
        get() = binding.stakingBalanceBonded

    val unbonding: StakingBalanceItemView
        get() = binding.stakingBalanceUnbonding

    val redeemable: StakingBalanceItemView
        get() = binding.stakingBalanceRedeemable

    init {
        inflate(context, R.layout.view_staking_balance, this)
        binding = ViewStakingBalanceBinding.bind(this)

        background = context.getCutCornerDrawable(fillColorRes = R.color.blurColor)
    }
}
