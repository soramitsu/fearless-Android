package jp.co.soramitsu.staking.api.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Synchronous selection intent, independent of delayed or conflated preference-flow emissions. */
class StakingSelectionRevision {
    class Snapshot internal constructor(internal val requested: StakingAssetSelection?)
    private var current = Snapshot(null)
    private var observesCatalog = false
    private var admitted: StakingAssetSelection? = null
    private val _changes = MutableStateFlow(current)
    /** Every intent invalidates presentation, even when selection values repeat. */
    val changes = _changes.asStateFlow()

    @Synchronized fun capture(): Snapshot = current

    /** Every explicit selection, including A→B→A, invalidates pending preparation. */
    @Synchronized fun changed(selection: StakingAssetSelection) {
        current = Snapshot(selection)
        _changes.value = current
    }

    /** A changed operational input revokes prior observations before any suspending repository read. */
    @Synchronized internal fun catalogChanged() {
        observesCatalog = true
        admitted = null
        current = Snapshot(current.requested)
        _changes.value = current
    }

    /** An observed result cannot grant authority for a different explicit intent. */
    @Synchronized internal fun catalogSelected(selection: StakingAssetSelection?) {
        admitted = selection
        current = Snapshot(current.requested)
        _changes.value = current
    }

    private fun admittedAction(type: StakingType, chainId: String, assetId: String): Boolean =
        !observesCatalog || admitted?.let {
            it.type == type && it.chainId == chainId && it.chainAssetId == assetId
        } == true

    /** Initial cached data is allowed; after explicit selection it must match the requested pool asset. */
    @Synchronized fun matches(snapshot: Snapshot, chainId: String, assetId: String): Boolean =
        current === snapshot && admittedAction(StakingType.POOL, chainId, assetId) && snapshot.requested.let {
            it == null || (it.type == StakingType.POOL && it.chainId == chainId && it.chainAssetId == assetId)
        }

    /** An action must use the requested scenario and exact asset, including before flows catch up. */
    @Synchronized fun matchesAction(snapshot: Snapshot, type: StakingType, chainId: String, assetId: String): Boolean =
        current === snapshot && admittedAction(type, chainId, assetId) && snapshot.requested.let {
            it == null || (it.type == type && it.chainId == chainId && it.chainAssetId == assetId)
        }

}
