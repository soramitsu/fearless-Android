package jp.co.soramitsu.nft.impl.domain.usecase.tokensbycontract

import jp.co.soramitsu.nft.domain.models.NFTCollection
import jp.co.soramitsu.nft.impl.domain.models.nft.CollectionWithTokensImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokensTrimmingMediator @Inject constructor() {

    operator fun invoke(factory: () -> Flow<NFTCollection.Loaded>): Flow<NFTCollection.Loaded> {
        return flow {
            val userOwnedTokensIds = mutableSetOf<BigInteger>()

            factory().map { collection ->
                if (collection !is CollectionWithTokensImpl) {
                    return@map collection
                }

                val trimmedTokens = collection.tokens.mapNotNull { token ->
                    when {
                        token.isUserOwnedToken ->
                            token.also { userOwnedTokensIds.add(it.tokenId) }

                        token.tokenId !in userOwnedTokensIds -> token

                        else -> null
                    }
                }

                collection.copy(trimmedTokens)
            }.collect(this)
        }
    }
}
