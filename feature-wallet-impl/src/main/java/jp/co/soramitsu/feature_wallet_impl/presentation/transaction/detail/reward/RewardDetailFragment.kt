package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail.reward

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.data.network.BlockExplorerUrlBuilder
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.formatDateTime
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.setTextColorRes
import jp.co.soramitsu.common.utils.showBrowser
import jp.co.soramitsu.feature_account_api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_account_api.presentation.actions.ExternalActionsSheet
import jp.co.soramitsu.feature_account_api.presentation.actions.ExternalViewCallback
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.model.OperationParcelizeModel
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.android.synthetic.main.fragment_reward_slash_details.rewardDetailDate
import kotlinx.android.synthetic.main.fragment_reward_slash_details.rewardDetailEra
import kotlinx.android.synthetic.main.fragment_reward_slash_details.rewardDetailHash
import kotlinx.android.synthetic.main.fragment_reward_slash_details.rewardDetailReward
import kotlinx.android.synthetic.main.fragment_reward_slash_details.rewardDetailRewardLabel
import kotlinx.android.synthetic.main.fragment_reward_slash_details.rewardDetailToolbar
import kotlinx.android.synthetic.main.fragment_reward_slash_details.rewardDetailValidator

class RewardDetailFragment : BaseFragment<RewardDetailViewModel>() {
    companion object {
        private const val KEY_PAYLOAD = "KEY_PAYLOAD"

        fun getBundle(payload: RewardDetailsPayload) = bundleOf(KEY_PAYLOAD to payload)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = layoutInflater.inflate(R.layout.fragment_reward_slash_details, container, false)

    override fun initViews() {
        rewardDetailToolbar.setHomeButtonListener { viewModel.backClicked() }

        rewardDetailHash.setWholeClickListener {
            viewModel.showExternalActionsClicked(ExternalActionsSource.TRANSACTION_HASH)
        }

        rewardDetailValidator.setWholeClickListener {
            viewModel.showExternalActionsClicked(ExternalActionsSource.VALIDATOR_ADDRESS)
        }
    }

    override fun inject() {
        val payload = argument<RewardDetailsPayload>(KEY_PAYLOAD)

        FeatureUtils.getFeature<WalletFeatureComponent>(
            requireContext(),
            WalletFeatureApi::class.java
        )
            .rewardDetailComponentFactory()
            .create(this, payload)
            .inject(this)
    }

    private fun amountColorRes(operation: OperationParcelizeModel.Reward) = when {
        operation.isReward -> R.color.green
        else -> R.color.white
    }

    override fun subscribe(viewModel: RewardDetailViewModel) {
        with(viewModel.payload.operation) {
            rewardDetailHash.setMessage(eventId)
            rewardDetailDate.text = time.formatDateTime(requireContext())
            rewardDetailReward.text = amount
            rewardDetailReward.setTextColorRes(amountColorRes(this))

            if (isReward) {
                rewardDetailRewardLabel.setText(R.string.staking_reward)
            } else {
                rewardDetailRewardLabel.setText(R.string.staking_slash)
            }
        }

        viewModel.showExternalRewardActionsEvent.observeEvent(::showExternalActions)

        viewModel.openBrowserEvent.observeEvent(::showBrowser)

        viewModel.validatorAddressModelLiveData.observe { addressModel ->
            if (addressModel != null) {
                rewardDetailValidator.setMessage(addressModel.nameOrAddress)
                rewardDetailValidator.setTextIcon(addressModel.image)
            } else {
                rewardDetailValidator.makeGone()
            }
        }

        viewModel.eraLiveData.observe {
            rewardDetailEra.text = it
        }
    }

    private fun showExternalActions(externalActionsSource: ExternalActionsSource) {
        val transaction = viewModel.payload.operation

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
        value = viewModel.payload.operation.eventId,
        explorers = viewModel.getSupportedExplorers(BlockExplorerUrlBuilder.Type.EVENT, viewModel.payload.operation.eventId),
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
                chainId = viewModel.payload.chainId,
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
