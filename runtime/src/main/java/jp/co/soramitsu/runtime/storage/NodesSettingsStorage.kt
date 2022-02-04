package jp.co.soramitsu.runtime.storage

import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

private const val MANUAL_SELECT_NODES = "MANUAL_SELECT_NODES"

class NodesSettingsStorage(private val preferences: Preferences) {
    fun getIsAutoSelectNodes(chainId: ChainId): Boolean {
        val nodesWithManualSelect = preferences.getStringSet(MANUAL_SELECT_NODES, emptySet())
        return !nodesWithManualSelect.contains(chainId)
    }

    fun setIsAutoSelectNodes(chainId: ChainId, isAuto: Boolean) {
        val nodesWithManualSelect = preferences.getStringSet(MANUAL_SELECT_NODES, emptySet()).toMutableSet()
        when {
            isAuto -> nodesWithManualSelect.remove(chainId)
            else -> nodesWithManualSelect.add(chainId)
        }
        preferences.putStringSet(MANUAL_SELECT_NODES, nodesWithManualSelect)
    }
}
