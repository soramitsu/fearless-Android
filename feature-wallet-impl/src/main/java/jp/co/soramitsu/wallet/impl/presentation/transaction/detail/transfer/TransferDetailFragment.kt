package jp.co.soramitsu.wallet.impl.presentation.transaction.detail.transfer

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.account.api.presentation.actions.ExternalActionsSheet
import jp.co.soramitsu.account.api.presentation.actions.ExternalViewCallback
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.utils.formatDateTime
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeInvisible
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.utils.setTextColorRes
import jp.co.soramitsu.common.utils.showBrowser
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentTransferDetailsBinding
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.model.OperationParcelizeModel
import jp.co.soramitsu.wallet.impl.presentation.model.OperationStatusAppearance

const val KEY_TRANSACTION = "KEY_DRAFT"
const val KEY_ASSET_PAYLOAD = "KEY_ASSET_PAYLOAD"
const val KEY_EXPLORER_TYPE = "KEY_EXPLORER_TYPE"

@AndroidEntryPoint
class TransferDetailFragment : BaseFragment<TransactionDetailViewModel>(R.layout.fragment_transfer_details) {

    companion object {
        fun getBundle(operation: OperationParcelizeModel.Transfer, assetPayload: AssetPayload, chainExplorerType: Chain.Explorer.Type?) =
            bundleOf(
                KEY_TRANSACTION to operation,
                KEY_ASSET_PAYLOAD to assetPayload,
                KEY_EXPLORER_TYPE to chainExplorerType
            )
    }

    private val binding by viewBinding(FragmentTransferDetailsBinding::bind)

    override val viewModel: TransactionDetailViewModel by viewModels()

    override fun initViews() {
        binding.transactionDetailToolbar.setHomeButtonListener { viewModel.backClicked() }

        binding.transactionDetailHash.setWholeClickListener {
            viewModel.showExternalActionsClicked(ExternalActionsSource.TRANSACTION_HASH)
        }

        binding.transactionDetailFrom.setWholeClickListener {
            viewModel.showExternalActionsClicked(ExternalActionsSource.FROM_ADDRESS)
        }

        binding.transactionDetailTo.setWholeClickListener {
            viewModel.showExternalActionsClicked(ExternalActionsSource.TO_ADDRESS)
        }

        binding.transactionDetailRepeat.setWholeClickListener {
            viewModel.repeatTransaction()
        }
    }

    private fun amountColorRes(operation: OperationParcelizeModel.Transfer) = when {
        operation.statusAppearance == OperationStatusAppearance.FAILED -> R.color.gray2
        operation.isIncome -> R.color.green
        else -> R.color.white
    }

    override fun subscribe(viewModel: TransactionDetailViewModel) {
        with(viewModel.operation) {
            binding.transactionDetailStatus.setText(statusAppearance.labelRes)
            binding.transactionDetailStatusIcon.setImageResource(statusAppearance.icon)

            binding.transactionDetailDate.text = time.formatDateTime(requireContext())

            if (isIncome) {
                hideOutgoingViews()
            } else {
                showOutgoungViews()
                binding.transactionDetailFee.text = fee
            }

            binding.transactionDetailAmount.text = amount
            binding.transactionDetailAmount.setTextColorRes(amountColorRes(this))

            if (hash != null) {
                binding.transactionDetailHash.setMessage(hash)
            } else {
                binding.transactionDetailHash.makeGone()
            }
        }

        viewModel.senderAddressModelLiveData.observe { addressModel ->
            binding.transactionDetailFrom.setMessage(addressModel.nameOrAddress)
            binding.transactionDetailFrom.setTextIcon(addressModel.image)
        }

        viewModel.recipientAddressModelLiveData.observe { addressModel ->
            binding.transactionDetailTo.setMessage(addressModel.nameOrAddress)
            binding.transactionDetailTo.setTextIcon(addressModel.image)
        }

        viewModel.retryAddressModelLiveData.observe { addressModel ->
            val name = addressModel.name
            if (name != null) {
                binding.transactionDetailRepeat.setTitle(name)
                binding.transactionDetailRepeat.setText(addressModel.address)
                binding.transactionDetailRepeat.showBody()
            } else {
                binding.transactionDetailRepeat.setTitle(addressModel.address)
                binding.transactionDetailRepeat.hideBody()
            }
            binding.transactionDetailRepeat.setAccountIcon(addressModel.image)
        }

        viewModel.showExternalTransactionActionsEvent.observeEvent(::showExternalActions)

        viewModel.openBrowserEvent.observeEvent(::showBrowser)
    }

    private fun hideOutgoingViews() {
        binding.transactionDetailFee.makeGone()
        binding.transactionDetailFeeLabel.makeGone()
        binding.transactionDetailDivider4.makeInvisible()
    }

    private fun showOutgoungViews() {
        with(binding) {
            transactionDetailFee.makeVisible()
            transactionDetailFeeLabel.makeVisible()
            transactionDetailDivider4.makeVisible()
        }
    }

    private fun showExternalActions(externalActionsSource: ExternalActionsSource) {
        val transaction = viewModel.operation

        when (externalActionsSource) {
            ExternalActionsSource.TRANSACTION_HASH -> showExternalTransactionActions()
            ExternalActionsSource.FROM_ADDRESS -> showExternalAddressActions(transaction.sender)
            ExternalActionsSource.TO_ADDRESS -> showExternalAddressActions(transaction.receiver)
        }
    }

    private fun showExternalAddressActions(address: String) = showExternalActionsSheet(
        copyLabelRes = R.string.common_copy_address,
        value = address,
        explorers = viewModel.getSupportedAddressExplorers(address),
        externalViewCallback = viewModel::openUrl
    )

    private fun showExternalTransactionActions() = viewModel.operation.hash?.let { hash ->
        showExternalActionsSheet(
            copyLabelRes = R.string.transaction_details_copy_hash,
            value = hash,
            explorers = viewModel.explorerType?.let { viewModel.getSupportedExplorers(it, hash) }
                .orEmpty(),
            externalViewCallback = viewModel::openUrl
        )
    }

    private fun showExternalActionsSheet(
        @StringRes copyLabelRes: Int,
        value: String,
        explorers: Map<Chain.Explorer.Type, String>,
        externalViewCallback: ExternalViewCallback
    ) {
        val payload = ExternalActionsSheet.Payload(
            copyLabel = copyLabelRes,
            content = ExternalAccountActions.Payload(
                value = value,
                explorers = explorers
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
