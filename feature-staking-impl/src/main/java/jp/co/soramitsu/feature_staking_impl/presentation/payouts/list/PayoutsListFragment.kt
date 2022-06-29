
package jp.co.soramitsu.feature_staking_impl.presentation.payouts.list

import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.mixin.impl.observeRetries
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentPayoutsListBinding
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import jp.co.soramitsu.feature_staking_impl.presentation.payouts.list.model.PendingPayoutsStatisticsModel

class PayoutsListFragment : BaseFragment<PayoutsListViewModel>(R.layout.fragment_payouts_list), PayoutAdapter.ItemHandler {

    lateinit var adapter: PayoutAdapter

    private val binding by viewBinding(FragmentPayoutsListBinding::bind)

    override fun initViews() {
        binding.payoutsListContainer.applyInsetter {
            type(statusBars = true) {
                padding()
            }
        }

        adapter = PayoutAdapter(this)
        binding.payoutsList.adapter = adapter

        binding.payoutsList.setHasFixedSize(true)

        binding.payoutsListToolbar.setHomeButtonListener { viewModel.backClicked() }
        onBackPressed { viewModel.backClicked() }

        binding.payoutsListAll.setOnClickListener {
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

                binding.payoutListContentGroup.setVisible(placeholderVisible.not())
                binding.payoutsPlaceholderGroup.setVisible(placeholderVisible)
                binding.payoutsListProgress.makeGone()

                adapter.submitList(it.data.payouts)

                binding.payoutsListAll.text = it.data.payoutAllTitle
            }
        }

        observeRetries(viewModel)
    }

    override fun payoutClicked(index: Int) {
        viewModel.payoutClicked(index)
    }
}
