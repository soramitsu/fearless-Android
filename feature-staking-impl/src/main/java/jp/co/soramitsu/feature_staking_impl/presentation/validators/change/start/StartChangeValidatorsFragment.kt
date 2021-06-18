package jp.co.soramitsu.feature_staking_impl.presentation.validators.change.start

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import kotlinx.android.synthetic.main.fragment_start_change_validators.startChangeValidatorsContainer
import kotlinx.android.synthetic.main.fragment_start_change_validators.startChangeValidatorsCustom
import kotlinx.android.synthetic.main.fragment_start_change_validators.startChangeValidatorsRecommended
import kotlinx.android.synthetic.main.fragment_start_change_validators.startChangeValidatorsRecommendedFeatures
import kotlinx.android.synthetic.main.fragment_start_change_validators.startChangeValidatorsToolbar

class StartChangeValidatorsFragment : BaseFragment<StartChangeValidatorsViewModel>() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_start_change_validators, container, false)
    }

    override fun initViews() {
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
            startChangeValidatorsRecommended.setInProgress(it)
            startChangeValidatorsCustom.setInProgress(it)
        }

        viewModel.recommendedFeaturesText.observe(startChangeValidatorsRecommendedFeatures::setText)
    }
}
