package jp.co.soramitsu.feature_staking_impl.presentation.staking.redeem

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
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentRedeemBinding
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import javax.inject.Inject

private const val PAYLOAD_KEY = "PAYLOAD_KEY"

class RedeemFragment : BaseFragment<RedeemViewModel>(R.layout.fragment_redeem) {

    @Inject protected lateinit var imageLoader: ImageLoader

    private val binding by viewBinding(FragmentRedeemBinding::bind)

    companion object {

        fun getBundle(payload: RedeemPayload) = Bundle().apply {
            putParcelable(PAYLOAD_KEY, payload)
        }
    }

    override fun initViews() {
        with(binding) {
            redeemContainer.applyInsetter {
                type(statusBars = true) {
                    padding()
                }

                consume(true)
            }

            redeemToolbar.setHomeButtonListener { viewModel.backClicked() }
            redeemConfirm.prepareForProgress(viewLifecycleOwner)
            redeemConfirm.setOnClickListener { viewModel.confirmClicked() }
            redeemOriginAccount.setWholeClickListener { viewModel.originAccountClicked() }
        }
    }

    override fun inject() {
        val payload = argument<RedeemPayload>(PAYLOAD_KEY)

        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .redeemFactory()
            .create(this, payload)
            .inject(this)
    }

    override fun subscribe(viewModel: RedeemViewModel) {
        observeRetries(viewModel)
        observeValidations(viewModel)
        setupExternalActions(viewModel)

        viewModel.showNextProgress.observe(binding.redeemConfirm::setProgress)

        viewModel.assetModelLiveData.observe {
            binding.redeemAmount.setAssetBalance(it.assetBalance)
            binding.redeemAmount.setAssetName(it.tokenName)
            binding.redeemAmount.setAssetImageUrl(it.imageUrl, imageLoader)
        }

        viewModel.amountLiveData.observe { (amount, fiatAmount) ->
            binding.redeemAmount.amountInput.setText(amount)
            fiatAmount?.let(binding.redeemAmount::setAssetBalanceFiatAmount)
        }

        viewModel.originAddressModelLiveData.observe {
            binding.redeemOriginAccount.setMessage(it.nameOrAddress)
            binding.redeemOriginAccount.setTextIcon(it.image)
        }

        viewModel.feeLiveData.observe(binding.redeemFee::setFeeStatus)
    }
}
