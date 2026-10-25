package jp.co.soramitsu.account.impl.presentation.recovery

import jp.co.soramitsu.coredb.recovery.EndpointRecordRef
import jp.co.soramitsu.coredb.recovery.decision.EndpointDecisionTargetOption

/** Ephemeral UI callbacks carry the rendered form identity or exact save snapshot. */
class SavedEndpointDecisionActions(
    val edit: (EndpointRecordRef) -> Unit = {},
    val retryLoad: (Any) -> Unit = {},
    val name: (Any, String) -> Unit = { _, _ -> },
    val url: (Any, String) -> Unit = { _, _ -> },
    val choose: (Any, EndpointDecisionTargetOption) -> Unit = { _, _ -> },
    val page: (Any, Int) -> Unit = { _, _ -> },
    val retryTargets: (Any) -> Unit = {},
    val reload: (Any) -> Unit = {},
    val save: (SavedEndpointDecisionForm) -> Unit = {},
    val attach: (SavedEndpointDecisionForm) -> Unit = {},
    val restore: (SavedEndpointDecisionForm) -> Unit = {},
    val select: (SavedEndpointDecisionForm) -> Unit = {},
    val refreshSelection: (SavedEndpointDecisionForm) -> Unit = {}
)
