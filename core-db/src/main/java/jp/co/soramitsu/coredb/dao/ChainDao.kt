package jp.co.soramitsu.coredb.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.utils.removedXcPrefix
import jp.co.soramitsu.coredb.dao.AssetDao.Companion.xcPrefix
import jp.co.soramitsu.coredb.model.AssetWithToken
import jp.co.soramitsu.coredb.model.chain.BoundedRuntimeNodes
import jp.co.soramitsu.coredb.model.chain.ChainAssetLocal
import jp.co.soramitsu.coredb.model.chain.ChainExplorerLocal
import jp.co.soramitsu.coredb.model.chain.ChainLocal
import jp.co.soramitsu.coredb.model.chain.ChainNodeLocal
import jp.co.soramitsu.coredb.model.chain.ChainRuntimeInfoLocal
import jp.co.soramitsu.coredb.model.chain.ChainTypesLocal
import jp.co.soramitsu.coredb.model.chain.CustomEndpointLocal
import jp.co.soramitsu.coredb.model.chain.EndpointProjectionLimits
import jp.co.soramitsu.coredb.model.chain.FundedCatalogAssetRow
import jp.co.soramitsu.coredb.model.chain.JoinedChainInfo
import jp.co.soramitsu.coredb.model.chain.OptionalCatalogText
import jp.co.soramitsu.coredb.model.chain.RuntimeAssetCatalogRow
import jp.co.soramitsu.coredb.model.chain.RuntimeChainCatalogRow
import jp.co.soramitsu.coredb.model.chain.RuntimeExplorerCatalogRow
import jp.co.soramitsu.coredb.model.chain.RuntimeNodeMetadata
import jp.co.soramitsu.coredb.recovery.selection.EndpointCatalogSnapshot
import jp.co.soramitsu.coredb.recovery.selection.EndpointMutationLane
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest

@Dao
abstract class ChainDao {

    /** Room caches this DAO, so every endpoint writer shares one database-lifetime order. */
    val endpointMutationLane = EndpointMutationLane()

    /** Fresh immutable endpoint input paired with a process version that every routed writer retires. */
    suspend fun readEndpointRuntime(chainId: String): EndpointMutationLane.RuntimeRead<JoinedChainInfo?> =
        endpointMutationLane.readRuntime(chainId) {
            getJoinChainInfo(chainId)?.let { EndpointCatalogSnapshot(listOf(it), emptySet()).chains.single() }
        }

    /**
     * Fetch outside this boundary. The non-suspending plan sees the fresh database snapshot,
     * including parents admitted before this wildcard, and must perform no I/O.
     * Nodes are deliberately absent from this metadata snapshot: an offline plan must explicitly
     * preserve cached catalog sets rather than persist optional text omitted by the operational reader.
     * A fetched replacement supplies complete incoming values; offline preservation performs no DML
     * on the preserved parent or any of its catalog children, except an explicitly admitted
     * unsupported ecosystem spelling may quarantine that parent through a one-column update.
     * The returned assets become eligible for wallet-row creation only after physical commit.
     */
    suspend fun replaceCatalog(
        operation: EndpointMutationLane.Operation,
        plan: (List<JoinedChainInfo>) -> EndpointCatalogSnapshot
    ): List<ChainAssetLocal> = endpointMutationLane.withOperation(operation) {
        replaceCatalogTransaction(operation, plan)
    }

    @Transaction
    protected open suspend fun replaceCatalogTransaction(
        operation: EndpointMutationLane.Operation,
        plan: (List<JoinedChainInfo>) -> EndpointCatalogSnapshot
    ): List<ChainAssetLocal> {
        endpointMutationLane.requireOwnedCatalog(operation)
        val local = readRuntimeCatalog(null, includeNodes = false)
        val input = plan(local)
        val remote = input.chains
        val remoteIds = remote.map { it.chain.id }.toSet()
        check(remoteIds.size == remote.size) { "Catalog parent identity is ambiguous" }
        endpointMutationLane.requireOwnedTargets(operation, local.map { it.chain.id } + remoteIds)
        val localParents = local.associateBy { it.chain.id }
        require(localParents.keys.containsAll(input.preservedCatalogChains)) { "Catalog preservation requires an existing parent" }
        val omittedParents = local.filter { it.chain.id !in remoteIds }
        val retainedIds = omittedParents.map { it.chain.id }.chunked(64)
            .flatMap { referencedCatalogChainIds(it) }.toSet()
        // Wallet balances, account keys and favorites outlive catalog availability. Preserve their
        // parent and catalog children while the unsupported family revokes runtime/staking access.
        for (item in omittedParents.filter { it.chain.id in retainedIds }) {
            if (Ecosystem.entries.any { it.name.equals(item.chain.ecosystem, ignoreCase = true) }) {
                check(updateUnsupportedCatalogEcosystem(item.chain.id,
                    ChainLocal.UNAVAILABLE_CATALOG_PREFIX + item.chain.ecosystem) == 1) {
                    "Retained catalog parent disappeared"
                }
            }
        }
        val replaceableLocal = local.filter { it.chain.id !in retainedIds }
        val replacements = remote.filter { it.chain.id !in input.preservedCatalogChains }
        // Projected optional text can be absent even when stored bytes exist. Complete fetched
        // values must therefore replace existing rows without comparing against that projection.
        updateChainsInside(replacements.filter { it.chain.id !in localParents }.map { it.chain },
            replacements.filter { it.chain.id in localParents }.map { it.chain })
        for ((chainId, ecosystem) in input.unsupportedEcosystemOverrides) {
            check(updateUnsupportedCatalogEcosystem(chainId, ecosystem) == 1) { "Unsupported ecosystem parent disappeared" }
        }
        replaceCatalogNodes(input)
        val addedAssets = replaceCatalogAssets(replaceableLocal, input)
        replaceCatalogExplorers(replaceableLocal.filter { it.chain.id !in input.preservedCatalogChains }, replacements)
        deleteChainsInside(omittedParents.filter { it.chain.id !in retainedIds }.map { it.chain })
        endpointMutationLane.grantFinal(operation) {}
        return addedAssets
    }

    @Query("UPDATE chains SET ecosystem = :ecosystem WHERE id = :chainId")
    protected abstract suspend fun updateUnsupportedCatalogEcosystem(chainId: String, ecosystem: String): Int

    @Query("SELECT id FROM chains WHERE id IN (:chainIds) AND (" +
        "EXISTS(SELECT 1 FROM assets WHERE assets.chainId = chains.id) OR " +
        "EXISTS(SELECT 1 FROM favorite_chains WHERE favorite_chains.chainId = chains.id) OR " +
        "EXISTS(SELECT 1 FROM chain_accounts WHERE chain_accounts.chainId = chains.id))")
    protected abstract suspend fun referencedCatalogChainIds(chainIds: List<String>): List<String>

    private suspend fun replaceCatalogNodes(input: EndpointCatalogSnapshot) {
        for (item in input.chains) {
            if (item.chain.id in input.preservedNodeChains) continue
            require(item.nodes.all { it.isDefault }) { "Catalog input cannot establish custom endpoint ownership" }
            // SQL compares complete keys in place. Existing custom names/URLs never cross a
            // CursorWindow merely to retain them, and reserved ownership always wins over catalog.
            val selected = selectedNodeRowId(item.chain.id)
            val defaults = item.nodes.filter { it.isDefault }
            deleteObsoleteCatalogNodes(item.chain.id, defaults.map { it.url })
            updateNodesInside(defaults, emptyList(), emptyList())
            if (selected != null && nodeRowIdExists(item.chain.id, selected)) {
                setSelectedNodeRowId(item.chain.id, selected)
            }
        }
    }

    private suspend fun replaceCatalogAssets(local: List<JoinedChainInfo>, input: EndpointCatalogSnapshot): List<ChainAssetLocal> {
        val previous = local.filter { it.chain.id !in input.preservedCatalogChains }.flatMap { it.assets }.associateBy { it.chainId to it.id }
        val incoming = input.chains.filter { it.chain.id !in input.preservedCatalogChains }.flatMap { it.assets }.associateBy { it.chainId to it.id }
        val added = incoming.filterKeys { it !in previous }.values.toList()
        insertChainAssets(added)
        updateChainAssets(incoming.filterKeys { it in previous }.values.toList())
        deleteChainAssets(previous.filter { (key, value) -> key !in incoming && value.chainId !in input.preservedAssetChains }.values.toList())
        return added
    }

    private suspend fun replaceCatalogExplorers(local: List<JoinedChainInfo>, remote: List<JoinedChainInfo>) {
        val previous = local.flatMap { it.explorers }.associateBy { it.chainId to it.type }
        val incoming = remote.flatMap { it.explorers }.associateBy { it.chainId to it.type }
        insertChainExplorers(incoming.filterKeys { it !in previous }.values.toList())
        updateChainExplorers(incoming.filterKeys { it in previous }.values.toList())
        deleteChainExplorers(previous.filterKeys { it !in incoming }.values.toList())
    }

    open suspend fun update(removed: List<ChainLocal>,
        newOrUpdated: List<JoinedChainInfo>) {
        val targets = removed.map { it.id } + newOrUpdated.map { it.chain.id }
        if (targets.isEmpty()) return
        update(endpointMutationLane.admit(targets), removed, newOrUpdated)
    }

    /** Explicit intent is captured before asynchronous validation or queueing. */
    suspend fun update(operation: EndpointMutationLane.Operation, removed: List<ChainLocal>,
        newOrUpdated: List<JoinedChainInfo>) =
        orderedUpdate(operation, removed.toList(), EndpointCatalogSnapshot(newOrUpdated, emptySet()).chains)

    private suspend fun orderedUpdate(operation: EndpointMutationLane.Operation, removed: List<ChainLocal>,
        newOrUpdated: List<JoinedChainInfo>) =
        endpointMutationLane.withOperation(operation) { updateTransaction(operation, removed, newOrUpdated) }

    @Transaction
    protected open suspend fun updateTransaction(operation: EndpointMutationLane.Operation, removed: List<ChainLocal>,
        newOrUpdated: List<JoinedChainInfo>) {
        endpointMutationLane.requireOwnedTargets(operation, removed.map { it.id } + newOrUpdated.map { it.chain.id })
        updateInside(removed, newOrUpdated)
        endpointMutationLane.grantFinal(operation) {}
    }

    private suspend fun updateInside(removed: List<ChainLocal>,
        newOrUpdated: List<JoinedChainInfo>) {
        for (node in newOrUpdated.flatMap(JoinedChainInfo::nodes)) {
            EndpointProjectionLimits.requireTextFits(node.chainId, node.url, node.name)
        }
        deleteChainsInside(removed)
        val chains = newOrUpdated.map(JoinedChainInfo::chain)
        insertChains(chains)
        updateChainsProjection(chains)
        for (info in newOrUpdated) {
            // This entry also seeds custom rows in existing callers. Only a genuinely new key
            // can establish ownership here; a catalog replay never revives a pending/tombstone key.
            for (node in info.nodes.filter { !it.isDefault }) {
                if (!isCustomEndpointKeyReserved(node.chainId, node.url)) insertChainNodeInside(node)
            }
            val catalog = info.nodes.filter { it.isDefault }
            deleteObsoleteCatalogNodes(info.chain.id, catalog.map { it.url })
            updateNodesInside(catalog, emptyList(), emptyList())
        }
        for (ids in chains.map { it.id }.chunked(64)) {
            deleteAssetsForChains(ids)
            deleteExplorersForChains(ids)
        }
        insertChainAssets(newOrUpdated.flatMap(JoinedChainInfo::assets))
        insertChainExplorers(newOrUpdated.flatMap(JoinedChainInfo::explorers))
    }

    open suspend fun updateChains(chainsToAdd: List<ChainLocal>, chainsToUpdate: List<ChainLocal>) {
        val targets = (chainsToAdd + chainsToUpdate).map { it.id }
        if (targets.isEmpty()) return
        updateChains(endpointMutationLane.admit(targets), chainsToAdd, chainsToUpdate)
    }

    /** Explicit intent is captured before asynchronous validation or queueing. */
    suspend fun updateChains(operation: EndpointMutationLane.Operation, chainsToAdd: List<ChainLocal>, chainsToUpdate: List<ChainLocal>) =
        orderedUpdateChains(operation, chainsToAdd.toList(), chainsToUpdate.toList())

    private suspend fun orderedUpdateChains(operation: EndpointMutationLane.Operation, chainsToAdd: List<ChainLocal>, chainsToUpdate: List<ChainLocal>) =
        endpointMutationLane.withOperation(operation) { updateChainsTransaction(operation, chainsToAdd, chainsToUpdate) }

    @Transaction
    protected open suspend fun updateChainsTransaction(operation: EndpointMutationLane.Operation, chainsToAdd: List<ChainLocal>, chainsToUpdate: List<ChainLocal>) {
        endpointMutationLane.requireOwnedTargets(operation, (chainsToAdd + chainsToUpdate).map { it.id })
        updateChainsInside(chainsToAdd, chainsToUpdate)
        endpointMutationLane.grantFinal(operation) {}
    }

    private suspend fun updateChainsInside(chainsToAdd: List<ChainLocal>, chainsToUpdate: List<ChainLocal>) {
        // INSERT OR REPLACE deletes the old parent, cascading every custom projection.
        // IGNORE + UPDATE retains the parent identity and the current selection.
        insertChains(chainsToAdd)
        updateChainsProjection(chainsToAdd + chainsToUpdate)
    }

    @Transaction
    open suspend fun updateAssets(
        assetsToAdd: List<ChainAssetLocal>,
        assetsToUpdate: List<ChainAssetLocal>,
        assetsToRemove: List<ChainAssetLocal>
    ) {
        insertChainAssets(assetsToAdd)
        updateChainAssets(assetsToUpdate)
        deleteChainAssets(assetsToRemove)
    }

    open suspend fun updateNodes(nodesToAdd: List<ChainNodeLocal>,
        nodesToUpdate: List<ChainNodeLocal>,
        nodesToRemove: List<ChainNodeLocal>) {
        val targets = (nodesToAdd + nodesToUpdate + nodesToRemove).map { it.chainId }
        if (targets.isEmpty()) return
        updateNodes(endpointMutationLane.admit(targets), nodesToAdd, nodesToUpdate, nodesToRemove)
    }

    /** Explicit intent is captured before asynchronous validation or queueing. */
    suspend fun updateNodes(operation: EndpointMutationLane.Operation, nodesToAdd: List<ChainNodeLocal>,
        nodesToUpdate: List<ChainNodeLocal>,
        nodesToRemove: List<ChainNodeLocal>) =
        orderedUpdateNodes(operation, nodesToAdd.toList(), nodesToUpdate.toList(), nodesToRemove.toList())

    private suspend fun orderedUpdateNodes(operation: EndpointMutationLane.Operation, nodesToAdd: List<ChainNodeLocal>, nodesToUpdate: List<ChainNodeLocal>, nodesToRemove: List<ChainNodeLocal>) =
        endpointMutationLane.withOperation(operation) { updateNodesTransaction(operation, nodesToAdd, nodesToUpdate, nodesToRemove) }

    @Transaction
    protected open suspend fun updateNodesTransaction(operation: EndpointMutationLane.Operation, nodesToAdd: List<ChainNodeLocal>,
        nodesToUpdate: List<ChainNodeLocal>,
        nodesToRemove: List<ChainNodeLocal>) {
        endpointMutationLane.requireOwnedTargets(operation, (nodesToAdd + nodesToUpdate + nodesToRemove).map { it.chainId })
        updateNodesInside(nodesToAdd, nodesToUpdate, nodesToRemove)
        endpointMutationLane.grantFinal(operation) {}
    }

    private suspend fun updateNodesInside(nodesToAdd: List<ChainNodeLocal>,
        nodesToUpdate: List<ChainNodeLocal>,
        nodesToRemove: List<ChainNodeLocal>) {
        for (node in nodesToAdd + nodesToUpdate) {
            EndpointProjectionLimits.requireTextFits(node.chainId, node.url, node.name)
        }
        val affectedIds = (nodesToAdd + nodesToUpdate + nodesToRemove).map { it.chainId }.toSet()
        if (affectedIds.isEmpty()) return
        // Only scalar row IDs cross the cursor here. An adopted name or URL can be much larger
        // than a CursorWindow and must never be reread merely to preserve selection.
        val selected = affectedIds.associateWith { selectedNodeRowId(it) }
        val adds = mutableListOf<ChainNodeLocal>()
        val updates = mutableListOf<ChainNodeLocal>()
        val removals = mutableListOf<ChainNodeLocal>()
        for ((source, target) in listOf(nodesToAdd to adds, nodesToUpdate to updates, nodesToRemove to removals)) {
            for (node in source) {
                if (!isCustomEndpointKeyReserved(node.chainId, node.url) && !isCustomProjection(node.chainId, node.url)) {
                    check(node.isDefault) { "Catalog input cannot establish custom endpoint ownership" }
                    target += node
                }
            }
        }
        for (node in adds) {
            insertCatalogNode(node.copy(isActive = false))
            updateCatalogNodeName(node.chainId, node.url, node.name)
        }
        for (node in updates) updateCatalogNodeName(node.chainId, node.url, node.name)
        deleteChainNodes(removals)
        for (chainId in affectedIds) {
            val previous = selected[chainId]?.takeIf { nodeRowIdExists(chainId, it) }
            var chosen = previous
            if (chosen == null) {
                for (node in adds + updates) {
                    if (node.chainId == chainId && node.isActive) {
                        chosen = nodeRowId(chainId, node.url)
                        if (chosen != null) break
                    }
                }
            }
            setSelectedNodeRowId(chainId, chosen ?: firstDefaultNodeRowId(chainId))
        }
    }

    @Transaction
    open suspend fun updateExplorers(
        explorersToAdd: MutableList<ChainExplorerLocal>,
        explorersToUpdate: MutableList<ChainExplorerLocal>,
        explorersToRemove: List<ChainExplorerLocal>
    ) {
        insertChainExplorers(explorersToAdd)
        updateChainExplorers(explorersToUpdate)
        deleteChainExplorers(explorersToRemove)
    }

    /** Parent loss detaches live ownership in the same transaction as the FK cascade. */
    open suspend fun deleteChains(chains: List<ChainLocal>) {
        val targets = chains.map { it.id }
        if (targets.isEmpty()) return
        deleteChains(endpointMutationLane.admit(targets), chains)
    }

    /** Explicit intent is captured before asynchronous validation or queueing. */
    suspend fun deleteChains(operation: EndpointMutationLane.Operation, chains: List<ChainLocal>) =
        orderedDeleteChains(operation, chains.toList())

    private suspend fun orderedDeleteChains(operation: EndpointMutationLane.Operation, chains: List<ChainLocal>) =
        endpointMutationLane.withOperation(operation) { deleteChainsTransaction(operation, chains) }

    @Transaction
    protected open suspend fun deleteChainsTransaction(operation: EndpointMutationLane.Operation, chains: List<ChainLocal>) {
        endpointMutationLane.requireOwnedTargets(operation, chains.map { it.id })
        deleteChainsInside(chains)
        endpointMutationLane.grantFinal(operation) {}
    }

    private suspend fun deleteChainsInside(chains: List<ChainLocal>) {
        if (chains.isEmpty()) return
        val batches = chains.map { it.id }.chunked(64)
        check(batches.none { hasUnincrementableAttachedRevision(it) }) { "Custom endpoint revision cannot advance" }
        for (ids in batches) detachCustomEndpoints(ids)
        deleteChainsProjection(chains)
    }

    @Delete
    protected abstract suspend fun deleteChainsProjection(chains: List<ChainLocal>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertChains(chains: List<ChainLocal>)

    /** Existing update-only callers receive the same ordered parent boundary. */
    open suspend fun updateChains(chains: List<ChainLocal>) = updateChains(emptyList(), chains)

    @Update
    protected abstract suspend fun updateChainsProjection(chains: List<ChainLocal>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertCatalogNode(node: ChainNodeLocal)

    @Query("UPDATE chain_nodes SET name = :name WHERE chainId = :chainId AND url = :url AND isDefault = 1")
    protected abstract suspend fun updateCatalogNodeName(chainId: String, url: String, name: String)

    /** Scalar lookups and pages avoid the API 26 bind ceiling without projecting old URL bytes. */
    protected suspend fun deleteObsoleteCatalogNodes(chainId: String, urls: List<String>) {
        val retained = urls.mapNotNull { nodeRowId(chainId, it) }.toSet()
        var afterRowId: Long? = null
        while (true) {
            val page = catalogDefaultNodeRowIds(chainId, afterRowId)
            if (page.isEmpty()) return
            for (id in page) {
                if (id !in retained) deleteUnreservedCatalogNodeByRowId(chainId, id)
            }
            afterRowId = page.last()
        }
    }

    @Query("SELECT rowid FROM chain_nodes WHERE chainId = :chainId AND isDefault = 1 " +
        "AND (:afterRowId IS NULL OR rowid > :afterRowId) ORDER BY rowid LIMIT 64")
    protected abstract suspend fun catalogDefaultNodeRowIds(chainId: String, afterRowId: Long?): List<Long>

    @Query("DELETE FROM chain_nodes WHERE chainId = :chainId AND rowid = :id AND isDefault = 1 " +
        "AND NOT EXISTS (SELECT 1 FROM custom_endpoint_records c WHERE c.chainId = chain_nodes.chainId AND c.url = chain_nodes.url)")
    protected abstract suspend fun deleteUnreservedCatalogNodeByRowId(chainId: String, id: Long)

    /** Explicit add atomically establishes ownership; only explicit add can revive a tombstone. */
    open suspend fun insertChainNode(node: ChainNodeLocal) {
        val targets = listOf(node.chainId)
        insertChainNode(endpointMutationLane.admit(targets), node)
    }

    /** Explicit intent is captured before asynchronous validation or queueing. */
    suspend fun insertChainNode(operation: EndpointMutationLane.Operation, node: ChainNodeLocal) =
        endpointMutationLane.withOperation(operation) { insertChainNodeTransaction(operation, node) }

    @Transaction
    protected open suspend fun insertChainNodeTransaction(operation: EndpointMutationLane.Operation, node: ChainNodeLocal) {
        endpointMutationLane.requireOwnedTargets(operation, listOf(node.chainId))
        insertChainNodeInside(node)
        endpointMutationLane.grantFinal(operation) {}
    }

    private suspend fun insertChainNodeInside(node: ChainNodeLocal) {
        EndpointProjectionLimits.requireTextFits(node.chainId, node.url, node.name)
        if (!node.isDefault) {
            val id = customEndpointId(node.chainId, node.url)
            if (id == null) {
                insertCustomEndpoint(CustomEndpointLocal(chainId = node.chainId, url = node.url, name = node.name, attached = true))
            } else {
                check(isDeletedCustomEndpoint(id)) { "Custom endpoint key is already owned" }
                val revision = customEndpointRevision(id)
                val next = nextCustomEndpointRevision(revision)
                check(reviveCustomEndpoint(id, revision, next, node.name) == 1) { "Custom endpoint ownership changed" }
            }
        } else {
            check(!isCustomEndpointKeyReserved(node.chainId, node.url)) { "Catalog key is reserved by user ownership" }
        }
        insertNodeProjection(node)
    }

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertNodeProjection(node: ChainNodeLocal)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertCustomEndpoint(record: CustomEndpointLocal)

    @OptIn(ExperimentalCoroutinesApi::class)
    open fun nodesFlow(chainId: String): Flow<List<ChainNodeLocal>> =
        combine(runtimeNodeInvalidationFlow(chainId), endpointMutationLane.runtimeChanges) { _, _ -> Unit }
            .mapLatest { readRuntimeNodes(chainId) }

    @Query("SELECT COUNT(*) FROM chain_nodes WHERE chainId = :chainId")
    protected abstract fun runtimeNodeInvalidationFlow(chainId: String): Flow<Long>

    @Query("SELECT COUNT(*) FROM chain_nodes")
    protected abstract fun runtimeNodeInvalidationFlow(): Flow<Long>

    @Transaction
    protected open suspend fun readRuntimeNodes(chainId: String): List<ChainNodeLocal> {
        return BoundedRuntimeNodes.read(chainId, { runtimeNodeMetadata(chainId, it) }) { rowId, field, offset, count ->
            when (field) {
                BoundedRuntimeNodes.Field.URL -> runtimeNodeUrlChunk(chainId, rowId, offset, count)
                BoundedRuntimeNodes.Field.NAME -> runtimeNodeNameChunk(chainId, rowId, offset, count)
            }
        }.nodes
    }

    @Query("SELECT rowid AS rowId, " +
        "CASE WHEN typeof(chainId) = 'text' THEN length(CAST(chainId AS BLOB)) END AS chainBytes, " +
        "CASE WHEN typeof(url) = 'text' THEN length(CAST(url AS BLOB)) END AS urlBytes, " +
        "CASE WHEN typeof(name) = 'text' THEN length(CAST(name AS BLOB)) END AS nameBytes, " +
        "CASE WHEN typeof(isActive) = 'integer' AND isActive IN (0,1) THEN isActive END AS active, " +
        "CASE WHEN typeof(isDefault) = 'integer' AND isDefault IN (0,1) THEN isDefault END AS `default`, " +
        "CASE WHEN typeof(url) = 'text' AND length(CAST(url AS BLOB)) <= 4096 THEN CAST(url AS BLOB) END AS urlInline, " +
        "CASE WHEN typeof(name) = 'text' AND length(CAST(name AS BLOB)) <= 4096 THEN CAST(name AS BLOB) END AS nameInline " +
        "FROM chain_nodes WHERE chainId = :chainId AND (:afterRowId IS NULL OR rowid > :afterRowId) " +
        "ORDER BY rowid LIMIT 64")
    protected abstract suspend fun runtimeNodeMetadata(chainId: String, afterRowId: Long?): List<RuntimeNodeMetadata>

    @Query("SELECT substr(CAST(url AS BLOB), :offset, :count) FROM chain_nodes WHERE chainId = :chainId AND rowid = :rowId")
    protected abstract suspend fun runtimeNodeUrlChunk(chainId: String, rowId: Long, offset: Int, count: Int): ByteArray?

    @Query("SELECT substr(CAST(name AS BLOB), :offset, :count) FROM chain_nodes WHERE chainId = :chainId AND rowid = :rowId")
    protected abstract suspend fun runtimeNodeNameChunk(chainId: String, rowId: Long, offset: Int, count: Int): ByteArray?

    open suspend fun selectNode(chainId: String, nodeUrl: String) {
        val targets = listOf(chainId)
        selectNode(endpointMutationLane.admit(targets), chainId, nodeUrl)
    }

    /** Explicit intent is captured before asynchronous validation or queueing. */
    suspend fun selectNode(operation: EndpointMutationLane.Operation, chainId: String, nodeUrl: String) =
        endpointMutationLane.withOperation(operation) { selectNodeTransaction(operation, chainId, nodeUrl) }

    @Transaction
    protected open suspend fun selectNodeTransaction(operation: EndpointMutationLane.Operation, chainId: String, nodeUrl: String) {
        endpointMutationLane.requireOwnedTargets(operation, listOf(chainId))
        selectNodeInside(chainId, nodeUrl)
        endpointMutationLane.grantFinal(operation) {}
    }

    private suspend fun selectNodeInside(chainId: String, nodeUrl: String) {
        // Existence is scalar: neither endpoint label nor URL is read through a CursorWindow.
        check(nodeRowId(chainId, nodeUrl) != null) { "Node is no longer available" }
        clearNodeSelection(chainId)
        makeNodeSelected(chainId, nodeUrl)
    }

    @Query("UPDATE chain_nodes SET isActive = 0 WHERE chainId = :chainId AND isActive = 1")
    protected abstract suspend fun clearNodeSelection(chainId: String)

    @Query("UPDATE chain_nodes SET isActive = 1 WHERE chainId = :chainId AND url = :nodeUrl")
    protected abstract suspend fun makeNodeSelected(chainId: String, nodeUrl: String)

    /**
     * Automatic failover for an unreachable catalog node. Admit it with [EndpointMutationLane.admitObserved]
     * from the settled runtime read that configured the failing transport, so it retires no pending intent
     * and any explicit or catalog intent since then wins. It moves only a selected catalog row whose URL is
     * [unreachableUrl], to the next catalog row by rowid (wrapping to the first), and only while [isAllowed]
     * still holds inside the writer. A custom selection is the user's own and is never replaced. Returns
     * whether the selection moved.
     */
    suspend fun selectNextCatalogNode(operation: EndpointMutationLane.Operation, chainId: String,
        unreachableUrl: String, isAllowed: () -> Boolean): Boolean =
        endpointMutationLane.withOperation(operation) {
            selectNextCatalogNodeTransaction(operation, chainId, unreachableUrl, isAllowed)
        }

    @Transaction
    protected open suspend fun selectNextCatalogNodeTransaction(operation: EndpointMutationLane.Operation, chainId: String,
        unreachableUrl: String, isAllowed: () -> Boolean): Boolean {
        endpointMutationLane.requireOwnedTargets(operation, listOf(chainId))
        // Only scalar row IDs cross the cursor; the failing URL is compared in place.
        val selected = selectedNodeRowId(chainId)
        val next = selected?.takeIf { it == nodeRowId(chainId, unreachableUrl) && isCatalogNodeRowId(chainId, it) }
            ?.let { nextCatalogNodeRowId(chainId, it) ?: firstDefaultNodeRowId(chainId) }
            ?.takeIf { it != selected && isAllowed() }
        if (next != null) setSelectedNodeRowId(chainId, next)
        endpointMutationLane.grantFinal(operation) {}
        return next != null
    }

    @Query("SELECT EXISTS (SELECT 1 FROM chain_nodes WHERE chainId = :chainId AND rowid = :id AND isDefault = 1)")
    protected abstract suspend fun isCatalogNodeRowId(chainId: String, id: Long): Boolean

    @Query("SELECT rowid FROM chain_nodes WHERE chainId = :chainId AND isDefault = 1 AND rowid > :afterRowId ORDER BY rowid LIMIT 1")
    protected abstract suspend fun nextCatalogNodeRowId(chainId: String, afterRowId: Long): Long?

    /** Explicit deletion preserves a revisioned tombstone, including when the parent is absent. */
    open suspend fun deleteNode(chainId: String, nodeUrl: String) {
        val targets = listOf(chainId)
        deleteNode(endpointMutationLane.admit(targets), chainId, nodeUrl)
    }

    /** Explicit intent is captured before asynchronous validation or queueing. */
    suspend fun deleteNode(operation: EndpointMutationLane.Operation, chainId: String, nodeUrl: String) =
        endpointMutationLane.withOperation(operation) { deleteNodeTransaction(operation, chainId, nodeUrl) }

    @Transaction
    protected open suspend fun deleteNodeTransaction(operation: EndpointMutationLane.Operation, chainId: String, nodeUrl: String) {
        endpointMutationLane.requireOwnedTargets(operation, listOf(chainId))
        deleteNodeInside(chainId, nodeUrl)
        endpointMutationLane.grantFinal(operation) {}
    }

    private suspend fun deleteNodeInside(chainId: String, nodeUrl: String) {
        val id = customEndpointId(chainId, nodeUrl)
        if (id != null && !isDeletedCustomEndpoint(id)) {
            val revision = customEndpointRevision(id)
            val next = nextCustomEndpointRevision(revision)
            check(tombstoneCustomEndpoint(id, revision, next) == 1) { "Custom endpoint ownership changed" }
        }
        deleteNodeProjection(chainId, nodeUrl)
    }

    @Query("DELETE FROM chain_nodes WHERE chainId = :chainId AND url = :nodeUrl")
    protected abstract suspend fun deleteNodeProjection(chainId: String, nodeUrl: String)

    @Query("SELECT * FROM chain_nodes WHERE chainId = :chainId AND url = :nodeUrl")
    abstract suspend fun getNode(chainId: String, nodeUrl: String): ChainNodeLocal

    /** Preserve active-URL, default and missing-row edit contracts while updating both stores. */
    open suspend fun updateNode(chainId: String, prevNodeUrl: String, nodeName: String, nodeUrl: String): Int {
        val targets = listOf(chainId)
        return updateNode(endpointMutationLane.admit(targets), chainId, prevNodeUrl, nodeName, nodeUrl)
    }

    /** Explicit intent is captured before asynchronous validation or queueing. */
    suspend fun updateNode(operation: EndpointMutationLane.Operation, chainId: String, prevNodeUrl: String, nodeName: String, nodeUrl: String): Int =
        endpointMutationLane.withOperation(operation) { updateNodeTransaction(operation, chainId, prevNodeUrl, nodeName, nodeUrl) }

    @Transaction
    protected open suspend fun updateNodeTransaction(operation: EndpointMutationLane.Operation, chainId: String, prevNodeUrl: String, nodeName: String, nodeUrl: String): Int {
        endpointMutationLane.requireOwnedTargets(operation, listOf(chainId))
        val result = updateNodeInside(chainId, prevNodeUrl, nodeName, nodeUrl)
        endpointMutationLane.grantFinal(operation) {}
        return result
    }

    private suspend fun updateNodeInside(chainId: String, prevNodeUrl: String, nodeName: String, nodeUrl: String): Int {
        if (!isEditableNode(chainId, prevNodeUrl, nodeUrl)) return 0
        EndpointProjectionLimits.requireTextFits(chainId, nodeUrl, nodeName)
        val id = checkNotNull(customEndpointId(chainId, prevNodeUrl)) { "Custom endpoint ownership is missing" }
        val revision = customEndpointRevision(id)
        val next = nextCustomEndpointRevision(revision)
        check(updateNodeProjection(chainId, prevNodeUrl, nodeName, nodeUrl) == 1) { "Node is no longer available" }
        check(updateCustomEndpoint(id, revision, next, nodeUrl, nodeName) == 1) { "Custom endpoint ownership changed" }
        return 1
    }

    @Query("SELECT EXISTS (SELECT 1 FROM chain_nodes WHERE chainId = :chainId AND url = :prevNodeUrl AND isDefault = 0 AND (isActive = 0 OR url = :nodeUrl))")
    protected abstract suspend fun isEditableNode(chainId: String, prevNodeUrl: String, nodeUrl: String): Boolean

    @Query("UPDATE chain_nodes SET name = :nodeName, url = :nodeUrl WHERE chainId = :chainId AND url = :prevNodeUrl AND isDefault = 0 AND (isActive = 0 OR url = :nodeUrl)")
    protected abstract suspend fun updateNodeProjection(chainId: String, prevNodeUrl: String, nodeName: String, nodeUrl: String): Int

    @Query("SELECT id FROM custom_endpoint_records WHERE chainId = :chainId AND url = :url")
    protected abstract suspend fun customEndpointId(chainId: String, url: String): Long?

    @Query("SELECT CASE WHEN typeof(revision) = 'integer' THEN revision ELSE 0 END FROM custom_endpoint_records WHERE id = :id")
    protected abstract suspend fun customEndpointRevision(id: Long): Long

    @Query("SELECT EXISTS (SELECT 1 FROM custom_endpoint_records WHERE id = :id AND deleted = 1 AND attached = 0)")
    protected abstract suspend fun isDeletedCustomEndpoint(id: Long): Boolean

    @Query("SELECT EXISTS (SELECT 1 FROM custom_endpoint_records WHERE chainId = :chainId AND url = :url)")
    protected abstract suspend fun isCustomEndpointKeyReserved(chainId: String, url: String): Boolean

    @Query("SELECT EXISTS (SELECT 1 FROM chain_nodes WHERE chainId = :chainId AND url = :url AND isDefault = 0)")
    protected abstract suspend fun isCustomProjection(chainId: String, url: String): Boolean

    @Query("UPDATE custom_endpoint_records SET revision = :nextRevision, name = :name, attached = 1, deleted = 0 WHERE id = :id AND revision = :revision AND deleted = 1 AND attached = 0")
    protected abstract suspend fun reviveCustomEndpoint(id: Long, revision: Long, nextRevision: Long, name: String): Int

    @Query("UPDATE custom_endpoint_records SET revision = :nextRevision, url = :url, name = :name WHERE id = :id AND revision = :revision AND attached = 1 AND deleted = 0")
    protected abstract suspend fun updateCustomEndpoint(id: Long, revision: Long, nextRevision: Long, url: String, name: String): Int

    @Query("UPDATE custom_endpoint_records SET revision = :nextRevision, attached = 0, deleted = 1 WHERE id = :id AND revision = :revision AND deleted = 0")
    protected abstract suspend fun tombstoneCustomEndpoint(id: Long, revision: Long, nextRevision: Long): Int

    @Query("SELECT EXISTS (SELECT 1 FROM custom_endpoint_records WHERE chainId IN (:chainIds) AND attached = 1 AND deleted = 0 AND (typeof(revision) != 'integer' OR revision <= 0 OR revision >= 9223372036854775807))")
    protected abstract suspend fun hasUnincrementableAttachedRevision(chainIds: List<String>): Boolean

    @Query("UPDATE custom_endpoint_records SET attached = 0, revision = revision + 1 WHERE chainId IN (:chainIds) AND attached = 1 AND deleted = 0")
    protected abstract suspend fun detachCustomEndpoints(chainIds: List<String>)

    @Query("SELECT rowid FROM chain_nodes WHERE chainId = :chainId AND isActive = 1 ORDER BY isDefault, rowid LIMIT 1")
    protected abstract suspend fun selectedNodeRowId(chainId: String): Long?

    @Query("SELECT rowid FROM chain_nodes WHERE chainId = :chainId AND url = :url")
    protected abstract suspend fun nodeRowId(chainId: String, url: String): Long?

    @Query("SELECT EXISTS (SELECT 1 FROM chain_nodes WHERE chainId = :chainId AND rowid = :id)")
    protected abstract suspend fun nodeRowIdExists(chainId: String, id: Long): Boolean

    @Query("SELECT rowid FROM chain_nodes WHERE chainId = :chainId AND isDefault = 1 ORDER BY rowid LIMIT 1")
    protected abstract suspend fun firstDefaultNodeRowId(chainId: String): Long?

    @Query("UPDATE chain_nodes SET isActive = CASE WHEN rowid = :id THEN 1 ELSE 0 END WHERE chainId = :chainId")
    protected abstract suspend fun setSelectedNodeRowId(chainId: String, id: Long?)

    @Query("DELETE FROM chain_assets WHERE chainId IN (:chainIds)")
    protected abstract suspend fun deleteAssetsForChains(chainIds: List<String>)

    @Query("DELETE FROM chain_explorers WHERE chainId IN (:chainIds)")
    protected abstract suspend fun deleteExplorersForChains(chainIds: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertChainAssets(assets: List<ChainAssetLocal>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertChainAssetsIgnoringConflicts(assets: List<ChainAssetLocal>)

    @Update
    protected abstract suspend fun updateChainAssets(assets: List<ChainAssetLocal>)

    @Delete
    protected abstract suspend fun deleteChainAssets(assets: List<ChainAssetLocal>)

    @Delete
    protected abstract suspend fun deleteChainNodes(nodes: List<ChainNodeLocal>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertChainExplorers(explorers: List<ChainExplorerLocal>)

    @Update
    protected abstract suspend fun updateChainExplorers(explorers: List<ChainExplorerLocal>)

    @Delete
    protected abstract suspend fun deleteChainExplorers(explorers: List<ChainExplorerLocal>)

    // Scalar wakes retain Room's table invalidations even when row counts do not change.
    @Query("SELECT (SELECT COUNT(*) FROM chains) + (SELECT COUNT(*) FROM chain_assets) + " +
        "(SELECT COUNT(*) FROM chain_explorers)")
    protected abstract fun catalogInvalidationFlow(): Flow<Long>

    @Transaction
    open suspend fun getJoinChainInfo(): List<JoinedChainInfo> = readRuntimeCatalog(null)

    @Transaction
    open suspend fun getJoinChainInfo(chainId: String): JoinedChainInfo? = readRuntimeCatalog(chainId).singleOrNull()

    @OptIn(ExperimentalCoroutinesApi::class)
    open fun joinChainInfoFlow(): Flow<List<JoinedChainInfo>> =
        combine(catalogInvalidationFlow(), runtimeNodeInvalidationFlow(), endpointMutationLane.runtimeChanges) { _, _, _ -> Unit }
            .mapLatest { getJoinChainInfo() }

    private suspend fun <T> forEachRuntimePage(
        load: suspend (Long?) -> List<T>, rowId: (T) -> Long, consume: suspend (T) -> Unit
    ) {
        var after: Long? = null
        while (true) {
            currentCoroutineContext().ensureActive()
            val page = load(after)
            check(page.size <= 64) { "Runtime catalog page exceeded its bound" }
            if (page.isEmpty()) return
            for (row in page) {
                currentCoroutineContext().ensureActive()
                val next = rowId(row)
                check(after == null || next > checkNotNull(after)) { "Runtime catalog page order changed" }
                after = next
                consume(row)
            }
        }
    }

    private suspend fun readRuntimeCatalog(chainId: String?, includeNodes: Boolean = true): List<JoinedChainInfo> {
        val chains = mutableListOf<ChainLocal>()
        forEachRuntimePage({ runtimeChainPage(chainId, it) }, { it.sourceRowId }) { row ->
            chains += row.chain.copy(xcm = OptionalCatalogText.read(row.xcmBytes, row.xcmInline) { offset, count ->
                runtimeChainXcmChunk(row.sourceRowId, offset, count)
            })
            row.xcmInline = null
        }
        if (chains.isEmpty()) return emptyList()
        // Read children only for existing parents, once per snapshot in the caller's Room transaction. Repeating
        // both scans for every parent makes each cold catalog observer unnecessarily expensive.
        val included = chains.mapTo(mutableSetOf()) { it.id }
        val assets = readRuntimeAssets(chainId, included).groupBy { it.chainId }
        val explorers = readRuntimeExplorers(chainId, included).groupBy { it.chainId }
        return chains.map { chain ->
            currentCoroutineContext().ensureActive()
            JoinedChainInfo(chain, if (includeNodes) readRuntimeNodes(chain.id) else emptyList(),
                assets[chain.id].orEmpty(), explorers[chain.id].orEmpty())
        }
    }

    private suspend fun readRuntimeAssets(chainId: String?, includedChainIds: Set<String>? = null): List<ChainAssetLocal> {
        val result = mutableListOf<ChainAssetLocal>()
        forEachRuntimePage({ runtimeAssetPage(chainId, it, includedChainIds != null) }, { it.sourceRowId }) { row ->
            if (includedChainIds != null && row.asset.chainId !in includedChainIds) return@forEachRuntimePage
            val purchase = OptionalCatalogText.read(row.purchaseBytes, row.purchaseInline) { offset, count ->
                runtimePurchaseChunk(row.sourceRowId, offset, count)
            }
            row.purchaseInline = null
            val price = OptionalCatalogText.read(row.priceBytes, row.priceInline) { offset, count ->
                runtimePriceChunk(row.sourceRowId, offset, count)
            }
            row.priceInline = null
            result += row.asset.copy(purchaseProviders = purchase, priceProvider = price)
        }
        return result
    }

    private suspend fun readRuntimeExplorers(chainId: String?, includedChainIds: Set<String>): List<ChainExplorerLocal> {
        val result = mutableListOf<ChainExplorerLocal>()
        forEachRuntimePage({ runtimeExplorerPage(chainId, it) }, { it.sourceRowId }) { row ->
            if (row.explorer.chainId !in includedChainIds) return@forEachRuntimePage
            result += row.explorer.copy(types = OptionalCatalogText.read(row.typesBytes, row.typesInline) { offset, count ->
                runtimeExplorerTypesChunk(row.sourceRowId, offset, count)
            } ?: "[]")
            row.typesInline = null
        }
        return result
    }

    // An unsupported family never crosses CursorWindow as unbounded text. Empty is explicitly unsupported,
    // while the complete stored value remains available to preservation-aware catalog writes.
    @Query("""SELECT rowid AS sourceRowId, `id`, `paraId`, `parentId`, `rank`, `name`, `minSupportedVersion`, `icon`, `prefix`, `isEthereumBased`, `isTestNet`, `hasCrowdloans`, `supportStakingPool`, `isEthereumChain`, `isChainlinkProvider`, `supportNft`, `isUsesAppId`, `identityChain`, CASE WHEN typeof(`ecosystem`) = 'text' AND length(CAST(`ecosystem` AS BLOB)) <= 64 THEN `ecosystem` ELSE '' END AS ecosystem, `androidMinAppVersion`, `remoteAssetsSource`, `tonBridgeUrl`, NULL AS xcm, `staking_url`, `staking_type`, `history_url`, `history_type`, `crowdloans_url`, `crowdloans_type`, CASE WHEN typeof(`xcm`) = 'text' THEN length(CAST(`xcm` AS BLOB)) END AS xcmBytes, CASE WHEN typeof(`xcm`) = 'text' AND length(CAST(`xcm` AS BLOB)) <= 65536 THEN CAST(`xcm` AS BLOB) END AS xcmInline FROM chains WHERE (:chainId IS NULL OR `id` = :chainId) AND rowid >= COALESCE(:afterRowId, -9223372036854775808) AND (:afterRowId IS NULL OR rowid > :afterRowId) ORDER BY rowid LIMIT 64""")
    protected abstract suspend fun runtimeChainPage(chainId: String?, afterRowId: Long?): List<RuntimeChainCatalogRow>

    @Query("""SELECT rowid AS sourceRowId, `id`, `name`, `symbol`, `chainId`, `icon`, `priceId`, `staking`, `precision`, NULL AS purchaseProviders, `isUtility`, `type`, `currencyId`, `existentialDeposit`, `color`, `isNative`, NULL AS priceProvider, `coinbaseUrl`, CASE WHEN typeof(`purchaseProviders`) = 'text' THEN length(CAST(`purchaseProviders` AS BLOB)) END AS purchaseBytes, CASE WHEN typeof(`purchaseProviders`) = 'text' AND length(CAST(`purchaseProviders` AS BLOB)) <= 65536 THEN CAST(`purchaseProviders` AS BLOB) END AS purchaseInline, CASE WHEN typeof(`priceProvider`) = 'text' THEN length(CAST(`priceProvider` AS BLOB)) END AS priceBytes, CASE WHEN typeof(`priceProvider`) = 'text' AND length(CAST(`priceProvider` AS BLOB)) <= 65536 THEN CAST(`priceProvider` AS BLOB) END AS priceInline FROM chain_assets WHERE (:chainId IS NULL OR `chainId` = :chainId) AND (:catalogOnly = 0 OR EXISTS (SELECT 1 FROM chains WHERE chains.id = chain_assets.chainId)) AND rowid >= COALESCE(:afterRowId, -9223372036854775808) AND (:afterRowId IS NULL OR rowid > :afterRowId) ORDER BY rowid LIMIT 64""")
    protected abstract suspend fun runtimeAssetPage(chainId: String?, afterRowId: Long?, catalogOnly: Boolean): List<RuntimeAssetCatalogRow>

    @Query("""SELECT rowid AS sourceRowId, `chainId`, `type`, '[]' AS types, `url`, CASE WHEN typeof(`types`) = 'text' THEN length(CAST(`types` AS BLOB)) END AS typesBytes, CASE WHEN typeof(`types`) = 'text' AND length(CAST(`types` AS BLOB)) <= 65536 THEN CAST(`types` AS BLOB) END AS typesInline FROM chain_explorers WHERE (:chainId IS NULL OR `chainId` = :chainId) AND EXISTS (SELECT 1 FROM chains WHERE chains.id = chain_explorers.chainId) AND rowid >= COALESCE(:afterRowId, -9223372036854775808) AND (:afterRowId IS NULL OR rowid > :afterRowId) ORDER BY rowid LIMIT 64""")
    protected abstract suspend fun runtimeExplorerPage(chainId: String?, afterRowId: Long?): List<RuntimeExplorerCatalogRow>

    @Query("SELECT substr(CAST(xcm AS BLOB), :offset, :count) FROM chains WHERE rowid = :rowId")
    protected abstract suspend fun runtimeChainXcmChunk(rowId: Long, offset: Int, count: Int): ByteArray?

    @Query("SELECT substr(CAST(purchaseProviders AS BLOB), :offset, :count) FROM chain_assets WHERE rowid = :rowId")
    protected abstract suspend fun runtimePurchaseChunk(rowId: Long, offset: Int, count: Int): ByteArray?

    @Query("SELECT substr(CAST(priceProvider AS BLOB), :offset, :count) FROM chain_assets WHERE rowid = :rowId")
    protected abstract suspend fun runtimePriceChunk(rowId: Long, offset: Int, count: Int): ByteArray?

    @Query("SELECT substr(CAST(types AS BLOB), :offset, :count) FROM chain_explorers WHERE rowid = :rowId")
    protected abstract suspend fun runtimeExplorerTypesChunk(rowId: Long, offset: Int, count: Int): ByteArray?

    @Query("SELECT * FROM chain_runtimes WHERE chainId = :chainId")
    abstract suspend fun runtimeInfo(chainId: String): ChainRuntimeInfoLocal?

    @Query("UPDATE chain_runtimes SET syncedVersion = :syncedVersion WHERE chainId = :chainId")
    abstract suspend fun updateSyncedRuntimeVersion(chainId: String, syncedVersion: Int)

    @Transaction
    open suspend fun updateRemoteRuntimeVersion(chainId: String, remoteVersion: Int) {
        if (isRuntimeInfoExists(chainId)) {
            updateRemoteRuntimeVersionUnsafe(chainId, remoteVersion)
        } else {
            insertRuntimeInfo(
                ChainRuntimeInfoLocal(
                    chainId,
                    syncedVersion = 0,
                    remoteVersion = remoteVersion
                )
            )
        }
    }

    @Query("UPDATE chain_runtimes SET remoteVersion = :remoteVersion WHERE chainId = :chainId")
    protected abstract suspend fun updateRemoteRuntimeVersionUnsafe(
        chainId: String,
        remoteVersion: Int
    )

    @Query("SELECT EXISTS (SELECT * FROM chain_runtimes WHERE chainId = :chainId)")
    protected abstract suspend fun isRuntimeInfoExists(chainId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertRuntimeInfo(runtimeInfoLocal: ChainRuntimeInfoLocal)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertTypes(types: List<ChainTypesLocal>)

    @Query("SELECT typesConfig FROM chain_types WHERE chainId = :chainId")
    abstract suspend fun getTypes(chainId: String): String?

    @Transaction
    open suspend fun getAssetsConfigs(): List<ChainAssetLocal> = readRuntimeAssets(null)

    /**
     * The networks the Send network field offers for an asset: those where the wallet holds the
     * same token and the portfolio shows it with a balance, that is an explicit Shown row or an Auto
     * (never toggled) row that holds funds. Sync stores every new row as Auto, so requiring an
     * explicit Shown left the list empty for new wallets and for anything received after the
     * upgrade. Explicitly hidden rows stay out. Balances are decimal text: NULL while loading,
     * "-1" on a load error.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    open fun observeChainsWithBalance(
        accountMetaId: Long,
        assetId: String
    ): Flow<Map<JoinedChainInfo, AssetWithToken>> {
        return observeAssetSymbolById(assetId).flatMapLatest { symbol ->
            if (symbol != null) {
                combine(
                    catalogInvalidationFlow(), walletBalanceInvalidationFlow(),
                    runtimeNodeInvalidationFlow(), endpointMutationLane.runtimeChanges
                ) { _, _, _, _ -> Unit }.mapLatest {
                    readFundedRuntimeCatalog(accountMetaId, symbol.removedXcPrefix())
                }
            } else {
                flowOf(emptyMap())
            }
        }
    }

    @Query("SELECT (SELECT COUNT(*) FROM assets) + (SELECT COUNT(*) FROM token_price)")
    protected abstract fun walletBalanceInvalidationFlow(): Flow<Long>

    @Transaction
    protected open suspend fun readFundedRuntimeCatalog(accountMetaId: Long, assetSymbol: String): Map<JoinedChainInfo, AssetWithToken> {
        val result = linkedMapOf<JoinedChainInfo, AssetWithToken>()
        for (row in fundedCatalogAssets(accountMetaId, assetSymbol)) {
            currentCoroutineContext().ensureActive()
            getJoinChainInfo(row.eligibleChainId)?.let { result[it] = row.balance }
        }
        return result
    }

    @Query(
        """
            SELECT symbol FROM chain_assets WHERE chain_assets.id = :assetId
        """
    )
    protected abstract fun observeAssetSymbolById(assetId: String): Flow<String?>

    @Transaction
    @Query(
        """
            SELECT c.id AS eligibleChainId, a.*, tp.* FROM chains c
            JOIN chain_assets ca ON ca.chainId = c.id AND ca.id = (
                SELECT ca_inner.id FROM chain_assets ca_inner
                LEFT JOIN assets a_inner ON
                    a_inner.chainId = c.id AND
                    a_inner.id = ca_inner.id AND
                    a_inner.metaId = :accountMetaId AND
                    (a_inner.enabled = 1 OR (a_inner.enabled IS NULL AND (
                        (a_inner.freeInPlanks IS NOT NULL AND a_inner.freeInPlanks NOT IN ('', '0') AND substr(a_inner.freeInPlanks, 1, 1) != '-') OR
                        (a_inner.reservedInPlanks IS NOT NULL AND a_inner.reservedInPlanks NOT IN ('', '0') AND substr(a_inner.reservedInPlanks, 1, 1) != '-'))))
                WHERE ca_inner.chainId = c.id
                AND ca_inner.symbol in (:assetSymbol, '$xcPrefix'||:assetSymbol)
                ORDER BY
                    CASE WHEN a_inner.id IS NOT NULL THEN 0 ELSE 1 END,
                    CASE WHEN ca_inner.symbol = :assetSymbol THEN 0 ELSE 1 END,
                    ca_inner.id
                LIMIT 1
            )
            JOIN assets a ON a.chainId = c.id AND a.id = ca.id AND a.metaId = :accountMetaId AND
                (a.enabled = 1 OR (a.enabled IS NULL AND (
                    (a.freeInPlanks IS NOT NULL AND a.freeInPlanks NOT IN ('', '0') AND substr(a.freeInPlanks, 1, 1) != '-') OR
                    (a.reservedInPlanks IS NOT NULL AND a.reservedInPlanks NOT IN ('', '0') AND substr(a.reservedInPlanks, 1, 1) != '-'))))
            LEFT JOIN token_price tp ON tp.priceId = a.tokenPriceId
        """
    )
    protected abstract suspend fun fundedCatalogAssets(
        accountMetaId: Long,
        assetSymbol: String
    ): List<FundedCatalogAssetRow>

}
