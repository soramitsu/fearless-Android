package jp.co.soramitsu.account.impl.domain

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.interfaces.TotalBalanceUseCase
import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.common.utils.DOLLAR_SIGN
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.fractionToPercentage
import jp.co.soramitsu.common.utils.isZero
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.percentageToFraction
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.coredb.model.AssetWithToken
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode

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

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(metaId: Long?): Flow<TotalBalance> {
        return when (metaId) {
            null -> accountRepository.selectedLightMetaAccountFlow()
            else -> flow { emit(accountRepository.getLightMetaAccount(metaId)) }
        }
            .flatMapLatest { assetDao.observeAssets(it.id) }
            .filter { it.isNotEmpty() }
            .map(::getTotalBalance)
            .flowOn(Dispatchers.IO)
            .onStart { emit(TotalBalance.Empty) }
    }

    private suspend fun getTotalBalance(assets: List<AssetWithToken>): TotalBalance = withContext(Dispatchers.IO) {
        val chainsById = chainsRepository.getChainsById()
        calculateTrustedTotalBalance(assets, chainsById)
    }
}

/**
 * Calculates net worth only from a canonical chain/asset registry match and a registry-bound
 * price identifier. Symbols and visibility preferences are deliberately irrelevant.
 */
internal fun calculateTrustedTotalBalance(
    assets: List<AssetWithToken>,
    chainsById: Map<String, Chain>
): TotalBalance {
    val trusted = assets.mapNotNull { current ->
        val chainAsset = chainsById[current.asset.chainId]
            ?.assetsById
            ?.get(current.asset.id)
            ?: return@mapNotNull null
        val trustedPriceIds = setOfNotNull(chainAsset.priceId, chainAsset.priceProvider?.id)
        val storedPriceId = current.asset.tokenPriceId
        val token = current.token
        if (storedPriceId == null || storedPriceId !in trustedPriceIds || token?.priceId != storedPriceId) {
            return@mapNotNull null
        }
        if (current.asset.freeInPlanks == null || current.asset.totalInPlanks.signum() <= 0 || token.fiatSymbol.isBlank()) {
            return@mapNotNull null
        }

        Triple(current, chainAsset, token)
    }

    val polkadotCurrency = trusted
        .firstOrNull { (current, _, _) -> current.asset.chainId == polkadotChainId }
        ?.third
        ?.fiatSymbol
    val fiatCurrency = trusted
        .groupingBy { it.third.fiatSymbol }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key
        ?: polkadotCurrency
        ?: DOLLAR_SIGN

    return trusted.fold(TotalBalance.Empty) { acc, (current, chainAsset, token) ->
        val totalDecimal = current.asset.totalInPlanks.toBigDecimal(scale = chainAsset.precision)
        val fiatAmount = totalDecimal.applyFiatRate(token.fiatRate) ?: BigDecimal.ZERO
        val balanceChangeToAdd = fiatAmount.multiply(token.recentRateChange.orZero())
            .percentageToFraction()

        val balance = acc.balance + fiatAmount
        val balanceChange = acc.balanceChange + balanceChangeToAdd
        val rate = when {
            balance.isZero() -> BigDecimal.ZERO
            else -> balanceChange.divide(balance, RoundingMode.HALF_UP).fractionToPercentage()
        }

        TotalBalance(
            balance = balance,
            fiatSymbol = fiatCurrency,
            balanceChange = balanceChange,
            rateChange = rate
        )
    }
}
