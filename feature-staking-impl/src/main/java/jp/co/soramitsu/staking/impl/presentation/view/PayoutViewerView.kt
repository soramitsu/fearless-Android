package jp.co.soramitsu.staking.impl.presentation.view

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.ViewPayoutViewerBinding
import jp.co.soramitsu.staking.impl.presentation.staking.main.model.RewardEstimation

class PayoutViewerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    private val binding: ViewPayoutViewerBinding

    init {
        orientation = VERTICAL

        inflate(context, R.layout.view_payout_viewer, this)
        binding = ViewPayoutViewerBinding.bind(this)
    }

    fun setRewardEstimation(estimation: RewardEstimation) {
        binding.payoutViewerInfo.setPercentageGain(estimation.gain)
        binding.payoutViewerInfo.setTokenAmount(estimation.amount)
        binding.payoutViewerInfo.setFiatAmount(estimation.fiatAmount)
    }

    fun setAccountInfo(addressModel: AddressModel) {
        binding.payoutViewerAccountView.setText(addressModel.address)
        binding.payoutViewerAccountView.setTitle(addressModel.name ?: "")
        binding.payoutViewerAccountView.setAccountIcon(addressModel.image)
    }

    fun setOnViewMoreClickListener(viewMore: () -> Unit) {
        binding.payoutViewerLearnMore.setOnClickListener { viewMore() }
    }
}
