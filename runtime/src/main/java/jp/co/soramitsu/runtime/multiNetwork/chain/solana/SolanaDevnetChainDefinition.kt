package jp.co.soramitsu.runtime.multiNetwork.chain.solana

import jp.co.soramitsu.common.domain.SOLANA_DEVNET_CHAIN_ID
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

object SolanaDevnetChainDefinition {

    const val CHAIN_ID: ChainId = SOLANA_DEVNET_CHAIN_ID
    private const val ICON_URL =
        "https://raw.githubusercontent.com/trustwallet/assets/master/blockchains/solana/info/logo.png"
    private const val EXPLORER_URL = "https://solscan.io/"

    private val nodes = listOf(
        ChainNode(
            url = "https://api.devnet.solana.com",
            name = "Solana Devnet RPC",
            isActive = true,
            isDefault = true
        ),
        ChainNode(
            url = "https://rpc.ankr.com/solana_devnet",
            name = "Ankr Devnet RPC",
            isActive = false,
            isDefault = true
        )
    )

    private val baseAsset = Asset(
        id = "SOL",
        name = "Solana Devnet",
        symbol = "SOL",
        iconUrl = ICON_URL,
        chainId = CHAIN_ID,
        priceId = "solana",
        precision = 9,
        staking = Asset.StakingType.UNSUPPORTED,
        purchaseProviders = emptyList(),
        chainName = "Solana Devnet",
        chainIcon = ICON_URL,
        isTestNet = true,
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

    val chain: Chain = Chain(
        id = CHAIN_ID,
        paraId = null,
        rank = null,
        name = "Solana Devnet",
        minSupportedVersion = null,
        assets = listOf(baseAsset),
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
        isTestNet = true,
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
