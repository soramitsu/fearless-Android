
package jp.co.soramitsu.feature_staking_impl.presentation.payouts.list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.mixin.impl.observeRetries
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import jp.co.soramitsu.feature_staking_impl.presentation.payouts.list.model.PendingPayoutsStatisticsModel
import kotlinx.android.synthetic.main.fragment_payouts_list.payoutListContentGroup
import kotlinx.android.synthetic.main.fragment_payouts_list.payoutsList
import kotlinx.android.synthetic.main.fragment_payouts_list.payoutsListAll
import kotlinx.android.synthetic.main.fragment_payouts_list.payoutsListContainer
import kotlinx.android.synthetic.main.fragment_payouts_list.payoutsListProgress
import kotlinx.android.synthetic.main.fragment_payouts_list.payoutsListToolbar
import kotlinx.android.synthetic.main.fragment_payouts_list.payoutsPlaceholderGroup

class PayoutsListFragment : BaseFragment<PayoutsListViewModel>(), PayoutAdapter.ItemHandler {

    lateinit var adapter: PayoutAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_payouts_list, container, false)
    }

    override fun initViews() {
        payoutsListContainer.applyInsetter {
            type(statusBars = true) {
                padding()
            }
        }

        adapter = PayoutAdapter(this)
        payoutsList.adapter = adapter

        payoutsList.setHasFixedSize(true)

        payoutsListToolbar.setHomeButtonListener { viewModel.backClicked() }
        onBackPressed { viewModel.backClicked() }

        payoutsListAll.setOnClickListener {
            viewModel.payoutAllClicked()
        }
    }

    override fun inject() {
        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .payoutsListFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: PayoutsListViewModel) {
        viewModel.payoutsStatisticsState.observe {
            if (it is LoadingState.Loaded<PendingPayoutsStatisticsModel>) {
                val placeholderVisible = it.data.placeholderVisible

                payoutListContentGroup.setVisible(placeholderVisible.not())
                payoutsPlaceholderGroup.setVisible(placeholderVisible)
                payoutsListProgress.makeGone()

                adapter.submitList(it.data.payouts)

                payoutsListAll.text = it.data.payoutAllTitle
            }
        }

        observeRetries(viewModel)
    }

    override fun payoutClicked(index: Int) {
        viewModel.payoutClicked(index)
    }
}
