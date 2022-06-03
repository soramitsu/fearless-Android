package jp.co.soramitsu.feature_staking_impl.presentation.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.model.RewardEstimation
import kotlinx.android.synthetic.main.view_payout_viewer.view.payoutViewerAccountView
import kotlinx.android.synthetic.main.view_payout_viewer.view.payoutViewerInfo
import kotlinx.android.synthetic.main.view_payout_viewer.view.payoutViewerLearnMore

class PayoutViewerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayout(context, attrs, defStyle) {

    init {
        orientation = VERTICAL

        View.inflate(context, R.layout.view_payout_viewer, this)

    }

    fun setRewardEstimation(estimation: RewardEstimation) {
        payoutViewerInfo.setPercentageGain(estimation.gain)
        payoutViewerInfo.setTokenAmount(estimation.amount)
        payoutViewerInfo.setFiatAmount(estimation.fiatAmount)
    }

    fun setAccountInfo(addressModel: AddressModel) {
        payoutViewerAccountView.setText(addressModel.address)
        payoutViewerAccountView.setTitle(addressModel.name ?: "")
        payoutViewerAccountView.setAccountIcon(addressModel.image)
    }

    fun setOnViewMoreClickListener(viewMore: () -> Unit) {
        payoutViewerLearnMore.setOnClickListener { viewMore() }
    }
}
