package jp.co.soramitsu.account.impl.presentation.recovery

import jp.co.soramitsu.coredb.recovery.EndpointField
import jp.co.soramitsu.coredb.recovery.EndpointInventorySource
import jp.co.soramitsu.feature_account_impl.R
import org.junit.Assert.assertEquals
import org.junit.Test

class EndpointInventoryLabelsTest {
    @Test fun everyCollectionHasItsOwnLabelAndOwnedRecordsAreIdentifiedAsCurrent() {
        val labels = EndpointInventorySource.values().map(::endpointSourceLabel)
        assertEquals(5, labels.toSet().size)
        assertEquals(R.string.saved_endpoint_source_owned, endpointSourceLabel(EndpointInventorySource.OWNED))
        assertEquals(R.string.saved_endpoint_source_current, endpointSourceLabel(EndpointInventorySource.CURRENT))
        assertEquals(R.string.saved_endpoint_source_historical, endpointSourceLabel(EndpointInventorySource.HISTORICAL))
        assertEquals(R.string.saved_endpoint_source_entry, endpointSourceLabel(EndpointInventorySource.UPGRADE_ENTRY))
        assertEquals(R.string.saved_endpoint_source_legacy, endpointSourceLabel(EndpointInventorySource.LEGACY))
    }

    @Test fun rawSelectionAndDefaultFieldsAreLabelledAsSavedValuesRatherThanNetworkActions() {
        assertEquals(6, EndpointField.values().map(::endpointFieldLabel).toSet().size)
        assertEquals(R.string.saved_endpoint_selection_value, endpointFieldLabel(EndpointField.IS_ACTIVE))
        assertEquals(R.string.saved_endpoint_default_value, endpointFieldLabel(EndpointField.IS_DEFAULT))
        assertEquals(R.string.saved_endpoint_network_type, endpointFieldLabel(EndpointField.NETWORK_TYPE))
        assertEquals(R.string.saved_endpoint_chain_reference, endpointFieldLabel(EndpointField.CHAIN_REFERENCE))
        assertEquals(R.string.saved_endpoints_name, endpointFieldLabel(EndpointField.NAME))
        assertEquals(R.string.saved_endpoints_url, endpointFieldLabel(EndpointField.ENDPOINT))
    }
}
