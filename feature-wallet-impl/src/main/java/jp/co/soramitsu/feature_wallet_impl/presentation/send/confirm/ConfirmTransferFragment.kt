package jp.co.soramitsu.feature_wallet_impl.presentation.send.confirm

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import coil.ImageLoader
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.feature_account_api.presentation.actions.setupExternalActions
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.observeTransferChecks
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.send.BalanceDetailsBottomSheet
import jp.co.soramitsu.feature_wallet_impl.presentation.send.TransferDraft
import kotlinx.android.synthetic.main.fragment_confirm_transfer.confirmAmountField
import kotlinx.android.synthetic.main.fragment_confirm_transfer.confirmFee
import kotlinx.android.synthetic.main.fragment_confirm_transfer.confirmFeeFiat
import kotlinx.android.synthetic.main.fragment_confirm_transfer.confirmTransferRecipientView
import kotlinx.android.synthetic.main.fragment_confirm_transfer.confirmTransferSenderView
import kotlinx.android.synthetic.main.fragment_confirm_transfer.confirmTransferSubmit
import kotlinx.android.synthetic.main.fragment_confirm_transfer.confirmTransferToolbar
import javax.inject.Inject

private const val KEY_DRAFT = "KEY_DRAFT"

class ConfirmTransferFragment : BaseFragment<ConfirmTransferViewModel>() {

    @Inject
    lateinit var imageLoader: ImageLoader

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

            val transferableAmount =
                resources.getString(R.string.wallet_send_transferable_amount_caption, it.available.formatTokenAmount(it.token.configuration))
            confirmAmountField.setAssetBalance(transferableAmount)
            confirmAmountField.setAssetName(it.token.configuration.symbol)
            confirmAmountField.setAssetImageUrl(it.token.configuration.iconUrl, imageLoader)

            with(viewModel.transferDraft) {
                confirmFee.text = fee.formatTokenAmount(chainAsset)
                confirmFeeFiat.text = it.token.fiatAmount(fee)?.formatAsCurrency()

                confirmAmountField.amountInput.setText(totalTransaction.formatTokenAmount(chainAsset))
                confirmAmountField.setAssetBalanceDollarAmount(it.token.fiatAmount(totalTransaction)?.formatAsCurrency())
            }
        }

        viewModel.recipientModel.observe {
            confirmTransferRecipientView.setTextIcon(it.image)
            confirmTransferRecipientView.setMessage(it.address)
        }

        viewModel.senderModel.observe {
            confirmTransferSenderView.setTextIcon(it.image)
            confirmTransferSenderView.setMessage(it.address)
        }

        viewModel.sendButtonStateLiveData.observe(confirmTransferSubmit::setState)

        viewModel.showBalanceDetailsEvent.observeEvent {
            BalanceDetailsBottomSheet(requireContext(), it).show()
        }
    }
}
