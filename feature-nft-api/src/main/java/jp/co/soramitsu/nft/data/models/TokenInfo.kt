package jp.co.soramitsu.nft.data.models

import java.math.BigInteger

class TokenInfo(
    val contract: Contract?,
    val id: TokenId?,
    val title: String?,
    val balance: String?,
    val description: String?,
    val media: List<Media>?,
    val metadata: TokenMetadata?,
    val contractMetadata: ContractMetadata?,
    val spamInfo: SpamInfo?
) {
    companion object;

    class Media(
        val gateway: String?,
        val thumbnail: String?,
        val raw: String?,
        val format: String?,
        val bytes: BigInteger?,
    ) {
        companion object;
    }

    class TokenMetadata(
        val name: String?,
        val description: String?,
        val backgroundColor: String?,
        val poster: String?,
        val image: String?,
        val externalUrl: String?
    ) {
        companion object;
    }

    class ContractMetadata(
        val name: String?,
        val symbol: String?,
        val totalSupply: String?,
        val tokenType: String?,
        val contractDeployer: String?,
        val deployedBlockNumber: BigInteger?,
        val openSea: OpenSea?,
    ) {
        companion object;

        class OpenSea(
            val floorPrice: Float?,
            val collectionName: String?,
            val safelistRequestStatus: String?,
            val imageUrl: String?,
            val description: String?,
            val externalUrl: String?,
            val twitterUsername: String?,
            val lastIngestedAt: String?
        ) {
            companion object;
        }
    }

    class SpamInfo(
        val isSpam: Boolean?,
        val classifications: List<String>?,
    )
}