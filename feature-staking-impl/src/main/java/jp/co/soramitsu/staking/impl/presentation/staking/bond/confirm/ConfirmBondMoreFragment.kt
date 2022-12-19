package jp.co.soramitsu.staking.impl.presentation.staking.bond.confirm

import android.os.Bundle
import androidx.fragment.app.viewModels
import coil.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.mixin.impl.observeValidations
import jp.co.soramitsu.common.view.setProgress
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.account.api.presentation.actions.setupExternalActions
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentConfirmBondMoreBinding
import javax.inject.Inject

const val PAYLOAD_KEY = "PAYLOAD_KEY"

@AndroidEntryPoint
class ConfirmBondMoreFragment : BaseFragment<ConfirmBondMoreViewModel>(R.layout.fragment_confirm_bond_more) {

    @Inject protected lateinit var imageLoader: ImageLoader

    override val viewModel: ConfirmBondMoreViewModel by viewModels()

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

    override fun subscribe(viewModel: ConfirmBondMoreViewModel) {
        observeValidations(viewModel)
        setupExternalActions(viewModel)

        viewModel.showNextProgress.observe(binding.confirmBondMoreConfirm::setProgress)

        viewModel.assetModelFlow.observe {
            binding.confirmBondMoreAmount.setAssetName(it.tokenName)
            binding.confirmBondMoreAmount.setAssetImageUrl(it.imageUrl, imageLoader)
        }

        viewModel.stakeCreatorBalanceFlow.observe {
            binding.confirmBondMoreAmount.setAssetBalance(it)
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
