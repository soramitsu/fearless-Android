package jp.co.soramitsu.feature_staking_impl.presentation.staking.rebond.confirm

import android.os.Bundle
import coil.ImageLoader
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.mixin.impl.observeRetries
import jp.co.soramitsu.common.mixin.impl.observeValidations
import jp.co.soramitsu.common.view.setProgress
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_account_api.presentation.actions.setupExternalActions
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentConfirmRebondBinding
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import javax.inject.Inject

private const val PAYLOAD_KEY = "PAYLOAD_KEY"

class ConfirmRebondFragment : BaseFragment<ConfirmRebondViewModel>(R.layout.fragment_confirm_rebond) {

    @Inject protected lateinit var imageLoader: ImageLoader

    private val binding by viewBinding(FragmentConfirmRebondBinding::bind)

    companion object {

        fun getBundle(payload: ConfirmRebondPayload) = Bundle().apply {
            putParcelable(PAYLOAD_KEY, payload)
        }
    }

    override fun initViews() {
        with(binding) {
            confirmRebondToolbar.applyInsetter {
                type(statusBars = true) {
                    padding()
                }
            }

            confirmRebondOriginAccount.setWholeClickListener { viewModel.originAccountClicked() }

            confirmRebondToolbar.setHomeButtonListener { viewModel.backClicked() }
            confirmRebondConfirm.prepareForProgress(viewLifecycleOwner)
            confirmRebondConfirm.setOnClickListener { viewModel.confirmClicked() }
        }
    }

    override fun inject() {
        val payload = argument<ConfirmRebondPayload>(PAYLOAD_KEY)

        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .confirmRebondFactory()
            .create(this, payload)
            .inject(this)
    }

    override fun subscribe(viewModel: ConfirmRebondViewModel) {
        observeValidations(viewModel)
        setupExternalActions(viewModel)
        observeRetries(viewModel)

        viewModel.showNextProgress.observe(binding.confirmRebondConfirm::setProgress)

        viewModel.assetModelFlow.observe {
            binding.confirmRebondAmount.setAssetBalance(it.assetBalance)
            binding.confirmRebondAmount.setAssetName(it.tokenName)
            binding.confirmRebondAmount.setAssetImageUrl(it.imageUrl, imageLoader)
        }

        binding.confirmRebondAmount.amountInput.setText(viewModel.amount)

        viewModel.amountFiatFLow.observe {
            it?.let(binding.confirmRebondAmount::setAssetBalanceFiatAmount)
        }

        viewModel.feeLiveData.observe(binding.confirmRebondFee::setFeeStatus)

        viewModel.originAddressModelLiveData.observe {
            binding.confirmRebondOriginAccount.setMessage(it.nameOrAddress)
            binding.confirmRebondOriginAccount.setTextIcon(it.image)
        }
    }
}
