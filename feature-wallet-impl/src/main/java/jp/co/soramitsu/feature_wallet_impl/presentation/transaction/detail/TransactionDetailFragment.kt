package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.StringRes
import jp.co.soramitsu.common.account.external.actions.ExternalAccountActions
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.showBrowser
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.model.TransactionModel
import jp.co.soramitsu.common.account.external.actions.ExternalActionsSheet
import jp.co.soramitsu.common.account.external.actions.ExternalViewCallback
import jp.co.soramitsu.feature_wallet_impl.util.formatAsToken
import jp.co.soramitsu.feature_wallet_impl.util.formatDateTime
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionDetailAmount
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionDetailDate
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionDetailFee
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionDetailFrom
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionDetailHash
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionDetailRepeat
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionDetailStatus
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionDetailStatusIcon
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionDetailTo
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionDetailToolbar
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionDetailTotal

private const val KEY_TRANSACTION = "KEY_DRAFT"

class TransactionDetailFragment : BaseFragment<TransactionDetailViewModel>() {

    companion object {
        fun getBundle(transaction: TransactionModel) = Bundle().apply {
            putParcelable(KEY_TRANSACTION, transaction)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = layoutInflater.inflate(R.layout.fragment_transaction_details, container, false)

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
        val transaction = argument<TransactionModel>(KEY_TRANSACTION)

        FeatureUtils.getFeature<WalletFeatureComponent>(
            requireContext(),
            WalletFeatureApi::class.java
        )
            .transactionDetailComponentFactory()
            .create(this, transaction)
            .inject(this)
    }

    override fun subscribe(viewModel: TransactionDetailViewModel) {
        with(viewModel.transaction) {
            transactionDetailStatus.setText(statusAppearance.labelRes)
            transactionDetailStatusIcon.setImageResource(statusAppearance.icon)

            transactionDetailDate.text = date.formatDateTime(requireContext())

            transactionDetailAmount.text = amount.formatAsToken(type)
            transactionDetailFee.text = fee?.formatAsToken(type) ?: getString(R.string.common_unknown)

            transactionDetailHash.setMessage(hash)

            transactionDetailTotal.text = total?.formatAsToken(type) ?: getString(R.string.common_unknown)
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
        val transaction = viewModel.transaction

        when (externalActionsSource) {
            ExternalActionsSource.TRANSACTION_HASH -> showExternalTransactionActions()
            ExternalActionsSource.FROM_ADDRESS -> showExternalAddressActions(R.string.transaction_details_from, transaction.senderAddress)
            ExternalActionsSource.TO_ADDRESS -> showExternalAddressActions(R.string.choose_amount_to, transaction.recipientAddress)
        }
    }

    private fun showExternalAddressActions(
        @StringRes titleRes: Int,
        address: String
    ) = showExternalActionsSheet(
        titleRes = titleRes,
        copyLabelRes = R.string.common_copy_address,
        value = address,
        externalViewCallback = viewModel::viewAccountExternalClicked
    )

    private fun showExternalTransactionActions() {
        showExternalActionsSheet(
            R.string.transaction_details_hash_title,
            R.string.transaction_details_copy_hash,
            viewModel.transaction.hash,
            viewModel::viewTransactionExternalClicked
        )
    }

    private fun showExternalActionsSheet(
        @StringRes titleRes: Int,
        @StringRes copyLabelRes: Int,
        value: String,
        externalViewCallback: ExternalViewCallback
    ) {
        val payload = ExternalActionsSheet.Payload(
            titleRes = titleRes,
            copyLabel = copyLabelRes,
            content = ExternalAccountActions.Payload(
                value = value,
                networkType = viewModel.transaction.type.networkType
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