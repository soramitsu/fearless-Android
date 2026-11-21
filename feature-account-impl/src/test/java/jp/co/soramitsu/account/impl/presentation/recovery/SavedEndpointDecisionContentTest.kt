package jp.co.soramitsu.account.impl.presentation.recovery

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.account.impl.testutil.string
import jp.co.soramitsu.account.impl.testutil.tap
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.core.runtime.EndpointRuntimeStatus
import jp.co.soramitsu.coredb.recovery.EndpointInventorySource
import jp.co.soramitsu.coredb.recovery.EndpointRecordRef
import jp.co.soramitsu.coredb.recovery.decision.EndpointDecisionTarget
import jp.co.soramitsu.coredb.recovery.decision.EndpointDecisionTargetOption
import jp.co.soramitsu.coredb.recovery.decision.EndpointDecisionTargetPage
import jp.co.soramitsu.feature_account_impl.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h5000dp")
class SavedEndpointDecisionContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val identity = Any()
    private val ownedRef = EndpointRecordRef(EndpointInventorySource.OWNED, 9, 3)
    private val calls = mutableListOf<String>()
    private val forms = mutableListOf<SavedEndpointDecisionForm>()
    private var chosen: EndpointDecisionTargetOption? = null
    private var form by mutableStateOf(SavedEndpointDecisionForm(identity = identity, ref = ownedRef))

    private val actions = SavedEndpointDecisionActions(
        retryLoad = { calls += "retryLoad:${it === identity}" },
        name = { id, value -> calls += "name:${id === identity}:$value" },
        url = { id, value -> calls += "url:${id === identity}:$value" },
        choose = { _, option -> calls += "choose:${option.rowId}"; chosen = option },
        page = { _, delta -> calls += "page:$delta" },
        retryTargets = { calls += "retryTargets" },
        reload = { calls += "reload" },
        save = { calls += "save"; forms += it },
        attach = { calls += "attach"; forms += it },
        restore = { calls += "restore"; forms += it },
        select = { calls += "select"; forms += it },
        refreshSelection = { calls += "refreshSelection"; forms += it }
    )

    private fun render(initial: SavedEndpointDecisionForm) {
        form = initial
        compose.setContent {
            FearlessAppTheme {
                SavedEndpointsScreen(
                    state = SavedEndpointsState(locked = false, decisionForm = form),
                    back = {}, unlock = {}, refresh = {}, retry = {}, previous = {}, next = {},
                    returnToList = { calls += "return" },
                    decisionActions = actions
                )
            }
        }
    }

    private fun target(family: EndpointDecisionTarget.Family, id: String = "polkadot-id") = mockk<EndpointDecisionTarget> {
        every { chainId } returns id
        every { this@mockk.family } returns family
    }

    private fun option(row: Long, label: String?, optionTarget: EndpointDecisionTarget?) = mockk<EndpointDecisionTargetOption> {
        every { rowId } returns row
        every { name } returns label
        every { target } returns optionTarget
    }

    private fun page(options: List<EndpointDecisionTargetOption>, previous: Long?, next: Long?) = mockk<EndpointDecisionTargetPage> {
        every { this@mockk.options } returns options
        every { previousBeforeRowId } returns previous
        every { nextAfterRowId } returns next
    }

    private fun ready(): SavedEndpointDecisionForm {
        val kusama = option(1, "Kusama", target(EndpointDecisionTarget.Family.SUBSTRATE, "kusama-id"))
        return SavedEndpointDecisionForm(
            identity = identity,
            ref = ownedRef,
            name = "Node",
            url = "wss://node.example",
            target = target(EndpointDecisionTarget.Family.SUBSTRATE),
            targetName = "Polkadot",
            targets = page(listOf(kusama, option(2, null, null)), previous = null, next = 2),
            loading = false,
            choosingTarget = true,
            inputRejected = true,
            saved = true,
            saveFailed = true,
            attachmentReady = true,
            attachmentSourceChanged = true,
            selection = SavedEndpointSelectionState(SavedEndpointSelectionState.Phase.AVAILABLE)
        )
    }

    @Test
    fun readyFormEditsChoosesSavesAttachesAndSelects() {
        render(ready())

        compose.onNodeWithText(string(R.string.saved_endpoint_choice_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.saved_endpoints_record, "9")).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.saved_endpoint_choice_invalid)).assertIsDisplayed()
        compose.onNodeWithTag("saved_endpoint_choice_selected")
            .assertTextEquals(string(R.string.saved_endpoint_choice_selected, savedEndpointDisplayText("polkadot-id")))
        compose.onNodeWithText(savedEndpointDisplayText("Polkadot")).assertIsDisplayed()
        compose.onNodeWithText(savedEndpointDisplayText("Kusama")).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.saved_endpoints_record, "2")).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.saved_endpoint_choice_unavailable)).assertIsDisplayed()
        compose.onNodeWithTag("saved_endpoint_choice_saved").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.saved_endpoint_choice_failed)).assertIsDisplayed()
        compose.onNodeWithTag("saved_endpoint_attachment_source_changed").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.saved_endpoint_attachment_intro)).assertIsDisplayed()
        compose.onNodeWithTag("saved_endpoint_selection_status")
            .assertTextEquals(string(R.string.saved_endpoint_selection_available))
        compose.onNodeWithText(string(R.string.saved_endpoint_choice_missing)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.saved_endpoints_previous)).assertIsNotEnabled()

        compose.onNodeWithTag("saved_endpoint_choice_name").performTextReplacement("Renamed")
        compose.onNodeWithTag("saved_endpoint_choice_url").performTextReplacement("wss://changed.example")
        compose.onNodeWithText(string(R.string.saved_endpoint_choice_refresh_targets)).tap()
        compose.onNodeWithTag("saved_endpoint_choice_target_1").tap()
        compose.onNodeWithText(string(R.string.saved_endpoints_next)).tap()
        compose.onNodeWithTag("saved_endpoint_choice_save").assertIsEnabled().tap()
        compose.onNodeWithText(string(R.string.saved_endpoint_choice_reload)).tap()
        compose.onNodeWithTag("saved_endpoint_attachment_action").tap()
        compose.onNodeWithTag("saved_endpoint_attachment_restore").tap()
        compose.onNodeWithTag("saved_endpoint_select").tap()
        compose.onNodeWithText(string(R.string.saved_endpoints_part_return)).tap()
        compose.waitForIdle()

        assertTrue(calls.contains("name:true:Renamed"))
        assertTrue(calls.contains("url:true:wss://changed.example"))
        assertEquals(
            listOf("page:0", "choose:1", "page:1", "save", "reload", "attach", "restore", "select", "return"),
            calls.filterNot { it.startsWith("name:") || it.startsWith("url:") }
        )
        assertEquals(1L, chosen?.rowId)
        // Each action carries the exact rendered form snapshot.
        assertEquals(4, forms.size)
        forms.forEach { assertSame(identity, it.identity) }
    }

    @Test
    fun loadingFormIsReadOnlyAndAsksForTheMissingParts() {
        render(SavedEndpointDecisionForm(identity = identity, ref = EndpointRecordRef(EndpointInventorySource.LEGACY, 4)))

        compose.onNodeWithText(string(R.string.saved_endpoints_loading)).assertIsDisplayed()
        compose.onNodeWithTag("saved_endpoint_choice_name").assertIsNotEnabled()
        compose.onNodeWithTag("saved_endpoint_choice_url").assertIsNotEnabled()
        compose.onNodeWithTag("saved_endpoint_choice_save").assertIsNotEnabled()
        compose.onNodeWithText(string(R.string.saved_endpoint_choice_missing)).assertIsDisplayed()
        compose.onNodeWithTag("saved_endpoint_choice_selected").assertDoesNotExist()
    }

    @Test
    fun failedLoadOffersRetry() {
        render(SavedEndpointDecisionForm(identity = identity, ref = ownedRef, loading = false, loadFailed = true))

        compose.onNodeWithText(string(R.string.saved_endpoints_read_failed)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.common_retry)).tap()
        compose.waitForIdle()

        assertEquals(listOf("retryLoad:true"), calls)
    }

    @Test
    fun targetCatalogLoadingFailureAndEmptyPage() {
        render(
            SavedEndpointDecisionForm(
                identity = identity, ref = ownedRef, loading = false, choosingTarget = true,
                targetsLoading = true, targetsFailed = true, targets = page(emptyList(), null, null)
            )
        )
        compose.onNodeWithText(string(R.string.saved_endpoints_loading)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.saved_endpoint_choice_empty)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.common_retry)).assertIsNotEnabled()

        form = form.copy(targetsLoading = false)
        compose.onNodeWithText(string(R.string.common_retry)).assertIsEnabled().tap()
        compose.waitForIdle()

        assertEquals(listOf("retryTargets"), calls)
    }

    @Test
    fun unsupportedTargetSavingAndAttachingStates() {
        render(
            SavedEndpointDecisionForm(
                identity = identity, ref = ownedRef, loading = false, choosingTarget = false,
                url = "wss://x", target = target(EndpointDecisionTarget.Family.UNSUPPORTED), saving = true, attaching = true
            )
        )

        compose.onNodeWithText(string(R.string.saved_endpoint_choice_unsupported)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.common_choose_network)).assertIsNotEnabled()
        compose.onNodeWithText(string(R.string.saved_endpoint_choice_saving)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.saved_endpoint_attachment_working)).assertIsDisplayed()
        compose.onNodeWithTag("saved_endpoint_choice_saved").assertDoesNotExist()
    }

    @Test
    fun everyAttachmentOutcomeHasItsMessage() {
        render(ready().copy(saveFailed = false, attachmentSourceChanged = false, selection = null))
        val messages = mapOf(
            SavedEndpointAttachmentStatus.ADDED_INACTIVE to R.string.saved_endpoint_attachment_added,
            SavedEndpointAttachmentStatus.REATTACHED_INACTIVE to R.string.saved_endpoint_attachment_restored,
            SavedEndpointAttachmentStatus.PRESENT_ACTIVE to R.string.saved_endpoint_attachment_present_active,
            SavedEndpointAttachmentStatus.PRESENT_INACTIVE to R.string.saved_endpoint_attachment_present_inactive,
            SavedEndpointAttachmentStatus.MISMATCH to R.string.saved_endpoint_attachment_mismatch,
            SavedEndpointAttachmentStatus.INVALID_RESPONSE to R.string.saved_endpoint_attachment_invalid,
            SavedEndpointAttachmentStatus.UNREACHABLE to R.string.saved_endpoint_attachment_unreachable,
            SavedEndpointAttachmentStatus.TOO_LARGE to R.string.saved_endpoint_attachment_large,
            SavedEndpointAttachmentStatus.TIMED_OUT to R.string.saved_endpoint_attachment_timeout,
            SavedEndpointAttachmentStatus.BUSY to R.string.saved_endpoint_attachment_busy,
            SavedEndpointAttachmentStatus.FAILED to R.string.saved_endpoint_attachment_failed
        )
        val succeeded = setOf(
            SavedEndpointAttachmentStatus.ADDED_INACTIVE, SavedEndpointAttachmentStatus.REATTACHED_INACTIVE,
            SavedEndpointAttachmentStatus.PRESENT_ACTIVE, SavedEndpointAttachmentStatus.PRESENT_INACTIVE
        )
        messages.forEach { (status, message) ->
            form = form.copy(attachmentStatus = status)
            compose.onNodeWithTag("saved_endpoint_attachment_status").assertTextEquals(string(message))
            // A completed attachment is not offered again; a failed one can be retried.
            if (status in succeeded) {
                compose.onNodeWithTag("saved_endpoint_attachment_action").assertDoesNotExist()
            } else {
                compose.onNodeWithTag("saved_endpoint_attachment_action").assertIsDisplayed()
            }
            compose.onNodeWithTag("saved_endpoint_choice_saved").assertDoesNotExist()
        }
    }

    @Test
    fun everySelectionPhaseAndRuntimeStateHasItsMessage() {
        render(ready().copy(saveFailed = false, attachmentReady = false))
        val phases = mapOf(
            SavedEndpointSelectionState.Phase.CHECKING to R.string.saved_endpoint_selection_checking,
            SavedEndpointSelectionState.Phase.NOT_ATTACHED to R.string.saved_endpoint_selection_unattached,
            SavedEndpointSelectionState.Phase.SAVING to R.string.saved_endpoint_selection_saving,
            SavedEndpointSelectionState.Phase.FAILED to R.string.saved_endpoint_selection_failed,
            SavedEndpointSelectionState.Phase.STALE to R.string.saved_endpoint_selection_stale
        )
        phases.forEach { (phase, message) ->
            form = form.copy(selection = SavedEndpointSelectionState(phase))
            compose.onNodeWithTag("saved_endpoint_selection_status").assertTextEquals(string(message))
            compose.onNodeWithTag("saved_endpoint_runtime_status").assertDoesNotExist()
        }
        // Saving locks the form; a stale or failed selection offers a refresh.
        form = form.copy(selection = SavedEndpointSelectionState(SavedEndpointSelectionState.Phase.FAILED))
        compose.onNodeWithTag("saved_endpoint_selection_refresh").tap()
        compose.waitForIdle()
        assertEquals(listOf("refreshSelection"), calls)

        form = form.copy(selection = SavedEndpointSelectionState(SavedEndpointSelectionState.Phase.SAVED, EndpointDecisionTarget.Family.SOLANA))
        compose.onNodeWithTag("saved_endpoint_selection_status").assertTextEquals(string(R.string.saved_endpoint_selection_saved))
        compose.onNodeWithTag("saved_endpoint_runtime_status").assertTextEquals(string(R.string.saved_endpoint_runtime_preferred))

        form = form.copy(selection = SavedEndpointSelectionState(SavedEndpointSelectionState.Phase.SAVED, EndpointDecisionTarget.Family.SUBSTRATE))
        compose.onNodeWithTag("saved_endpoint_runtime_status").assertTextEquals(string(R.string.saved_endpoint_runtime_not_in_use))

        val node = mockk<ChainNode>()
        val runtimeMessages = listOf(
            Triple(EndpointRuntimeStatus.Phase.PENDING, true, R.string.saved_endpoint_runtime_pending),
            Triple(EndpointRuntimeStatus.Phase.NOT_IN_USE, true, R.string.saved_endpoint_runtime_not_in_use),
            Triple(EndpointRuntimeStatus.Phase.UNAVAILABLE, true, R.string.saved_endpoint_runtime_unavailable),
            Triple(EndpointRuntimeStatus.Phase.PAUSED, true, R.string.saved_endpoint_runtime_paused),
            Triple(EndpointRuntimeStatus.Phase.CONNECTING, true, R.string.saved_endpoint_runtime_connecting),
            Triple(EndpointRuntimeStatus.Phase.CONNECTED, true, R.string.saved_endpoint_runtime_connected),
            Triple(EndpointRuntimeStatus.Phase.CONNECTED, false, R.string.saved_endpoint_runtime_pending)
        )
        runtimeMessages.forEach { (phase, applied, message) ->
            val runtime = mockk<EndpointRuntimeStatus> {
                every { isCurrentFor(node) } returns true
                every { this@mockk.phase } returns phase
                every { this@mockk.applied } returns applied
            }
            form = form.copy(
                selection = SavedEndpointSelectionState(
                    SavedEndpointSelectionState.Phase.SAVED, EndpointDecisionTarget.Family.SUBSTRATE, node, runtime
                )
            )
            compose.onNodeWithTag("saved_endpoint_runtime_status").assertTextEquals(string(message))
        }
    }

    @Test
    fun defaultActionsIgnoreEveryCallback() {
        val defaults = SavedEndpointDecisionActions()
        val snapshot = SavedEndpointDecisionForm(identity = identity, ref = ownedRef)
        val results = listOf(
            defaults.edit(ownedRef), defaults.retryLoad(identity), defaults.name(identity, "n"), defaults.url(identity, "u"),
            defaults.choose(identity, option(1, null, null)), defaults.page(identity, 1), defaults.retryTargets(identity),
            defaults.reload(identity), defaults.save(snapshot), defaults.attach(snapshot), defaults.restore(snapshot),
            defaults.select(snapshot), defaults.refreshSelection(snapshot)
        )

        assertEquals(List(13) { Unit }, results)
    }
}
