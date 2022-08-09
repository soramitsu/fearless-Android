package jp.co.soramitsu.featurewalletapi.domain

import jp.co.soramitsu.featurewalletapi.domain.model.Token
import kotlinx.coroutines.flow.Flow

interface TokenUseCase {

    suspend fun currentToken(): Token

    fun currentTokenFlow(): Flow<Token>
}
