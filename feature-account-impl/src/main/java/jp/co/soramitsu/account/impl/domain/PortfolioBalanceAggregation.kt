package jp.co.soramitsu.account.impl.domain

import java.math.BigDecimal
import java.math.MathContext
import jp.co.soramitsu.account.api.domain.model.PortfolioBalance
import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.isDetectedAsset
import jp.co.soramitsu.wallet.impl.data.mappers.toDomainAssets
import jp.co.soramitsu.wallet.impl.data.repository.CurrentAccountAssetSnapshot
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState
import jp.co.soramitsu.wallet.impl.domain.model.FiatHoldingValuation
import jp.co.soramitsu.wallet.impl.domain.model.FiatValuationContext
import jp.co.soramitsu.wallet.impl.domain.model.aggregateFiatHoldings
import jp.co.soramitsu.wallet.impl.domain.model.knownOrNull

/** Uses the snapshot's catalog, account selection, bounded quantities and exact quote currency. */
internal fun calculateTrustedTotalBalance(
    snapshot: CurrentAccountAssetSnapshot,
    isTestnet: (Chain) -> Boolean,
    hasTrustedPrice: (chainId: String, assetId: String) -> Boolean
): TotalBalance {
    val context = snapshot.context
    if (!context.fiat.isValid) return TotalBalance.Unavailable
    val holdings = snapshot.toDomainAssets { hasTrustedPrice(it.chainId, it.id) }
        .filter { asset ->
            val configuration = asset.token.configuration
            !isTestnet(snapshot.chainsById.getValue(configuration.chainId)) &&
                !configuration.isDetectedAsset()
        }
    val (hidden, shown) = holdings.partition { it.enabled == false }
    return TotalBalance.Ready(
        meta = context.meta,
        chainsById = snapshot.chainsById,
        currencyId = context.fiat.currencyId,
        fiatSymbol = context.fiat.displayUnit,
        quotesReady = snapshot.quotesReady,
        shown = summarizeHoldings(context.fiat, shown),
        hidden = summarizeHoldings(context.fiat, hidden),
        allHoldings = summarizeHoldings(context.fiat, holdings)
    )
}

/** Header quantity is free plus reserved. Unavailable quantity must not be filtered as unfunded. */
internal fun summarizeHoldings(context: FiatValuationContext, assets: List<Asset>): PortfolioBalance {
    val total = aggregateFiatHoldings(context, assets.map { asset ->
        FiatHoldingValuation(asset.totalState, asset.token.valuedAmount(asset.totalState))
    })
    val funded = assets.filter { asset ->
        val quantity = asset.totalState
        quantity !is AssetAmountState.Known || quantity.value.signum() != 0
    }
    val completeAmount = total.total.knownOrNull()
    val change = if (completeAmount == null) null else recentChange(funded)
    val rateChange = when {
        change == null || completeAmount == null -> null
        completeAmount.signum() == 0 -> BigDecimal.ZERO
        else -> change.divide(completeAmount, MathContext.DECIMAL128).movePointRight(2)
    }
    return PortfolioBalance(
        amount = completeAmount,
        knownSubtotal = total.knownSubtotal,
        change = change,
        rateChange = rateChange,
        holdingCount = funded.size,
        pricedCount = total.pricedCount,
        unpricedAssetCount = total.unpricedFundedCount,
        unknownQuantityCount = total.unknownQuantityCount
    )
}

/** Current value and recent change have independent availability; missing change is never zero. */
private fun recentChange(assets: List<Asset>): BigDecimal? {
    var change = BigDecimal.ZERO
    for (asset in assets) {
        val value = asset.fiatAmount ?: return null
        val percent = asset.token.recentRateChange ?: return null
        change += value.multiply(percent).movePointLeft(2)
    }
    return change
}
