package jp.co.soramitsu.nft.impl.domain.models.nft

import jp.co.soramitsu.common.utils.formatting.shortenAddress
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.nft.data.models.TokenInfo
import jp.co.soramitsu.nft.domain.models.NFT
import jp.co.soramitsu.shared_utils.extensions.requireHexPrefix
import java.math.BigInteger

class NFTImpl(
    tokenInfo: TokenInfo,
    override val chainId: ChainId,
    override val chainName: String
) : NFT {

    override val title: String by lazy {
        when {
            !tokenInfo.title.isNullOrBlank() -> tokenInfo.title.orEmpty()

            !tokenInfo.metadata?.name.isNullOrBlank() -> tokenInfo.metadata?.name.orEmpty()

            else -> {
                val tokenIdShortString = buildString {
                    if (tokenId <= BigInteger("99")) {
                        append(tokenId)
                        return@buildString
                    }

                    append(tokenId.toString().take(DEFAULT_TOKEN_ID_SHORTENED_LENGTH))
                    append("...")
                }

                "$collectionName #$tokenIdShortString"
            }
        }
    }

    override val thumbnail: String by lazy {
        val media = tokenInfo.media?.firstOrNull {
            !it.thumbnail.isNullOrBlank() || !it.gateway.isNullOrBlank()
        }

        when {
            !media?.thumbnail.isNullOrBlank() -> media?.thumbnail.orEmpty()

            !media?.gateway.isNullOrBlank() -> media?.gateway.orEmpty()

            !tokenInfo.contractMetadata?.openSea?.imageUrl.isNullOrBlank() ->
                tokenInfo.contractMetadata?.openSea?.imageUrl.orEmpty()

            else -> ""
        }
    }

    override val description: String by lazy {
        tokenInfo.contractMetadata?.openSea?.description ?: collectionName
    }

    override val collectionName: String by lazy {
        when {
            !tokenInfo.contractMetadata?.openSea?.collectionName.isNullOrBlank() ->
                tokenInfo.contractMetadata?.openSea?.collectionName.orEmpty()

            !tokenInfo.contractMetadata?.name.isNullOrBlank() ->
                tokenInfo.contractMetadata?.name.orEmpty()

            else -> contractAddress.requireHexPrefix().drop(DEFAULT_HEX_PREFIX_LENGTH)
                .shortenAddress(DEFAULT_CONTRACT_ADDRESS_SHORTENED_LENGTH)
        }
    }

    override val contractAddress: String by lazy {
        tokenInfo.contract?.address.orEmpty()
    }

    override val creatorAddress: String? by lazy {
        tokenInfo.contractMetadata?.contractDeployer
    }

    override val isUserOwnedToken: Boolean by lazy {
        !tokenInfo.balance.isNullOrEmpty() && tokenInfo.balance?.toIntOrNull() != 0
    }

    override val tokenId: BigInteger by lazy {
        tokenInfo.id?.tokenId?.requireHexPrefix()?.drop(2)?.run {
            BigInteger(this, DEFAULT_TOKEN_ID_RADIX)
        } ?: BigInteger("-1")
    }

    override val tokenType: String by lazy {
        tokenInfo.contractMetadata?.tokenType.orEmpty()
    }

    override val date: String? by lazy {
        null
    }

    override val price: String by lazy {
        ""
    }

    private companion object {
        const val DEFAULT_HEX_PREFIX_LENGTH = 2
        const val DEFAULT_CONTRACT_ADDRESS_SHORTENED_LENGTH = 3
        const val DEFAULT_TOKEN_ID_SHORTENED_LENGTH = 3
        const val DEFAULT_TOKEN_ID_RADIX = 16
    }
}
