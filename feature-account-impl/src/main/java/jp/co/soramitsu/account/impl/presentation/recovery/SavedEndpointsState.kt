package jp.co.soramitsu.account.impl.presentation.recovery

import jp.co.soramitsu.coredb.recovery.LegacyEndpointPage
import jp.co.soramitsu.coredb.recovery.EndpointInventorySource
import jp.co.soramitsu.coredb.recovery.EndpointPage

/** Only an authenticated, resumed destination can hold a bounded archive page. */
data class SavedEndpointsState(
    val page: LegacyEndpointPage? = null,
    val loading: Boolean = false,
    val readFailed: Boolean = false,
    val locked: Boolean = true,
    val unlockFailed: Boolean = false,
    val inspection: SavedEndpointInspectionState? = null,
    val source: EndpointInventorySource = EndpointInventorySource.LEGACY,
    val inventoryPage: EndpointPage? = null,
    val inventoryInspection: SavedEndpointRecordInspectionState? = null,
    val decisionForm: SavedEndpointDecisionForm? = null
)
