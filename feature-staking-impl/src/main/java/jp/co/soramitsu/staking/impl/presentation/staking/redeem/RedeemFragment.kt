package jp.co.soramitsu.staking.impl.presentation.staking.redeem

import android.os.Bundle
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import coil.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.mixin.impl.observeRetries
import jp.co.soramitsu.common.mixin.impl.observeValidations
import jp.co.soramitsu.common.view.setProgress
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.account.api.presentation.actions.setupExternalActions
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentRedeemBinding
import javax.inject.Inject

const val PAYLOAD_KEY = "PAYLOAD_KEY"

@AndroidEntryPoint
class RedeemFragment : BaseFragment<RedeemViewModel>(R.layout.fragment_redeem) {

    @Inject protected lateinit var imageLoader: ImageLoader

    private val binding by viewBinding(FragmentRedeemBinding::bind)

    override val viewModel: RedeemViewModel by viewModels()

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
            accountAddressView.setWholeClickListener { viewModel.originAccountClicked() }
        }
    }

    override fun subscribe(viewModel: RedeemViewModel) {
        observeRetries(viewModel)
        observeValidations(viewModel)
        setupExternalActions(viewModel)

        viewModel.showNextProgress.observe(binding.redeemConfirm::setProgress)

        viewModel.assetModelFlow.observe {
            binding.redeemAmount.setAssetName(it.tokenName)
            binding.redeemAmount.setAssetImageUrl(it.imageUrl, imageLoader)
        }

        viewModel.stakingUnlockAmount.observe {
            binding.redeemAmount.amountInput.setText(it)
        }

        viewModel.enteredFiatAmountFlow.observe {
            it?.let(binding.redeemAmount::setAssetBalanceFiatAmount)
        }

        viewModel.feeLiveData.observe {
            binding.redeemFee.setFeeStatus(it)
        }

        viewModel.collatorLiveData.observe {
            it.ifPresent {
                binding.collatorAddressView.isVisible = true
                binding.collatorAddressView.setMessage(it.nameOrAddress)
                binding.collatorAddressView.setTextIcon(it.image)
            }
        }

        viewModel.accountLiveData.observe {
            it.ifPresent {
                binding.accountAddressView.isVisible = true
                binding.accountAddressView.setMessage(it.nameOrAddress)
                binding.accountAddressView.setTextIcon(it.image)
            }
        }

        viewModel.feeLiveData.observe(binding.redeemFee::setFeeStatus)
    }
}
