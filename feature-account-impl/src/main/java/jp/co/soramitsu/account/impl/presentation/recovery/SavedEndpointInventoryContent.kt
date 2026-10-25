package jp.co.soramitsu.account.impl.presentation.recovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.theme.fw.FwRecoveryButton
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import jp.co.soramitsu.common.compose.theme.fw.FwSurface
import jp.co.soramitsu.coredb.recovery.EndpointChunkState
import jp.co.soramitsu.coredb.recovery.EndpointField
import jp.co.soramitsu.coredb.recovery.EndpointInventorySource
import jp.co.soramitsu.coredb.recovery.EndpointRecordRef
import jp.co.soramitsu.coredb.recovery.EndpointTextState
import jp.co.soramitsu.coredb.recovery.EndpointValue
import jp.co.soramitsu.coredb.recovery.LegacyEndpointInteger
import jp.co.soramitsu.coredb.recovery.LegacyEndpointStorage
import jp.co.soramitsu.feature_account_impl.R
import java.util.Locale

/** A collection choice never implies attachment, network verification or selection. */
@Composable
internal fun SavedEndpointSourcePicker(source: EndpointInventorySource, select: (EndpointInventorySource) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        EndpointInventoryAction(stringResource(R.string.saved_endpoint_collection, stringResource(endpointSourceLabel(source))),
            { expanded = true }, modifier = Modifier.testTag("saved_endpoint_collection"))
        DropdownMenu(expanded, { expanded = false }) {
            EndpointInventorySource.values().forEach { choice ->
                DropdownMenuItem(onClick = { expanded = false; select(choice) },
                    modifier = Modifier.testTag("saved_endpoint_source_${choice.name.lowercase(Locale.ROOT)}")) {
                    Text(stringResource(endpointSourceLabel(choice)))
                }
            }
        }
    }
}

internal fun LazyListScope.savedEndpointInventoryPage(
    state: SavedEndpointsState, refresh: () -> Unit, retry: () -> Unit,
    previous: () -> Unit, next: () -> Unit, inspect: (EndpointRecordRef, EndpointField) -> Unit,
    edit: (EndpointRecordRef) -> Unit = {}
) {
    item {
        EndpointInventoryAction(stringResource(R.string.saved_endpoints_refresh), refresh, enabled = !state.loading)
        if (state.loading) { CircularProgressIndicator(); Text(stringResource(R.string.saved_endpoints_loading)) }
        if (state.readFailed) {
            Text(stringResource(R.string.saved_endpoints_read_failed))
            EndpointInventoryAction(stringResource(R.string.common_retry), retry, enabled = !state.loading)
        }
    }
    val page = state.inventoryPage?.takeIf { it.source == state.source } ?: return
    if (page.records.isEmpty()) {
        item { Text(stringResource(if (page.present) R.string.saved_endpoints_empty else R.string.saved_endpoint_source_absent)) }
    } else {
        item {
            Text(stringResource(R.string.saved_endpoints_escaped))
            if (page.source != EndpointInventorySource.OWNED) Text(stringResource(R.string.saved_endpoint_historical_notice))
        }
        items(page.records, key = { "${it.ref.source}:${it.ref.rowId}:${it.ref.revision}" }) { record ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Divider()
                Text(stringResource(R.string.saved_endpoints_record, record.ref.rowId.toString()),
                    style = MaterialTheme.typography.h6, modifier = Modifier.semantics { heading() })
                record.ownership?.let { ownership ->
                    Text(stringResource(when {
                        ownership.deleted -> R.string.saved_endpoint_deleted
                        ownership.attached -> R.string.saved_endpoint_attached
                        else -> R.string.saved_endpoint_pending
                    }))
                }
                record.values.forEach { (field, value) ->
                    SavedInventoryField(field, value, !state.loading) { inspect(record.ref, field) }
                }
                EndpointInventoryAction(stringResource(R.string.saved_endpoint_choice_edit),
                    { edit(record.ref) }, enabled = !state.loading)
            }
        }
    }
    item {
        EndpointInventoryAction(stringResource(R.string.saved_endpoints_previous), previous,
            enabled = !state.loading && page.previousBeforeRowId != null)
        EndpointInventoryAction(stringResource(R.string.saved_endpoints_next), next,
            enabled = !state.loading && page.nextAfterRowId != null)
    }
}

internal fun LazyListScope.savedEndpointRecordInspection(
    inspection: SavedEndpointRecordInspectionState, retry: () -> Unit,
    previous: () -> Unit, next: () -> Unit, returnToList: () -> Unit
) {
    item {
        Text(stringResource(R.string.saved_endpoints_record, inspection.ref.rowId.toString()),
            style = MaterialTheme.typography.h6, modifier = Modifier.semantics { heading() })
        Text(stringResource(endpointFieldLabel(inspection.field)), style = MaterialTheme.typography.h6,
            modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.saved_endpoints_part_escaped))
        if (inspection.loading) { CircularProgressIndicator(); Text(stringResource(R.string.saved_endpoints_loading)) }
        if (inspection.readFailed) {
            Text(stringResource(R.string.saved_endpoints_read_failed))
            EndpointInventoryAction(stringResource(R.string.common_retry), retry, enabled = !inspection.loading)
        }
        val chunk = inspection.chunk
        if (chunk != null) {
            when (chunk.state) {
                EndpointChunkState.AVAILABLE -> {
                    val bytes = checkNotNull(chunk.bytes)
                    Text(if (bytes.isEmpty()) stringResource(R.string.saved_endpoints_part_empty,
                        chunk.byteOffset.toString(), checkNotNull(chunk.totalByteLength).toString())
                    else stringResource(R.string.saved_endpoints_part_range, (chunk.byteOffset + 1).toString(),
                        (chunk.byteOffset + bytes.size).toString(), checkNotNull(chunk.totalByteLength).toString()))
                    Text(savedEndpointDisplayBytes(bytes), modifier = Modifier.testTag("saved_endpoint_inventory_chunk"),
                        style = MaterialTheme.typography.body1.copy(textDirection = TextDirection.Ltr))
                }
                EndpointChunkState.NULL_VALUE -> Text(stringResource(R.string.saved_endpoints_not_saved))
                EndpointChunkState.NON_BYTE_STORAGE -> Text(chunk.integerValue?.toString() ?: checkNotNull(chunk.realValue).toString(),
                    style = MaterialTheme.typography.body1.copy(textDirection = TextDirection.Ltr))
                EndpointChunkState.STALE_REVISION -> Text(stringResource(R.string.saved_endpoint_changed))
                EndpointChunkState.SOURCE_ABSENT, EndpointChunkState.ROW_ABSENT -> Text(stringResource(R.string.saved_endpoints_part_unavailable))
            }
        }
        EndpointInventoryAction(stringResource(R.string.saved_endpoints_part_previous), previous,
            enabled = !inspection.loading && chunk?.previousByteOffset != null)
        EndpointInventoryAction(stringResource(R.string.saved_endpoints_part_next), next,
            enabled = !inspection.loading && chunk?.nextByteOffset != null)
        EndpointInventoryAction(stringResource(R.string.saved_endpoints_part_return), returnToList)
    }
}

/** Legacy scalar summaries keep their existing read contract; anomalous values use the exact generic inspector. */
@Composable
internal fun SavedLegacyEndpointMetadata(field: EndpointField, value: LegacyEndpointInteger, enabled: Boolean, inspect: () -> Unit) {
    Text(stringResource(endpointFieldLabel(field)), style = MaterialTheme.typography.subtitle1)
    Text(value.value?.toString() ?: stringResource(if (value.storage == LegacyEndpointStorage.NULL) {
        R.string.saved_endpoints_not_saved
    } else R.string.saved_endpoints_unrecognized))
    if (value.storage != LegacyEndpointStorage.INTEGER && value.storage != LegacyEndpointStorage.NULL) {
        EndpointInventoryAction(stringResource(R.string.saved_endpoint_inspect_field, stringResource(endpointFieldLabel(field))), inspect, enabled = enabled)
    }
}

@Composable
private fun SavedInventoryField(field: EndpointField, value: EndpointValue, enabled: Boolean, inspect: () -> Unit) {
    val label = stringResource(endpointFieldLabel(field))
    Text(label, style = MaterialTheme.typography.subtitle1)
    val rendered = when (value) {
        EndpointValue.Null -> stringResource(R.string.saved_endpoints_not_saved)
        is EndpointValue.Integer -> value.value.toString()
        is EndpointValue.Real -> value.value.toString()
        is EndpointValue.Blob -> stringResource(R.string.saved_endpoints_unrecognized)
        is EndpointValue.Text -> when (value.state) {
            EndpointTextState.AVAILABLE -> savedEndpointDisplayText(checkNotNull(value.value))
            EndpointTextState.OVERSIZED -> stringResource(R.string.saved_endpoints_too_large)
            EndpointTextState.INVALID_UTF8 -> stringResource(R.string.saved_endpoints_invalid_text)
        }
    }
    Text(rendered, style = MaterialTheme.typography.body1.copy(textDirection = TextDirection.Ltr))
    val length = when (value) {
        is EndpointValue.Text -> value.byteLength
        is EndpointValue.Blob -> value.byteLength
        else -> null
    }
    if (length != null && (value is EndpointValue.Blob || value is EndpointValue.Text && value.state != EndpointTextState.AVAILABLE)) {
        Text(stringResource(R.string.saved_endpoints_bytes, length.toString()), style = MaterialTheme.typography.body2)
        EndpointInventoryAction(stringResource(R.string.saved_endpoint_inspect_field, label), inspect, enabled = enabled)
    }
}

@Composable
private fun EndpointInventoryAction(text: String, action: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    if (enabled && FwRestyle.isEnabled(FwSurface.Recovery)) FwRecoveryButton(text, action, modifier)
    else TextButton(action, modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = enabled) { Text(text) }
}

internal fun endpointSourceLabel(source: EndpointInventorySource): Int = when (source) {
    EndpointInventorySource.LEGACY -> R.string.saved_endpoint_source_legacy
    EndpointInventorySource.HISTORICAL -> R.string.saved_endpoint_source_historical
    EndpointInventorySource.UPGRADE_ENTRY -> R.string.saved_endpoint_source_entry
    EndpointInventorySource.CURRENT -> R.string.saved_endpoint_source_current
    EndpointInventorySource.OWNED -> R.string.saved_endpoint_source_owned
}

internal fun endpointFieldLabel(field: EndpointField): Int = when (field) {
    EndpointField.NAME -> R.string.saved_endpoints_name
    EndpointField.ENDPOINT -> R.string.saved_endpoints_url
    EndpointField.CHAIN_REFERENCE -> R.string.saved_endpoint_chain_reference
    EndpointField.NETWORK_TYPE -> R.string.saved_endpoint_network_type
    EndpointField.IS_DEFAULT -> R.string.saved_endpoint_default_value
    EndpointField.IS_ACTIVE -> R.string.saved_endpoint_selection_value
}
