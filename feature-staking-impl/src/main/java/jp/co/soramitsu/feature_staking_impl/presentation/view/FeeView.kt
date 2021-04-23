package jp.co.soramitsu.feature_staking_impl.presentation.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.presentation.common.fee.FeeStatus
import jp.co.soramitsu.feature_staking_impl.presentation.common.fee.FeeViews
import jp.co.soramitsu.feature_staking_impl.presentation.common.fee.displayFeeStatus
import kotlinx.android.synthetic.main.view_fee.view.feeFiat
import kotlinx.android.synthetic.main.view_fee.view.feeProgress
import kotlinx.android.synthetic.main.view_fee.view.feeToken

class FeeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : ConstraintLayout(context, attrs, defStyle) {

    init {
        View.inflate(context, R.layout.view_fee, this)
    }

    private val feeViews by lazy {
        FeeViews(feeProgress, feeFiat, feeToken)
    }

    fun setFeeStatus(feeStatus: FeeStatus) {
        displayFeeStatus(feeStatus, feeViews, hiddenState = View.INVISIBLE)
    }
}
