package jp.co.soramitsu.feature_staking_impl.presentation.validators.change.custom.review

import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.setTextColorRes
import jp.co.soramitsu.common.utils.toggle
import jp.co.soramitsu.common.view.ButtonState
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentReviewCustomValidatorsBinding
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import jp.co.soramitsu.feature_staking_impl.presentation.validators.ValidatorsAdapter
import jp.co.soramitsu.feature_staking_impl.presentation.validators.ValidatorsAdapter.Mode.EDIT
import jp.co.soramitsu.feature_staking_impl.presentation.validators.ValidatorsAdapter.Mode.VIEW
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.ValidatorModel

class ReviewCustomValidatorsFragment :
    BaseFragment<ReviewCustomValidatorsViewModel>(R.layout.fragment_review_custom_validators),
    ValidatorsAdapter.ItemHandler {

    private val adapter: ValidatorsAdapter by lazy(LazyThreadSafetyMode.NONE) {
        ValidatorsAdapter(this)
    }

    private val binding by viewBinding(FragmentReviewCustomValidatorsBinding::bind)

    override fun initViews() {
        binding.reviewCustomValidatorsList.adapter = adapter

        binding.reviewCustomValidatorsList.setHasFixedSize(true)

        binding.reviewCustomValidatorsToolbar.setHomeButtonListener { viewModel.backClicked() }
        onBackPressed { viewModel.backClicked() }

        binding.reviewCustomValidatorsNext.setOnClickListener {
            viewModel.nextClicked()
        }

        binding.reviewCustomValidatorsToolbar.setRightActionClickListener {
            viewModel.isInEditMode.toggle()
        }
    }

    override fun inject() {
        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .reviewCustomValidatorsComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: ReviewCustomValidatorsViewModel) {
        viewModel.selectedValidatorModels.observe(adapter::submitList)

        viewModel.selectionStateFlow.observe {
            binding.reviewCustomValidatorsAccounts.setTextColorRes(if (it.isOverflow) R.color.red else R.color.gray1)
            binding.reviewCustomValidatorsAccounts.text = it.selectedHeaderText

            binding.reviewCustomValidatorsNext.setState(if (it.isOverflow) ButtonState.DISABLED else ButtonState.NORMAL)
            binding.reviewCustomValidatorsNext.text = it.nextButtonText
        }

        viewModel.isInEditMode.observe {
            adapter.modeChanged(if (it) EDIT else VIEW)

            val rightActionRes = if (it) R.string.common_done else R.string.common_edit

            binding.reviewCustomValidatorsToolbar.setTextRight(getString(rightActionRes))
        }
    }

    override fun validatorInfoClicked(validatorModel: ValidatorModel) {
        viewModel.validatorInfoClicked(validatorModel)
    }

    override fun removeClicked(validatorModel: ValidatorModel) {
        viewModel.deleteClicked(validatorModel)
    }

    override fun validatorClicked(validatorModel: ValidatorModel) {
        viewModel.validatorInfoClicked(validatorModel)
    }
}
