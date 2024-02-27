package jp.co.soramitsu.nft.impl.navigation

import jp.co.soramitsu.nft.domain.models.NFT
import jp.co.soramitsu.nft.navigation.NFTNavGraphRoute
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.flow.Flow

@Suppress("ComplexInterface")
interface InternalNFTRouter {

    fun createNavGraphRoutesFlow(): Flow<NFTNavGraphRoute>

    fun createNavGraphActionsFlow(): Flow<NavAction>

    fun <T : NFTNavGraphRoute> destination(clazz: Class<T>): T?

    fun back()

    fun openCollectionNFTsScreen(selectedChainId: ChainId, contractAddress: String)

    fun openDetailsNFTScreen(token: NFT)

    fun openChooseRecipientScreen(token: NFT)

    fun openNFTSendScreen(token: NFT, receiver: String)

    fun openAddressHistory(chainId: ChainId): Flow<String>

    fun openWalletSelectionScreen(selectedWalletId: Long?): Flow<Long>

    fun openQRCodeScanner()

    fun openSuccessScreen(txHash: String, chainId: ChainId)

    fun openErrorsScreen(title: String? = null, message: String)

    fun showToast(message: String)

    fun shareText(text: String)
}
