package jp.co.soramitsu.common.wallet

import com.google.gson.Gson
import jp.co.soramitsu.common.model.UniversalWalletChainRegistry
import jp.co.soramitsu.common.model.UniversalWalletChainRegistryEntry
import jp.co.soramitsu.common.model.UniversalWalletEcosystem
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.model.UniversalWalletRegistryAsset
import jp.co.soramitsu.common.model.UniversalWalletRegistryEndpoint
import jp.co.soramitsu.common.model.UniversalWalletRegistryEndpointKind
import jp.co.soramitsu.common.model.UniversalWalletRegistryValidationError
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalWalletRegistryContractTest {

    @Test
    fun `validates and serializes public chain registry entries`() {
        val registry = UniversalWalletChainRegistry(chains = listOf(solanaMainnet(), taira()))

        assertTrue(registry.validationErrors().isEmpty())

        val json = Gson().toJson(registry)
        assertTrue(json.contains("\"ecosystem\":\"solana\""))
        assertTrue(json.contains("\"kind\":\"indexer\""))
        assertTrue(json.contains("\"kind\":\"torii-mcp\""))
        assertTrue(json.contains("\"readOnly\":true"))
        assertTrue(json.contains("\"features\":[\"transfer\"]"))
    }

    @Test
    fun `allows disabled gated networks without endpoints`() {
        val nexus = UniversalWalletChainRegistryEntry(
            id = "sora-nexus-mainnet",
            ecosystem = UniversalWalletEcosystem.Iroha,
            chainId = "sora:nexus:global",
            displayName = "SORA Nexus",
            enabledByDefault = false,
            features = listOf("transfer", "offline-cash", "sccp", "governance"),
            endpoints = emptyList()
        )

        assertTrue(nexus.validationErrors().isEmpty())
    }

    @Test
    fun `actual default registry entries validate and expose expected public endpoints`() {
        val registry = UniversalWalletRegistry.chainRegistry

        assertTrue(registry.validationErrors().isEmpty())
        assertTrue(registry.chains.map { it.id }.containsAll(
            listOf(
                "bitcoin-mainnet",
                "bitcoin-testnet",
                "ton-mainnet",
                "solana-mainnet",
                "solana-devnet",
                "taira-testnet",
                "sora-nexus-mainnet"
            )
        ))

        val solana = registry.chains.first { it.id == "solana-mainnet" }
        assertTrue(solana.endpoints.any {
            it.kind == UniversalWalletRegistryEndpointKind.Indexer &&
                it.url == "https://si.soramitsu.io" &&
                it.readOnly
        })
        assertTrue(solana.endpoints.any {
            it.kind == UniversalWalletRegistryEndpointKind.Rpc &&
                it.url == "https://api.mainnet-beta.solana.com" &&
                !it.readOnly
        })

        val ton = registry.chains.first { it.id == "ton-mainnet" }
        assertTrue(ton.endpoints.any {
            it.kind == UniversalWalletRegistryEndpointKind.Indexer &&
                it.url == "https://ti.soramitsu.io" &&
                it.readOnly
        })

        val taira = registry.chains.first { it.id == "taira-testnet" }
        assertTrue(taira.features == listOf("transfer"))
        assertTrue(taira.endpoints.any {
            it.kind == UniversalWalletRegistryEndpointKind.ToriiMcp &&
                it.url == "https://taira.sora.org/v1/mcp" &&
                !it.readOnly
        })

        val nexus = registry.chains.first { it.id == "sora-nexus-mainnet" }
        assertTrue(!nexus.enabledByDefault)
        assertTrue(nexus.features == listOf("transfer", "offline-cash", "sccp", "governance"))
        assertTrue(nexus.endpoints.any {
            it.kind == UniversalWalletRegistryEndpointKind.ToriiMcp &&
                it.url == "https://minamoto.sora.org/v1/mcp" &&
                !it.readOnly
        })
    }

    @Test
    fun `rejects malformed registry entries and public write indexers`() {
        val chain = solanaMainnet().copy(
            id = "../bad",
            ecosystem = "unknown",
            chainId = "bad chain",
            displayName = "bad\u0000name",
            nativeAsset = UniversalWalletRegistryAsset(
                id = "bad id",
                symbol = "sol",
                decimals = 256,
                name = "bad\u0000name"
            ),
            derivationPath = "m/44'/x",
            slip44CoinType = -1,
            features = listOf("governance", "governance", "bad feature"),
            endpoints = listOf(
                UniversalWalletRegistryEndpoint(
                    id = "bad endpoint",
                    kind = UniversalWalletRegistryEndpointKind.Indexer,
                    url = "http://si.soramitsu.io",
                    readOnly = false,
                    priority = -1
                )
            )
        )

        val errors = chain.validationErrors()

        assertTrue(errors.contains(UniversalWalletRegistryValidationError.InvalidId))
        assertTrue(errors.contains(UniversalWalletRegistryValidationError.InvalidEcosystem))
        assertTrue(errors.contains(UniversalWalletRegistryValidationError.InvalidChainId))
        assertTrue(errors.contains(UniversalWalletRegistryValidationError.InvalidDisplayName))
        assertTrue(errors.contains(UniversalWalletRegistryValidationError.InvalidAssetId))
        assertTrue(errors.contains(UniversalWalletRegistryValidationError.InvalidAssetSymbol))
        assertTrue(errors.contains(UniversalWalletRegistryValidationError.InvalidAssetDecimals))
        assertTrue(errors.contains(UniversalWalletRegistryValidationError.InvalidAssetName))
        assertTrue(errors.contains(UniversalWalletRegistryValidationError.InvalidDerivationPath))
        assertTrue(errors.contains(UniversalWalletRegistryValidationError.InvalidSlip44CoinType))
        assertTrue(errors.contains(UniversalWalletRegistryValidationError.DuplicateFeatureId))
        assertTrue(errors.contains(UniversalWalletRegistryValidationError.InvalidFeatureId))
        assertTrue(errors.contains(UniversalWalletRegistryValidationError.InvalidEndpointId))
        assertTrue(errors.contains(UniversalWalletRegistryValidationError.InvalidEndpointUrl))
        assertTrue(errors.contains(UniversalWalletRegistryValidationError.InvalidEndpointPriority))
        assertTrue(errors.contains(UniversalWalletRegistryValidationError.PublicWriteIndexer))
    }

    @Test
    fun `rejects duplicate chain and endpoint identifiers`() {
        val duplicateEndpoint = solanaMainnet().copy(
            endpoints = listOf(indexer(), indexer())
        )
        val registry = UniversalWalletChainRegistry(
            schemaVersion = 99,
            chains = listOf(solanaMainnet(), solanaMainnet(), duplicateEndpoint)
        )

        val errors = registry.validationErrors()

        assertTrue(errors.contains(UniversalWalletRegistryValidationError.InvalidSchemaVersion))
        assertTrue(errors.contains(UniversalWalletRegistryValidationError.DuplicateChainId))
        assertTrue(errors.contains(UniversalWalletRegistryValidationError.DuplicateEndpointId))
    }

    private fun solanaMainnet() = UniversalWalletChainRegistryEntry(
        id = "solana-mainnet",
        ecosystem = UniversalWalletEcosystem.Solana,
        chainId = "solana:mainnet",
        displayName = "Solana",
        enabledByDefault = true,
        nativeAsset = UniversalWalletRegistryAsset(
            id = "SOL",
            symbol = "SOL",
            decimals = 9,
            name = "Solana"
        ),
        derivationPath = "m/44'/501'/0'/0'",
        slip44CoinType = 501,
        endpoints = listOf(
            indexer(),
            UniversalWalletRegistryEndpoint(
                id = "solana-mainnet-rpc",
                kind = UniversalWalletRegistryEndpointKind.Rpc,
                url = "https://api.mainnet-beta.solana.com",
                readOnly = false,
                priority = 1
            )
        )
    )

    private fun taira() = UniversalWalletChainRegistryEntry(
        id = "taira-testnet",
        ecosystem = UniversalWalletEcosystem.Iroha,
        chainId = "iroha3-taira",
        displayName = "Taira Testnet",
        enabledByDefault = true,
        features = listOf("transfer"),
        endpoints = listOf(
            UniversalWalletRegistryEndpoint(
                id = "taira-torii-mcp",
                kind = UniversalWalletRegistryEndpointKind.ToriiMcp,
                url = "https://taira.sora.org/v1/mcp",
                readOnly = false
            )
        )
    )

    private fun indexer() = UniversalWalletRegistryEndpoint(
        id = "solana-mainnet-indexer",
        kind = UniversalWalletRegistryEndpointKind.Indexer,
        url = "https://si.soramitsu.io",
        readOnly = true
    )
}
