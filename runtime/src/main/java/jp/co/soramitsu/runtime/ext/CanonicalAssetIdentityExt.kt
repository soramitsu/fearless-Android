package jp.co.soramitsu.runtime.ext

import jp.co.soramitsu.common.model.AssetKey
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

/**
 * The single ecosystem mapping used whenever a network-scoped [AssetKey] is created.
 *
 * Bitcoin, Solana and Iroha are represented by compatibility `Chain` records whose legacy
 * ecosystem value can be Substrate. Their network identity must win over that storage detail.
 */
fun Chain.canonicalEcosystemId(): String = when {
    isUniversalWalletBitcoin() -> "bitcoin"
    isUniversalWalletSolana() -> "solana"
    isUniversalWalletIroha() -> "iroha"
    else -> ecosystem.name.lowercase()
}

fun Chain.assetKey(assetId: String): AssetKey = AssetKey(
    ecosystem = canonicalEcosystemId(),
    chainId = id,
    assetId = assetId
)

/** Registry UUIDs are storage keys; currencyId is the exact runtime/contract/mint identity. */
fun Asset.canonicalAssetId(): String = currencyId?.trim()?.takeIf(String::isNotEmpty) ?: id

fun Chain.assetKey(asset: Asset): AssetKey = assetKey(asset.canonicalAssetId())
