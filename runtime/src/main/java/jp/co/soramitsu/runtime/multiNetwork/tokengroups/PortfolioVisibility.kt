package jp.co.soramitsu.runtime.multiNetwork.tokengroups

import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import java.math.BigDecimal

/**
 * The one visibility and zero rule of the shared portfolio contract (workspace root
 * `docs/portfolio-contract.md` §3-4). The portfolio list, Manage assets, Search, the asset pickers,
 * the totals and background sync all ask this class, so a row is never shown by one screen and
 * counted differently or left out by another.
 *
 * Preferences are tri-state per (wallet, chainId, assetId): `enabled = true` is an explicit Shown,
 * `false` an explicit Hidden and `null` Auto. Only user actions write Shown or Hidden.
 *
 * Testnets are hidden behind the per-wallet Testnets toggle (lead decision), are never zero
 * defaults and are never counted in any total.
 */
class PortfolioVisibility(private val configSource: SharedPortfolioConfigSource) {

    private val config: SharedPortfolioConfig
        get() = configSource.config

    /** isTestnet(chain) = 'testnet' in chain.options || chain.chainId in testnetChainIds. */
    fun isTestnet(chain: Chain): Boolean = config.isTestnet(chain)

    /**
     * isZeroDefault(c, a) = !isTestnet(c) && !sendVetoed(c)
     *     && ((c.chainId, a.id) in defaultVisibleAtZero
     *         || (a == primaryUtility(c) && (isFavourite(c) || isOnlySelectedNetwork(c))))
     *
     * Callers ask only about assets the wallet holds an account for. If the bundled zero list is
     * unusable, the computed rule applies instead: the primary utility asset of a ranked mainnet.
     */
    fun isZeroDefault(
        chain: Chain,
        asset: Asset,
        isFavourite: Boolean,
        isOnlySelectedNetwork: Boolean,
        isSendVetoed: Boolean
    ): Boolean {
        if (isTestnet(chain) || isSendVetoed) return false

        val isPrimaryUtility = chain.utilityAsset?.id == asset.id
        val isListed = config.defaultVisibleAtZero
            ?.contains(canonicalChainId(chain.id), asset.id)
            ?: (isPrimaryUtility && chain.rank != null)
        if (isListed) return true

        return isPrimaryUtility && (isFavourite || isOnlySelectedNetwork)
    }

    /**
     * visible = Hidden ? false : Shown ? true : (total > 0 || isZeroDefault), after the testnet
     * gate: a testnet row appears only while the Testnets toggle is on or that testnet is the one
     * network the user selected. Explicit Shown is honoured at zero, as 4.2.0 displayed it.
     */
    @Suppress("LongParameterList")
    fun isVisible(
        enabled: Boolean?,
        total: BigDecimal?,
        chain: Chain,
        asset: Asset,
        isFavourite: Boolean,
        isOnlySelectedNetwork: Boolean,
        isSendVetoed: Boolean,
        showTestnets: Boolean
    ): Boolean {
        if (isTestnet(chain) && !showTestnets && !isOnlySelectedNetwork) return false

        return when (enabled) {
            false -> false
            true -> true
            null -> {
                total == null || total > BigDecimal.ZERO ||
                    isZeroDefault(chain, asset, isFavourite, isOnlySelectedNetwork, isSendVetoed)
            }
        }
    }

    /**
     * Where Search lists an asset the wallet holds an account for: the rows the portfolio shows,
     * then explicitly hidden assets (they can be shown again from there), then every other
     * reviewed registry asset, so a token the wallet does not hold yet can still be found and
     * received. Testnets stay behind the Testnets toggle, as in the list. A detected token that the
     * portfolio does not show is reviewed in the portfolio, never offered by Search. Null: Search
     * does not list the asset.
     */
    @Suppress("LongParameterList")
    fun searchSection(
        enabled: Boolean?,
        total: BigDecimal?,
        chain: Chain,
        asset: Asset,
        isFavourite: Boolean,
        isOnlySelectedNetwork: Boolean,
        isSendVetoed: Boolean,
        showTestnets: Boolean
    ): AssetSearchSection? {
        if (isTestnet(chain) && !showTestnets && !isOnlySelectedNetwork) return null

        return when {
            enabled == false -> AssetSearchSection.Hidden
            isVisible(enabled, total, chain, asset, isFavourite, isOnlySelectedNetwork, isSendVetoed, showTestnets) ->
                AssetSearchSection.Portfolio
            asset.isDetectedAsset() -> null
            else -> AssetSearchSection.Other
        }
    }

    /**
     * The Send, swap "from" and cross-chain pickers list what the wallet can spend: a positive
     * sendable balance, whatever the preference, because hiding an asset never blocks spending it.
     * A testnet is listed while the Testnets toggle is on or when it is the network the picker is
     * limited to.
     */
    fun isPickableToSpend(
        sendAvailable: BigDecimal?,
        chain: Chain,
        isPickerNetwork: Boolean,
        showTestnets: Boolean
    ): Boolean {
        if (isTestnet(chain) && !showTestnets && !isPickerNetwork) return false

        return sendAvailable != null && sendAvailable > BigDecimal.ZERO
    }

    /**
     * The Receive and swap "to" pickers list every reviewed registry asset the wallet has an
     * account for, held or not, hidden or not: receiving needs no balance. Detected tokens are not
     * offered for a deposit. Testnets follow [isPickableToSpend].
     */
    fun isPickableToReceive(
        chain: Chain,
        asset: Asset,
        isPickerNetwork: Boolean,
        showTestnets: Boolean
    ): Boolean {
        if (isTestnet(chain) && !showTestnets && !isPickerNetwork) return false

        return !asset.isDetectedAsset()
    }

    /**
     * The header total, network subtotals and group totals count the same set: everything that is
     * not hidden and not on a testnet. Wallet-management screens add the hidden part back on top.
     */
    fun isCountedInTotals(enabled: Boolean?, chain: Chain): Boolean = enabled != false && !isTestnet(chain)

    /**
     * Trusted price = the asset's exact registry price, unless the asset is on the bundled
     * `untrustedAssets` list. A listed asset adds 0 fiat everywhere a price is shown or summed and
     * counts as an asset without price.
     */
    fun hasTrustedPrice(chainId: ChainId, assetId: String): Boolean {
        return !config.isPriceUntrusted(canonicalChainId(chainId), assetId)
    }

    /**
     * [hasTrustedPrice] for a stored asset, which also knows its network: a testnet token has no
     * value, so it never carries a price either, whatever priceId the registry gives it (Canary
     * Matrixchain cENJ is priced as Enjin Coin). Rows, details and send screens then show no fiat
     * for it, as the totals never count it.
     */
    fun hasTrustedPrice(asset: Asset): Boolean {
        val isTestnetAsset = asset.isTestNet ||
            config.testnetChainIds?.contains(canonicalChainId(asset.chainId)) == true ||
            config.testnetChainIds?.contains(asset.chainId) == true

        return !isTestnetAsset && hasTrustedPrice(asset.chainId, asset.id)
    }

    private fun canonicalChainId(chainId: ChainId): ChainId = APP_OWNED_CHAIN_ID_ALIASES[chainId] ?: chainId

    private companion object {
        /**
         * App-owned networks are known by a short id and a chain id (root
         * `config/portfolio/app-owned-chains.v1.json` aliases); the shared lists use the chain id.
         */
        val APP_OWNED_CHAIN_ID_ALIASES: Map<ChainId, ChainId> = with(UniversalWalletRegistry) {
            mapOf(
                bitcoinMainnet.id to bitcoinMainnet.chainId,
                bitcoinTestnet.id to bitcoinTestnet.chainId,
                solanaMainnet.id to solanaMainnet.chainId,
                solanaDevnet.id to solanaDevnet.chainId,
                taira.id to taira.chainId,
                nexus.id to nexus.chainId
            )
        }
    }
}

/** The Search results section of an asset, in the order Search shows them. */
enum class AssetSearchSection {
    /** Exactly the rows the portfolio list shows. */
    Portfolio,

    /** Explicitly hidden by the user; they can be shown again from Search. */
    Hidden,

    /** Reviewed registry assets the portfolio does not show: not held yet and not a zero default. */
    Other
}

/**
 * A detected asset was found on chain or by an indexer instead of being a reviewed registry entry.
 * It is never grouped, never priced and never reported as value the total leaves out.
 */
fun Asset.isDetectedAsset(): Boolean {
    return type == ChainAssetType.Unknown ||
        (type == ChainAssetType.Jetton && !isUtility && priceId == null)
}
