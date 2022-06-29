package jp.co.soramitsu.feature_staking_impl.presentation.staking.balance.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintLayout
import jp.co.soramitsu.common.utils.setTextOrHide
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.ViewStakingBalanceItemBinding

class StakingBalanceItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : ConstraintLayout(context, attrs, defStyle) {

    private val binding: ViewStakingBalanceItemBinding

    init {
        inflate(context, R.layout.view_staking_balance_item, this)
        binding = ViewStakingBalanceItemBinding.bind(this)

        attrs?.let(::applyAttrs)
    }

    private fun applyAttrs(attrs: AttributeSet) {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.StakingBalanceItemView)

        val title = typedArray.getString(R.styleable.StakingBalanceItemView_title)
        title?.let { binding.stakingBalanceItemKind.text = title }

        typedArray.recycle()
    }

    fun setTitle(title: String) {
        binding.stakingBalanceItemKind.text = title
    }

    fun setTitle(@StringRes titleResId: Int) {
        binding.stakingBalanceItemKind.text = context.getText(titleResId)
    }

    fun setTokenAmount(amount: String) {
        binding.stakingBalanceItemToken.text = amount
    }

    fun setFiatAmount(amount: String?) {
        binding.stakingBalanceItemFiat.setTextOrHide(amount)
    }
}
