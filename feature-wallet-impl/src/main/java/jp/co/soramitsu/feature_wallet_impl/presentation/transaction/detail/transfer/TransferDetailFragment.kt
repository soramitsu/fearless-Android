package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail.transfer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.StringRes
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.formatDateTime
import jp.co.soramitsu.common.utils.showBrowser
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.feature_account_api.presenatation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_account_api.presenatation.actions.ExternalActionsSheet
import jp.co.soramitsu.feature_account_api.presenatation.actions.ExternalViewCallback
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.model.OperationModel
import jp.co.soramitsu.feature_wallet_impl.presentation.model.TransactionModel
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail.ExternalActionsSource
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail.TransactionDetailViewModel
import kotlinx.android.synthetic.main.fragment_transfer_details.transactionDetailAmount
import kotlinx.android.synthetic.main.fragment_transfer_details.transactionDetailDate
import kotlinx.android.synthetic.main.fragment_transfer_details.transactionDetailFee
import kotlinx.android.synthetic.main.fragment_transfer_details.transactionDetailFrom
import kotlinx.android.synthetic.main.fragment_transfer_details.transactionDetailHash
import kotlinx.android.synthetic.main.fragment_transfer_details.transactionDetailRepeat
import kotlinx.android.synthetic.main.fragment_transfer_details.transactionDetailStatus
import kotlinx.android.synthetic.main.fragment_transfer_details.transactionDetailStatusIcon
import kotlinx.android.synthetic.main.fragment_transfer_details.transactionDetailTo
import kotlinx.android.synthetic.main.fragment_transfer_details.transactionDetailToolbar
import kotlinx.android.synthetic.main.fragment_transfer_details.transactionDetailTotal

private const val KEY_TRANSACTION = "KEY_DRAFT"

class TransferDetailFragment : BaseFragment<TransactionDetailViewModel>() {

    companion object {
        fun getBundle(operation: OperationModel) = Bundle().apply {
            putParcelable(KEY_TRANSACTION, operation)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = layoutInflater.inflate(R.layout.fragment_transfer_details, container, false)

    override fun initViews() {
        transactionDetailToolbar.setHomeButtonListener { viewModel.backClicked() }

        transactionDetailHash.setWholeClickListener {
            viewModel.showExternalActionsClicked(ExternalActionsSource.TRANSACTION_HASH)
        }

        transactionDetailFrom.setWholeClickListener {
            viewModel.showExternalActionsClicked(ExternalActionsSource.FROM_ADDRESS)
        }

        transactionDetailTo.setWholeClickListener {
            viewModel.showExternalActionsClicked(ExternalActionsSource.TO_ADDRESS)
        }

        transactionDetailRepeat.setWholeClickListener {
            viewModel.repeatTransaction()
        }
    }

    override fun inject() {
        val operation = argument<OperationModel>(KEY_TRANSACTION)

        FeatureUtils.getFeature<WalletFeatureComponent>(
            requireContext(),
            WalletFeatureApi::class.java
        )
            .transactionDetailComponentFactory()
            .create(this, operation)
            .inject(this)
    }

    override fun subscribe(viewModel: TransactionDetailViewModel) {
        with(viewModel.operation) {
            transactionDetailStatus.setText(statusAppearance.labelRes)
            transactionDetailStatusIcon.setImageResource(statusAppearance.icon)

            transactionDetailDate.text = time.formatDateTime(requireContext())

            assert(viewModel.operation.transactionType is OperationModel.TransactionModelType.Transfer)
            val amount = viewModel.operation.transactionType.operationAmount
            val fee = viewModel.operation.transactionType.operationFee
            transactionDetailAmount.text = amount.formatTokenAmount(tokenType)
            transactionDetailFee.text = fee.formatTokenAmount(tokenType)

            transactionDetailHash.setMessage(hash)

            transactionDetailTotal.text = (amount + fee).formatTokenAmount(tokenType)
        }

        viewModel.senderAddressModelLiveData.observe { addressModel ->
            transactionDetailFrom.setMessage(addressModel.address)
            transactionDetailFrom.setTextIcon(addressModel.image)
        }

        viewModel.recipientAddressModelLiveData.observe { addressModel ->
            transactionDetailTo.setMessage(addressModel.address)
            transactionDetailTo.setTextIcon(addressModel.image)
        }

        viewModel.retryAddressModelLiveData.observe {
            transactionDetailRepeat.setTitle(it.address)
            transactionDetailRepeat.setAccountIcon(it.image)
        }

        viewModel.showExternalTransactionActionsEvent.observeEvent(::showExternalActions)

        viewModel.openBrowserEvent.observeEvent(::showBrowser)
    }

    private fun showExternalActions(externalActionsSource: ExternalActionsSource) {
        val transaction = viewModel.operation.transactionType as OperationModel.TransactionModelType.Transfer

        when (externalActionsSource) {
            ExternalActionsSource.TRANSACTION_HASH -> showExternalTransactionActions()
            ExternalActionsSource.FROM_ADDRESS -> showExternalAddressActions(transaction.sender)
            ExternalActionsSource.TO_ADDRESS -> showExternalAddressActions(transaction.receiver)
        }
    }

    private fun showExternalAddressActions(
        address: String
    ) = showExternalActionsSheet(
        copyLabelRes = R.string.common_copy_address,
        value = address,
        externalViewCallback = viewModel::viewAccountExternalClicked
    )

    private fun showExternalTransactionActions() {
        showExternalActionsSheet(
            R.string.transaction_details_copy_hash,
            viewModel.operation.hash,
            viewModel::viewTransactionExternalClicked
        )
    }

    private fun showExternalActionsSheet(
        @StringRes copyLabelRes: Int,
        value: String,
        externalViewCallback: ExternalViewCallback
    ) {
        val payload = ExternalActionsSheet.Payload(
            copyLabel = copyLabelRes,
            content = ExternalAccountActions.Payload(
                value = value,
                networkType = Node.NetworkType.POLKADOT //TODO add networktype to operationModel
            )
        )

        ExternalActionsSheet(
            context = requireContext(),
            payload = payload,
            onCopy = viewModel::copyStringClicked,
            onViewExternal = externalViewCallback
        )
            .show()
    }
}
