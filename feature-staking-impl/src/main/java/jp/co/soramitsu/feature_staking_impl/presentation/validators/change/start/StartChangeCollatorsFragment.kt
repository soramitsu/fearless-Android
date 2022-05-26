package jp.co.soramitsu.feature_staking_impl.presentation.validators.change.start

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import kotlinx.android.synthetic.main.fragment_start_change_collators.startChangeCollatorsContainer
import kotlinx.android.synthetic.main.fragment_start_change_collators.startChangeCollatorsCustom
import kotlinx.android.synthetic.main.fragment_start_change_collators.startChangeCollatorsRecommended
import kotlinx.android.synthetic.main.fragment_start_change_collators.startChangeCollatorsRecommendedFeatures
import kotlinx.android.synthetic.main.fragment_start_change_collators.startChangeCollatorsToolbar

class StartChangeCollatorsFragment : BaseFragment<StartChangeCollatorsViewModel>() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_start_change_collators, container, false)
    }

    override fun initViews() {
        startChangeCollatorsContainer.applyInsetter {
            type(statusBars = true) {
                padding()
            }
        }

        startChangeCollatorsToolbar.setHomeButtonListener { viewModel.backClicked() }
        onBackPressed { viewModel.backClicked() }

        startChangeCollatorsRecommended.setOnClickListener { viewModel.goToRecommendedClicked() }
        startChangeCollatorsCustom.setOnClickListener { viewModel.goToCustomClicked() }
    }

    override fun inject() {
        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .startChangeCollatorsComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: StartChangeCollatorsViewModel) {
        viewModel.collatorsLoading.observe {
            startChangeCollatorsRecommended.setInProgress(it)
            startChangeCollatorsCustom.setInProgress(it)
        }

        viewModel.getRecommendedFeaturesIds().map { resId ->
            (layoutInflater.inflate(R.layout.item_algorithm_criteria, startChangeCollatorsRecommendedFeatures, false) as? TextView)?.also {
                it.text = getString(resId)
                startChangeCollatorsRecommendedFeatures.addView(it)
            }
        }

        viewModel.customCollatorsTexts.observe {
            startChangeCollatorsCustom.title.text = it.title
            startChangeCollatorsCustom.setBadgeText(it.badge)
        }
    }
}
