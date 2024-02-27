package jp.co.soramitsu.nft.data.models.wrappers

import jp.co.soramitsu.nft.data.models.ContractInfo
import jp.co.soramitsu.nft.data.models.TokenInfo
import jp.co.soramitsu.nft.data.pagination.PageBackStack

sealed interface NFTResponse {

    class UserOwnedContracts(
        override val items: Sequence<ContractInfo>,
        override val nextPage: String?
    ) : NFTResponse, PageBackStack.PageResult.ValidPage<ContractInfo> {
        override fun updateItems(items: Sequence<ContractInfo>) = UserOwnedContracts(items, nextPage)

        companion object;
    }

    class TokensCollection(
        override val items: Sequence<TokenInfo>,
        override val nextPage: String?
    ) : NFTResponse, PageBackStack.PageResult.ValidPage<TokenInfo> {
        override fun updateItems(items: Sequence<TokenInfo>) = TokensCollection(items, nextPage)

        companion object;
    }

    class TokenOwners(
        val ownersList: List<String>
    ) : NFTResponse {
        companion object
    }
}
