package jp.co.soramitsu.runtime.multiNetwork.tokengroups

import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** Resolves the bundled curated groups against the chains the app currently knows. */
class TokenGroupRegistry(
    private val configSource: SharedPortfolioConfigSource,
    private val chainsRepository: ChainsRepository
) {

    fun resolve(chains: List<Chain>): ResolvedTokenGroups {
        val config = configSource.config
        return resolveTokenGroups(config.tokenGroups, chains, config::isTestnet)
    }

    fun observeResolvedGroups(): Flow<ResolvedTokenGroups> {
        return chainsRepository.chainsFlow()
            .map(::resolve)
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
    }
}

/**
 * A member is active only when the live registry has exactly one non-testnet chain with its
 * chainId, that chain has exactly one verified asset with its assetId (EVM contract ids compare
 * case-insensitively, every other id exactly), and the asset still has the reviewed symbol,
 * precision and priceId. Inactive members render as separate rows; an approved group needs at
 * least two active members to render as one combined row.
 */
fun resolveTokenGroups(
    catalog: TokenGroupCatalog?,
    chains: List<Chain>,
    isTestnet: (Chain) -> Boolean
): ResolvedTokenGroups {
    if (catalog == null) return ResolvedTokenGroups.EMPTY
    val chainsById = chains.groupBy(Chain::id)

    val groups = catalog.renderableGroups.mapNotNull { group ->
        val activeMembers = group.members.mapNotNull { member ->
            resolveMember(group, member, chainsById, isTestnet)
        }
        activeMembers.takeIf { it.size >= MIN_ACTIVE_MEMBERS }?.let {
            ResolvedTokenGroup(
                id = group.id,
                symbol = group.symbol,
                name = group.name,
                precision = group.members.first().precision,
                members = it
            )
        }
    }

    return ResolvedTokenGroups(groups)
}

private const val MIN_ACTIVE_MEMBERS = 2

private fun resolveMember(
    group: TokenGroupDefinition,
    member: TokenGroupMemberDefinition,
    chainsById: Map<ChainId, List<Chain>>,
    isTestnet: (Chain) -> Boolean
): ResolvedTokenGroupMember? {
    if (!member.isRegistryMember || !group.acceptsSymbol(member.symbol)) return null

    val chain = chainsById[member.chainId]?.singleOrNull() ?: return null
    if (isTestnet(chain)) return null

    val ignoreCase = chain.ecosystem == Ecosystem.Ethereum || chain.ecosystem == Ecosystem.EthereumBased
    val asset = chain.assets
        .filter { it.id.equals(member.assetId, ignoreCase = ignoreCase) }
        .singleOrNull()
        ?: return null

    val matchesReview = !asset.isDetectedAsset() &&
        asset.symbol.equals(member.symbol, ignoreCase = true) &&
        asset.precision == member.precision &&
        asset.priceId == group.priceId

    return ResolvedTokenGroupMember(chainId = chain.id, assetId = asset.id).takeIf { matchesReview }
}

data class ResolvedTokenGroups(val groups: List<ResolvedTokenGroup>) {

    private val groupsByMember: Map<ResolvedTokenGroupMember, ResolvedTokenGroup> =
        groups.flatMap { group -> group.members.map { it to group } }.toMap()

    private val groupsById: Map<String, ResolvedTokenGroup> = groups.associateBy(ResolvedTokenGroup::id)

    /** The combined row an asset belongs to, or null when it renders as its own row. */
    fun groupOf(chainId: ChainId, assetId: String): ResolvedTokenGroup? {
        return groupsByMember[ResolvedTokenGroupMember(chainId, assetId)]
    }

    fun members(groupId: String): List<ResolvedTokenGroupMember> = groupsById[groupId]?.members.orEmpty()

    companion object {
        val EMPTY = ResolvedTokenGroups(emptyList())
    }
}

data class ResolvedTokenGroup(
    val id: String,
    val symbol: String,
    val name: String,
    /** Every member shares this precision, so member totals can be summed. */
    val precision: Int,
    /** Active members in curated order. */
    val members: List<ResolvedTokenGroupMember>
)

/** Exactly the chain's own ids, so callers can look the asset up with `chain.assetsById`. */
data class ResolvedTokenGroupMember(
    val chainId: ChainId,
    val assetId: String
)
