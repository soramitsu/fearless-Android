package jp.co.soramitsu.account.impl.presentation.recovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.theme.fw.FwRecoveryButton
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import jp.co.soramitsu.common.compose.theme.fw.FwSurface
import jp.co.soramitsu.coredb.recovery.EndpointInventorySource
import jp.co.soramitsu.coredb.recovery.decision.EndpointDecisionTarget
import jp.co.soramitsu.feature_account_impl.R

/** Inline, scrollable editing preserves the existing recovery typography and touch sizes. */
internal fun LazyListScope.savedEndpointDecisionForm(
    form: SavedEndpointDecisionForm,
    actions: SavedEndpointDecisionActions,
    returnToList: () -> Unit
) {
    val editable = !form.loading && !form.loadFailed && !form.saving && !form.attaching && !form.attachmentRequiresRefresh &&
        form.selection?.phase != SavedEndpointSelectionState.Phase.SAVING
    item {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.saved_endpoint_choice_title), style = MaterialTheme.typography.h6,
                modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.saved_endpoints_record, form.ref.rowId.toString()))
            Text(stringResource(R.string.saved_endpoint_choice_intro))
            if (form.loading) {
                CircularProgressIndicator()
                Text(stringResource(R.string.saved_endpoints_loading))
            }
            if (form.loadFailed) {
                Text(stringResource(R.string.saved_endpoints_read_failed))
                DecisionAction(stringResource(R.string.common_retry), { actions.retryLoad(form.identity) })
            }
            // PIN ownership defines this lifetime; nested field state must not be serialized.
            CompositionLocalProvider(LocalSaveableStateRegistry provides null) {
                OutlinedTextField(form.name, { actions.name(form.identity, it) },
                    modifier = Modifier.fillMaxWidth().testTag("saved_endpoint_choice_name"),
                    label = { Text(stringResource(R.string.saved_endpoints_name)) },
                    enabled = editable, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false))
                OutlinedTextField(form.url, { actions.url(form.identity, it) },
                    modifier = Modifier.fillMaxWidth().testTag("saved_endpoint_choice_url"),
                    label = { Text(stringResource(R.string.saved_endpoints_url)) },
                    enabled = editable, singleLine = false, minLines = 2, maxLines = 5,
                    textStyle = MaterialTheme.typography.body1.copy(textDirection = TextDirection.Ltr),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false, keyboardType = KeyboardType.Uri))
            }
            if (form.inputRejected) Text(stringResource(R.string.saved_endpoint_choice_invalid))
            Text(stringResource(R.string.saved_endpoint_choice_target), style = MaterialTheme.typography.h6,
                modifier = Modifier.semantics { heading() })
            form.target?.let { target ->
                Text(stringResource(R.string.saved_endpoint_choice_selected, savedEndpointDisplayText(target.chainId)),
                    modifier = Modifier.testTag("saved_endpoint_choice_selected"),
                    style = MaterialTheme.typography.body1.copy(textDirection = TextDirection.Ltr))
                form.targetName?.let { Text(savedEndpointDisplayText(it)) }
                if (target.family == EndpointDecisionTarget.Family.UNSUPPORTED) {
                    Text(stringResource(R.string.saved_endpoint_choice_unsupported))
                }
            }
            DecisionAction(stringResource(if (form.choosingTarget) R.string.saved_endpoint_choice_refresh_targets
                    else R.string.common_choose_network),
                { actions.page(form.identity, 0) }, enabled = editable && !form.targetsLoading)
            if (form.targetsLoading) {
                CircularProgressIndicator()
                Text(stringResource(R.string.saved_endpoints_loading))
            }
            if (form.targetsFailed) {
                Text(stringResource(R.string.saved_endpoints_read_failed))
                DecisionAction(stringResource(R.string.common_retry),
                    { actions.retryTargets(form.identity) }, enabled = editable && !form.targetsLoading)
            }
            if (form.choosingTarget && form.targets?.options?.isEmpty() == true) Text(stringResource(R.string.saved_endpoint_choice_empty))
        }
    }
    items(if (form.choosingTarget) form.targets?.options.orEmpty() else emptyList(), key = { "target:${it.rowId}" }) { option ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Divider()
            Text(option.name?.let(::savedEndpointDisplayText)
                ?: stringResource(R.string.saved_endpoints_record, option.rowId.toString()))
            val optionTarget = option.target
            if (optionTarget == null) {
                Text(stringResource(R.string.saved_endpoint_choice_unavailable))
            } else {
                Text(savedEndpointDisplayText(optionTarget.chainId),
                    style = MaterialTheme.typography.body1.copy(textDirection = TextDirection.Ltr))
                DecisionAction(stringResource(R.string.saved_endpoint_choice_choose),
                    { actions.choose(form.identity, option) },
                    modifier = Modifier.testTag("saved_endpoint_choice_target_${option.rowId}"),
                    enabled = editable && !form.targetsLoading)
            }
        }
    }
    item {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (form.choosingTarget && form.targets != null) {
                DecisionAction(stringResource(R.string.saved_endpoints_previous),
                    { actions.page(form.identity, -1) },
                    enabled = editable && !form.targetsLoading && form.targets.previousBeforeRowId != null)
                DecisionAction(stringResource(R.string.saved_endpoints_next),
                    { actions.page(form.identity, 1) },
                    enabled = editable && !form.targetsLoading && form.targets.nextAfterRowId != null)
            }
            if (form.url.isEmpty() || form.target == null) Text(stringResource(R.string.saved_endpoint_choice_missing))
            if (form.saving) {
                CircularProgressIndicator()
                Text(stringResource(R.string.saved_endpoint_choice_saving))
            }
            if (form.saved && !form.attaching && form.attachmentStatus == null) Text(stringResource(R.string.saved_endpoint_choice_saved),
                modifier = Modifier.testTag("saved_endpoint_choice_saved"))
            if (form.saveFailed) {
                Text(stringResource(R.string.saved_endpoint_choice_failed))
                DecisionAction(stringResource(R.string.saved_endpoint_choice_reload),
                    { actions.reload(form.identity) }, enabled = editable)
            }
            DecisionAction(stringResource(R.string.saved_endpoint_choice_save), { actions.save(form) },
                modifier = Modifier.testTag("saved_endpoint_choice_save"),
                enabled = editable && !form.targetsLoading && form.url.isNotEmpty() && form.target != null)
            if (form.attachmentSourceChanged) {
                Text(stringResource(R.string.saved_endpoint_attachment_source_changed),
                    modifier = Modifier.testTag("saved_endpoint_attachment_source_changed"))
            }
            if (form.attaching) {
                CircularProgressIndicator()
                Text(stringResource(R.string.saved_endpoint_attachment_working))
            }
            form.attachmentStatus?.let { status ->
                Text(stringResource(attachmentMessage(status)), modifier = Modifier.testTag("saved_endpoint_attachment_status"))
            }
            val attachmentSucceeded = form.attachmentStatus in setOf(
                SavedEndpointAttachmentStatus.ADDED_INACTIVE, SavedEndpointAttachmentStatus.REATTACHED_INACTIVE,
                SavedEndpointAttachmentStatus.PRESENT_ACTIVE, SavedEndpointAttachmentStatus.PRESENT_INACTIVE
            )
            if (form.attachmentReady && !form.attachmentRequiresRefresh && !attachmentSucceeded &&
                form.target?.family in setOf(EndpointDecisionTarget.Family.SUBSTRATE, EndpointDecisionTarget.Family.SOLANA)) {
                Text(stringResource(R.string.saved_endpoint_attachment_intro))
                DecisionAction(stringResource(R.string.saved_endpoint_attachment_add),
                    { actions.attach(form) }, modifier = Modifier.testTag("saved_endpoint_attachment_action"),
                    enabled = editable && !form.targetsLoading)
                if (form.ref.source == EndpointInventorySource.OWNED) {
                    DecisionAction(stringResource(R.string.saved_endpoint_attachment_restore),
                        { actions.restore(form) }, modifier = Modifier.testTag("saved_endpoint_attachment_restore"),
                        enabled = editable && !form.targetsLoading)
                }
            }
            form.selection?.let { selection ->
                Text(stringResource(selectionMessage(selection.phase)), modifier = Modifier.testTag("saved_endpoint_selection_status"))
                selection.runtimeState?.let { runtime ->
                    Text(stringResource(selectionRuntimeMessage(runtime)), modifier = Modifier.testTag("saved_endpoint_runtime_status"))
                }
                if (selection.phase == SavedEndpointSelectionState.Phase.AVAILABLE) {
                    DecisionAction(stringResource(R.string.saved_endpoint_select), { actions.select(form) },
                        modifier = Modifier.testTag("saved_endpoint_select"), enabled = editable && !form.targetsLoading)
                }
                if (selection.phase in setOf(SavedEndpointSelectionState.Phase.STALE, SavedEndpointSelectionState.Phase.FAILED)) {
                    DecisionAction(stringResource(R.string.saved_endpoint_selection_refresh), { actions.refreshSelection(form) },
                        modifier = Modifier.testTag("saved_endpoint_selection_refresh"), enabled = editable && !form.targetsLoading)
                }
            }
            DecisionAction(stringResource(R.string.saved_endpoints_part_return), returnToList)
        }
    }
}

@Composable
private fun DecisionAction(text: String, action: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    if (enabled && FwRestyle.isEnabled(FwSurface.Recovery)) {
        FwRecoveryButton(text, action, modifier = modifier)
    } else {
        TextButton(action, enabled = enabled, modifier = modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(text)
        }
    }
}

private fun attachmentMessage(status: SavedEndpointAttachmentStatus): Int = when (status) {
    SavedEndpointAttachmentStatus.ADDED_INACTIVE -> R.string.saved_endpoint_attachment_added
    SavedEndpointAttachmentStatus.REATTACHED_INACTIVE -> R.string.saved_endpoint_attachment_restored
    SavedEndpointAttachmentStatus.PRESENT_ACTIVE -> R.string.saved_endpoint_attachment_present_active
    SavedEndpointAttachmentStatus.PRESENT_INACTIVE -> R.string.saved_endpoint_attachment_present_inactive
    SavedEndpointAttachmentStatus.MISMATCH -> R.string.saved_endpoint_attachment_mismatch
    SavedEndpointAttachmentStatus.INVALID_RESPONSE -> R.string.saved_endpoint_attachment_invalid
    SavedEndpointAttachmentStatus.UNREACHABLE -> R.string.saved_endpoint_attachment_unreachable
    SavedEndpointAttachmentStatus.TOO_LARGE -> R.string.saved_endpoint_attachment_large
    SavedEndpointAttachmentStatus.TIMED_OUT -> R.string.saved_endpoint_attachment_timeout
    SavedEndpointAttachmentStatus.BUSY -> R.string.saved_endpoint_attachment_busy
    SavedEndpointAttachmentStatus.FAILED -> R.string.saved_endpoint_attachment_failed
}

private fun selectionMessage(phase: SavedEndpointSelectionState.Phase): Int = when (phase) {
    SavedEndpointSelectionState.Phase.CHECKING -> R.string.saved_endpoint_selection_checking
    SavedEndpointSelectionState.Phase.AVAILABLE -> R.string.saved_endpoint_selection_available
    SavedEndpointSelectionState.Phase.NOT_ATTACHED -> R.string.saved_endpoint_selection_unattached
    SavedEndpointSelectionState.Phase.SAVING -> R.string.saved_endpoint_selection_saving
    SavedEndpointSelectionState.Phase.SAVED -> R.string.saved_endpoint_selection_saved
    SavedEndpointSelectionState.Phase.FAILED -> R.string.saved_endpoint_selection_failed
    SavedEndpointSelectionState.Phase.STALE -> R.string.saved_endpoint_selection_stale
}

private fun selectionRuntimeMessage(runtime: SavedEndpointSelectionState.Runtime): Int = when (runtime) {
    SavedEndpointSelectionState.Runtime.PREFERRED -> R.string.saved_endpoint_runtime_preferred
    SavedEndpointSelectionState.Runtime.PENDING -> R.string.saved_endpoint_runtime_pending
    SavedEndpointSelectionState.Runtime.NOT_IN_USE -> R.string.saved_endpoint_runtime_not_in_use
    SavedEndpointSelectionState.Runtime.UNAVAILABLE -> R.string.saved_endpoint_runtime_unavailable
    SavedEndpointSelectionState.Runtime.PAUSED -> R.string.saved_endpoint_runtime_paused
    SavedEndpointSelectionState.Runtime.CONNECTING -> R.string.saved_endpoint_runtime_connecting
    SavedEndpointSelectionState.Runtime.CONNECTED -> R.string.saved_endpoint_runtime_connected
}
