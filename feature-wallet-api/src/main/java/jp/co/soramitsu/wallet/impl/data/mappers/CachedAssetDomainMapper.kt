package jp.co.soramitsu.wallet.impl.data.mappers

import jp.co.soramitsu.coredb.model.cache.CachedAssetWithPriceRead
import jp.co.soramitsu.coredb.model.cache.CachedNumericState
import jp.co.soramitsu.wallet.impl.data.repository.CurrentAccountAssetSnapshot
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState
import jp.co.soramitsu.wallet.impl.domain.model.FiatValuationContext
import jp.co.soramitsu.wallet.impl.data.repository.configuredFiatToken
import jp.co.soramitsu.core.models.Asset as CoreAsset

/** Preserve read availability; only the bounded diagnostic category enters domain state. */
fun <T> CachedNumericState<T>.toAssetAmountState(): AssetAmountState<T> = when (this) {
    CachedNumericState.Missing -> AssetAmountState.Missing
    is CachedNumericState.Known -> AssetAmountState.Known(value)
    is CachedNumericState.Unavailable -> AssetAmountState.Unavailable("cached-${issue.reason.name}")
}

/** No nullable, writer-entity or zero-filled intermediate representation is constructed. */
fun mapCachedAssetToAsset(
    cached: CachedAssetWithPriceRead,
    configuration: CoreAsset,
    minSupportedVersion: String?,
    hasTrustedPrice: Boolean,
    context: FiatValuationContext
): Asset = with(cached.asset) {
    require(chainId == configuration.chainId && id == configuration.id) { "Asset configuration identity mismatch" }
    Asset(
        metaId = metaId,
        token = configuredFiatToken(configuration, cached.token, hasTrustedPrice, context),
        accountId = accountId.copyOf(),
        freeInPlanksState = free.toAssetAmountState(),
        reservedInPlanksState = reserved.toAssetAmountState(),
        miscFrozenInPlanksState = miscFrozen.toAssetAmountState(),
        feeFrozenInPlanksState = feeFrozen.toAssetAmountState(),
        bondedInPlanksState = bonded.toAssetAmountState(),
        redeemableInPlanksState = redeemable.toAssetAmountState(),
        unbondingInPlanksState = unbonding.toAssetAmountState(),
        sortIndex = sortIndex,
        enabled = enabled,
        minSupportedVersion = minSupportedVersion,
        chainAccountName = chainAccountName,
        markedNotNeed = markedNotNeed,
        status = status
    )
}

/** Mapping uses exactly the catalog which selected these rows, with no second repository read. */
fun CurrentAccountAssetSnapshot.toDomainAssets(hasTrustedPrice: (CoreAsset) -> Boolean): List<Asset> =
    assets.mapNotNull { cached ->
        val chain = chainsById[cached.asset.chainId] ?: return@mapNotNull null
        val configuration = chain.assetsById[cached.asset.id] ?: return@mapNotNull null
        mapCachedAssetToAsset(cached, configuration, chain.minSupportedVersion, hasTrustedPrice(configuration), context.fiat)
    }
