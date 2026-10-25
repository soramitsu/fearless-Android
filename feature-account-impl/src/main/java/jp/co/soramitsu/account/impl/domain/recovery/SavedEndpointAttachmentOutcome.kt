package jp.co.soramitsu.account.impl.domain.recovery

import jp.co.soramitsu.common.data.network.endpoint.EndpointProbeOutcome
import jp.co.soramitsu.coredb.recovery.decision.EndpointAttachmentResult

/** Transient result for the current action only; never a reusable verification or selection grant. */
internal sealed class SavedEndpointAttachmentOutcome {
    class Stored(val result: EndpointAttachmentResult) : SavedEndpointAttachmentOutcome()
    class Rejected(val reason: EndpointProbeOutcome) : SavedEndpointAttachmentOutcome() {
        init { require(reason != EndpointProbeOutcome.MATCH) }
    }
}
