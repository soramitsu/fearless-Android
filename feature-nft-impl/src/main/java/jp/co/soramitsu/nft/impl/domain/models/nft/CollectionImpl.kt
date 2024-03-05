package jp.co.soramitsu.nft.impl.domain.models.nft

import jp.co.soramitsu.common.utils.formatting.shortenAddress
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.nft.data.models.ContractInfo
import jp.co.soramitsu.nft.domain.models.NFTCollection
import jp.co.soramitsu.shared_utils.extensions.requireHexPrefix

class CollectionImpl(
    override val chainId: ChainId,
    override val chainName: String,
    response: ContractInfo
) : NFTCollection.Loaded.Result.Collection {

    override val contractAddress: String by lazy {
        response.address.orEmpty()
    }

    override val collectionName: String by lazy {
        if (!response.title.isNullOrBlank()) {
            response.title.orEmpty()
        } else if (!response.openSea?.collectionName.isNullOrBlank()) {
            response.openSea?.collectionName.orEmpty()
        } else if (!response.name.isNullOrBlank()) {
            response.name.orEmpty()
        } else {
            contractAddress.requireHexPrefix().drop(DEFAULT_HEX_PREFIX_LENGTH)
                .shortenAddress(DEFAULT_CONTRACT_ADDRESS_SHORTENED_LENGTH)
        }
    }

    override val description: String by lazy {
        response.openSea?.description ?: collectionName
    }

    override val imageUrl: String by lazy {
        response.media?.firstOrNull {
            !it.thumbnail.isNullOrBlank()
        }?.thumbnail ?: response.openSea?.imageUrl.orEmpty()
    }

    override val type: String by lazy {
        response.tokenType.orEmpty()
    }

    override val balance: Int by lazy {
        response.totalBalance ?: response.numDistinctTokensOwned ?: 0
    }

    override val collectionSize: Int by lazy {
        response.totalSupply ?: response.totalBalance ?: 0
    }

    private companion object {
        const val DEFAULT_HEX_PREFIX_LENGTH = 2
        const val DEFAULT_CONTRACT_ADDRESS_SHORTENED_LENGTH = 3
    }
}
