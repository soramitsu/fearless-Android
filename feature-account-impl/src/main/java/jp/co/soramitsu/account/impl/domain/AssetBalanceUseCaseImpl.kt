package jp.co.soramitsu.account.impl.domain

import jp.co.soramitsu.account.api.domain.interfaces.AssetBalanceUseCase
import jp.co.soramitsu.account.api.domain.model.AssetBalance
import jp.co.soramitsu.common.utils.DOLLAR_SIGN
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.fractionToPercentage
import jp.co.soramitsu.common.utils.isZero
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.percentageToFraction
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.coredb.model.AssetWithToken
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.math.RoundingMode

class AssetBalanceUseCaseImpl(
    private val assetDao: AssetDao,
    private val chainsRepository: ChainsRepository,
): AssetBalanceUseCase {

    override suspend fun invoke(assetId: String): AssetBalance {
        val assets = assetDao.getAssets(id = assetId)
        return sumAssetBalances(assets)
    }

    override fun observe(assetId: String): Flow<AssetBalance> {
        return assetDao.observeAssets(id = assetId).map(::sumAssetBalances)
    }

    private suspend fun sumAssetBalances(assets: List<AssetWithToken>): AssetBalance {
        val fiatCurrency = assets.find { it.asset.chainId == polkadotChainId }?.token?.fiatSymbol

        val chainsById = chainsRepository.getChainsById()

        return assets.fold(AssetBalance.Empty) { acc, current ->
            val chainAsset = chainsById.getValue(current.asset.chainId).assets
                .firstOrNull { it.id == current.asset.id }
                ?: return@fold AssetBalance.Empty

            val currentAssetTotal =
                current.asset.freeInPlanks.orZero() + current.asset.reservedInPlanks.orZero()
            val currentAssetTotalDecimal = currentAssetTotal.toBigDecimal(scale = chainAsset.precision)
            val currentFiatAmount = currentAssetTotalDecimal.applyFiatRate(current.token?.fiatRate)

            val totalFiatBalanceToAdd = currentFiatAmount ?: BigDecimal.ZERO
            val balanceFiatChangeToAdd = currentFiatAmount?.multiply(current.token?.recentRateChange.orZero())
                ?.percentageToFraction().orZero()

            val assetBalance = acc.assetBalance + currentAssetTotalDecimal
            val fiatBalance = acc.fiatBalance + totalFiatBalanceToAdd

            val fiatBalanceChange = acc.fiatBalanceChange + balanceFiatChangeToAdd
            val rateChange = when {
                fiatBalance.isZero() -> BigDecimal.ZERO
                else -> fiatBalanceChange.divide(fiatBalance, RoundingMode.HALF_UP).fractionToPercentage()
            }

            AssetBalance(
                assetBalance = assetBalance,
                rateChange = rateChange,
                assetSymbol = chainAsset.symbol,
                fiatBalance = fiatBalance,
                fiatBalanceChange = fiatBalanceChange,
                fiatSymbol = current.token?.fiatSymbol ?: fiatCurrency ?: DOLLAR_SIGN,
            )
        }
    }
}