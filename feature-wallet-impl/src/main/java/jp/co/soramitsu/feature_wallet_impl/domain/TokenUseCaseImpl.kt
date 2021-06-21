package jp.co.soramitsu.feature_wallet_impl.domain

import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.currentNetworkType
import jp.co.soramitsu.feature_wallet_api.domain.TokenUseCase
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.TokenRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest

class TokenUseCaseImpl(
    private val tokenRepository: TokenRepository,
    private val accountRepository: AccountRepository
) : TokenUseCase {

    override suspend fun currentToken(): Token {
        return tokenRepository.getToken(accountRepository.currentNetworkType())!!
    }

    override fun currentTokenFlow(): Flow<Token> {
        return accountRepository.selectedNetworkTypeFlow()
            .flatMapLatest(tokenRepository::observeToken)
    }
}
