package jp.co.soramitsu.nft.impl.navigation

import jp.co.soramitsu.nft.domain.models.NFT
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.walletselector.light.WalletSelectionMode
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class InternalNFTRouterImpl(
    private val walletRouter: WalletRouter
): InternalNFTRouter {

    private val mutableDestinationsFlow =
        MutableSharedFlow<Destination>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val destinationsFlow: SharedFlow<Destination> = mutableDestinationsFlow

    override fun back() {
        mutableDestinationsFlow.tryEmit(Destination.Action.BackPressed)
    }

    override fun openCollectionNFTsScreen(selectedChainId: ChainId, contractAddress: String) {
        mutableDestinationsFlow.tryEmit(Destination.NestedNavGraphRoute.CollectionNFTsScreen(selectedChainId, contractAddress))
    }

    override fun openChooseRecipientScreen(token: NFT.Full) {
        mutableDestinationsFlow.tryEmit(Destination.NestedNavGraphRoute.ChooseNFTRecipientScreen(token))
    }

    override fun openNFTSendScreen(token: NFT.Full, receiver: String) {
        mutableDestinationsFlow.tryEmit(Destination.NestedNavGraphRoute.ConfirmNFTSendScreen(token, receiver, false))
    }

    override fun openAddressHistory(chainId: ChainId): Flow<String> {
        return walletRouter.openAddressHistoryWithResult(chainId)
    }

    override fun openWalletSelectionScreen(selectedWalletId: Long?): Flow<Long> {
        return walletRouter.openWalletSelectorForResult(
            selectedWalletId = selectedWalletId,
            walletSelectionMode = WalletSelectionMode.ExternalSelectedWallet
        )
    }

    override fun openQRCodeScanner() {
        mutableDestinationsFlow.tryEmit(Destination.Action.QRCodeScanner)
    }

    override fun openSuccessScreen(txHash: String, chainId: ChainId) {
        walletRouter.openOperationSuccess(txHash, chainId)
    }

    override fun openErrorsScreen(message: String) {
        mutableDestinationsFlow.tryEmit(Destination.Action.ShowError(message))
    }

}