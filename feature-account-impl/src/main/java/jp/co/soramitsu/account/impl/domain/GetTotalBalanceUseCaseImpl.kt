package jp.co.soramitsu.account.impl.domain

import java.math.BigDecimal
import jp.co.soramitsu.common.utils.DOLLAR_SIGN
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.interfaces.GetTotalBalanceUseCase
import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class GetTotalBalanceUseCaseImpl(
    private val accountRepository: AccountRepository,
    private val chainRegistry: ChainRegistry,
    private val assetDao: AssetDao
) : GetTotalBalanceUseCase {

    override operator fun invoke(metaId: Long?): Flow<TotalBalance> {
        return when (metaId) {
            null -> accountRepository.selectedMetaAccountFlow()
            else -> flow { emit(accountRepository.getMetaAccount(metaId)) }
        }
            .flatMapLatest { assetDao.observeAssets(it.id) }
            .filter { it.isNotEmpty() }
            .map { items ->
                items.fold(TotalBalance.Empty) { acc, current ->
                    val chainAsset = chainRegistry.chainsById.first().getValue(current.asset.chainId).assets
                        .firstOrNull { it.id == current.asset.tokenSymbol.lowercase() }
                        ?: return@fold TotalBalance.Empty

                    val total = current.asset.freeInPlanks.orZero() + current.asset.reservedInPlanks.orZero()
                    val totalDecimal = total.toBigDecimal(scale = chainAsset.precision)
                    val fiatAmount = totalDecimal.applyFiatRate(current.token.fiatRate)

                    val toAdd = fiatAmount ?: BigDecimal.ZERO

                    val balance = acc.balance + toAdd
                    TotalBalance(balance, current.token.fiatSymbol ?: DOLLAR_SIGN)
                }
            }
    }
}
