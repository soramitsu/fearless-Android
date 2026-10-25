package jp.co.soramitsu.wallet.impl.presentation.balance.chainselector

import jp.co.soramitsu.runtime.multiNetwork.tokengroups.ResolvedTokenGroupMember
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.ResolvedTokenGroups

/** There is no ticker/currency-id fallback. An ungrouped asset stays on its own network. */
object ExactNetworkAssetSwitch {
    fun candidates(chainId: String, assetId: String, groups: ResolvedTokenGroups, accountNetworks: Set<String>): List<ResolvedTokenGroupMember> {
        val group = groups.groupOf(chainId, assetId)
        return (group?.members ?: listOf(ResolvedTokenGroupMember(chainId, assetId)))
            .filter { it.chainId in accountNetworks }.distinct()
    }

    fun selected(candidates: List<ResolvedTokenGroupMember>, chainId: String): String? =
        candidates.filter { it.chainId == chainId }.singleOrNull()?.assetId
}
