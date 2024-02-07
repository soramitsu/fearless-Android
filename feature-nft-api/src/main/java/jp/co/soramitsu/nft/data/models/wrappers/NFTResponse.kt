package jp.co.soramitsu.nft.data.models.wrappers

import jp.co.soramitsu.nft.data.models.ContractInfo
import jp.co.soramitsu.nft.data.models.TokenInfo

sealed interface NFTResponse {

    class UserOwnedContracts(
        val contracts: List<ContractInfo>,
        val nextPage: String?
    ) : NFTResponse {
        companion object;
    }

    class TokensCollection(
        val tokenInfoList: List<TokenInfo>,
        val nextPage: String?
    ) : NFTResponse {
        companion object;
    }

    class TokenOwners(
        val ownersList: List<String>
    ) : NFTResponse {
        companion object
    }
}
