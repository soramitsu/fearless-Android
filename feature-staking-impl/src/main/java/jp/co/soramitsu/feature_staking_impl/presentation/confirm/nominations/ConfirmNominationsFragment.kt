package jp.co.soramitsu.feature_staking_impl.presentation.confirm.nominations

import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentConfirmNominationsBinding
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import jp.co.soramitsu.feature_staking_impl.presentation.validators.ValidatorsAdapter
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.ValidatorModel

class ConfirmNominationsFragment : BaseFragment<ConfirmNominationsViewModel>(R.layout.fragment_confirm_nominations), ValidatorsAdapter.ItemHandler {

    lateinit var adapter: ValidatorsAdapter

    private val binding by viewBinding(FragmentConfirmNominationsBinding::bind)

    override fun initViews() {
        adapter = ValidatorsAdapter(this)
        binding.confirmNominationsList.adapter = adapter

        binding.confirmNominationsList.setHasFixedSize(true)

        binding.confirmNominationsToolbar.setHomeButtonListener {
            viewModel.backClicked()
        }
    }

    override fun inject() {
        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .confirmNominationsComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: ConfirmNominationsViewModel) {
        viewModel.selectedValidatorsLiveData.observe(adapter::submitList)

        viewModel.toolbarTitle.observe(binding.confirmNominationsToolbar::setTitle)
    }

    override fun validatorInfoClicked(validatorModel: ValidatorModel) {
        viewModel.validatorInfoClicked(validatorModel)
    }
}
