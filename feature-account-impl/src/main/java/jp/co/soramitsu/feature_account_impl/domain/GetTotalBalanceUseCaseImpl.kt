package jp.co.soramitsu.feature_account_impl.domain

import jp.co.soramitsu.common.utils.applyDollarRate
import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.GetTotalBalanceUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.math.BigDecimal

class GetTotalBalanceUseCaseImpl(private val accountRepository: AccountRepository,
                                 private val assetDao: AssetDao) : GetTotalBalanceUseCase {

    override operator fun invoke(): Flow<BigDecimal> {
        return accountRepository.selectedMetaAccountFlow()
            .flatMapLatest { assetDao.observeAssets(it.id) }
            .filter { it.isNotEmpty() }
            .map {
                it.fold(BigDecimal.ZERO) { acc, current ->
                    current.asset
                    val total = current.asset.freeInPlanks + current.asset.reservedInPlanks
                    val totalDecimal = total.toBigDecimal(scale = 2)
                    val dollarAmount = totalDecimal.applyDollarRate(current.token.dollarRate)

                    val toAdd = dollarAmount ?: BigDecimal.ZERO

                    acc + toAdd

                }
            }
    }
}
