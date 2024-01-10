package jp.co.soramitsu.nft.domain

import jp.co.soramitsu.nft.domain.models.NFTCollection
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

interface NFTTransferInteractor {

    suspend fun networkFeeFlow(
        token: NFTCollection.NFT.Full,
        receiver: String,
        erc1155TransferAmount: BigDecimal? // null for erc721
    ): Flow<BigDecimal>

    suspend fun send(
        token: NFTCollection.NFT.Full,
        receiver: String,
        erc1155TransferAmount: BigDecimal? // null for erc721
    ): Result<String>

}
