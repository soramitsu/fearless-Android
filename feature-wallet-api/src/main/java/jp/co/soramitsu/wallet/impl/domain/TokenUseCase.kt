package jp.co.soramitsu.wallet.impl.domain

import jp.co.soramitsu.wallet.impl.domain.model.Token
import kotlinx.coroutines.flow.Flow

interface TokenUseCase {

    suspend fun currentToken(): Token

    fun currentTokenFlow(): Flow<Token>
}
