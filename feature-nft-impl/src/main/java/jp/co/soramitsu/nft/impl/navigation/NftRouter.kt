package jp.co.soramitsu.nft.impl.navigation

import jp.co.soramitsu.nft.domain.models.NFT
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.flow.SharedFlow

interface NftRouter {

    val destinationsFlow: SharedFlow<Destination>

    fun back()

    fun openCollectionNFTsScreen(selectedChainId: ChainId, contractAddress: String)

    fun openChooseRecipientScreen(token: NFT.Full)

    fun openNFTSendScreen(token: NFT.Full, receiver: String)

    fun openAddressHistory(chainId: ChainId)

    fun openWalletSelectionScreen(onSelected: (metaAccountId: Long) -> Unit)

    fun openQRCodeScanner()

    fun openSuccessScreen(txHash: String, chainId: ChainId)

    fun openErrorsScreen(message: String)

}

