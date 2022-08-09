package jp.co.soramitsu.wallet.impl.domain.implementations

import jp.co.soramitsu.wallet.impl.domain.TokenUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.TokenRepository
import jp.co.soramitsu.wallet.impl.domain.model.Token
import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import jp.co.soramitsu.runtime.state.chainAsset
import jp.co.soramitsu.runtime.state.selectedAssetFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest

class TokenUseCaseImpl(
    private val tokenRepository: TokenRepository,
    private val sharedState: SingleAssetSharedState
) : TokenUseCase {

    override suspend fun currentToken(): Token {
        val chainAsset = sharedState.chainAsset()

        return tokenRepository.getToken(chainAsset)
    }

    override fun currentTokenFlow(): Flow<Token> {
        return sharedState.selectedAssetFlow().flatMapLatest { chainAsset ->
            tokenRepository.observeToken(chainAsset)
        }
    }
}
