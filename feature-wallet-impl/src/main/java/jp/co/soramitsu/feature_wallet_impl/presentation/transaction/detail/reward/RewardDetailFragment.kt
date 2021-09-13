package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail.reward

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.StringRes
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.data.network.ExternalAnalyzer
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.formatDateTime
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.networkType
import jp.co.soramitsu.common.utils.setTextColorRes
import jp.co.soramitsu.common.utils.showBrowser
import jp.co.soramitsu.feature_account_api.presenatation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_account_api.presenatation.actions.ExternalActionsSheet
import jp.co.soramitsu.feature_account_api.presenatation.actions.ExternalViewCallback
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.model.OperationParcelizeModel
import kotlinx.android.synthetic.main.fragment_reward_slash_details.rewardDetailDate
import kotlinx.android.synthetic.main.fragment_reward_slash_details.rewardDetailEra
import kotlinx.android.synthetic.main.fragment_reward_slash_details.rewardDetailHash
import kotlinx.android.synthetic.main.fragment_reward_slash_details.rewardDetailReward
import kotlinx.android.synthetic.main.fragment_reward_slash_details.rewardDetailRewardLabel
import kotlinx.android.synthetic.main.fragment_reward_slash_details.rewardDetailToolbar
import kotlinx.android.synthetic.main.fragment_reward_slash_details.rewardDetailValidator

private const val KEY_REWARD = "KEY_REWARD"

class RewardDetailFragment : BaseFragment<RewardDetailViewModel>() {
    companion object {
        fun getBundle(operation: OperationParcelizeModel.Reward) = Bundle().apply {
            putParcelable(KEY_REWARD, operation)
        }
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
        val operation = argument<OperationParcelizeModel.Reward>(KEY_REWARD)

        FeatureUtils.getFeature<WalletFeatureComponent>(
            requireContext(),
            WalletFeatureApi::class.java
        )
            .rewardDetailComponentFactory()
            .create(this, operation)
            .inject(this)
    }

    private fun amountColorRes(operation: OperationParcelizeModel.Reward) = when {
        operation.isReward -> R.color.green
        else -> R.color.white
    }

    override fun subscribe(viewModel: RewardDetailViewModel) {
        with(viewModel.operation) {
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
        val transaction = viewModel.operation

        when (externalActionsSource) {
            ExternalActionsSource.TRANSACTION_HASH -> showExternalEventActions()
            ExternalActionsSource.VALIDATOR_ADDRESS -> showExternalAddressActions(transaction.validator!!)
        }
    }

    private fun showExternalAddressActions(
        address: String
    ) = showExternalActionsSheet(
        copyLabelRes = R.string.common_copy_address,
        value = address,
        externalViewCallback = viewModel::viewAccountExternalClicked
    )

    private fun showExternalEventActions() {
        showExternalActionsSheet(
            R.string.common_copy_id,
            viewModel.operation.eventId,
            viewModel::viewEventExternalClicked,
            forceForbid = setOf(ExternalAnalyzer.SUBSCAN)
        )
    }

    private fun showExternalActionsSheet(
        @StringRes copyLabelRes: Int,
        value: String,
        externalViewCallback: ExternalViewCallback,
        forceForbid: Set<ExternalAnalyzer> = emptySet(),
    ) {
        val payload = ExternalActionsSheet.Payload(
            copyLabel = copyLabelRes,
            content = ExternalAccountActions.Payload(
                value = value,
                networkType = viewModel.operation.address.networkType()
            ),
            forceForbid = forceForbid
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
