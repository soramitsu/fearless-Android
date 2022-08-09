package jp.co.soramitsu.featurestakingimpl.presentation.validators.change.recommended

import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentRecommendedValidatorsBinding
import jp.co.soramitsu.featurestakingimpl.presentation.validators.ValidatorsAdapter
import jp.co.soramitsu.featurestakingimpl.presentation.validators.change.ValidatorModel

@AndroidEntryPoint
class RecommendedValidatorsFragment : BaseFragment<RecommendedValidatorsViewModel>(R.layout.fragment_recommended_validators), ValidatorsAdapter.ItemHandler {

    lateinit var adapter: ValidatorsAdapter

    private val binding by viewBinding(FragmentRecommendedValidatorsBinding::bind)

    override val viewModel: RecommendedValidatorsViewModel by viewModels()

    override fun initViews() {
        adapter = ValidatorsAdapter(this)
        binding.recommendedValidatorsList.adapter = adapter

        binding.recommendedValidatorsList.setHasFixedSize(true)

        binding.recommendedValidatorsToolbar.setHomeButtonListener { viewModel.backClicked() }
        onBackPressed { viewModel.backClicked() }

        binding.recommendedValidatorsNext.setOnClickListener {
            viewModel.nextClicked()
        }
    }

    override fun subscribe(viewModel: RecommendedValidatorsViewModel) {
        viewModel.recommendedValidatorModels.observe {
            adapter.submitList(it)

            binding.recommendedValidatorsProgress.setVisible(false)
            binding.recommendedValidatorsNext.setVisible(true)
            binding.recommendedValidatorsNext.isEnabled = it.isNotEmpty()
            binding.recommendedValidatorsList.setVisible(true)
        }

        viewModel.selectedTitle.observe(binding.recommendedValidatorsAccounts::setText)
    }

    override fun validatorInfoClicked(validatorModel: ValidatorModel) {
        viewModel.validatorInfoClicked(validatorModel)
    }
}
