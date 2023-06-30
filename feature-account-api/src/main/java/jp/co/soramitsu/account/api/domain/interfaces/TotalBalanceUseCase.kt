package jp.co.soramitsu.account.api.domain.interfaces

import jp.co.soramitsu.account.api.domain.model.TotalBalance
import kotlinx.coroutines.flow.Flow

interface TotalBalanceUseCase {

    /**
     * Calculate total account balance
     *
     * @param metaId - when specified calculation performed for particular metaAccountId, if not - for current metaAccountId
     */
    suspend operator fun invoke(metaId: Long? = null): TotalBalance
    fun observe(metaId: Long? = null): Flow<TotalBalance>
}
