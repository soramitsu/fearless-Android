package jp.co.soramitsu.feature_account_api.domain.interfaces

import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

interface GetTotalBalanceUseCase {
    operator fun invoke(): Flow<BigDecimal>
}
