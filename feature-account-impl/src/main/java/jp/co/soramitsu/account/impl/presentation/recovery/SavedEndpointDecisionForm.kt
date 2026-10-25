package jp.co.soramitsu.account.impl.presentation.recovery

import jp.co.soramitsu.coredb.recovery.EndpointRecordRef
import jp.co.soramitsu.coredb.recovery.decision.EndpointDecisionTarget
import jp.co.soramitsu.coredb.recovery.decision.EndpointDecisionTargetPage

/** Bounded, ephemeral editing state. Never serialize this into saved instance state. */
data class SavedEndpointDecisionForm(
    val identity: Any,
    val ref: EndpointRecordRef,
    val name: String = "",
    val url: String = "",
    val target: EndpointDecisionTarget? = null,
    val targetName: String? = null,
    val targets: EndpointDecisionTargetPage? = null,
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val choosingTarget: Boolean = true,
    val targetsLoading: Boolean = false,
    val targetsFailed: Boolean = false,
    val inputRejected: Boolean = false,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val saved: Boolean = false,
    val attachmentReady: Boolean = false,
    val attachmentSourceChanged: Boolean = false,
    val attaching: Boolean = false,
    val attachmentStatus: SavedEndpointAttachmentStatus? = null,
    val attachmentRequiresRefresh: Boolean = false,
    val selection: SavedEndpointSelectionState? = null
) {
    override fun toString(): String = "SavedEndpointDecisionForm(loading=$loading, saving=$saving, saved=$saved)"
}
