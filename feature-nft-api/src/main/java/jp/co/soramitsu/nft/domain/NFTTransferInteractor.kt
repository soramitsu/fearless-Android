package jp.co.soramitsu.nft.domain

import jp.co.soramitsu.nft.domain.models.NFT
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

interface NFTTransferInteractor {

    /**
     * Flow for listening network fees of transferring single NFT token of ERC721/ERC1155 ETH contract.
     *
     * @param token - token of ERC721/ERC1155 ETH contract for estimating network fees
     *
     * @param receiver - receiver's public hash address
     *
     * @param canReceiverAcceptToken - if false ensures that tokens
     * will not be sent to a user who can accept them
     * but this comes with higher Gas(fees) prices
     *
     * So, if we are sure that tokens will be accept by receiver
     * then we can reduce Gas price, and set to true
     *
     * Applicable case: user has already transferred to an account
     * and transaction succeeded, thus we are sure that
     * we can omit an unnecessary check
     *
     * Works for tokens of type ERC721*
     */
    suspend fun networkFeeFlow(
        token: NFT.Full,
        receiver: String,
        canReceiverAcceptToken: Boolean
    ): Flow<Result<BigDecimal>>

    /**
     * Transfer of single NFT token of ERC721/ERC1155 ETH contract.
     *
     * @param token - token of ERC721/ERC1155 ETH contract to be transferred
     *
     * @param receiver - receiver's public hash address
     *
     * @param canReceiverAcceptToken - if false ensures that tokens
     * will not be sent to a user who can accept them
     * but this comes with higher Gas(fees) prices
     *
     * So, if we are sure that tokens will be accept by receiver
     * then we can reduce Gas price, and set to true
     *
     * Applicable case: user has already transferred to an account
     * and transaction succeeded, thus we are sure that
     * we can omit an unnecessary check
     *
     * Works for tokens of type ERC721*
     */
    suspend fun send(
        token: NFT.Full,
        receiver: String,
        canReceiverAcceptToken: Boolean
    ): Result<String>
}
