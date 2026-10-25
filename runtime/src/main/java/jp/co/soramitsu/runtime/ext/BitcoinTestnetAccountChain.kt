package jp.co.soramitsu.runtime.ext

import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

/** Retain an address-only catalog row so testnet keys survive absent/disabled discovery. */
internal fun List<Chain>.withBitcoinTestnetAccountChain(localIds: Set<String>): List<Chain> {
    val profile = UniversalWalletRegistry.bitcoinTestnet
    val aliases = setOf(profile.id, profile.chainId)
    val remoteIds = map { it.id }.toSet()
    // Keep every existing alias: deleting one would cascade into its independent keys.
    val ids = (localIds.filter { it.lowercase() in aliases }.toSet() - remoteIds).ifEmpty {
        if (remoteIds.none { it.lowercase() in aliases }) setOf(profile.id) else emptySet()
    }
    if (ids.isEmpty()) return this
    return this + ids.map { id ->
        Chain(
            id = id, paraId = null, rank = null, name = profile.name,
            minSupportedVersion = null, assets = emptyList(), nodes = emptyList(),
            explorers = emptyList(), externalApi = null, icon = "", addressPrefix = 0,
            isEthereumBased = false, isTestNet = true, hasCrowdloans = false, parentId = null,
            supportStakingPool = false, isEthereumChain = false, chainlinkProvider = false,
            supportNft = false, isUsesAppId = false, identityChain = null,
            ecosystem = Ecosystem.Substrate, remoteAssetsSource = null
        )
    }
}
