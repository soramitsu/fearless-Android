package jp.co.soramitsu.runtime.multiNetwork.chain

import jp.co.soramitsu.common.resources.ContextManager
import jp.co.soramitsu.runtime.BuildConfig
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.model.AssetLocal
import jp.co.soramitsu.coredb.model.accountId
import jp.co.soramitsu.coredb.recovery.selection.EndpointCatalogSnapshot
import jp.co.soramitsu.runtime.ext.withBitcoinTestnetAccountChain
import jp.co.soramitsu.runtime.ext.isUniversalWalletBitcoin
import jp.co.soramitsu.runtime.ext.isUniversalWalletIroha
import jp.co.soramitsu.runtime.ext.isUniversalWalletSolana
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.ChainFetcher
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainRemote
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ChainSyncService(
    private val dao: ChainDao,
    private val chainFetcher: ChainFetcher,
    private val metaAccountDao: MetaAccountDao,
    private val assetsDao: AssetDao,
    private val contextManager: ContextManager,
    private val nodeUrlPolicy: RemoteNodeUrlPolicy = RemoteNodeUrlPolicy.forBuild(BuildConfig.DEBUG),
    private val logger: (String) -> Unit = {}
) : XcmDiscoverySnapshotProvider {

    private val xcmDiscoveryRefreshMutex = Mutex()
    @Volatile
    private var currentProcessXcmDiscoveryChains: List<Chain>? = null

    suspend fun syncUp() = xcmDiscoveryRefreshMutex.withLock {
        // Clear before every attempt. A failed refresh must never leave a
        // previously fetched or persisted route eligible for XCM discovery.
        currentProcessXcmDiscoveryChains = null
        val remoteChains = configChainsSyncUp()
        currentProcessXcmDiscoveryChains = remoteChains.toList()
    }

    override suspend fun getCurrentProcessXcmDiscoveryChains(): List<Chain> =
        currentProcessXcmDiscoveryChains?.toList().orEmpty()

    private suspend fun configChainsSyncUp(): List<Chain> {
        var fetchFailure: Exception? = null
        var unsupportedEcosystems: Map<String, String> = emptyMap()
        val malformedIds = mutableSetOf<String>()
        val fetchedChains = try {
            val fetched = chainFetcher.getChains()
            require(fetched.map { it.chainId }.toSet().size == fetched.size) { "Remote catalog parent identity is ambiguous" }
            val enabled = fetched.filter { !it.disabled }
            val (supported, unsupported) = enabled.partition { operationalEcosystemOrNull(it.ecosystem) != null }
            // Retain unambiguous unsupported observations even if another supported entry cannot map.
            // The offline fallback then preserves cached rows but still quarantines these exact IDs.
            unsupportedEcosystems = unsupported.associate { it.chainId to it.ecosystem }
            supported.mapNotNull { remote -> remote.toChainOrSkip(malformedIds) }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            fetchFailure = failure
            null
        }
        val accountChainIds = metaAccountDao.getJoinedMetaAccountsInfo()
            .flatMap { account -> account.chainAccounts.map { it.chainId } }
        val operation = dao.endpointMutationLane.admitCatalogReplacement()
        var remoteChains: List<Chain> = emptyList()
        var nativeIds: Set<String> = emptySet()
        var catalogSize = 0
        val assetsToAdd = dao.replaceCatalog(operation) { freshLocal ->
            // Both fallback data and removals come from this transaction's fresh snapshot.
            // Network fetch and account reads have already finished outside the writer lane.
            val preservedIds = freshLocal.map { it.chain.id } + accountChainIds + fetchedChains.orEmpty().map(Chain::id) +
                unsupportedEcosystems.keys + malformedIds
            val nativeChains = nativeWalletChains(preservedIds.toSet())
            nativeIds = nativeChains.map(Chain::id).toSet()
            // Cached operational metadata can omit oversized values or contain unknown cached enums.
            // Keep it out of the domain round trip and preserve its stored catalog rows as a unit.
            val preservedCatalog = freshLocal.filter {
                it.chain.id !in nativeIds &&
                    (fetchedChains == null || it.chain.id in unsupportedEcosystems || it.chain.id in malformedIds)
            }
            val preservedCatalogIds = preservedCatalog.map { it.chain.id }.toSet()
            remoteChains = (fetchedChains.orEmpty().filter { it.id !in nativeIds } + nativeChains)
                .withBitcoinTestnetAccountChain(preservedIds.toSet())
                .filter { it.id !in preservedCatalogIds }
            val unsupportedOverrides = unsupportedEcosystems.filterKeys { it in preservedCatalogIds }
            val catalog = preservedCatalog + remoteChains.map(::mapChainToChainLocal)
            catalogSize = catalog.size
            EndpointCatalogSnapshot(catalog, remoteChains.filter {
                it.remoteAssetsSource != null || it.isUniversalWalletSolana() || it.isUniversalWalletIroha()
            }.map(Chain::id).toSet(), preservedCatalogChains = preservedCatalogIds,
                unsupportedEcosystemOverrides = unsupportedOverrides)
        }
        val remoteMapping = remoteChains.associateBy(Chain::id)
        val newLocalAssets = metaAccountDao.getJoinedMetaAccountsInfo().flatMap { accountInfo ->
            val metaAccount = accountInfo.metaAccount
            assetsToAdd.mapNotNull { asset ->
                val chain = remoteMapping[asset.chainId] ?: return@mapNotNull null
                val chainAccount = accountInfo.chainAccounts.firstOrNull { it.chainId == chain.id }
                val native = chain.isUniversalWalletBitcoin() || chain.isUniversalWalletSolana() || chain.isUniversalWalletIroha()
                val accountId = chainAccount?.accountId ?: if (native) null else metaAccount.accountId(chain)
                if (accountId == null) return@mapNotNull null
                AssetLocal(accountId = accountId, id = asset.id, chainId = asset.chainId, metaId = metaAccount.id,
                    tokenPriceId = asset.priceId, enabled = null)
            }
        }
        // Preserve automatic visibility and existing wallet hide tombstones. Definition removal
        // never deletes a wallet-scoped balance/preference row by its non-unique asset identifier.
        assetsDao.insertAssets(newLocalAssets)
        logger("catalog replacement committed: $catalogSize")
        fetchFailure?.let { throw it }
        // Only a successful current-process fetch supplies XCM discovery data.
        return checkNotNull(fetchedChains).filter { it.id !in nativeIds }
    }

    // One malformed catalog entry (a missing name or address prefix, say) must not stop every chain from syncing:
    // it is skipped and logged, and the caller keeps its cached catalog row as it was.
    private fun ChainRemote.toChainOrSkip(malformedIds: MutableSet<String>): Chain? = try {
        withAdmittedNodes().toChain()
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: Exception) {
        // Gson leaves required fields null when the entry lacks them.
        val id: String? = chainId
        id?.let(malformedIds::add)
        logger("skipped malformed remote chain $id: ${failure.message}")
        null
    }

    // Nodes the build may not connect to are dropped. A chain left without any is malformed: its cached row stays.
    private fun ChainRemote.withAdmittedNodes(): ChainRemote {
        val listed = nodes ?: return this
        val ecosystem = requireNotNull(operationalEcosystemOrNull(ecosystem)) { "Unsupported chain ecosystem" }
        val admitted = listed.filter { nodeUrlPolicy.admits(it.url, ecosystem) }
        if (admitted.size == listed.size) return this
        logger("dropped ${listed.size - admitted.size} node(s) of remote chain $chainId: not an encrypted endpoint")
        require(admitted.isNotEmpty() || listed.isEmpty()) { "Remote chain $chainId has no encrypted node" }
        return copy(nodes = admitted)
    }
}
