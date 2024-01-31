package jp.co.soramitsu.account.impl.domain

import jp.co.soramitsu.account.api.domain.interfaces.AssetBalanceUseCase
import jp.co.soramitsu.account.api.domain.model.AssetBalance
import jp.co.soramitsu.common.utils.DOLLAR_SIGN
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.fractionToPercentage
import jp.co.soramitsu.common.utils.isZero
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.percentageToFraction
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.coredb.model.AssetWithToken
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.math.RoundingMode
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks

class AssetBalanceUseCaseImpl(
    private val assetDao: AssetDao,
    private val chainsRepository: ChainsRepository,
): AssetBalanceUseCase {

    override suspend fun invoke(accountMetaId: Long, assetId: String): AssetBalance {
        val assets = assetDao.getAssets(accountMetaId = accountMetaId, id = assetId)
        return sumAssetBalances(assets)
    }

    override fun observe(accountMetaId: Long, assetId: String): Flow<AssetBalance> {
        return assetDao.observeAssets(accountMetaId = accountMetaId, id = assetId).map(::sumAssetBalances)
    }

    private suspend fun sumAssetBalances(assets: List<AssetWithToken>): AssetBalance {
        val fiatCurrency = assets.firstNotNullOfOrNull { it.token?.fiatSymbol }

        val chainsById = chainsRepository.getChainsById()

        return assets.fold(AssetBalance.Empty) { acc, current ->
            val chainAsset = chainsById.getValue(current.asset.chainId).assets
                .firstOrNull { it.id == current.asset.id }
                ?: return@fold AssetBalance.Empty

            val currentAssetTransferable = current.asset.transferableInPlanks
            val currentAssetTransferableDecimal = chainAsset.amountFromPlanks(currentAssetTransferable)
            val currentFiatAmount = currentAssetTransferableDecimal.applyFiatRate(current.token?.fiatRate)

            val totalFiatBalanceToAdd = currentFiatAmount ?: BigDecimal.ZERO
            val balanceFiatChangeToAdd = currentFiatAmount?.multiply(current.token?.recentRateChange.orZero())
                ?.percentageToFraction().orZero()

            val assetBalance = acc.assetBalance + currentAssetTransferableDecimal
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