package jp.co.soramitsu.staking.impl.presentation.validators.change.start

import android.widget.TextView
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentStartChangeCollatorsBinding

@AndroidEntryPoint
class StartChangeCollatorsFragment : BaseFragment<StartChangeCollatorsViewModel>(R.layout.fragment_start_change_collators) {

    private val binding by viewBinding(FragmentStartChangeCollatorsBinding::bind)

    override val viewModel: StartChangeCollatorsViewModel by viewModels()

    override fun initViews() {
        binding.startChangeCollatorsContainer.applyInsetter {
            type(statusBars = true) {
                padding()
            }
        }

        binding.startChangeCollatorsToolbar.setHomeButtonListener { viewModel.backClicked() }
        onBackPressed { viewModel.backClicked() }

        binding.startChangeCollatorsRecommended.setOnClickListener { viewModel.goToRecommendedClicked() }
        binding.startChangeCollatorsCustom.setOnClickListener { viewModel.goToCustomClicked() }
    }

    override fun subscribe(viewModel: StartChangeCollatorsViewModel) {
        viewModel.collatorsLoading.observe {
            binding.startChangeCollatorsRecommended.setInProgress(it)
            binding.startChangeCollatorsCustom.setInProgress(it)
        }

        viewModel.getRecommendedFeaturesIds().map { resId ->
            (layoutInflater.inflate(R.layout.item_algorithm_criteria, binding.startChangeCollatorsRecommendedFeatures, false) as? TextView)?.also {
                it.text = getString(resId)
                binding.startChangeCollatorsRecommendedFeatures.addView(it)
            }
        }

        viewModel.customCollatorsTexts.observe {
            binding.startChangeCollatorsCustom.title.text = it.title
            binding.startChangeCollatorsCustom.setBadgeText(it.badge)
        }
    }
}
