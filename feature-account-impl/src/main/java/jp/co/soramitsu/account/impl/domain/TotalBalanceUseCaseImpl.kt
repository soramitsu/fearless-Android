package jp.co.soramitsu.account.impl.domain

import java.math.BigDecimal
import java.math.RoundingMode
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.interfaces.TotalBalanceUseCase
import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.common.utils.DOLLAR_SIGN
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.fractionToPercentage
import jp.co.soramitsu.common.utils.isNotZero
import jp.co.soramitsu.common.utils.isZero
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.percentageToFraction
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.coredb.model.AssetWithToken
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class TotalBalanceUseCaseImpl(
    private val accountRepository: AccountRepository,
    private val chainsRepository: ChainsRepository,
    private val assetDao: AssetDao
) : TotalBalanceUseCase {

    override suspend operator fun invoke(metaId: Long?): TotalBalance {
        return withContext(Dispatchers.Default) {
            val metaAccount = when (metaId) {
                null -> accountRepository.getSelectedLightMetaAccount()
                else -> accountRepository.getLightMetaAccount(metaId)
            }
            val assets = assetDao.getAssets(metaAccount.id)
            getTotalBalance(assets)
        }
    }

    override fun observe(metaId: Long?): Flow<TotalBalance> {
        return when (metaId) {
            null -> accountRepository.selectedLightMetaAccountFlow()
            else -> flow { emit(accountRepository.getLightMetaAccount(metaId)) }
        }
            .flatMapLatest { assetDao.observeAssets(it.id) }
            .filter { it.isNotEmpty() }
            .map(::getTotalBalance)
            .flowOn(Dispatchers.Default)
    }

    private suspend fun getTotalBalance(assets: List<AssetWithToken>): TotalBalance {
        val chainsById = chainsRepository.getChainsById()

        val polkadotCurrency = assets.find { it.asset.chainId == polkadotChainId }?.token?.fiatSymbol

        val filtered = assets
            .asSequence()
            .filter { it.asset.freeInPlanks != null && it.asset.freeInPlanks.isNotZero() && it.token?.fiatSymbol != null }
            .toList()

        // todo I did this workaround because sometimes there is a wrong symbol in asset list. Need research
        val fiatSymbolsInAssets = filtered.map { it.token?.fiatSymbol }.toSet()
        val fiatCurrency =
            runCatching { fiatSymbolsInAssets.maxBy { s -> filtered.count { it.token?.fiatSymbol == s } } }.getOrNull() ?: polkadotCurrency

        return filtered.fold(TotalBalance.Empty) { acc, current ->
            val chainAsset = chainsById.getValue(current.asset.chainId).assets
                .firstOrNull { it.id == current.asset.id }
                ?: return@fold TotalBalance.Empty

            val total =
                current.asset.freeInPlanks.orZero() + current.asset.reservedInPlanks.orZero()
            val totalDecimal = total.toBigDecimal(scale = chainAsset.precision)
            val fiatAmount = totalDecimal.applyFiatRate(current.token?.fiatRate)

            val totalBalanceToAdd = fiatAmount ?: BigDecimal.ZERO
            val balanceChangeToAdd = fiatAmount?.multiply(current.token?.recentRateChange.orZero())
                ?.percentageToFraction().orZero()

            val balance = acc.balance + totalBalanceToAdd
            val balanceChange = acc.balanceChange + balanceChangeToAdd
            val rate = when {
                balance.isZero() -> BigDecimal.ZERO
                else -> balanceChange.divide(balance, RoundingMode.HALF_UP).fractionToPercentage()
            }

            TotalBalance(
                balance = balance,
                fiatSymbol = fiatCurrency ?: DOLLAR_SIGN,
                balanceChange = balanceChange,
                rateChange = rate
            )
        }
    }
}
