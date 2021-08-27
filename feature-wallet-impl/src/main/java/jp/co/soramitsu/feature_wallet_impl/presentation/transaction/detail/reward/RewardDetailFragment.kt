package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail.reward

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.StringRes
import jp.co.soramitsu.common.utils.showBrowser
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.formatDateTime
import jp.co.soramitsu.common.utils.networkType
import jp.co.soramitsu.common.utils.setTextColorRes
import jp.co.soramitsu.feature_account_api.presenatation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_account_api.presenatation.actions.ExternalActionsSheet
import jp.co.soramitsu.feature_account_api.presenatation.actions.ExternalViewCallback
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.model.OperationModel
import jp.co.soramitsu.feature_wallet_impl.presentation.model.RewardParcelizeModel
import jp.co.soramitsu.feature_wallet_impl.presentation.model.TransferParcelizeModel
import kotlinx.android.synthetic.main.fragment_reward_slash_details.*
import kotlinx.android.synthetic.main.fragment_reward_slash_details.rewardDetailHash

private const val KEY_REWARD = "KEY_REWARD"

class RewardDetailFragment : BaseFragment<RewardDetailViewModel>() {
    companion object {
        fun getBundle(operation: RewardParcelizeModel) = Bundle().apply {
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
        val operation = argument<RewardParcelizeModel>(KEY_REWARD)

        FeatureUtils.getFeature<WalletFeatureComponent>(
            requireContext(),
            WalletFeatureApi::class.java
        )
            .rewardDetailComponentFactory()
            .create(this, operation)
            .inject(this)
    }

    private fun amountColorRes(operation: RewardParcelizeModel) = when {
        operation.isFailed -> R.color.gray2
        operation.isIncome -> R.color.green
        else -> R.color.white
    }

    override fun subscribe(viewModel: RewardDetailViewModel) {
        with(viewModel.operation) {
            rewardDetailHash.setMessage(hash)
            rewardDetailStatus.setText(messageId)
            rewardDetailStatusIcon.setImageResource(iconId)
            rewardDetailDate.text = time.formatDateTime(requireContext())
            rewardDetailReward.text = formattedAmount
            rewardDetailReward.setTextColorRes(amountColorRes(this))
        }

        viewModel.showExternalRewardActionsEvent.observeEvent(::showExternalActions)

        viewModel.openBrowserEvent.observeEvent(::showBrowser)

        viewModel.validatorAddressModelLiveData.observe { addressModel ->
            rewardDetailValidator.setMessage(addressModel.nameOrAddress)
            rewardDetailValidator.setTextIcon(addressModel.image)
        }

        viewModel.eraLiveData.observe {
            rewardDetailEra.text = it
        }
    }

    private fun showExternalActions(externalActionsSource: ExternalActionsSource) {
        val transaction = viewModel.operation

        when (externalActionsSource) {
            ExternalActionsSource.TRANSACTION_HASH -> showExternalTransactionActions()
            ExternalActionsSource.VALIDATOR_ADDRESS -> showExternalAddressActions(transaction.validator)
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
                networkType = viewModel.operation.address.networkType()
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
