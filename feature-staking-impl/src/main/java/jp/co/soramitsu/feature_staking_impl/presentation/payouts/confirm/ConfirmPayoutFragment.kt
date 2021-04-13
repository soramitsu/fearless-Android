package jp.co.soramitsu.feature_staking_impl.presentation.payouts.confirm

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import jp.co.soramitsu.feature_staking_impl.presentation.payouts.model.PendingPayoutParcelable
import java.util.ArrayList

class ConfirmPayoutFragment : BaseFragment<ConfirmPayoutViewModel>() {

    companion object {
        private const val KEY_PAYOUTS = "validator"

        fun getBundle(payouts: ArrayList<PendingPayoutParcelable>): Bundle {
            return Bundle().apply {
                putParcelableArrayList(KEY_PAYOUTS, payouts)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_confirm_payout, container, false)
    }

    override fun initViews() {

    }

    override fun inject() {
        val payouts = argument<List<PendingPayoutParcelable>>(KEY_PAYOUTS)

        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .confirmPayoutFactory()
            .create(this, payouts)
            .inject(this)
    }

    override fun subscribe(viewModel: ConfirmPayoutViewModel) {
    }
}
