package jp.co.soramitsu.feature_wallet_impl.presentation.send.confirm

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.model.icon
import jp.co.soramitsu.feature_wallet_impl.presentation.send.TransferDraft
import jp.co.soramitsu.feature_wallet_impl.util.formatAsToken
import kotlinx.android.synthetic.main.fragment_confirm_transfer.confirmTransferAmount
import kotlinx.android.synthetic.main.fragment_confirm_transfer.confirmTransferBalance
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
        confirmTransferRecipientView.setOnCopyClickListener { viewModel.copyRecipientAddressClicked() }

        confirmTransferToolbar.setHomeButtonListener { viewModel.backClicked() }

        confirmTransferSubmit.setOnClickListener { viewModel.submitClicked() }
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

    override fun subscribe(viewModel: ConfirmTransferViewModel) {
        with(viewModel.transferDraft) {
            confirmTransferBalance.text = balance.formatAsToken(token)

            confirmTransferToken.setIcon(token.icon)
            confirmTransferToken.setText(token.displayName)

            confirmTransferFee.text = fee.formatAsToken(token)

            confirmTransferTotal.text = total.formatAsToken(token)

            confirmTransferAmount.setText(amount.toPlainString(), TextView.BufferType.NORMAL)
        }

        viewModel.recipientModel.observe {
            confirmTransferRecipientView.setIcon(it.image)
            confirmTransferRecipientView.setAddress(it.address)
        }

        viewModel.transferSubmittingLiveData.observe { submitting ->
            val text = if (submitting) R.string.wallet_send_progress else R.string.wallet_send_confirm_transfer

            confirmTransferSubmit.isEnabled = !submitting
            confirmTransferSubmit.setText(text)
        }
    }
}