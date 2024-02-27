package jp.co.soramitsu.nft.impl.domain.models.nft

import jp.co.soramitsu.common.utils.formatting.shortenAddress
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.nft.data.models.TokenInfo
import jp.co.soramitsu.nft.data.pagination.PageBackStack
import jp.co.soramitsu.nft.domain.models.NFT
import jp.co.soramitsu.nft.domain.models.NFTCollection
import jp.co.soramitsu.shared_utils.extensions.requireHexPrefix

class CollectionWithTokensImpl(
    private val response: PageBackStack.PageResult.ValidPage<TokenInfo>,
    override val chainId: ChainId,
    override val chainName: String,
    override val tokens: Sequence<NFT>
) : NFTCollection.Loaded.Result.Collection.WithTokens {

    private val suitableToken by lazy {
        response.items.firstOrNull {
            !it.contract?.address.isNullOrBlank() && it.contractMetadata != null
        }
    }

    private val contractMetadata by lazy {
        suitableToken?.contractMetadata
    }

    private val itemsCount by lazy {
        response.items.count()
    }

    override val contractAddress: String by lazy {
        suitableToken?.contract?.address.orEmpty()
    }

    override val collectionName: String by lazy {
        when {
            !suitableToken?.contractMetadata?.openSea?.collectionName.isNullOrBlank() ->
                suitableToken?.contractMetadata?.openSea?.collectionName.orEmpty()

            !suitableToken?.contractMetadata?.name.isNullOrBlank() ->
                suitableToken?.contractMetadata?.name.orEmpty()

            else -> contractAddress.requireHexPrefix().drop(DEFAULT_HEX_PREFIX_LENGTH)
                .shortenAddress(DEFAULT_CONTRACT_ADDRESS_SHORTENED_LENGTH)
        }
    }

    override val description: String by lazy {
        contractMetadata?.openSea?.description ?: collectionName
    }

    override val imageUrl: String by lazy {
        val media = suitableToken?.media?.firstOrNull {
            !it.thumbnail.isNullOrBlank() || !it.gateway.isNullOrBlank()
        }

        when {
            !media?.thumbnail.isNullOrBlank() -> media?.thumbnail.orEmpty()

            !media?.gateway.isNullOrBlank() -> media?.gateway.orEmpty()

            !contractMetadata?.openSea?.imageUrl.isNullOrBlank() ->
                contractMetadata?.openSea?.imageUrl.orEmpty()

            else -> ""
        }
    }

    override val type: String by lazy {
        contractMetadata?.tokenType.orEmpty()
    }

    override val balance: Int by lazy {
        itemsCount
    }

    override val collectionSize: Int by lazy {
        contractMetadata?.totalSupply?.toIntOrNull() ?: itemsCount
    }

    fun copy(tokens: Sequence<NFT>): CollectionWithTokensImpl {
        return CollectionWithTokensImpl(
            response, chainId, chainName, tokens
        )
    }

    private companion object {
        const val DEFAULT_HEX_PREFIX_LENGTH = 2
        const val DEFAULT_CONTRACT_ADDRESS_SHORTENED_LENGTH = 3
    }
}
