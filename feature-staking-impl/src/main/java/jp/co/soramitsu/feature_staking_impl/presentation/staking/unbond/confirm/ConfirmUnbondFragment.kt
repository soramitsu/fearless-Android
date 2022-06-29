package jp.co.soramitsu.feature_staking_impl.presentation.staking.unbond.confirm

import android.os.Bundle
import coil.ImageLoader
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.mixin.impl.observeValidations
import jp.co.soramitsu.common.view.setProgress
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_account_api.presentation.actions.setupExternalActions
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentConfirmUnbondBinding
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import javax.inject.Inject

private const val PAYLOAD_KEY = "PAYLOAD_KEY"

class ConfirmUnbondFragment : BaseFragment<ConfirmUnbondViewModel>(R.layout.fragment_confirm_unbond) {

    @Inject protected lateinit var imageLoader: ImageLoader

    private val binding by viewBinding(FragmentConfirmUnbondBinding::bind)

    companion object {

        fun getBundle(payload: ConfirmUnbondPayload) = Bundle().apply {
            putParcelable(PAYLOAD_KEY, payload)
        }
    }

    override fun initViews() {
        with(binding) {
            confirmUnbondToolbar.applyInsetter {
                type(statusBars = true) {
                    padding()
                }
            }

            confirmUnbondOriginAccount.setWholeClickListener { viewModel.originAccountClicked() }

            confirmUnbondToolbar.setHomeButtonListener { viewModel.backClicked() }
            confirmUnbondConfirm.prepareForProgress(viewLifecycleOwner)
            confirmUnbondConfirm.setOnClickListener { viewModel.confirmClicked() }
        }
    }

    override fun inject() {
        val payload = argument<ConfirmUnbondPayload>(PAYLOAD_KEY)

        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .confirmUnbondFactory()
            .create(this, payload)
            .inject(this)
    }

    override fun subscribe(viewModel: ConfirmUnbondViewModel) {
        observeValidations(viewModel)
        setupExternalActions(viewModel)

        viewModel.showNextProgress.observe(binding.confirmUnbondConfirm::setProgress)

        viewModel.assetModelFlow.observe {
            binding.confirmUnbondAmount.setAssetBalance(it.assetBalance)
            binding.confirmUnbondAmount.setAssetName(it.tokenName)
            binding.confirmUnbondAmount.setAssetImageUrl(it.imageUrl, imageLoader)
        }

        binding.confirmUnbondAmount.amountInput.setText(viewModel.amount)

        viewModel.amountFiatFLow.observe {
            it?.let(binding.confirmUnbondAmount::setAssetBalanceFiatAmount)
        }

        viewModel.feeStatusLiveData.observe(binding.confirmUnbondFee::setFeeStatus)

        viewModel.originAddressModelLiveData.observe {
            binding.confirmUnbondOriginAccount.setMessage(it.nameOrAddress)
            binding.confirmUnbondOriginAccount.setTextIcon(it.image)
        }
    }
}
