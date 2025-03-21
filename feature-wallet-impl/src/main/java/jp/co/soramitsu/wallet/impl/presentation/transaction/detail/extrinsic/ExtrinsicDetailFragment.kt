package jp.co.soramitsu.wallet.impl.presentation.transaction.detail.extrinsic

import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.data.network.BlockExplorerUrlBuilder
import jp.co.soramitsu.common.utils.formatDateTime
import jp.co.soramitsu.common.utils.showBrowser
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.account.api.presentation.actions.ExternalActionsSheet
import jp.co.soramitsu.account.api.presentation.actions.ExternalViewCallback
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentExtrinsicDetailsBinding
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

@AndroidEntryPoint
class ExtrinsicDetailFragment : BaseFragment<ExtrinsicDetailViewModel>(R.layout.fragment_extrinsic_details) {
    companion object {
        const val PAYLOAD_KEY = "PAYLOAD_KEY"

        fun getBundle(payload: ExtrinsicDetailsPayload) = bundleOf(PAYLOAD_KEY to payload)
    }

    private val binding by viewBinding(FragmentExtrinsicDetailsBinding::bind)

    override val viewModel: ExtrinsicDetailViewModel by viewModels()

    override fun initViews() {
        binding.extrinsicDetailToolbar.setHomeButtonListener { viewModel.backClicked() }

        binding.extrinsicDetailHash.setWholeClickListener {
            viewModel.showExternalActionsClicked(ExternalActionsSource.TRANSACTION_HASH)
        }

        binding.extrinsicDetailFrom.setWholeClickListener {
            viewModel.showExternalActionsClicked(ExternalActionsSource.FROM_ADDRESS)
        }
    }

    override fun subscribe(viewModel: ExtrinsicDetailViewModel) {
        with(viewModel.payload.operation) {
            with(binding) {
                extrinsicDetailHash.setMessage(hash)
                extrinsicDetailStatus.setText(statusAppearance.labelRes)
                extrinsicDetailStatusIcon.setImageResource(statusAppearance.icon)
                extrinsicDetailDate.text = time.formatDateTime(requireContext())
                extrinsicDetailModule.text = module
                extrinsicDetailCall.text = call
                extrinsicDetailFee.text = fee
            }
        }

        viewModel.showExternalExtrinsicActionsEvent.observeEvent(::showExternalActions)
        viewModel.openBrowserEvent.observeEvent(::showBrowser)

        viewModel.fromAddressModelLiveData.observe { addressModel ->
            binding.extrinsicDetailFrom.setMessage(addressModel.nameOrAddress)
            binding.extrinsicDetailFrom.setTextIcon(addressModel.image)
        }
    }

    private fun showExternalActions(externalActionsSource: ExternalActionsSource) {
        when (externalActionsSource) {
            ExternalActionsSource.TRANSACTION_HASH -> showExternalTransactionActions()
            ExternalActionsSource.FROM_ADDRESS -> showExternalAddressActions(viewModel.payload.operation.originAddress)
        }
    }

    private fun showExternalAddressActions(
        address: String
    ) = showExternalActionsSheet(
        copyLabelRes = R.string.common_copy_address,
        value = address,
        explorers = viewModel.getSupportedAddressExplorers(address),
        externalViewCallback = viewModel::openUrl
    )

    private fun showExternalTransactionActions() = showExternalActionsSheet(
        copyLabelRes = R.string.transaction_details_copy_hash,
        value = viewModel.payload.operation.hash,
        explorers = viewModel.getSupportedExplorers(BlockExplorerUrlBuilder.Type.EXTRINSIC, viewModel.payload.operation.hash),
        externalViewCallback = viewModel::openUrl
    )

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
