package jp.co.soramitsu.feature_staking_impl.presentation.staking.controller.confirm

import android.os.Bundle
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.mixin.impl.observeValidations
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_account_api.presentation.actions.setupExternalActions
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentConfirmSetControllerBinding
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent

private const val PAYLOAD_KEY = "PAYLOAD_KEY"

class ConfirmSetControllerFragment : BaseFragment<ConfirmSetControllerViewModel>(R.layout.fragment_confirm_set_controller) {
    companion object {
        fun getBundle(payload: ConfirmSetControllerPayload) = Bundle().apply {
            putParcelable(PAYLOAD_KEY, payload)
        }
    }

    private val binding by viewBinding(FragmentConfirmSetControllerBinding::bind)

    override fun initViews() {
        with(binding) {
            confirmSetControllerToolbar.applyInsetter {
                type(statusBars = true) {
                    padding()
                }
            }

            confirmSetControllerToolbar.setHomeButtonListener { viewModel.back() }

            confirmSetControllerConfirm.setOnClickListener { viewModel.confirmClicked() }

            confirmSetControllerStashAccount.setWholeClickListener { viewModel.openStashExternalActions() }
            confirmSetControllerDestinationAccount.setWholeClickListener { viewModel.openControllerExternalActions() }
        }
    }

    override fun inject() {
        val payload = argument<ConfirmSetControllerPayload>(PAYLOAD_KEY)

        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .confirmSetControllerFactory()
            .create(this, payload)
            .inject(this)
    }

    override fun subscribe(viewModel: ConfirmSetControllerViewModel) {
        observeValidations(viewModel)
        setupExternalActions(viewModel)

        viewModel.feeStatusLiveData.observe(binding.confirmSetControllerFee::setFeeStatus)

        viewModel.stashAddressLiveData.observe {
            binding.confirmSetControllerStashAccount.setTextIcon(it.image)
            binding.confirmSetControllerStashAccount.setMessage(it.nameOrAddress)
        }

        viewModel.controllerAddressLiveData.observe {
            binding.confirmSetControllerDestinationAccount.setTextIcon(it.image)
            binding.confirmSetControllerDestinationAccount.setMessage(it.nameOrAddress)
        }
    }
}
