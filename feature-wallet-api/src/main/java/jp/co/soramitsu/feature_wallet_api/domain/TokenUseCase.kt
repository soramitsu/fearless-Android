package jp.co.soramitsu.feature_wallet_api.domain

import kotlinx.coroutines.flow.Flow

interface TokenUseCase {

    suspend fun currentToken(): Token

    fun currentTokenFlow(): Flow<Token>
}
