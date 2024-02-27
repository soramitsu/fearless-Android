package jp.co.soramitsu.nft.impl.navigation

import jp.co.soramitsu.nft.domain.models.NFT
import jp.co.soramitsu.nft.navigation.NFTNavGraphRoute
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.walletselector.light.WalletSelectionMode
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onEach
import java.util.Stack

class InternalNFTRouterImpl(
    private val walletRouter: WalletRouter
) : InternalNFTRouter {

    private val routesStack = Stack<NFTNavGraphRoute>()

    private val mutableRoutesFlow =
        MutableSharedFlow<NFTNavGraphRoute>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val mutableActionsFlow =
        MutableSharedFlow<NavAction>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override fun createNavGraphRoutesFlow(): Flow<NFTNavGraphRoute> = mutableRoutesFlow.onEach { routesStack.push(it) }

    override fun createNavGraphActionsFlow(): Flow<NavAction> =
        mutableActionsFlow.onEach { if (it is NavAction.BackPressed && !routesStack.isEmpty()) routesStack.pop() }

    override fun <T : NFTNavGraphRoute> destination(clazz: Class<T>): T? {
        return routesStack.filterIsInstance(clazz).lastOrNull()
    }

    override fun back() {
        mutableActionsFlow.tryEmit(NavAction.BackPressed)
    }

    override fun openCollectionNFTsScreen(selectedChainId: ChainId, contractAddress: String) {
        mutableRoutesFlow.tryEmit(NFTNavGraphRoute.CollectionNFTsScreen(selectedChainId, contractAddress))
    }

    override fun openDetailsNFTScreen(token: NFT) {
        mutableRoutesFlow.tryEmit(NFTNavGraphRoute.DetailsNFTScreen(token))
    }

    override fun openChooseRecipientScreen(token: NFT) {
        mutableRoutesFlow.tryEmit(NFTNavGraphRoute.ChooseNFTRecipientScreen(token))
    }

    override fun openNFTSendScreen(token: NFT, receiver: String) {
        mutableRoutesFlow.tryEmit(NFTNavGraphRoute.ConfirmNFTSendScreen(token, receiver, false))
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
        mutableActionsFlow.tryEmit(NavAction.QRCodeScanner)
    }

    override fun openSuccessScreen(txHash: String, chainId: ChainId) {
        walletRouter.openOperationSuccess(txHash, chainId)
    }

    override fun openErrorsScreen(title: String?, message: String) {
        mutableActionsFlow.tryEmit(NavAction.ShowError(title, message))
    }

    override fun showToast(message: String) {
        mutableActionsFlow.tryEmit(NavAction.ShowToast(message))
    }

    override fun shareText(text: String) {
        mutableActionsFlow.tryEmit(NavAction.ShareText(text))
    }
}
