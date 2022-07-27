package jp.co.soramitsu.feature_staking_impl.presentation.validators.change.recommended

import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentRecommendedValidatorsBinding
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import jp.co.soramitsu.feature_staking_impl.presentation.validators.ValidatorsAdapter
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.ValidatorModel

class RecommendedValidatorsFragment : BaseFragment<RecommendedValidatorsViewModel>(R.layout.fragment_recommended_validators), ValidatorsAdapter.ItemHandler {

    lateinit var adapter: ValidatorsAdapter

    private val binding by viewBinding(FragmentRecommendedValidatorsBinding::bind)

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

    override fun inject() {
        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .recommendedValidatorsComponentFactory()
            .create(this)
            .inject(this)
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
