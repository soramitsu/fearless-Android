package jp.co.soramitsu.feature_staking_impl.presentation.validators.change.recommended

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import jp.co.soramitsu.feature_staking_impl.presentation.validators.CollatorsAdapter
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.CollatorModel
import kotlinx.android.synthetic.main.fragment_recommended_validators.recommendedValidatorsAccounts
import kotlinx.android.synthetic.main.fragment_recommended_validators.recommendedValidatorsList
import kotlinx.android.synthetic.main.fragment_recommended_validators.recommendedValidatorsNext
import kotlinx.android.synthetic.main.fragment_recommended_validators.recommendedValidatorsProgress
import kotlinx.android.synthetic.main.fragment_recommended_validators.recommendedValidatorsRewards
import kotlinx.android.synthetic.main.fragment_recommended_validators.recommendedValidatorsToolbar

class RecommendedCollatorsFragment : BaseFragment<RecommendedCollatorsViewModel>(), CollatorsAdapter.ItemHandler {

    lateinit var adapter: CollatorsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_recommended_validators, container, false)
    }

    override fun initViews() {
        adapter = CollatorsAdapter(this)
        recommendedValidatorsList.adapter = adapter

        recommendedValidatorsList.setHasFixedSize(true)

        recommendedValidatorsToolbar.setHomeButtonListener { viewModel.backClicked() }
        onBackPressed { viewModel.backClicked() }

        recommendedValidatorsNext.setOnClickListener {
            viewModel.nextClicked()
        }
        recommendedValidatorsToolbar.setTitle(viewModel.toolbarTitle)
        recommendedValidatorsRewards.text = getString(R.string.staking_rewards_apr)
        recommendedValidatorsNext.text = getString(R.string.staking_select_collator_title)
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
            recommendedValidatorsProgress.setVisible(false)
            recommendedValidatorsNext.setVisible(true)
            recommendedValidatorsNext.isEnabled = it.isNotEmpty() && selectedAny
            recommendedValidatorsList.setVisible(true)
            recommendedValidatorsAccounts.text = selectedText
        }

        viewModel.selectedTitle.observe(recommendedValidatorsAccounts::setText)
    }

    override fun collatorInfoClicked(collatorModel: CollatorModel) {
        viewModel.collatorInfoClicked(collatorModel)
    }

    override fun collatorClicked(collatorModel: CollatorModel) {
        viewModel.collatorClicked(collatorModel)
    }
}
