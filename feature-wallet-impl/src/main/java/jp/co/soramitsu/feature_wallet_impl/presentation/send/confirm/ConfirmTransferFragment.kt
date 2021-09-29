package jp.co.soramitsu.feature_wallet_impl.presentation.send.confirm

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_account_api.presenatation.actions.setupExternalActions
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.send.BalanceDetailsBottomSheet
import jp.co.soramitsu.feature_wallet_impl.presentation.send.TransferDraft
import jp.co.soramitsu.feature_wallet_impl.presentation.send.observeTransferChecks
import kotlinx.android.synthetic.main.fragment_confirm_transfer.confirmTransferAmount
import kotlinx.android.synthetic.main.fragment_confirm_transfer.confirmTransferBalance
import kotlinx.android.synthetic.main.fragment_confirm_transfer.confirmTransferBalanceLabel
import kotlinx.android.synthetic.main.fragment_confirm_transfer.confirmTransferFee
import kotlinx.android.synthetic.main.fragment_confirm_transfer.confirmTransferRecipientView
import kotlinx.android.synthetic.main.fragment_confirm_transfer.confirmTransferSubmit
import kotlinx.android.synthetic.main.fragment_confirm_transfer.confirmTransferToken
import kotlinx.android.synthetic.main.fragment_confirm_transfer.confirmTransferToolbar
import kotlinx.android.synthetic.main.fragment_confirm_transfer.confirmTransferTotal

private const val KEY_DRAFT = "KEY_DRAFT"

class ConfirmTransferFragment : BaseFragment<ConfirmTransferViewModel>() {

    companion object {
        fun getBundle(transferDraft: TransferDraft) = Bundle().apply {
            putParcelable(KEY_DRAFT, transferDraft)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = layoutInflater.inflate(R.layout.fragment_confirm_transfer, container, false)

    override fun initViews() {
        confirmTransferRecipientView.setActionClickListener { viewModel.copyRecipientAddressClicked() }

        confirmTransferToolbar.setHomeButtonListener { viewModel.backClicked() }

        confirmTransferSubmit.setOnClickListener { viewModel.submitClicked() }
        confirmTransferSubmit.prepareForProgress(viewLifecycleOwner)

        confirmTransferBalanceLabel.setOnClickListener { viewModel.availableBalanceClicked() }
    }

    override fun inject() {
        val transferDraft = argument<TransferDraft>(KEY_DRAFT)

        FeatureUtils.getFeature<WalletFeatureComponent>(
            requireContext(),
            WalletFeatureApi::class.java
        )
            .confirmTransferComponentFactory()
            .create(this, transferDraft)
            .inject(this)
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

            confirmTransferBalance.text = it.available.formatTokenAmount(it.token.configuration)

            with(viewModel.transferDraft) {
                // TODO wallet - icon
//            confirmTransferToken.setTextIcon(chainAsset.icon)
                confirmTransferToken.setMessage(chainAsset.symbol)

                confirmTransferFee.text = fee.formatTokenAmount(chainAsset)

                confirmTransferTotal.text = totalTransaction.formatTokenAmount(chainAsset)

                confirmTransferAmount.setMessage(amount.toPlainString())
            }
        }

        viewModel.recipientModel.observe {
            confirmTransferRecipientView.setTextIcon(it.image)
            confirmTransferRecipientView.setMessage(it.address)
        }

        viewModel.sendButtonStateLiveData.observe(confirmTransferSubmit::setState)

        viewModel.showBalanceDetailsEvent.observeEvent {
            BalanceDetailsBottomSheet(requireContext(), it).show()
        }
    }
}
