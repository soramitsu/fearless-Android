package jp.co.soramitsu.feature_wallet_impl.presentation

import it.airgap.beaconsdk.message.SignPayloadBeaconRequest
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_impl.domain.beacon.SignStatus
import jp.co.soramitsu.feature_wallet_impl.presentation.model.TransactionModel
import jp.co.soramitsu.feature_wallet_impl.presentation.send.TransferDraft
import kotlinx.coroutines.flow.Flow

interface WalletRouter {
    fun openAssetDetails(type: Token.Type)

    fun back()

    fun openChooseRecipient()

    fun openChooseAmount(recipientAddress: String)

    fun openConfirmTransfer(transferDraft: TransferDraft)

    fun finishSendFlow()

    fun openRepeatTransaction(recipientAddress: String)

    fun openTransactionDetail(transaction: TransactionModel)

    fun openAddAccount()

    fun openChangeAccountFromWallet()

    fun openReceive()

    fun openSignBeaconTransaction(payload: String)

    val beaconSignStatus: Flow<SignStatus>

    fun setBeaconSignStatus(status: SignStatus)
}
