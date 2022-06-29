package jp.co.soramitsu.feature_staking_impl.presentation.validators.change.start

import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentStartChangeValidatorsBinding
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent

class StartChangeValidatorsFragment : BaseFragment<StartChangeValidatorsViewModel>(R.layout.fragment_start_change_validators) {

    private val binding by viewBinding(FragmentStartChangeValidatorsBinding::bind)

    override fun initViews() {
        with(binding) {
            startChangeValidatorsContainer.applyInsetter {
                type(statusBars = true) {
                    padding()
                }
            }

            startChangeValidatorsToolbar.setHomeButtonListener { viewModel.backClicked() }
            onBackPressed { viewModel.backClicked() }

            startChangeValidatorsRecommended.setOnClickListener { viewModel.goToRecommendedClicked() }
            startChangeValidatorsCustom.setOnClickListener { viewModel.goToCustomClicked() }
        }
    }

    override fun inject() {
        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .startChangeValidatorsComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: StartChangeValidatorsViewModel) {
        viewModel.validatorsLoading.observe {
            binding.startChangeValidatorsRecommended.setInProgress(it)
            binding.startChangeValidatorsCustom.setInProgress(it)
        }

        viewModel.recommendedFeaturesText.observe(binding.startChangeValidatorsRecommendedFeatures::setText)

        viewModel.customValidatorsTexts.observe {
            binding.startChangeValidatorsCustom.title.text = it.title
            binding.startChangeValidatorsCustom.setBadgeText(it.badge)
        }
    }
}
