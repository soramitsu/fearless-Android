package jp.co.soramitsu.feature_staking_impl.presentation.staking.bond.confirm

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
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentConfirmBondMoreBinding
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import javax.inject.Inject

private const val PAYLOAD_KEY = "PAYLOAD_KEY"

class ConfirmBondMoreFragment : BaseFragment<ConfirmBondMoreViewModel>(R.layout.fragment_confirm_bond_more) {

    @Inject protected lateinit var imageLoader: ImageLoader

    private val binding by viewBinding(FragmentConfirmBondMoreBinding::bind)

    companion object {

        fun getBundle(payload: ConfirmBondMorePayload) = Bundle().apply {
            putParcelable(PAYLOAD_KEY, payload)
        }
    }

    override fun initViews() {
        with(binding) {
            confirmBondMoreToolbar.applyInsetter {
                type(statusBars = true) {
                    padding()
                }
            }

            confirmBondMoreOriginAccount.setWholeClickListener { viewModel.originAccountClicked() }

            confirmBondMoreToolbar.setHomeButtonListener { viewModel.backClicked() }
            confirmBondMoreConfirm.prepareForProgress(viewLifecycleOwner)
            confirmBondMoreConfirm.setOnClickListener { viewModel.confirmClicked() }
        }
    }

    override fun inject() {
        val payload = argument<ConfirmBondMorePayload>(PAYLOAD_KEY)

        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .confirmBondMoreFactory()
            .create(this, payload)
            .inject(this)
    }

    override fun subscribe(viewModel: ConfirmBondMoreViewModel) {
        observeValidations(viewModel)
        setupExternalActions(viewModel)

        viewModel.showNextProgress.observe(binding.confirmBondMoreConfirm::setProgress)

        viewModel.assetModelFlow.observe {
            binding.confirmBondMoreAmount.setAssetBalance(it.assetBalance)
            binding.confirmBondMoreAmount.setAssetName(it.tokenName)
            binding.confirmBondMoreAmount.setAssetImageUrl(it.imageUrl, imageLoader)
        }

        binding.confirmBondMoreAmount.amountInput.setText(viewModel.amount)

        viewModel.amountFiatFLow.observe {
            it?.let(binding.confirmBondMoreAmount::setAssetBalanceFiatAmount)
        }

        viewModel.feeStatusLiveData.observe(binding.confirmBondMoreFee::setFeeStatus)

        viewModel.originAddressModelLiveData.observe {
            binding.confirmBondMoreOriginAccount.setMessage(it.nameOrAddress)
            binding.confirmBondMoreOriginAccount.setTextIcon(it.image)
        }
    }
}
