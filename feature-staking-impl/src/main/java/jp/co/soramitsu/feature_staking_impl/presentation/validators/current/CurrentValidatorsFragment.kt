package jp.co.soramitsu.feature_staking_impl.presentation.validators.current

import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentCurrentValidatorsBinding
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import jp.co.soramitsu.feature_staking_impl.presentation.validators.current.model.NominatedValidatorModel

class CurrentValidatorsFragment : BaseFragment<CurrentValidatorsViewModel>(R.layout.fragment_current_validators), CurrentValidatorsAdapter.Handler {

    lateinit var adapter: CurrentValidatorsAdapter

    private val binding by viewBinding(FragmentCurrentValidatorsBinding::bind)

    override fun initViews() {
        adapter = CurrentValidatorsAdapter(this)

        with(binding) {
            currentValidatorsContainer.applyInsetter {
                type(statusBars = true) {
                    padding()
                }
            }

            currentValidatorsList.adapter = adapter

            currentValidatorsList.setHasFixedSize(true)

            currentValidatorsToolbar.setHomeButtonListener { viewModel.backClicked() }

            currentValidatorsToolbar.setRightActionClickListener { viewModel.changeClicked() }
        }
    }

    override fun inject() {
        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .currentValidatorsFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: CurrentValidatorsViewModel) {
        viewModel.currentValidatorModelsLiveData.observe { loadingState ->
            when (loadingState) {
                is LoadingState.Loading -> {
                    binding.currentValidatorsList.makeGone()
                    binding.currentValidatorsProgress.makeVisible()
                }

                is LoadingState.Loaded -> {
                    binding.currentValidatorsList.makeVisible()
                    binding.currentValidatorsProgress.makeGone()

                    adapter.submitList(loadingState.data)
                }
            }
        }

        viewModel.shouldShowOversubscribedNoRewardWarning.observe {
            binding.currentValidatorsOversubscribedMessage.setVisible(it)
            binding.payoutDivider.setVisible(it)
        }
    }

    override fun infoClicked(validatorModel: NominatedValidatorModel) {
        viewModel.validatorInfoClicked(validatorModel.addressModel.address)
    }
}
