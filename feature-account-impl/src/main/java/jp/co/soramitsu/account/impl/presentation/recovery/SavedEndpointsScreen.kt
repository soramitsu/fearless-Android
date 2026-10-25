package jp.co.soramitsu.account.impl.presentation.recovery

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.theme.fw.FwRecoveryAppearance
import jp.co.soramitsu.common.compose.theme.fw.FwRecoveryButton
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import jp.co.soramitsu.common.compose.theme.fw.FwSurface
import jp.co.soramitsu.common.compose.theme.fw.LocalFwColors
import jp.co.soramitsu.coredb.recovery.EndpointInventorySource
import jp.co.soramitsu.coredb.recovery.EndpointRecordRef
import jp.co.soramitsu.coredb.recovery.EndpointField
import jp.co.soramitsu.coredb.recovery.LegacyEndpointChunkState
import jp.co.soramitsu.coredb.recovery.LegacyEndpointField
import jp.co.soramitsu.coredb.recovery.LegacyEndpointText
import jp.co.soramitsu.coredb.recovery.LegacyEndpointTextState
import jp.co.soramitsu.feature_account_impl.R

/** Offline saved records remain hidden until this destination has an active authentication grant. */
@Composable
fun SavedEndpointsScreen(
    state: SavedEndpointsState,
    back: () -> Unit,
    unlock: () -> Unit,
    refresh: () -> Unit,
    retry: () -> Unit,
    previous: () -> Unit,
    next: () -> Unit,
    inspect: (Long, LegacyEndpointField) -> Unit = { _, _ -> },
    retryInspection: () -> Unit = {},
    previousInspection: () -> Unit = {},
    nextInspection: () -> Unit = {},
    returnToList: () -> Unit = {},
    selectSource: (EndpointInventorySource) -> Unit = {},
    inspectRecord: (EndpointRecordRef, EndpointField) -> Unit = { _, _ -> },
    retryRecordInspection: () -> Unit = {},
    previousRecordInspection: () -> Unit = {},
    nextRecordInspection: () -> Unit = {},
    decisionActions: SavedEndpointDecisionActions = SavedEndpointDecisionActions()
) {
    val inspection = state.inspection
    BackHandler(enabled = !state.locked && (state.decisionForm != null || inspection != null || state.inventoryInspection != null), onBack = back)
    FwRecoveryAppearance {
        val background = if (FwRestyle.isEnabled(FwSurface.Recovery)) {
            Modifier.background(LocalFwColors.current.bgBase)
        } else Modifier
        LazyColumn(
            modifier = Modifier.fillMaxSize().then(background).testTag("saved_endpoints_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SavedEndpointAction(stringResource(R.string.wallet_recovery_back), back)
                Text(stringResource(R.string.saved_endpoints_title), style = MaterialTheme.typography.h5,
                    modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.saved_endpoints_intro), modifier = Modifier.padding(top = 16.dp))
                if (!state.locked) SavedEndpointSourcePicker(state.source, selectSource)
            }
            if (state.locked) {
                item {
                    Text(stringResource(R.string.saved_endpoints_locked))
                    if (state.unlockFailed) Text(stringResource(R.string.saved_endpoints_unlock_failed))
                    SavedEndpointAction(stringResource(R.string.saved_endpoints_unlock), unlock)
                }
            } else if (state.decisionForm != null) {
                savedEndpointDecisionForm(state.decisionForm, decisionActions, returnToList)
            } else if (state.inventoryInspection != null) {
                savedEndpointRecordInspection(state.inventoryInspection, retryRecordInspection,
                    previousRecordInspection, nextRecordInspection, returnToList)
            } else if (inspection != null) {
                item {
                    Text(stringResource(R.string.saved_endpoints_record, inspection.originalRowId.toString()),
                        style = MaterialTheme.typography.h6, modifier = Modifier.semantics { heading() })
                    Text(stringResource(if (inspection.field == LegacyEndpointField.NAME) {
                        R.string.saved_endpoints_name
                    } else R.string.saved_endpoints_url), style = MaterialTheme.typography.h6,
                        modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.saved_endpoints_escaped))
                    Text(stringResource(R.string.saved_endpoints_part_escaped))
                    if (inspection.loading) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.saved_endpoints_loading))
                    }
                    if (inspection.readFailed) {
                        Text(stringResource(R.string.saved_endpoints_read_failed))
                        SavedEndpointAction(stringResource(R.string.common_retry), retryInspection, !inspection.loading)
                    }
                    val chunk = inspection.chunk
                    if (chunk != null) {
                        when (chunk.state) {
                            LegacyEndpointChunkState.AVAILABLE -> {
                                val bytes = checkNotNull(chunk.bytes)
                                Text(if (bytes.isEmpty()) {
                                    stringResource(R.string.saved_endpoints_part_empty,
                                        chunk.byteOffset.toString(), checkNotNull(chunk.totalByteLength).toString())
                                } else {
                                    stringResource(R.string.saved_endpoints_part_range,
                                        (chunk.byteOffset + 1).toString(), checkNotNull(chunk.endByteOffsetExclusive).toString(),
                                        checkNotNull(chunk.totalByteLength).toString())
                                })
                                Text(savedEndpointDisplayBytes(bytes), modifier = Modifier.testTag("saved_endpoint_chunk"),
                                    style = MaterialTheme.typography.body1.copy(textDirection = TextDirection.Ltr))
                            }
                            LegacyEndpointChunkState.ARCHIVE_ABSENT, LegacyEndpointChunkState.ROW_ABSENT ->
                                Text(stringResource(R.string.saved_endpoints_part_unavailable))
                            LegacyEndpointChunkState.NULL_VALUE -> Text(stringResource(R.string.saved_endpoints_not_saved))
                            LegacyEndpointChunkState.UNSUPPORTED_STORAGE -> Text(stringResource(R.string.saved_endpoints_unrecognized))
                        }
                    }
                    SavedEndpointAction(stringResource(R.string.saved_endpoints_part_previous), previousInspection,
                        !inspection.loading && chunk?.previousByteOffset != null)
                    SavedEndpointAction(stringResource(R.string.saved_endpoints_part_next), nextInspection,
                        !inspection.loading && chunk?.nextByteOffset != null)
                    SavedEndpointAction(stringResource(R.string.saved_endpoints_part_return), returnToList)
                }
            } else if (state.source != EndpointInventorySource.LEGACY) {
                savedEndpointInventoryPage(state, refresh, retry, previous, next, inspectRecord, decisionActions.edit)
            } else {
                item {
                    SavedEndpointAction(stringResource(R.string.saved_endpoints_refresh), refresh, !state.loading)
                    if (state.loading) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.saved_endpoints_loading))
                    }
                    if (state.readFailed) {
                        Text(stringResource(R.string.saved_endpoints_read_failed))
                        SavedEndpointAction(stringResource(R.string.common_retry), retry, !state.loading)
                    }
                }
                val page = state.page
                if (page != null) {
                    if (page.records.isEmpty()) {
                        item {
                            Text(stringResource(if (page.archivePresent) R.string.saved_endpoints_empty else R.string.saved_endpoints_absent))
                        }
                    } else {
                        item { Text(stringResource(R.string.saved_endpoints_escaped)) }
                        items(page.records, key = { it.originalRowId }) { record ->
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Divider()
                                Text(stringResource(R.string.saved_endpoints_record, record.originalRowId.toString()),
                                    style = MaterialTheme.typography.h6, modifier = Modifier.semantics { heading() })
                                SavedEndpointField(stringResource(R.string.saved_endpoints_name), record.name,
                                    stringResource(R.string.saved_endpoints_inspect_name), !state.loading) {
                                    inspect(record.originalRowId, LegacyEndpointField.NAME)
                                }
                                SavedEndpointField(stringResource(R.string.saved_endpoints_url), record.endpoint,
                                    stringResource(R.string.saved_endpoints_inspect_url), !state.loading) {
                                    inspect(record.originalRowId, LegacyEndpointField.ENDPOINT)
                                }
                                Text(stringResource(R.string.saved_endpoints_network_code,
                                    record.networkType.value?.toString() ?: stringResource(R.string.saved_endpoints_unknown)))
                                val ref = EndpointRecordRef(EndpointInventorySource.LEGACY, record.originalRowId)
                                if (record.networkType.value == null) {
                                    SavedLegacyEndpointMetadata(EndpointField.NETWORK_TYPE, record.networkType, !state.loading) {
                                        inspectRecord(ref, EndpointField.NETWORK_TYPE)
                                    }
                                }
                                SavedLegacyEndpointMetadata(EndpointField.IS_DEFAULT, record.isDefault, !state.loading) {
                                    inspectRecord(ref, EndpointField.IS_DEFAULT)
                                }
                                SavedLegacyEndpointMetadata(EndpointField.IS_ACTIVE, record.isActive, !state.loading) {
                                    inspectRecord(ref, EndpointField.IS_ACTIVE)
                                }
                                SavedEndpointAction(stringResource(R.string.saved_endpoint_choice_edit),
                                    { decisionActions.edit(ref) }, !state.loading)
                            }
                        }
                    }
                    item {
                        SavedEndpointAction(stringResource(R.string.saved_endpoints_previous), previous,
                            !state.loading && page.previousBeforeRowId != null)
                        SavedEndpointAction(stringResource(R.string.saved_endpoints_next), next,
                            !state.loading && page.nextAfterRowId != null)
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedEndpointAction(text: String, action: () -> Unit, enabled: Boolean = true) {
    if (enabled && FwRestyle.isEnabled(FwSurface.Recovery)) {
        FwRecoveryButton(text, action)
    } else {
        TextButton(onClick = action, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(text)
        }
    }
}

@Composable
private fun SavedEndpointField(
    label: String,
    field: LegacyEndpointText,
    inspectLabel: String,
    enabled: Boolean,
    inspect: () -> Unit
) {
    Text(label, style = MaterialTheme.typography.subtitle1)
    val text = when (field.state) {
        LegacyEndpointTextState.AVAILABLE -> savedEndpointDisplayText(checkNotNull(field.value))
        LegacyEndpointTextState.NULL -> stringResource(R.string.saved_endpoints_not_saved)
        LegacyEndpointTextState.UNSUPPORTED_STORAGE -> stringResource(R.string.saved_endpoints_unrecognized)
        LegacyEndpointTextState.OVERSIZED -> stringResource(R.string.saved_endpoints_too_large)
        LegacyEndpointTextState.INVALID_UTF8 -> stringResource(R.string.saved_endpoints_invalid_text)
    }
    Text(text, style = MaterialTheme.typography.body1.copy(textDirection = TextDirection.Ltr))
    if (field.state != LegacyEndpointTextState.AVAILABLE && field.byteLength != null) {
        Text(stringResource(R.string.saved_endpoints_bytes, field.byteLength.toString()), style = MaterialTheme.typography.body2)
    }
    if (field.state != LegacyEndpointTextState.AVAILABLE && field.state != LegacyEndpointTextState.NULL) {
        SavedEndpointAction(inspectLabel, inspect, enabled)
    }
}
