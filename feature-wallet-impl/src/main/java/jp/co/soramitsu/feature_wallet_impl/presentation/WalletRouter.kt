package jp.co.soramitsu.feature_wallet_impl.presentation

import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_impl.presentation.model.ExtrinsicParcelizeModel
import jp.co.soramitsu.feature_wallet_impl.presentation.model.RewardParcelizeModel
import jp.co.soramitsu.feature_wallet_impl.presentation.model.TransferParcelizeModel
import jp.co.soramitsu.feature_wallet_impl.presentation.send.TransferDraft

interface WalletRouter {
    fun openAssetDetails(type: Token.Type)

    fun back()

    fun openChooseRecipient()

    fun openChooseAmount(recipientAddress: String)

    fun openConfirmTransfer(transferDraft: TransferDraft)

    fun finishSendFlow()

    fun openRepeatTransaction(recipientAddress: String)

    fun openTransferDetail(transaction: TransferParcelizeModel)

    fun openExtrinsicDetail(extrinsic: ExtrinsicParcelizeModel)

    fun openRewardDetail(reward: RewardParcelizeModel)

    fun openAddAccount()

    fun openChangeAccountFromWallet()

    fun openReceive()
}
