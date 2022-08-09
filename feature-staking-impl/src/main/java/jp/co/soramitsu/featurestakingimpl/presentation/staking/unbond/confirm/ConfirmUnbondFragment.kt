package jp.co.soramitsu.featurestakingimpl.presentation.staking.unbond.confirm

import android.os.Bundle
import androidx.fragment.app.viewModels
import coil.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.mixin.impl.observeValidations
import jp.co.soramitsu.common.view.setProgress
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.featureaccountapi.presentation.actions.setupExternalActions
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentConfirmUnbondBinding
import javax.inject.Inject

const val PAYLOAD_KEY = "PAYLOAD_KEY"

@AndroidEntryPoint
class ConfirmUnbondFragment : BaseFragment<ConfirmUnbondViewModel>(R.layout.fragment_confirm_unbond) {

    @Inject protected lateinit var imageLoader: ImageLoader

    private val binding by viewBinding(FragmentConfirmUnbondBinding::bind)

    override val viewModel: ConfirmUnbondViewModel by viewModels()

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
