package jp.co.soramitsu.nft.impl.domain.usecase.tokensbycontract

import jp.co.soramitsu.nft.data.models.TokenInfo
import jp.co.soramitsu.nft.data.pagination.PageBackStack
import jp.co.soramitsu.nft.data.pagination.PagedResponse
import jp.co.soramitsu.nft.domain.models.NFTCollection
import jp.co.soramitsu.nft.impl.domain.models.nft.CollectionWithTokensImpl
import jp.co.soramitsu.nft.impl.domain.models.nft.NFTImpl
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokensMappingAdapter @Inject constructor(
    private val chainsRepository: ChainsRepository
) {

    private val chainRef: AtomicReference<Chain?> = AtomicReference(null)

    operator fun invoke(factory: () -> Flow<PagedResponse<TokenInfo>>): Flow<NFTCollection.Loaded> =
        factory().map { it.mapToNFTCollectionResultWithToken() }
            .onCompletion { chainRef.set(null) }.flowOn(Dispatchers.Default)

    private suspend fun PagedResponse<TokenInfo>.mapToNFTCollectionResultWithToken(): NFTCollection.Loaded {
        val chain = chainRef.get().let { savedChain ->
            if (savedChain == null || savedChain.id != tag) {
                chainsRepository.getChain(tag as ChainId).also { chainRef.set(it) }
            } else {
                savedChain
            }
        }

        val (chainId, chainName) = chain.run { id to name }

        return result.mapCatching { pageResult ->
            if (pageResult !is PageBackStack.PageResult.ValidPage) {
                return@mapCatching NFTCollection.Loaded.Result.Empty(chainId, chainName)
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
            NFTCollection.Loaded.WithFailure(
                chainId = chainId,
                chainName = chainName,
                throwable = throwable
            )
        }
    }
}
