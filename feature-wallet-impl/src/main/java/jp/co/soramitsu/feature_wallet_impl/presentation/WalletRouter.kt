package jp.co.soramitsu.feature_wallet_impl.presentation

import jp.co.soramitsu.feature_wallet_impl.presentation.model.OperationParcelizeModel
import jp.co.soramitsu.feature_wallet_impl.presentation.send.TransferDraft

interface WalletRouter {
    fun openAssetDetails(type: Token.Type)

    fun back()

    fun openChooseRecipient()

    fun openFilter()

    fun openChooseAmount(recipientAddress: String)

    fun openConfirmTransfer(transferDraft: TransferDraft)

    fun finishSendFlow()

    fun openRepeatTransaction(recipientAddress: String)

    fun openTransferDetail(transaction: OperationParcelizeModel.Transfer)

    fun openExtrinsicDetail(extrinsic: OperationParcelizeModel.Extrinsic)

    fun openRewardDetail(reward: OperationParcelizeModel.Reward)

    fun openAddAccount()

    fun openChangeAccountFromWallet()

    fun openReceive()
}
