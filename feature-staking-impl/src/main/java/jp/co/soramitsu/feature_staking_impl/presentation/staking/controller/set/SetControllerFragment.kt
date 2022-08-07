package jp.co.soramitsu.feature_staking_impl.presentation.staking.controller.set

import jp.co.soramitsu.common.address.AddressChooserBottomSheetDialog
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.mixin.impl.observeValidations
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_account_api.presentation.actions.setupExternalActions
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentSetControllerAccountBinding
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent

class SetControllerFragment : BaseFragment<SetControllerViewModel>(R.layout.fragment_set_controller_account) {

    private val binding by viewBinding(FragmentSetControllerAccountBinding::bind)

    override fun initViews() {
        onBackPressed { viewModel.backClicked() }
        with(binding) {
            setControllerContinueBtn.setOnClickListener { viewModel.continueClicked() }

            setControllerStashAccount.setWholeClickListener { viewModel.openExternalActions() }
            setControllerDestinationAccount.setWholeClickListener { viewModel.openAccounts() }

            setControllerLearnMore.setOnClickListener { viewModel.onMoreClicked() }
            setControllerToolbar.setHomeButtonListener { viewModel.backClicked() }
        }
    }

    override fun inject() {
        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .setControllerFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: SetControllerViewModel) {
        observeValidations(viewModel)

        viewModel.stashAccountModel.observe {
            binding.setControllerStashAccount.setTextIcon(it.image)
            binding.setControllerStashAccount.setMessage(it.nameOrAddress)
        }

        viewModel.controllerAccountModel.observe {
            binding.setControllerDestinationAccount.setTextIcon(it.image)
            binding.setControllerDestinationAccount.setMessage(it.nameOrAddress)
        }

        viewModel.feeLiveData.observe(binding.setControllerFee::setFeeStatus)

        setupExternalActions(viewModel)

        viewModel.showControllerChooserEvent.observeEvent(::showControllerChooser)

        viewModel.showNotStashAccountWarning.observe(binding.setControllerNotStashWarning::setVisible)

        viewModel.isContinueButtonAvailable.observe {
            binding.setControllerContinueBtn.isEnabled = it
        }
    }

    private fun showControllerChooser(payload: DynamicListBottomSheet.Payload<AddressModel>) {
        AddressChooserBottomSheetDialog(
            requireContext(),
            payload,
            viewModel::payoutControllerChanged,
            R.string.staking_controller_account
        ).show()
    }
}
