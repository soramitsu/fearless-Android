package jp.co.soramitsu.account.impl.domain

import java.math.BigDecimal
import java.math.MathContext
import jp.co.soramitsu.account.api.domain.model.AssetBalance
import jp.co.soramitsu.account.api.domain.model.PortfolioBalance
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.isDetectedAsset
import jp.co.soramitsu.wallet.impl.data.mappers.toDomainAssets
import jp.co.soramitsu.wallet.impl.data.repository.CurrentAccountAssetSnapshot
import jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState
import jp.co.soramitsu.wallet.impl.domain.model.FiatHoldingValuation
import jp.co.soramitsu.wallet.impl.domain.model.aggregateFiatHoldings
import jp.co.soramitsu.wallet.impl.domain.model.knownOrNull

/** Select one exact chain/asset from the captured catalog and value transferable quantities. */
internal fun calculateAssetBalance(
    snapshot: CurrentAccountAssetSnapshot,
    chainId: String,
    assetId: String,
    isTestnet: (Chain) -> Boolean,
    hasTrustedPrice: (String, String) -> Boolean
): AssetBalance {
    val context = snapshot.context
    if (!context.fiat.isValid) return AssetBalance.Unavailable
    val chain = snapshot.chainsById[chainId] ?: return AssetBalance.Unavailable
    val configuration = chain.assetsById[assetId] ?: return AssetBalance.Unavailable
    if (configuration.chainId != chainId || configuration.id != assetId) return AssetBalance.Unavailable
    val trusted = !isTestnet(chain) && !configuration.isDetectedAsset() && hasTrustedPrice(chainId, assetId)
    val matchingRows = snapshot.assets.filter { it.asset.chainId == chainId && it.asset.id == assetId }
    // An absent cache row does not prove that this particular asset was remotely fetched as zero.
    if (matchingRows.isEmpty()) return AssetBalance.Unavailable
    val assets = snapshot.copy(assets = matchingRows).toDomainAssets { trusted }
    val quantities = assets.map { it.transferableState }
    val known = quantities.mapNotNull { it.knownOrNull() }
    val unknownCount = quantities.size - known.size
    val knownSubtotal = known.fold(BigDecimal.ZERO, BigDecimal::add)
    val fiat = aggregateFiatHoldings(context.fiat, assets.map { asset ->
        FiatHoldingValuation(asset.transferableState, asset.token.valuedAmount(asset.transferableState))
    })
    val amount = fiat.total.knownOrNull()
    val funded = assets.filter { asset ->
        val quantity = asset.transferableState
        quantity !is AssetAmountState.Known || quantity.value.signum() != 0
    }
    val change = if (amount == null) null else funded.fold(BigDecimal.ZERO as BigDecimal?) { acc, asset ->
        val value = asset.token.valuedAmount(asset.transferableState)?.amount?.knownOrNull()
        val rate = asset.token.recentRateChange
        if (acc == null || value == null || rate == null) null else acc + value.multiply(rate).movePointLeft(2)
    }
    val rateChange = when {
        amount == null || change == null -> null
        amount.signum() == 0 -> BigDecimal.ZERO
        else -> change.divide(amount, MathContext.DECIMAL128).movePointRight(2)
    }
    return AssetBalance.Ready(
        meta = context.meta,
        configuration = configuration,
        currencyId = context.fiat.currencyId,
        fiatSymbol = context.fiat.displayUnit,
        quotesReady = snapshot.quotesReady,
        transferable = knownSubtotal.takeIf { unknownCount == 0 },
        knownTransferableSubtotal = knownSubtotal.takeIf { unknownCount == 0 || known.isNotEmpty() },
        unknownQuantityCount = unknownCount,
        fiat = PortfolioBalance(amount, fiat.knownSubtotal, change, rateChange, funded.size,
            fiat.pricedCount, fiat.unpricedFundedCount, fiat.unknownQuantityCount)
    )
}
