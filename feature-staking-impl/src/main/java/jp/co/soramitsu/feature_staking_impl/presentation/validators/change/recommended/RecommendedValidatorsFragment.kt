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
import jp.co.soramitsu.feature_staking_impl.presentation.validators.ValidatorsAdapter
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.ValidatorModel
import kotlinx.android.synthetic.main.fragment_recommended_validators.recommendedValidatorsAccounts
import kotlinx.android.synthetic.main.fragment_recommended_validators.recommendedValidatorsList
import kotlinx.android.synthetic.main.fragment_recommended_validators.recommendedValidatorsNext
import kotlinx.android.synthetic.main.fragment_recommended_validators.recommendedValidatorsProgress
import kotlinx.android.synthetic.main.fragment_recommended_validators.recommendedValidatorsToolbar

class RecommendedValidatorsFragment : BaseFragment<RecommendedValidatorsViewModel>(), ValidatorsAdapter.ItemHandler {

    lateinit var adapter: ValidatorsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_recommended_validators, container, false)
    }

    override fun initViews() {
        adapter = ValidatorsAdapter(this)
        recommendedValidatorsList.adapter = adapter

        recommendedValidatorsList.setHasFixedSize(true)

        recommendedValidatorsToolbar.setHomeButtonListener { viewModel.backClicked() }
        onBackPressed { viewModel.backClicked() }

        recommendedValidatorsNext.setOnClickListener {
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

            recommendedValidatorsProgress.setVisible(false)
            recommendedValidatorsNext.setVisible(true)
            recommendedValidatorsList.setVisible(true)
        }

        viewModel.selectedTitle.observe(recommendedValidatorsAccounts::setText)
    }

    override fun validatorInfoClicked(validatorModel: ValidatorModel) {
        viewModel.validatorInfoClicked(validatorModel)
    }
}
