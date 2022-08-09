package jp.co.soramitsu.featurestakingimpl.presentation.validators.change.start

import android.widget.TextView
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentStartChangeValidatorsBinding

@AndroidEntryPoint
class StartChangeValidatorsFragment : BaseFragment<StartChangeValidatorsViewModel>(R.layout.fragment_start_change_validators) {

    private val binding by viewBinding(FragmentStartChangeValidatorsBinding::bind)

    override val viewModel: StartChangeValidatorsViewModel by viewModels()

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

    override fun subscribe(viewModel: StartChangeValidatorsViewModel) {
        viewModel.validatorsLoading.observe {
            binding.startChangeValidatorsRecommended.setInProgress(it)
            binding.startChangeValidatorsCustom.setInProgress(it)
        }

        viewModel.getRecommendedFeaturesIds().map { resId ->
            (layoutInflater.inflate(R.layout.item_algorithm_criteria, binding.startChangeValidatorsRecommendedFeatures, false) as? TextView)?.also {
                it.text = getString(resId)
                binding.startChangeValidatorsRecommendedFeatures.addView(it)
            }
        }

        viewModel.customValidatorsTexts.observe {
            binding.startChangeValidatorsCustom.title.text = it.title
            binding.startChangeValidatorsCustom.setBadgeText(it.badge)
        }
    }
}
