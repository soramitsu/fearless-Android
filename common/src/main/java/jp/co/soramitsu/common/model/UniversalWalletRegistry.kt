package jp.co.soramitsu.common.model

object UniversalWalletRegistry {
    const val BITCOIN_MAINNET_INDEXER_BASE_URL = "https://blockstream.info/api"
    const val BITCOIN_TESTNET_INDEXER_BASE_URL = "https://blockstream.info/testnet/api"
    const val TON_INDEXER_BASE_URL = "https://ti.soramitsu.io"
    const val SOLANA_INDEXER_BASE_URL = "https://si.soramitsu.io"
    const val SOLANA_MAINNET_RPC_URL = "https://api.mainnet-beta.solana.com"
    const val SOLANA_DEVNET_RPC_URL = "https://api.devnet.solana.com"

    val bitcoinMainnet = BitcoinNetwork(
        id = "bitcoin-mainnet",
        chainId = "bitcoin:mainnet",
        name = "Bitcoin",
        slip44CoinType = 0,
        addressHrp = "bc",
        accountPath = UniversalWalletDerivationPaths.BITCOIN_MAINNET_ACCOUNT,
        firstReceivePath = UniversalWalletDerivationPaths.BITCOIN_MAINNET_FIRST_RECEIVE,
        indexerBaseUrl = BITCOIN_MAINNET_INDEXER_BASE_URL,
        defaultGapLimit = 20,
        enabledByDefault = true,
        nativeAsset = BitcoinNativeAsset(
            id = "BTC",
            symbol = "BTC",
            decimals = 8
        )
    )

    val bitcoinTestnet = BitcoinNetwork(
        id = "bitcoin-testnet",
        chainId = "bitcoin:testnet",
        name = "Bitcoin Testnet",
        slip44CoinType = 1,
        addressHrp = "tb",
        accountPath = UniversalWalletDerivationPaths.BITCOIN_TESTNET_ACCOUNT,
        firstReceivePath = UniversalWalletDerivationPaths.BITCOIN_TESTNET_FIRST_RECEIVE,
        indexerBaseUrl = BITCOIN_TESTNET_INDEXER_BASE_URL,
        defaultGapLimit = 20,
        enabledByDefault = false,
        nativeAsset = BitcoinNativeAsset(
            id = "BTC",
            symbol = "BTC",
            decimals = 8
        )
    )

    val solanaMainnet = SolanaNetwork(
        id = "solana-mainnet",
        chainId = "solana:mainnet",
        name = "Solana",
        indexerBaseUrl = SOLANA_INDEXER_BASE_URL,
        rpcUrl = SOLANA_MAINNET_RPC_URL,
        enabledByDefault = true,
        nativeAsset = SolanaNativeAsset(
            id = "SOL",
            symbol = "SOL",
            decimals = 9
        )
    )

    val solanaDevnet = SolanaNetwork(
        id = "solana-devnet",
        chainId = "solana:devnet",
        name = "Solana Devnet",
        indexerBaseUrl = SOLANA_INDEXER_BASE_URL,
        rpcUrl = SOLANA_DEVNET_RPC_URL,
        enabledByDefault = false,
        nativeAsset = SolanaNativeAsset(
            id = "SOL",
            symbol = "SOL",
            decimals = 9
        )
    )

    val taira = IrohaNetwork(
        id = "taira-testnet",
        chainId = "iroha3-taira",
        chainDiscriminant = 369,
        toriiBaseUrl = "https://taira.sora.org",
        mcpPath = "/v1/mcp",
        enabledByDefault = true,
        features = listOf("transfer")
    )

    val nexus = IrohaNetwork(
        id = "sora-nexus-mainnet",
        chainId = "sora:nexus:global",
        chainDiscriminant = 753,
        toriiBaseUrl = "https://minamoto.sora.org",
        mcpPath = "/v1/mcp",
        enabledByDefault = false,
        features = listOf("transfer", "offline-cash", "sccp", "governance")
    )

    val bitcoinMainnetRegistryEntry = bitcoinMainnet.toRegistryEntry()
    val bitcoinTestnetRegistryEntry = bitcoinTestnet.toRegistryEntry()
    val tonMainnetRegistryEntry = UniversalWalletChainRegistryEntry(
        id = "ton-mainnet",
        ecosystem = UniversalWalletEcosystem.Ton,
        chainId = "ton:mainnet",
        displayName = "TON",
        enabledByDefault = true,
        nativeAsset = UniversalWalletRegistryAsset(
            id = "TON",
            symbol = "TON",
            decimals = 9,
            name = "Toncoin"
        ),
        derivationPath = UniversalWalletDerivationPaths.TON_DEFAULT,
        slip44CoinType = 607,
        endpoints = listOf(
            UniversalWalletRegistryEndpoint(
                id = "ton-mainnet-indexer",
                kind = UniversalWalletRegistryEndpointKind.Indexer,
                url = TON_INDEXER_BASE_URL,
                readOnly = true
            )
        )
    )
    val solanaMainnetRegistryEntry = solanaMainnet.toRegistryEntry(
        derivationPath = UniversalWalletDerivationPaths.SOLANA_DEFAULT,
        slip44CoinType = 501
    )
    val solanaDevnetRegistryEntry = solanaDevnet.toRegistryEntry(
        derivationPath = UniversalWalletDerivationPaths.SOLANA_DEFAULT,
        slip44CoinType = 501
    )
    val tairaRegistryEntry = taira.toRegistryEntry(displayName = "Taira Testnet")
    val nexusRegistryEntry = nexus.toRegistryEntry(displayName = "SORA Nexus")
    val chainRegistry = UniversalWalletChainRegistry(
        chains = listOf(
            bitcoinMainnetRegistryEntry,
            bitcoinTestnetRegistryEntry,
            tonMainnetRegistryEntry,
            solanaMainnetRegistryEntry,
            solanaDevnetRegistryEntry,
            tairaRegistryEntry,
            nexusRegistryEntry
        )
    )

    data class IrohaNetwork(
        val id: String,
        val chainId: String,
        val chainDiscriminant: Int,
        val toriiBaseUrl: String?,
        val mcpPath: String,
        val enabledByDefault: Boolean,
        val features: List<String>
    )

    data class BitcoinNetwork(
        val id: String,
        val chainId: String,
        val name: String,
        val slip44CoinType: Int,
        val addressHrp: String,
        val accountPath: String,
        val firstReceivePath: String,
        val indexerBaseUrl: String,
        val defaultGapLimit: Int,
        val enabledByDefault: Boolean,
        val nativeAsset: BitcoinNativeAsset
    )

    data class BitcoinNativeAsset(
        val id: String,
        val symbol: String,
        val decimals: Int
    )

    data class SolanaNetwork(
        val id: String,
        val chainId: String,
        val name: String,
        val indexerBaseUrl: String,
        val rpcUrl: String,
        val enabledByDefault: Boolean,
        val nativeAsset: SolanaNativeAsset
    )

    data class SolanaNativeAsset(
        val id: String,
        val symbol: String,
        val decimals: Int
    )

    private fun BitcoinNetwork.toRegistryEntry() = UniversalWalletChainRegistryEntry(
        id = id,
        ecosystem = UniversalWalletEcosystem.Bitcoin,
        chainId = chainId,
        displayName = name,
        enabledByDefault = enabledByDefault,
        nativeAsset = UniversalWalletRegistryAsset(
            id = nativeAsset.id,
            symbol = nativeAsset.symbol,
            decimals = nativeAsset.decimals,
            name = name
        ),
        derivationPath = accountPath,
        slip44CoinType = slip44CoinType,
        endpoints = listOf(
            UniversalWalletRegistryEndpoint(
                id = "$id-indexer",
                kind = UniversalWalletRegistryEndpointKind.Indexer,
                url = indexerBaseUrl,
                readOnly = true
            )
        )
    )

    private fun SolanaNetwork.toRegistryEntry(
        derivationPath: String,
        slip44CoinType: Int
    ) = UniversalWalletChainRegistryEntry(
        id = id,
        ecosystem = UniversalWalletEcosystem.Solana,
        chainId = chainId,
        displayName = name,
        enabledByDefault = enabledByDefault,
        nativeAsset = UniversalWalletRegistryAsset(
            id = nativeAsset.id,
            symbol = nativeAsset.symbol,
            decimals = nativeAsset.decimals,
            name = name
        ),
        derivationPath = derivationPath,
        slip44CoinType = slip44CoinType,
        endpoints = listOf(
            UniversalWalletRegistryEndpoint(
                id = "$id-indexer",
                kind = UniversalWalletRegistryEndpointKind.Indexer,
                url = indexerBaseUrl,
                readOnly = true
            ),
            UniversalWalletRegistryEndpoint(
                id = "$id-rpc",
                kind = UniversalWalletRegistryEndpointKind.Rpc,
                url = rpcUrl,
                readOnly = false,
                priority = 1
            )
        )
    )

    private fun IrohaNetwork.toRegistryEntry(displayName: String) = UniversalWalletChainRegistryEntry(
        id = id,
        ecosystem = UniversalWalletEcosystem.Iroha,
        chainId = chainId,
        displayName = displayName,
        enabledByDefault = enabledByDefault,
        derivationPath = UniversalWalletDerivationPaths.IROHA_DEFAULT,
        slip44CoinType = 617,
        features = features,
        endpoints = toriiBaseUrl?.let { baseUrl ->
            listOf(
                UniversalWalletRegistryEndpoint(
                    id = "$id-torii-mcp",
                    kind = UniversalWalletRegistryEndpointKind.ToriiMcp,
                    url = baseUrl.removeSuffix("/") + mcpPath,
                    readOnly = false
                )
            )
        } ?: emptyList()
    )
}
