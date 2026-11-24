package jp.co.soramitsu.runtime.assethub

import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.runtime.multiNetwork.chain.model.kusamaChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.westendChainId

/**
 * Genesis-bound staking migration routes. Token symbols and parachain numbers are not authority.
 *
 * Since the Asset Hub migration the relays hold no staking state, so relay staking and pools stay off whatever the
 * catalogue says. A Hub offers staking and pools only for its native asset and only while the catalogue marks that
 * asset with [CATALOGUE_STAKING]. Builds before 4.3.1 map that unknown value to unsupported, so the switch never
 * reaches staking code that predates the Hub runtimes, and removing it from the catalogue turns Hub staking off.
 */
object AssetHubStaking {
    const val POLKADOT = "68d56f15f85d3136970ec16946040bc1752654e906147f7e43e9d539d7c3de2f"
    const val KUSAMA = "48239ef607d7928874027a43a67689209727dfb3d3dc5e5b03a39bdc2eda771a"
    const val WESTEND = "67f9723393ef76214df0118c34bbbd3dbebc8ed46a10973a8c969d48fe7598c9"

    /** The catalogue `staking` value that enables staking on a Hub's native asset. */
    const val CATALOGUE_STAKING = "assetHub"

    private val hubs = mapOf(polkadotChainId to POLKADOT, kusamaChainId to KUSAMA, westendChainId to WESTEND)
    private val nativeAssets = mapOf(
        POLKADOT to "887a17c7-1370-4de0-97dd-5422e294fa75",
        KUSAMA to "1e0c2ec6-935f-49bd-a854-5e12ee6c9f1b",
        WESTEND to "a3868e1b-922e-42d4-b73e-b41712f0843c"
    )
    private val peopleChains = mapOf(
        POLKADOT to "67fa177a097bfa18f77ea95ab56e9bcdfeb0e5b8a40e46298bb93e16b6fc5008",
        KUSAMA to "c1af4cb4eb3918e5db15086c0cc5ec17fb334f728b7c65dd44bfe1e174ff8b3f"
    )

    /** Where a saved relay staking choice lives now. */
    fun destination(chainId: String): String = hubs[chainId] ?: chainId

    fun relay(chainId: String): String? = hubs.entries.singleOrNull { it.value == chainId }?.key

    fun isHub(chainId: String): Boolean = chainId in nativeAssets

    fun isMigratedRelay(chainId: String): Boolean = chainId in hubs

    /** Validator identities of a staking Hub live on its People chain. */
    fun identityChain(chainId: String, configured: String?, stakingEnabled: Boolean): String? =
        peopleChains[chainId]?.takeIf { stakingEnabled } ?: configured

    fun poolPolicy(chainId: String, configured: Boolean, assets: List<Asset>): Boolean = when {
        isMigratedRelay(chainId) -> false
        isHub(chainId) -> assets.any { it.supportStakingPool }
        else -> configured
    }

    /** Applies the catalogue switch while a remote catalogue entry is mapped. */
    fun remoteAssetPolicy(asset: Asset, catalogueStaking: String?, parentId: String?): Asset =
        policy(asset, catalogueStaking == CATALOGUE_STAKING, parentId)

    /** Persisted rows carry the result of [remoteAssetPolicy]; this re-checks the identity they claim. */
    fun localAssetPolicy(asset: Asset, parentId: String?): Asset =
        policy(asset, asset.staking == Asset.StakingType.RELAYCHAIN, parentId)

    private fun policy(asset: Asset, enabled: Boolean, parentId: String?): Asset {
        if (isMigratedRelay(asset.chainId)) {
            return asset.copy(staking = Asset.StakingType.UNSUPPORTED, supportStakingPool = false)
        }
        if (!isHub(asset.chainId)) return asset
        val native = enabled && parentId == relay(asset.chainId) && asset.id == nativeAssets[asset.chainId] &&
            asset.isUtility && asset.currencyId == null && asset.type == ChainAssetType.Normal
        return asset.copy(
            staking = if (native) Asset.StakingType.RELAYCHAIN else Asset.StakingType.UNSUPPORTED,
            supportStakingPool = native
        )
    }
}
