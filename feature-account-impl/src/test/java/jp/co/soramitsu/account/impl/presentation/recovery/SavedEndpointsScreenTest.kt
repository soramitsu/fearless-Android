package jp.co.soramitsu.account.impl.presentation.recovery

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.account.impl.testutil.string
import jp.co.soramitsu.account.impl.testutil.tap
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.coredb.recovery.EndpointChunkState
import jp.co.soramitsu.coredb.recovery.EndpointField
import jp.co.soramitsu.coredb.recovery.EndpointInventorySource
import jp.co.soramitsu.coredb.recovery.EndpointOwnership
import jp.co.soramitsu.coredb.recovery.EndpointPage
import jp.co.soramitsu.coredb.recovery.EndpointRecord
import jp.co.soramitsu.coredb.recovery.EndpointRecordRef
import jp.co.soramitsu.coredb.recovery.EndpointSourceProvenance
import jp.co.soramitsu.coredb.recovery.EndpointTextState
import jp.co.soramitsu.coredb.recovery.EndpointValue
import jp.co.soramitsu.coredb.recovery.EndpointValueChunk
import jp.co.soramitsu.coredb.recovery.LegacyEndpointChunkState
import jp.co.soramitsu.coredb.recovery.LegacyEndpointField
import jp.co.soramitsu.coredb.recovery.LegacyEndpointInteger
import jp.co.soramitsu.coredb.recovery.LegacyEndpointPage
import jp.co.soramitsu.coredb.recovery.LegacyEndpointRecord
import jp.co.soramitsu.coredb.recovery.LegacyEndpointStorage
import jp.co.soramitsu.coredb.recovery.LegacyEndpointText
import jp.co.soramitsu.coredb.recovery.LegacyEndpointTextState
import jp.co.soramitsu.coredb.recovery.LegacyEndpointValueChunk
import jp.co.soramitsu.feature_account_impl.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h5000dp")
class SavedEndpointsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private var state by mutableStateOf(SavedEndpointsState())

    private fun render(initial: SavedEndpointsState) {
        state = initial
        compose.setContent {
            FearlessAppTheme {
                SavedEndpointsScreen(
                    state = state,
                    back = { calls += "back" },
                    unlock = { calls += "unlock" },
                    refresh = { calls += "refresh" },
                    retry = { calls += "retry" },
                    previous = { calls += "previous" },
                    next = { calls += "next" },
                    inspect = { row, field -> calls += "inspect:$row:$field" },
                    retryInspection = { calls += "retryInspection" },
                    previousInspection = { calls += "previousInspection" },
                    nextInspection = { calls += "nextInspection" },
                    returnToList = { calls += "return" },
                    selectSource = { calls += "source:$it" },
                    inspectRecord = { ref, field -> calls += "inspectRecord:${ref.source}:${ref.rowId}:$field" },
                    retryRecordInspection = { calls += "retryRecord" },
                    previousRecordInspection = { calls += "previousRecord" },
                    nextRecordInspection = { calls += "nextRecord" },
                    decisionActions = SavedEndpointDecisionActions(edit = { calls += "edit:${it.source}:${it.rowId}" })
                )
            }
        }
    }

    private fun text(value: String) = LegacyEndpointText(LegacyEndpointStorage.TEXT, value.length.toLong(), LegacyEndpointTextState.AVAILABLE, value)
    private fun integer(value: Long) = LegacyEndpointInteger(LegacyEndpointStorage.INTEGER, null, value)

    private val readableRecord = LegacyEndpointRecord(
        originalRowId = 1,
        name = text("Home node"),
        endpoint = text("wss://node.example"),
        networkType = integer(2),
        isDefault = integer(0),
        isActive = integer(1)
    )
    private val damagedRecord = LegacyEndpointRecord(
        originalRowId = 2,
        name = LegacyEndpointText(LegacyEndpointStorage.NULL, null, LegacyEndpointTextState.NULL, null),
        endpoint = LegacyEndpointText(LegacyEndpointStorage.TEXT, 9000, LegacyEndpointTextState.OVERSIZED, null),
        networkType = LegacyEndpointInteger(LegacyEndpointStorage.TEXT, 3, null),
        isDefault = LegacyEndpointInteger(LegacyEndpointStorage.NULL, null, null),
        isActive = LegacyEndpointInteger(LegacyEndpointStorage.BLOB, 4, null)
    )

    @Test
    fun lockedArchiveHidesRecordsUntilUnlocked() {
        render(SavedEndpointsState(locked = true, unlockFailed = true))

        compose.onNodeWithText(string(R.string.saved_endpoints_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.saved_endpoints_locked)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.saved_endpoints_unlock_failed)).assertIsDisplayed()
        compose.onNodeWithTag("saved_endpoint_collection").assertDoesNotExist()

        compose.onNodeWithText(string(R.string.saved_endpoints_unlock)).tap()
        compose.onNodeWithText(string(R.string.wallet_recovery_back)).tap()
        compose.waitForIdle()

        assertEquals(listOf("unlock", "back"), calls)
    }

    @Test
    fun legacyRecordsShowReadableValuesAndOfferInspectionOfDamagedOnes() {
        render(
            SavedEndpointsState(
                locked = false,
                page = LegacyEndpointPage(true, listOf(readableRecord, damagedRecord), previousBeforeRowId = null, nextAfterRowId = 2)
            )
        )

        compose.onNodeWithText(string(R.string.saved_endpoints_record, "1")).assertIsDisplayed()
        compose.onNodeWithText(savedEndpointDisplayText("Home node")).assertIsDisplayed()
        compose.onNodeWithText(savedEndpointDisplayText("wss://node.example")).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.saved_endpoints_network_code, "2")).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.saved_endpoints_network_code, string(R.string.saved_endpoints_unknown))).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.saved_endpoints_too_large)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.saved_endpoints_bytes, "9000")).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.saved_endpoints_inspect_name)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.saved_endpoints_previous)).assertIsNotEnabled()

        compose.onNodeWithText(string(R.string.saved_endpoints_inspect_url)).tap()
        compose.onNodeWithText(string(R.string.saved_endpoint_inspect_field, string(R.string.saved_endpoint_network_type))).tap()
        compose.onNodeWithText(string(R.string.saved_endpoint_inspect_field, string(R.string.saved_endpoint_selection_value))).tap()
        compose.onAllNodesWithText(string(R.string.saved_endpoint_choice_edit))[0].tap()
        compose.onNodeWithText(string(R.string.saved_endpoints_next)).assertIsEnabled().tap()
        compose.onNodeWithText(string(R.string.saved_endpoints_refresh)).tap()
        compose.waitForIdle()

        assertEquals(
            listOf(
                "inspect:2:${LegacyEndpointField.ENDPOINT}",
                "inspectRecord:LEGACY:2:${EndpointField.NETWORK_TYPE}",
                "inspectRecord:LEGACY:2:${EndpointField.IS_ACTIVE}",
                "edit:LEGACY:1",
                "next",
                "refresh"
            ),
            calls
        )
    }

    @Test
    fun legacyListReportsLoadingFailureAndEmptyOrAbsentArchives() {
        render(SavedEndpointsState(locked = false, loading = true))
        compose.onNodeWithText(string(R.string.saved_endpoints_loading)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.saved_endpoints_refresh)).assertIsNotEnabled()

        state = SavedEndpointsState(locked = false, readFailed = true)
        compose.onNodeWithText(string(R.string.saved_endpoints_read_failed)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.common_retry)).tap()

        state = SavedEndpointsState(locked = false, page = LegacyEndpointPage(true, emptyList(), null, null))
        compose.onNodeWithText(string(R.string.saved_endpoints_empty)).assertIsDisplayed()

        state = SavedEndpointsState(locked = false, page = LegacyEndpointPage(false, emptyList(), null, null))
        compose.onNodeWithText(string(R.string.saved_endpoints_absent)).assertIsDisplayed()
        compose.waitForIdle()

        assertEquals(listOf("retry"), calls)
    }

    @Test
    fun collectionPickerOffersEverySource() {
        render(SavedEndpointsState(locked = false))

        compose.onNodeWithText(
            string(R.string.saved_endpoint_collection, string(R.string.saved_endpoint_source_legacy))
        ).assertIsDisplayed()
        compose.onNodeWithTag("saved_endpoint_collection").tap()
        compose.onNodeWithText(string(R.string.saved_endpoint_source_historical)).assertExists()
        compose.onNodeWithText(string(R.string.saved_endpoint_source_entry)).assertExists()
        compose.onNodeWithText(string(R.string.saved_endpoint_source_current)).assertExists()
        compose.onNodeWithTag("saved_endpoint_source_owned").tap()
        compose.waitForIdle()

        assertEquals(listOf("source:OWNED"), calls)
    }

    @Test
    fun legacyFieldInspectionShowsEachChunkState() {
        val available = mockk<LegacyEndpointValueChunk> {
            every { state } returns LegacyEndpointChunkState.AVAILABLE
            every { bytes } returns "wss".toByteArray()
            every { byteOffset } returns 0
            every { totalByteLength } returns 3
            every { endByteOffsetExclusive } returns 3
            every { previousByteOffset } returns null
            every { nextByteOffset } returns 3
        }
        render(
            SavedEndpointsState(
                locked = false,
                inspection = SavedEndpointInspectionState(5, LegacyEndpointField.ENDPOINT, chunk = available, loading = false)
            )
        )
        compose.onNodeWithText(string(R.string.saved_endpoints_record, "5")).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.saved_endpoints_part_range, "1", "3", "3")).assertIsDisplayed()
        compose.onNodeWithTag("saved_endpoint_chunk").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.saved_endpoints_part_previous)).assertIsNotEnabled()
        compose.onNodeWithText(string(R.string.saved_endpoints_part_next)).tap()
        compose.onNodeWithText(string(R.string.saved_endpoints_part_return)).tap()

        val empty = mockk<LegacyEndpointValueChunk> {
            every { state } returns LegacyEndpointChunkState.AVAILABLE
            every { bytes } returns ByteArray(0)
            every { byteOffset } returns 4
            every { totalByteLength } returns 4
            every { previousByteOffset } returns 0
            every { nextByteOffset } returns null
        }
        state = state.copy(inspection = SavedEndpointInspectionState(5, LegacyEndpointField.NAME, chunk = empty, loading = false))
        compose.onNodeWithText(string(R.string.saved_endpoints_part_empty, "4", "4")).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.saved_endpoints_part_previous)).tap()

        listOf(
            LegacyEndpointChunkState.ROW_ABSENT to R.string.saved_endpoints_part_unavailable,
            LegacyEndpointChunkState.NULL_VALUE to R.string.saved_endpoints_not_saved,
            LegacyEndpointChunkState.UNSUPPORTED_STORAGE to R.string.saved_endpoints_unrecognized
        ).forEach { (chunkState, message) ->
            val chunk = mockk<LegacyEndpointValueChunk> {
                every { this@mockk.state } returns chunkState
                every { previousByteOffset } returns null
                every { nextByteOffset } returns null
            }
            state = state.copy(inspection = SavedEndpointInspectionState(5, LegacyEndpointField.NAME, chunk = chunk, loading = false))
            compose.onNodeWithText(string(message)).assertIsDisplayed()
        }

        state = state.copy(inspection = SavedEndpointInspectionState(5, LegacyEndpointField.NAME, loading = false, readFailed = true))
        compose.onNodeWithText(string(R.string.saved_endpoints_read_failed)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.common_retry)).tap()
        state = state.copy(inspection = SavedEndpointInspectionState(5, LegacyEndpointField.NAME, loading = true))
        compose.onNodeWithText(string(R.string.saved_endpoints_loading)).assertIsDisplayed()
        compose.waitForIdle()

        assertEquals(listOf("nextInspection", "return", "previousInspection", "retryInspection"), calls)
    }

    private fun modernPage(source: EndpointInventorySource, records: List<EndpointRecord>, next: Long? = null): EndpointPage =
        EndpointPage(
            source = source,
            present = true,
            provenance = if (source == EndpointInventorySource.OWNED) null else EndpointSourceProvenance(81, 81),
            records = records,
            previousBeforeRowId = null,
            nextAfterRowId = next
        )

    @Test
    fun ownedRecordsShowOwnershipAndDamagedValues() {
        val ref = EndpointRecordRef(EndpointInventorySource.OWNED, 9, 3)
        val record = EndpointRecord(
            ref,
            mapOf(
                EndpointField.NAME to EndpointValue.Text(5, EndpointTextState.AVAILABLE, "Owned"),
                EndpointField.ENDPOINT to EndpointValue.Text(9000, EndpointTextState.OVERSIZED, null),
                EndpointField.CHAIN_REFERENCE to EndpointValue.Null
            ),
            EndpointOwnership(attached = true, deleted = false, originalSnapshotRowId = 7)
        )
        render(
            SavedEndpointsState(
                locked = false,
                source = EndpointInventorySource.OWNED,
                inventoryPage = modernPage(EndpointInventorySource.OWNED, listOf(record), next = 9)
            )
        )

        compose.onNodeWithText(string(R.string.saved_endpoint_attached)).assertIsDisplayed()
        compose.onNodeWithText(savedEndpointDisplayText("Owned")).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.saved_endpoints_too_large)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.saved_endpoints_not_saved)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.saved_endpoint_historical_notice)).assertDoesNotExist()

        compose.onNodeWithText(string(R.string.saved_endpoint_inspect_field, string(R.string.saved_endpoints_url))).tap()
        compose.onNodeWithText(string(R.string.saved_endpoint_choice_edit)).tap()
        compose.onNodeWithText(string(R.string.saved_endpoints_next)).tap()
        compose.waitForIdle()

        assertEquals(listOf("inspectRecord:OWNED:9:${EndpointField.ENDPOINT}", "edit:OWNED:9", "next"), calls)
    }

    @Test
    fun archivedCollectionsShowTheHistoricalNoticeAndEveryValueKind() {
        val ref = EndpointRecordRef(EndpointInventorySource.CURRENT, 4)
        val record = EndpointRecord(
            ref,
            mapOf(
                EndpointField.NAME to EndpointValue.Text(3, EndpointTextState.INVALID_UTF8, null),
                EndpointField.ENDPOINT to EndpointValue.Blob(12),
                EndpointField.CHAIN_REFERENCE to EndpointValue.Text(4, EndpointTextState.AVAILABLE, "kusa"),
                EndpointField.IS_DEFAULT to EndpointValue.Integer(1),
                EndpointField.IS_ACTIVE to EndpointValue.Real(0.5)
            )
        )
        render(
            SavedEndpointsState(
                locked = false,
                source = EndpointInventorySource.CURRENT,
                inventoryPage = modernPage(EndpointInventorySource.CURRENT, listOf(record))
            )
        )

        compose.onNodeWithText(string(R.string.saved_endpoint_historical_notice)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.saved_endpoints_invalid_text)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.saved_endpoints_unrecognized)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.saved_endpoints_bytes, "12")).assertIsDisplayed()
        compose.onNodeWithText(savedEndpointDisplayText("kusa")).assertIsDisplayed()
        compose.onNodeWithText("1").assertIsDisplayed()
        compose.onNodeWithText("0.5").assertIsDisplayed()

        // A page read for another collection is not shown under this one.
        state = state.copy(source = EndpointInventorySource.HISTORICAL)
        compose.onNodeWithText(savedEndpointDisplayText("kusa")).assertDoesNotExist()

        state = SavedEndpointsState(
            locked = false,
            source = EndpointInventorySource.UPGRADE_ENTRY,
            inventoryPage = EndpointPage(EndpointInventorySource.UPGRADE_ENTRY, false, null, emptyList(), null, null)
        )
        compose.onNodeWithText(string(R.string.saved_endpoint_source_absent)).assertIsDisplayed()

        state = SavedEndpointsState(
            locked = false,
            source = EndpointInventorySource.OWNED,
            inventoryPage = modernPage(EndpointInventorySource.OWNED, emptyList()),
            loading = true,
            readFailed = true
        )
        compose.onNodeWithText(string(R.string.saved_endpoints_empty)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.saved_endpoints_loading)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.saved_endpoints_read_failed)).assertIsDisplayed()
    }

    @Test
    fun recordInspectionShowsEachChunkState() {
        val ref = EndpointRecordRef(EndpointInventorySource.OWNED, 9, 3)
        fun chunk(chunkState: EndpointChunkState, configure: EndpointValueChunk.() -> Unit = {}) = mockk<EndpointValueChunk> {
            every { state } returns chunkState
            every { previousByteOffset } returns null
            every { nextByteOffset } returns null
            configure()
        }
        val available = chunk(EndpointChunkState.AVAILABLE) {
            every { bytes } returns "wss://".toByteArray()
            every { byteOffset } returns 2
            every { totalByteLength } returns 20
            every { previousByteOffset } returns 0
            every { nextByteOffset } returns 8
        }
        render(
            SavedEndpointsState(
                locked = false,
                source = EndpointInventorySource.OWNED,
                inventoryInspection = SavedEndpointRecordInspectionState(ref, EndpointField.ENDPOINT, chunk = available, loading = false)
            )
        )
        compose.onNodeWithText(string(R.string.saved_endpoints_part_range, "3", "8", "20")).assertIsDisplayed()
        compose.onNodeWithTag("saved_endpoint_inventory_chunk").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.saved_endpoints_part_previous)).tap()
        compose.onNodeWithText(string(R.string.saved_endpoints_part_next)).tap()
        compose.onNodeWithText(string(R.string.saved_endpoints_part_return)).tap()

        val empty = chunk(EndpointChunkState.AVAILABLE) {
            every { bytes } returns ByteArray(0)
            every { byteOffset } returns 0
            every { totalByteLength } returns 0
        }
        state = state.copy(inventoryInspection = SavedEndpointRecordInspectionState(ref, EndpointField.NAME, chunk = empty, loading = false))
        compose.onNodeWithText(string(R.string.saved_endpoints_part_empty, "0", "0")).assertIsDisplayed()

        val integer = chunk(EndpointChunkState.NON_BYTE_STORAGE) { every { integerValue } returns 42 }
        state = state.copy(inventoryInspection = SavedEndpointRecordInspectionState(ref, EndpointField.IS_DEFAULT, chunk = integer, loading = false))
        compose.onNodeWithText("42").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.saved_endpoint_default_value)).assertIsDisplayed()

        val real = chunk(EndpointChunkState.NON_BYTE_STORAGE) {
            every { integerValue } returns null
            every { realValue } returns 1.5
        }
        state = state.copy(inventoryInspection = SavedEndpointRecordInspectionState(ref, EndpointField.IS_ACTIVE, chunk = real, loading = false))
        compose.onNodeWithText("1.5").assertIsDisplayed()

        listOf(
            EndpointChunkState.NULL_VALUE to R.string.saved_endpoints_not_saved,
            EndpointChunkState.STALE_REVISION to R.string.saved_endpoint_changed,
            EndpointChunkState.SOURCE_ABSENT to R.string.saved_endpoints_part_unavailable
        ).forEach { (chunkState, message) ->
            state = state.copy(
                inventoryInspection = SavedEndpointRecordInspectionState(ref, EndpointField.CHAIN_REFERENCE, chunk = chunk(chunkState), loading = false)
            )
            compose.onNodeWithText(string(message)).assertIsDisplayed()
            compose.onNodeWithText(string(R.string.saved_endpoint_chain_reference)).assertIsDisplayed()
        }

        state = state.copy(inventoryInspection = SavedEndpointRecordInspectionState(ref, EndpointField.NAME, loading = false, readFailed = true))
        compose.onNodeWithText(string(R.string.common_retry)).tap()
        state = state.copy(inventoryInspection = SavedEndpointRecordInspectionState(ref, EndpointField.NAME, loading = true))
        compose.onNodeWithText(string(R.string.saved_endpoints_loading)).assertIsDisplayed()
        compose.waitForIdle()

        assertEquals(listOf("previousRecord", "nextRecord", "return", "retryRecord"), calls)
    }
}
