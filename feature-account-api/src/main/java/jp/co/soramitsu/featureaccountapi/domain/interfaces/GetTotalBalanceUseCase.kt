package jp.co.soramitsu.featureaccountapi.domain.interfaces

import jp.co.soramitsu.featureaccountapi.domain.model.TotalBalance
import kotlinx.coroutines.flow.Flow

interface GetTotalBalanceUseCase {
    /**
     * Calculate total account balance
     *
     * @param metaId - when specified calculation performed for particular metaAccountId, if not - for current metaAccountId
     */
    operator fun invoke(metaId: Long? = null): Flow<TotalBalance>
}
