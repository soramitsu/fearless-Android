package jp.co.soramitsu.feature_staking_impl.presentation.staking.rewardDestination.select

import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.mixin.impl.observeBrowserEvents
import jp.co.soramitsu.common.mixin.impl.observeRetries
import jp.co.soramitsu.common.mixin.impl.observeValidations
import jp.co.soramitsu.common.view.ButtonState
import jp.co.soramitsu.common.view.setProgress
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentSelectRewardDestinationBinding
import jp.co.soramitsu.feature_staking_impl.presentation.common.rewardDestination.observeRewardDestinationChooser

@AndroidEntryPoint
class SelectRewardDestinationFragment : BaseFragment<SelectRewardDestinationViewModel>(R.layout.fragment_select_reward_destination) {

    private val binding by viewBinding(FragmentSelectRewardDestinationBinding::bind)

    override val viewModel: SelectRewardDestinationViewModel by viewModels()

    override fun initViews() {
        with(binding) {
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
    }

    override fun subscribe(viewModel: SelectRewardDestinationViewModel) {
        observeRetries(viewModel)
        observeValidations(viewModel)
        observeBrowserEvents(viewModel)
        observeRewardDestinationChooser(viewModel, binding.selectRewardDestinationChooser)

        viewModel.showNextProgress.observe(binding.selectRewardDestinationContinue::setProgress)

        viewModel.feeLiveData.observe(binding.selectRewardDestinationFee::setFeeStatus)

        viewModel.continueAvailable.observe {
            val state = if (it) ButtonState.NORMAL else ButtonState.DISABLED

            binding.selectRewardDestinationContinue.setState(state)
        }
    }
}
