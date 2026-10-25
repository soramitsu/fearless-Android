package jp.co.soramitsu.coredb.recovery.selection

import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.coredb.model.chain.JoinedChainInfo

/** Catalog inputs derived from the fresh lane-owned snapshot; no caller-owned lists are retained. */
class EndpointCatalogSnapshot(
    chains: List<JoinedChainInfo>,
    preservedAssetChains: Set<String>,
    preservedNodeChains: Set<String> = emptySet(),
    preservedCatalogChains: Set<String> = emptySet(),
    unsupportedEcosystemOverrides: Map<String, String> = emptyMap()
) {
    val chains = chains.map {
        it.copy(nodes = it.nodes.toList(), assets = it.assets.toList(), explorers = it.explorers.toList())
    }
    val preservedAssetChains = preservedAssetChains.toSet()

    /** Operational projections may omit optional text; these parents and all catalog children stay stored verbatim. */
    val preservedCatalogChains = preservedCatalogChains.toSet()

    /** Catalog preservation includes nodes, whose unreadable text must never be projected back into storage. */
    val preservedNodeChains = preservedNodeChains.toSet() + this.preservedCatalogChains

    /** Preserve a fetched unsupported spelling exactly while retaining every other cached cell. */
    val unsupportedEcosystemOverrides = unsupportedEcosystemOverrides.toMap()

    init {
        require(this.preservedCatalogChains.containsAll(this.unsupportedEcosystemOverrides.keys)) {
            "Unsupported ecosystem override requires catalog preservation"
        }
        require(this.unsupportedEcosystemOverrides.values.none { raw -> Ecosystem.entries.any { it.name.equals(raw, ignoreCase = true) } }) {
            "Supported ecosystems require a complete fetched replacement"
        }
        val parents = this.chains.map { it.chain.id }.toSet()
        require(parents.size == this.chains.size) { "Catalog parent identity is ambiguous" }
        require(parents.containsAll(this.preservedAssetChains) && parents.containsAll(this.preservedNodeChains)) {
            "Catalog preservation target differs"
        }
        for (item in this.chains) {
            val id = item.chain.id
            if (id in this.preservedNodeChains) {
                require(item.nodes.isEmpty()) { "Preserved catalog nodes cannot also request replacement" }
            }
            require(
                item.nodes.all { it.chainId == id } && item.assets.all { it.chainId == id } &&
                item.explorers.all { it.chainId == id }
            ) { "Catalog child target differs" }
            require(
                item.nodes.map { it.url }.toSet().size == item.nodes.size &&
                item.assets.map { it.id }.toSet().size == item.assets.size &&
                item.explorers.map { it.type }.toSet().size == item.explorers.size
            ) {
                "Catalog child identity is ambiguous"
            }
        }
    }
}
