package jp.co.soramitsu.nft.data.models

class ContractInfo(
    val address: String?,
    val totalBalance: Int?,
    val numDistinctTokensOwned: Int?,
    val isSpam: Boolean?,
    val tokenId: String?,
    val name: String?,
    val title: String?,
    val symbol: String?,
    val totalSupply: Int?,
    val tokenType: String?,
    val contractDeployer: String?,
    val deployedBlockNumber: String?,
    val openSea: TokenInfo.ContractMetadata.OpenSea?,
    val media: List<TokenInfo.Media>?,
) {
    companion object;
}