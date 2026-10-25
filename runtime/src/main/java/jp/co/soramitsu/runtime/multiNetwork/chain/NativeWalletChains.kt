package jp.co.soramitsu.runtime.multiNetwork.chain

import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

/**
 * App-owned mainnet definitions do not depend on the Substrate/EVM registry.
 * Preserve recognized historical ids as separate catalog entries: rewriting an
 * id would disconnect a stored chain account or its asset visibility preference.
 * Testnets and Iroha are deliberately not enrolled here.
 */
internal fun nativeWalletChains(existingChainIds: Set<String> = emptySet()): List<Chain> {
    val bitcoin = UniversalWalletRegistry.bitcoinMainnet
    val solana = UniversalWalletRegistry.solanaMainnet
    return listOf(
        nativeWalletChain(
            bitcoin.id, bitcoin.name, bitcoin.nativeAsset.id, bitcoin.nativeAsset.symbol,
            bitcoin.nativeAsset.decimals, "bitcoin", bitcoin.indexerBaseUrl,
            Chain.ExternalApi.Section.Type.BITCOIN, emptyList()
        ),
        nativeWalletChain(
            solana.id, solana.name, solana.nativeAsset.id, solana.nativeAsset.symbol,
            solana.nativeAsset.decimals, "solana", solana.indexerBaseUrl,
            Chain.ExternalApi.Section.Type.SOLANA,
            listOf(ChainNode(solana.rpcUrl, solana.name, isActive = true, isDefault = true))
        )
    ).flatMap { chain ->
        val historicalId = if (chain.id == bitcoin.id) bitcoin.chainId else solana.chainId
        val retainedIds = listOf(chain.id, historicalId).filter(existingChainIds::contains)
            .ifEmpty { listOf(chain.id) }
        retainedIds.map { retainedId ->
            chain.copy(id = retainedId, assets = chain.assets.map { it.copy(chainId = retainedId) })
        }
    }
}

private fun nativeWalletChain(
    id: String,
    name: String,
    assetId: String,
    symbol: String,
    precision: Int,
    priceId: String,
    indexerUrl: String,
    historyType: Chain.ExternalApi.Section.Type,
    nodes: List<ChainNode>
): Chain = Chain(
    id = id,
    paraId = null,
    rank = 1,
    name = name,
    minSupportedVersion = null,
    assets = listOf(Asset(
        id = assetId,
        name = name,
        symbol = symbol,
        iconUrl = "file:///android_asset/native-icons/$priceId.svg",
        chainId = id,
        chainName = name,
        chainIcon = "file:///android_asset/native-icons/$priceId.svg",
        isTestNet = false,
        priceId = priceId,
        precision = precision,
        staking = Asset.StakingType.UNSUPPORTED,
        purchaseProviders = null,
        supportStakingPool = false,
        isUtility = true,
        type = ChainAssetType.Normal,
        currencyId = null,
        existentialDeposit = null,
        color = null,
        isNative = true
    )),
    nodes = nodes,
    explorers = emptyList(),
    externalApi = Chain.ExternalApi(
        staking = null,
        history = Chain.ExternalApi.Section(historyType, indexerUrl),
        crowdloans = null
    ),
    icon = "file:///android_asset/native-icons/$priceId.svg",
    addressPrefix = 0,
    isEthereumBased = false,
    isTestNet = false,
    hasCrowdloans = false,
    parentId = null,
    supportStakingPool = false,
    isEthereumChain = false,
    chainlinkProvider = false,
    supportNft = false,
    isUsesAppId = false,
    identityChain = null,
    // The persisted compatibility enum predates native networks. Consumers must
    // dispatch by the native id/history type before this legacy field.
    ecosystem = Ecosystem.Substrate,
    remoteAssetsSource = null
)
