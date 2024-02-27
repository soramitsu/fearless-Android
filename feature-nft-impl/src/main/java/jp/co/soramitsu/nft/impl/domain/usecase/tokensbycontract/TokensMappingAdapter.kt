package jp.co.soramitsu.nft.impl.domain.usecase.tokensbycontract

import jp.co.soramitsu.nft.data.models.TokenInfo
import jp.co.soramitsu.nft.data.pagination.PageBackStack
import jp.co.soramitsu.nft.data.pagination.PagedResponse
import jp.co.soramitsu.nft.domain.models.NFTCollectionResult
import jp.co.soramitsu.nft.impl.domain.models.nft.CollectionWithTokensImpl
import jp.co.soramitsu.nft.impl.domain.models.nft.NFTImpl
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

class TokensMappingAdapter @Inject constructor(
    private val chainsRepository: ChainsRepository
) {

    private val chainRef: AtomicReference<Chain?> = AtomicReference(null)

    operator fun invoke(factory: () -> Flow<PagedResponse<TokenInfo>>): Flow<NFTCollectionResult> =
        factory().map { it.mapToNFTCollectionResultWithToken() }.flowOn(Dispatchers.Default)

    private suspend fun PagedResponse<TokenInfo>.mapToNFTCollectionResultWithToken(): NFTCollectionResult {
        val (chainId, chainName) =
            (chainRef.get() ?: chainsRepository.getChain(tag as ChainId).also { chainRef.set(it) })
                .run { id to name }

        return result.mapCatching { pageResult ->
            if (pageResult !is PageBackStack.PageResult.ValidPage) {
                return@mapCatching NFTCollectionResult.Empty(chainId, chainName)
            }

            CollectionWithTokensImpl(
                response = pageResult,
                chainId = chainId,
                chainName = chainName,
                tokens = pageResult.items
                    .filter { token -> token.id?.tokenId != null }
                    .map { token -> NFTImpl(token, chainId, chainName) }
            )
        }.getOrElse { throwable ->
            NFTCollectionResult.Error(
                chainId = chainId,
                chainName = chainName,
                throwable = throwable
            )
        }
    }
}
