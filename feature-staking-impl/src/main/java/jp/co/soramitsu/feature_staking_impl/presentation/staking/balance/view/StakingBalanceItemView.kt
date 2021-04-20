package jp.co.soramitsu.feature_staking_impl.presentation.staking.balance.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import jp.co.soramitsu.common.utils.setTextOrHide
import jp.co.soramitsu.feature_staking_impl.R
import kotlinx.android.synthetic.main.view_staking_balance_item.view.stakingBalanceItemFiat
import kotlinx.android.synthetic.main.view_staking_balance_item.view.stakingBalanceItemKind
import kotlinx.android.synthetic.main.view_staking_balance_item.view.stakingBalanceItemToken

class StakingBalanceItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : ConstraintLayout(context, attrs, defStyle) {

    init {
        View.inflate(context, R.layout.view_staking_balance_item, this)

        attrs?.let(::applyAttrs)
    }

    private fun applyAttrs(attrs: AttributeSet) {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.StakingBalanceItemView)

        val title = typedArray.getString(R.styleable.StakingBalanceItemView_title)
        title?.let { stakingBalanceItemKind.text = title }

        typedArray.recycle()
    }

    fun setTokenAmount(amount: String) {
        stakingBalanceItemToken.text = amount
    }

    fun setFiatAmount(amount: String?) {
        stakingBalanceItemFiat.setTextOrHide(amount)
    }
}
