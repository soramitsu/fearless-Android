package jp.co.soramitsu.wallet.impl.presentation.transaction.detail.reward

import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.data.network.BlockExplorerUrlBuilder
import jp.co.soramitsu.common.utils.formatDateTime
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.setTextColorRes
import jp.co.soramitsu.common.utils.showBrowser
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.account.api.presentation.actions.ExternalActionsSheet
import jp.co.soramitsu.account.api.presentation.actions.ExternalViewCallback
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentRewardSlashDetailsBinding
import jp.co.soramitsu.wallet.impl.presentation.model.OperationParcelizeModel
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

@AndroidEntryPoint
class RewardDetailFragment : BaseFragment<RewardDetailViewModel>(R.layout.fragment_reward_slash_details) {
    companion object {
        const val KEY_PAYLOAD = "KEY_PAYLOAD"

        fun getBundle(payload: RewardDetailsPayload) = bundleOf(KEY_PAYLOAD to payload)
    }

    private val binding by viewBinding(FragmentRewardSlashDetailsBinding::bind)

    override val viewModel: RewardDetailViewModel by viewModels()

    override fun initViews() {
        binding.rewardDetailToolbar.setHomeButtonListener { viewModel.backClicked() }

        binding.rewardDetailHash.setWholeClickListener {
            viewModel.showExternalActionsClicked(ExternalActionsSource.TRANSACTION_HASH)
        }

        binding.rewardDetailValidator.setWholeClickListener {
            viewModel.showExternalActionsClicked(ExternalActionsSource.VALIDATOR_ADDRESS)
        }
    }

    private fun amountColorRes(operation: OperationParcelizeModel.Reward) = when {
        operation.isReward -> R.color.green
        else -> R.color.white
    }

    override fun subscribe(viewModel: RewardDetailViewModel) {
        with(viewModel.payload!!.operation) {
            binding.rewardDetailHash.setMessage(eventId)
            binding.rewardDetailDate.text = time.formatDateTime(requireContext())
            binding.rewardDetailReward.text = amount
            binding.rewardDetailReward.setTextColorRes(amountColorRes(this))

            if (isReward) {
                binding.rewardDetailRewardLabel.setText(R.string.staking_reward)
            } else {
                binding.rewardDetailRewardLabel.setText(R.string.staking_slash)
            }
        }

        viewModel.showExternalRewardActionsEvent.observeEvent(::showExternalActions)

        viewModel.openBrowserEvent.observeEvent(::showBrowser)

        viewModel.validatorAddressModelLiveData.observe { addressModel ->
            if (addressModel != null) {
                binding.rewardDetailValidator.setMessage(addressModel.nameOrAddress)
                binding.rewardDetailValidator.setTextIcon(addressModel.image)
            } else {
                binding.rewardDetailValidator.makeGone()
            }
        }

        viewModel.eraLiveData.observe {
            binding.rewardDetailEra.text = it
        }
    }

    private fun showExternalActions(externalActionsSource: ExternalActionsSource) {
        val transaction = viewModel.payload!!.operation

        when (externalActionsSource) {
            ExternalActionsSource.TRANSACTION_HASH -> showExternalEventActions()
            ExternalActionsSource.VALIDATOR_ADDRESS -> showExternalAddressActions(transaction.validator!!)
        }
    }

    private fun showExternalAddressActions(address: String) = showExternalActionsSheet(
        copyLabelRes = R.string.common_copy_address,
        value = address,
        explorers = viewModel.getSupportedExplorers(BlockExplorerUrlBuilder.Type.ACCOUNT, address),
        externalViewCallback = viewModel::openUrl
    )

    private fun showExternalEventActions() = showExternalActionsSheet(
        copyLabelRes = R.string.common_copy_id,
        value = viewModel.payload!!.operation.eventId,
        explorers = viewModel.getSupportedExplorers(BlockExplorerUrlBuilder.Type.EVENT, viewModel.payload!!.operation.eventId),
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
                chainId = viewModel.payload!!.chainId,
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
