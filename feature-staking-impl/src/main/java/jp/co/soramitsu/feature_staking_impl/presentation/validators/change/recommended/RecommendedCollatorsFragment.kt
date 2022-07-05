package jp.co.soramitsu.feature_staking_impl.presentation.validators.change.recommended

import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import jp.co.soramitsu.feature_staking_impl.presentation.validators.CollatorsAdapter
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.CollatorModel
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentRecommendedValidatorsBinding

class RecommendedCollatorsFragment :
    BaseFragment<RecommendedCollatorsViewModel>(R.layout.fragment_recommended_validators),
    CollatorsAdapter.ItemHandler {

    private val binding by viewBinding(FragmentRecommendedValidatorsBinding::bind)

    lateinit var adapter: CollatorsAdapter

    override fun initViews() {
        adapter = CollatorsAdapter(this)
        binding.recommendedValidatorsList.adapter = adapter

        binding.recommendedValidatorsList.setHasFixedSize(true)

        binding.recommendedValidatorsToolbar.setHomeButtonListener { viewModel.backClicked() }
        onBackPressed { viewModel.backClicked() }

        binding.recommendedValidatorsNext.setOnClickListener {
            viewModel.nextClicked()
        }
        binding.recommendedValidatorsToolbar.setTitle(viewModel.toolbarTitle)
        binding.recommendedValidatorsRewards.text = getString(R.string.staking_rewards_apr)
        binding.recommendedValidatorsNext.text = getString(R.string.staking_select_collator_title)
    }

    override fun inject() {
        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .recommendedCollatorsComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: RecommendedCollatorsViewModel) {
        viewModel.recommendedCollatorModels.observe {
            adapter.submitList(it)

            val selectedAny = it.any { collator -> collator.isChecked == true }
            val selectedText = "${getString(R.string.common_selected)}: ${if (selectedAny) 1 else ""}"
            binding.recommendedValidatorsProgress.setVisible(false)
            binding.recommendedValidatorsNext.setVisible(true)
            binding.recommendedValidatorsNext.isEnabled = it.isNotEmpty() && selectedAny
            binding.recommendedValidatorsList.setVisible(true)
            binding.recommendedValidatorsAccounts.text = selectedText
        }

        viewModel.selectedTitle.observe(binding.recommendedValidatorsAccounts::setText)
    }

    override fun collatorInfoClicked(collatorModel: CollatorModel) {
        viewModel.collatorInfoClicked(collatorModel)
    }

    override fun collatorClicked(collatorModel: CollatorModel) {
        viewModel.collatorClicked(collatorModel)
    }
}
