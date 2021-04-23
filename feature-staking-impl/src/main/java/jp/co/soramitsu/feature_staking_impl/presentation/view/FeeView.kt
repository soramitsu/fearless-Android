package jp.co.soramitsu.feature_staking_impl.presentation.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.utils.setTextOrHide
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.presentation.common.fee.FeeStatus
import kotlinx.android.synthetic.main.view_fee.view.feeContent
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

    fun setFeeStatus(feeStatus: FeeStatus) {
        when (feeStatus) {
            is FeeStatus.Loading -> {
                feeContent.makeGone()
                feeProgress.makeVisible()
            }
            is FeeStatus.Error -> {
                feeToken.text = context.getString(R.string.common_error_general_title)

                feeToken.makeVisible()
                feeFiat.makeGone()
                feeProgress.makeGone()
            }
            is FeeStatus.Loaded -> {
                feeContent.makeVisible()
                feeToken.text = feeStatus.feeModel.displayToken
                feeFiat.setTextOrHide(feeStatus.feeModel.displayFiat)

                feeProgress.makeGone()
            }
        }
    }
}
