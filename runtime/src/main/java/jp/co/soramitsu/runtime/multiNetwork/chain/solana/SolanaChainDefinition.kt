package jp.co.soramitsu.runtime.multiNetwork.chain.solana

import jp.co.soramitsu.common.domain.SOLANA_CHAIN_ID
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.Asset.StakingType
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

object SolanaChainDefinition {

    const val CHAIN_ID: ChainId = SOLANA_CHAIN_ID
    private const val ICON_URL = "https://raw.githubusercontent.com/trustwallet/assets/master/blockchains/solana/info/logo.png"
    private const val EXPLORER_URL = "https://solscan.io/"

    private val nodes = listOf(
        ChainNode(
            url = "https://rpc.ankr.com/solana",
            name = "Ankr RPC",
            isActive = true,
            isDefault = true
        ),
        ChainNode(
            url = "https://api.mainnet-beta.solana.com",
            name = "Solana Labs RPC",
            isActive = false,
            isDefault = true
        )
    )

    private val baseAsset = Asset(
        id = "SOL",
        name = "Solana",
        symbol = "SOL",
        iconUrl = ICON_URL,
        chainId = CHAIN_ID,
        priceId = "solana",
        precision = 9,
        staking = StakingType.UNSUPPORTED,
        purchaseProviders = emptyList(),
        chainName = "Solana",
        chainIcon = ICON_URL,
        isTestNet = false,
        supportStakingPool = false,
        isUtility = true,
        type = null,
        currencyId = null,
        existentialDeposit = "0.0001",
        color = "#9945FF",
        isNative = true,
        priceProvider = null,
        coinbaseUrl = null
    )

    private val memecoinAssets = listOf(
        Asset(
            id = "BONK",
            name = "Bonk",
            symbol = "BONK",
            iconUrl = "https://assets.coingecko.com/coins/images/28566/small/bonk.jpg",
            chainId = CHAIN_ID,
            priceId = "bonk",
            precision = 5,
            staking = StakingType.UNSUPPORTED,
            purchaseProviders = emptyList(),
            chainName = "Solana",
            chainIcon = ICON_URL,
            isTestNet = false,
            supportStakingPool = false,
            isUtility = false,
            type = null,
            currencyId = null,
            existentialDeposit = null,
            color = "#F7931A",
            isNative = false,
            priceProvider = null,
            coinbaseUrl = null
        ),
        Asset(
            id = "WIF",
            name = "dogwifhat",
            symbol = "WIF",
            iconUrl = "https://assets.coingecko.com/coins/images/34289/small/wif.png",
            chainId = CHAIN_ID,
            priceId = "dogwifhat",
            precision = 6,
            staking = StakingType.UNSUPPORTED,
            purchaseProviders = emptyList(),
            chainName = "Solana",
            chainIcon = ICON_URL,
            isTestNet = false,
            supportStakingPool = false,
            isUtility = false,
            type = null,
            currencyId = null,
            existentialDeposit = null,
            color = "#FFC107",
            isNative = false,
            priceProvider = null,
            coinbaseUrl = null
        ),
        Asset(
            id = "PEPE",
            name = "Pepe Solana",
            symbol = "PEPE",
            iconUrl = "https://assets.coingecko.com/coins/images/29850/small/pepe.jpg",
            chainId = CHAIN_ID,
            priceId = "pepe",
            precision = 6,
            staking = StakingType.UNSUPPORTED,
            purchaseProviders = emptyList(),
            chainName = "Solana",
            chainIcon = ICON_URL,
            isTestNet = false,
            supportStakingPool = false,
            isUtility = false,
            type = null,
            currencyId = null,
            existentialDeposit = null,
            color = "#00A86B",
            isNative = false,
            priceProvider = null,
            coinbaseUrl = null
        )
    )

    val memecoinAssetIds = memecoinAssets.map { it.id }.toSet()

    val chain: Chain = Chain(
        id = CHAIN_ID,
        paraId = null,
        rank = 25,
        name = "Solana",
        minSupportedVersion = null,
        assets = listOf(baseAsset) + memecoinAssets,
        nodes = nodes,
        explorers = listOf(
            Chain.Explorer(
                type = Chain.Explorer.Type.UNKNOWN,
                types = listOf("account", "transaction"),
                url = EXPLORER_URL
            )
        ),
        externalApi = null,
        icon = ICON_URL,
        addressPrefix = 0,
        isEthereumBased = false,
        isTestNet = false,
        hasCrowdloans = false,
        parentId = null,
        supportStakingPool = false,
        isEthereumChain = false,
        chainlinkProvider = false,
        supportNft = true,
        isUsesAppId = false,
        identityChain = null,
        ecosystem = Ecosystem.Ethereum,
        androidMinAppVersion = null,
        remoteAssetsSource = null,
        tonBridgeUrl = null
    )
}
