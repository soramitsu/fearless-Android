package jp.co.soramitsu.feature_wallet_impl.presentation

import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_impl.presentation.model.OperationParcelizeModel
import jp.co.soramitsu.feature_wallet_impl.presentation.send.TransferDraft

interface WalletRouter {
    fun openAssetDetails(type: Token.Type)

    fun back()

    fun openChooseRecipient()

    fun openChooseAmount(recipientAddress: String)

    fun openConfirmTransfer(transferDraft: TransferDraft)

    fun finishSendFlow()

    fun openRepeatTransaction(recipientAddress: String)

    fun openTransferDetail(transaction: OperationParcelizeModel.TransferModel)

    fun openExtrinsicDetail(extrinsic: OperationParcelizeModel.ExtrinsicModel)

    fun openRewardDetail(reward: OperationParcelizeModel.RewardModel)

    fun openAddAccount()

    fun openChangeAccountFromWallet()

    fun openReceive()
}
