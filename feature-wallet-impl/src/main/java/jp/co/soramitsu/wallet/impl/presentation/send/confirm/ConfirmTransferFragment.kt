package jp.co.soramitsu.wallet.impl.presentation.send.confirm

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import coil.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import jp.co.soramitsu.account.api.presentation.actions.setupExternalActions
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentConfirmTransferBinding
import jp.co.soramitsu.wallet.api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.wallet.api.presentation.mixin.observeTransferChecks
import jp.co.soramitsu.wallet.impl.presentation.send.BalanceDetailsBottomSheet
import jp.co.soramitsu.wallet.impl.presentation.send.TransferDraft

const val KEY_DRAFT = "KEY_DRAFT"

@AndroidEntryPoint
class ConfirmTransferFragment : BaseFragment<ConfirmTransferViewModel>(R.layout.fragment_confirm_transfer) {

    @Inject
    lateinit var imageLoader: ImageLoader

    private val binding by viewBinding(FragmentConfirmTransferBinding::bind)

    override val viewModel: ConfirmTransferViewModel by viewModels()

    companion object {
        fun getBundle(transferDraft: TransferDraft) = Bundle().apply {
            putParcelable(KEY_DRAFT, transferDraft)
        }
    }

    override fun initViews() {
        binding.confirmTransferRecipientView.setActionClickListener { viewModel.copyRecipientAddressClicked() }

        binding.confirmTransferToolbar.setHomeButtonListener { viewModel.backClicked() }

        binding.confirmTransferSubmit.setOnClickListener { viewModel.submitClicked() }
        binding.confirmTransferSubmit.prepareForProgress(viewLifecycleOwner)
    }

    override fun buildErrorDialog(title: String, errorMessage: String): AlertDialog {
        val base = super.buildErrorDialog(title, errorMessage)

        base.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.common_ok)) { _, _ ->
            viewModel.errorAcknowledged()
        }

        return base
    }

    override fun subscribe(viewModel: ConfirmTransferViewModel) {
        setupExternalActions(viewModel)

        observeTransferChecks(viewModel, viewModel::warningConfirmed, viewModel::errorAcknowledged)

        viewModel.assetLiveData.observe {
            val chainAsset = it.token.configuration

            val transferableAmount =
                resources.getString(R.string.wallet_send_transferable_amount_caption, it.available.orZero().formatTokenAmount(it.token.configuration))
            binding.confirmAmountField.setAssetBalance(transferableAmount)
            binding.confirmAmountField.setAssetName(it.token.configuration.symbol.uppercase())
            binding.confirmAmountField.setAssetImageUrl(it.token.configuration.iconUrl, imageLoader)

            with(viewModel.transferDraft) {
                binding.confirmFee.text = fee.formatTokenAmount(chainAsset)
                binding.confirmFeeFiat.text = it.token.fiatAmount(fee)?.formatAsCurrency(it.token.fiatSymbol)

                binding.confirmAmountField.amountInput.setText(totalTransaction.formatTokenAmount(chainAsset))
                binding.confirmAmountField.setAssetBalanceFiatAmount(it.token.fiatAmount(totalTransaction)?.formatAsCurrency(it.token.fiatSymbol))

                tip?.let { tip ->
                    binding.confirmAmountTipGroup.makeVisible()
                    binding.confirmTip.text = tip.formatTokenAmount(chainAsset)
                    binding.confirmTipFiat.text = it.token.fiatAmount(tip)?.formatAsCurrency(it.token.fiatSymbol)
                }
            }
        }

        viewModel.recipientModel.observe {
            binding.confirmTransferRecipientView.setTextIcon(it.image)
            binding.confirmTransferRecipientView.setMessage(it.address)
        }

        viewModel.senderModel.observe {
            binding.confirmTransferSenderView.setTextIcon(it.image)
            binding.confirmTransferSenderView.setMessage(it.address)
        }

        viewModel.sendButtonStateLiveData.observe(binding.confirmTransferSubmit::setState)

        viewModel.showBalanceDetailsEvent.observeEvent {
            BalanceDetailsBottomSheet(requireContext(), it).show()
        }
    }
}
