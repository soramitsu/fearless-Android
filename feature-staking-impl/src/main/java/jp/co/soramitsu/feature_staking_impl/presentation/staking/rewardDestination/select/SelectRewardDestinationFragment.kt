package jp.co.soramitsu.feature_staking_impl.presentation.staking.rewardDestination.select

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.mixin.impl.observeBrowserEvents
import jp.co.soramitsu.common.mixin.impl.observeRetries
import jp.co.soramitsu.common.mixin.impl.observeValidations
import jp.co.soramitsu.common.view.setProgress
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import jp.co.soramitsu.feature_staking_impl.presentation.common.rewardDestination.observeRewardDestinationChooser
import kotlinx.android.synthetic.main.fragment_select_reward_destination.selectRewardDestinationChooser
import kotlinx.android.synthetic.main.fragment_select_reward_destination.selectRewardDestinationContainer
import kotlinx.android.synthetic.main.fragment_select_reward_destination.selectRewardDestinationContinue
import kotlinx.android.synthetic.main.fragment_select_reward_destination.selectRewardDestinationFee
import kotlinx.android.synthetic.main.fragment_select_reward_destination.selectRewardDestinationToolbar

class SelectRewardDestinationFragment : BaseFragment<SelectRewardDestinationViewModel>() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_select_reward_destination, container, false)
    }

    override fun initViews() {
        selectRewardDestinationContainer.applyInsetter {
            type(statusBars = true) {
                padding()
            }

            consume(true)
        }

        selectRewardDestinationToolbar.setHomeButtonListener { viewModel.backClicked() }

        selectRewardDestinationContinue.prepareForProgress(viewLifecycleOwner)
        selectRewardDestinationContinue.setOnClickListener { viewModel.nextClicked() }
    }

    override fun inject() {
        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .selectRewardDestinationFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: SelectRewardDestinationViewModel) {
        observeRetries(viewModel)
        observeValidations(viewModel)
        observeBrowserEvents(viewModel)
        observeRewardDestinationChooser(viewModel, selectRewardDestinationChooser)

        viewModel.showNextProgress.observe(selectRewardDestinationContinue::setProgress)

        viewModel.feeLiveData.observe(selectRewardDestinationFee::setFeeStatus)
    }
}
