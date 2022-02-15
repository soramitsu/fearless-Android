package jp.co.soramitsu.feature_account_api.domain.interfaces

import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

interface GetTotalBalanceUseCase {
    /**
     * Calculate total account balance
     *
     * @param metaId - when specified calculation performed for particular metaAccountId, if not - for current metaAccountId
     */
    operator fun invoke(metaId: Long? = null): Flow<BigDecimal>
}
