package jp.co.soramitsu.app.root.presentation.main

import android.content.Context
import android.content.res.Configuration
import android.os.CancellationSignal
import android.os.Looper
import android.os.LocaleList
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.account.impl.presentation.recovery.SavedEndpointInspectionState
import jp.co.soramitsu.account.impl.presentation.recovery.SavedEndpointsScreen
import jp.co.soramitsu.account.impl.presentation.recovery.SavedEndpointsState
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.coredb.recovery.LegacyEndpointChunkState
import jp.co.soramitsu.coredb.recovery.LegacyEndpointField
import jp.co.soramitsu.coredb.recovery.LegacyEndpointInventoryReader
import jp.co.soramitsu.coredb.recovery.LegacyEndpointValueChunk
import jp.co.soramitsu.coredb.recovery.LegacyEndpointValueChunkReader
import jp.co.soramitsu.coredb.recovery.LegacyEndpointInteger
import jp.co.soramitsu.coredb.recovery.LegacyEndpointPage
import jp.co.soramitsu.coredb.recovery.LegacyEndpointRecord
import jp.co.soramitsu.coredb.recovery.LegacyEndpointStorage
import jp.co.soramitsu.coredb.recovery.LegacyEndpointText
import jp.co.soramitsu.coredb.recovery.LegacyEndpointTextState
import java.util.Locale
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SavedEndpointsUiInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<PolkaswapNavigationLayoutTestActivity>()
    private var state by mutableStateOf(SavedEndpointsState())
    private var back = 0
    private var unlock = 0
    private var refresh = 0
    private var retry = 0
    private var previous = 0
    private var next = 0
    private val inspections = mutableListOf<Pair<Long, LegacyEndpointField>>()
    private var retryInspection = 0
    private var previousInspection = 0
    private var nextInspection = 0
    private var returnToList = 0
    private var renderContext: Context? = null

    @Test fun lockedScreenNeverRendersEvenSuppliedStaleRecordsAndOffersBackAndUnlock() {
        state = SavedEndpointsState(page = page(record(0)), locked = true)
        render()
        compose.onNodeWithText("\"wss://example.invalid\"").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.saved_endpoints_record, "0")).assertDoesNotExist()
        click(R.string.saved_endpoints_unlock)
        click(R.string.wallet_recovery_back)
        compose.runOnIdle { assertEquals(1, unlock); assertEquals(1, back); assertEquals(0, refresh) }
    }

    @Test fun completeEscapedValuesAndEmptyNameRemainReadableAtDoubleTextSize() {
        val endpoint = "wss://example.invalid/" + "x".repeat(2021) + "\u0000"
        val escaped = "\"wss://example.invalid/" + "x".repeat(2021) + "\\u0000\""
        state = SavedEndpointsState(page = page(record(0, value(""), value(endpoint))), locked = false)
        render(fontScale = 2f)
        scrollTo("\"\"")
        compose.onNodeWithText("\"\"").assertIsDisplayed()
        scrollTo(escaped)
        compose.onNodeWithText(escaped).assertExists()
        compose.onNode(hasText(escaped) and hasClickAction()).assertDoesNotExist()
        scrollTo(text(R.string.saved_endpoints_network_code, "77"))
        compose.onNodeWithText(text(R.string.saved_endpoints_network_code, "77")).assertIsDisplayed()
    }

    @Test fun missingAndLoadingPagingActionsAreDisabledAndAvailableCursorActionsFireOnce() {
        state = SavedEndpointsState(page = page(record(0)).copy(nextAfterRowId = 0), locked = false)
        render()
        scrollTo(text(R.string.saved_endpoints_previous))
        compose.onNodeWithText(text(R.string.saved_endpoints_previous)).assertIsNotEnabled()
        click(R.string.saved_endpoints_next)
        compose.runOnIdle { assertEquals(1, next); state = state.copy(loading = true) }
        scrollTo(text(R.string.saved_endpoints_next))
        compose.onNodeWithText(text(R.string.saved_endpoints_next)).assertIsNotEnabled()
        scrollTo(text(R.string.saved_endpoints_refresh))
        compose.onNodeWithText(text(R.string.saved_endpoints_refresh)).assertIsNotEnabled()
        compose.runOnIdle { state = SavedEndpointsState(page = page(record(1)).copy(previousBeforeRowId = 1), locked = false) }
        click(R.string.saved_endpoints_previous)
        scrollTo(text(R.string.saved_endpoints_next))
        compose.onNodeWithText(text(R.string.saved_endpoints_next)).assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, previous); assertEquals(1, next) }
    }

    @Test fun localReadFailureKeepsLastPageAndOffersRetryWithoutExceptionDetails() {
        state = SavedEndpointsState(page = page(record(0)), readFailed = true, locked = false)
        render()
        scrollTo(text(R.string.saved_endpoints_read_failed))
        compose.onNodeWithText(text(R.string.saved_endpoints_read_failed)).assertIsDisplayed()
        click(R.string.common_retry)
        scrollTo("\"wss://example.invalid\"")
        compose.onNodeWithText("\"wss://example.invalid\"").assertIsDisplayed()
        click(R.string.saved_endpoints_refresh)
        compose.runOnIdle { assertEquals(1, retry); assertEquals(1, refresh) }
    }

    @Test fun absentAndEmptyArchivesHaveDifferentMessagesAndRefreshStaysAvailable() {
        state = SavedEndpointsState(page = LegacyEndpointPage(false, emptyList(), null, null), locked = false)
        render()
        scrollTo(text(R.string.saved_endpoints_absent))
        compose.onNodeWithText(text(R.string.saved_endpoints_absent)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.saved_endpoints_empty)).assertDoesNotExist()
        compose.runOnIdle { state = state.copy(page = LegacyEndpointPage(true, emptyList(), null, null)) }
        scrollTo(text(R.string.saved_endpoints_empty))
        compose.onNodeWithText(text(R.string.saved_endpoints_empty)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.saved_endpoints_absent)).assertDoesNotExist()
        click(R.string.saved_endpoints_refresh)
        compose.runOnIdle { assertEquals(1, refresh) }
    }

    @Test fun opaqueOversizedAndInvalidTextStatesAreExplicitWithoutInventedValues() {
        val states = listOf(
            LegacyEndpointTextState.NULL to R.string.saved_endpoints_not_saved,
            LegacyEndpointTextState.UNSUPPORTED_STORAGE to R.string.saved_endpoints_unrecognized,
            LegacyEndpointTextState.OVERSIZED to R.string.saved_endpoints_too_large,
            LegacyEndpointTextState.INVALID_UTF8 to R.string.saved_endpoints_invalid_text
        )
        state = SavedEndpointsState(page = LegacyEndpointPage(true, states.mapIndexed { index, (kind, _) ->
            record(index.toLong(), endpoint = LegacyEndpointText(
                when (kind) {
                    LegacyEndpointTextState.NULL -> LegacyEndpointStorage.NULL
                    LegacyEndpointTextState.UNSUPPORTED_STORAGE -> LegacyEndpointStorage.BLOB
                    else -> LegacyEndpointStorage.TEXT
                },
                if (kind == LegacyEndpointTextState.NULL) null else 1048577L, kind, null))
        }, null, null), locked = false)
        render()
        states.forEach { (_, label) ->
            scrollTo(text(label)); compose.onNodeWithText(text(label)).assertIsDisplayed()
        }
        compose.onNodeWithText("\"wss://example.invalid\"").assertDoesNotExist()
    }

    @Test fun pausingStateImmediatelyRemovesDisplayedRecordsAndShowsUnlockFailureWhenPresent() {
        state = SavedEndpointsState(page = page(record(0)), locked = false)
        render()
        scrollTo("\"wss://example.invalid\"")
        compose.onNodeWithText("\"wss://example.invalid\"").assertIsDisplayed()
        compose.runOnIdle { state = SavedEndpointsState(unlockFailed = true) }
        compose.onNodeWithText("\"wss://example.invalid\"").assertDoesNotExist()
        scrollTo(text(R.string.saved_endpoints_unlock_failed))
        compose.onNodeWithText(text(R.string.saved_endpoints_unlock_failed)).assertIsDisplayed()
        click(R.string.saved_endpoints_unlock)
        compose.runOnIdle { assertEquals(1, unlock) }
    }

    @Test fun inspectionActionsCarryExactSignedRowAndFieldFromRealInventory() {
        val pages = listOf(Long.MIN_VALUE, Long.MAX_VALUE).map { rowId ->
            withMemoryArchive(listOf(MemoryArchiveRow(rowId, byteArrayOf(0xff.toByte()), byteArrayOf(0xfe.toByte())))) {
                LegacyEndpointInventoryReader(it).readPage(CancellationSignal())
            }
        }
        state = SavedEndpointsState(page = pages.first(), locked = false)
        render()
        pages.forEach { page ->
            compose.runOnIdle { state = SavedEndpointsState(page = page, locked = false) }
            val row = page.records.single()
            assertEquals(LegacyEndpointTextState.INVALID_UTF8, row.name.state)
            assertEquals(LegacyEndpointTextState.INVALID_UTF8, row.endpoint.state)
            scrollTo(text(R.string.saved_endpoints_record, row.originalRowId.toString()))
            compose.onNodeWithText(text(R.string.saved_endpoints_record, row.originalRowId.toString())).assertIsDisplayed()
            click(R.string.saved_endpoints_inspect_name)
            click(R.string.saved_endpoints_inspect_url)
        }
        compose.runOnIdle {
            assertEquals(listOf(Long.MIN_VALUE to LegacyEndpointField.NAME, Long.MIN_VALUE to LegacyEndpointField.ENDPOINT,
                Long.MAX_VALUE to LegacyEndpointField.NAME, Long.MAX_VALUE to LegacyEndpointField.ENDPOINT), inspections)
        }
        compose.onNodeWithTag("saved_endpoint_chunk").assertDoesNotExist()
    }

    @Test fun lockedInspectionHidesSuppliedStaleChunkAndHidesItAgainAfterRevocation() {
        val chunk = withMemoryArchive(listOf(MemoryArchiveRow(-7, "name".toByteArray(), "wss://stale.invalid/secret".toByteArray()))) {
            LegacyEndpointValueChunkReader(it).readChunk(CancellationSignal(), -7, LegacyEndpointField.ENDPOINT)
        }
        val selected = inspection(chunk)
        state = SavedEndpointsState(locked = true, inspection = selected)
        render()
        compose.onNodeWithTag("saved_endpoint_chunk").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.saved_endpoints_record, "-7")).assertDoesNotExist()
        click(R.string.saved_endpoints_unlock)
        compose.runOnIdle { assertEquals(1, unlock); state = state.copy(locked = false) }
        scrollTo("\"wss://stale.invalid/secret\"")
        compose.onNodeWithTag("saved_endpoint_chunk").assertTextEquals("\"wss://stale.invalid/secret\"")
        compose.runOnIdle { state = state.copy(locked = true) }
        compose.onNodeWithTag("saved_endpoint_chunk").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.saved_endpoints_record, "-7")).assertDoesNotExist()
    }

    @Test fun realBoundedInvalidBytesRemainPlainEscapedTextAtNarrowDoubleTextSize() {
        val prefix = "wss://example.invalid/".toByteArray() + byteArrayOf(0, 0xff.toByte()) +
            "😀\uDB40\uDC20".toByteArray(Charsets.UTF_8)
        val payload = prefix + ByteArray(1536 - prefix.size) { 'a'.code.toByte() }
        val chunk = withMemoryArchive(listOf(MemoryArchiveRow(Long.MIN_VALUE, "name".toByteArray(), payload))) {
            LegacyEndpointValueChunkReader(it).readChunk(CancellationSignal(), Long.MIN_VALUE, LegacyEndpointField.ENDPOINT)
        }
        assertEquals(512, chunk.byteCount)
        assertEquals(1536L, chunk.totalByteLength)
        val expected = "\"wss://example.invalid/\\u0000\\xff😀\\udb40\\udc20" + "a".repeat(512 - prefix.size) + "\""
        state = SavedEndpointsState(locked = false, inspection = inspection(chunk))
        render(fontScale = 2f, english = true)
        val explanation = text(R.string.saved_endpoints_part_escaped)
        assertTrue(explanation.contains("\\xNN"))
        assertFalse(explanation.contains("\\\\xNN"))
        scrollTo(explanation)
        compose.onNodeWithText(explanation).assertExists()
        scrollTo(text(R.string.saved_endpoints_part_range, "1", "512", "1536"))
        compose.onNodeWithText(text(R.string.saved_endpoints_part_range, "1", "512", "1536")).assertIsDisplayed()
        scrollTo(expected)
        compose.onNodeWithTag("saved_endpoint_chunk").assertTextEquals(expected).assertIsDisplayed()
        compose.onNode(hasText(expected) and hasClickAction()).assertDoesNotExist()
        scrollTo(text(R.string.saved_endpoints_part_next))
        compose.onNodeWithText(text(R.string.saved_endpoints_part_next)).assertIsEnabled()
    }

    @Test fun adjacentSlicesExposeSplitUtf8BytesWithoutReplacementOrCombiningReads() {
        val payload = ByteArray(511) { 'b'.code.toByte() } + "😀tail".toByteArray(Charsets.UTF_8)
        val chunks = withMemoryArchive(listOf(MemoryArchiveRow(-19, "name".toByteArray(), payload))) { db ->
            listOf(0L, 512L).map { offset ->
                LegacyEndpointValueChunkReader(db).readChunk(CancellationSignal(), -19, LegacyEndpointField.ENDPOINT, offset)
            }
        }
        val expected = listOf("\"" + "b".repeat(511) + "\\xf0\"", "\"\\x9f\\x98\\x80tail\"")
        state = SavedEndpointsState(locked = false, inspection = inspection(chunks.first()))
        render(fontScale = 2f)
        chunks.forEachIndexed { index, chunk ->
            compose.runOnIdle { state = state.copy(inspection = inspection(chunk)) }
            scrollTo(expected[index])
            compose.onNodeWithTag("saved_endpoint_chunk").assertTextEquals(expected[index])
            compose.onNode(hasText(expected[index]) and hasClickAction()).assertDoesNotExist()
            val range = text(R.string.saved_endpoints_part_range, (chunk.byteOffset + 1).toString(),
                checkNotNull(chunk.endByteOffsetExclusive).toString(), "519")
            scrollTo(range)
            compose.onNodeWithText(range).assertIsDisplayed()
        }
    }

    @Test fun exactCurrentRangesAndAvailablePartCursorsDriveTheirOwnCallbacks() {
        val chunks = withMemoryArchive(listOf(MemoryArchiveRow(91, ByteArray(1536) { 'n'.code.toByte() }, "endpoint".toByteArray()))) { db ->
            listOf(0L, 512L, 1024L).map { offset ->
                LegacyEndpointValueChunkReader(db).readChunk(CancellationSignal(), 91, LegacyEndpointField.NAME, offset)
            }
        }
        state = SavedEndpointsState(locked = false, inspection = inspection(chunks.first()))
        render()
        chunks.forEach { chunk ->
            compose.runOnIdle { state = state.copy(inspection = inspection(chunk)) }
            val range = text(R.string.saved_endpoints_part_range, (chunk.byteOffset + 1).toString(),
                checkNotNull(chunk.endByteOffsetExclusive).toString(), "1536")
            scrollTo(range)
            compose.onNodeWithText(range).assertIsDisplayed()
            scrollTo(text(R.string.saved_endpoints_part_previous))
            if (chunk.byteOffset == 0L) compose.onNodeWithText(text(R.string.saved_endpoints_part_previous)).assertIsNotEnabled()
            else click(R.string.saved_endpoints_part_previous)
            scrollTo(text(R.string.saved_endpoints_part_next))
            if (chunk.byteOffset == 1024L) compose.onNodeWithText(text(R.string.saved_endpoints_part_next)).assertIsNotEnabled()
            else click(R.string.saved_endpoints_part_next)
        }
        compose.runOnIdle {
            assertEquals(2, previousInspection); assertEquals(2, nextInspection)
            assertEquals(0, previous); assertEquals(0, next)
        }
    }

    @Test fun emptyValueAndNonzeroTerminalSliceDiscloseOffsetTotalAndOriginalSignedRow() {
        val chunks = withMemoryArchive(listOf(MemoryArchiveRow(Long.MIN_VALUE, byteArrayOf(), ByteArray(1536) { 'x'.code.toByte() }))) { db ->
            listOf(LegacyEndpointValueChunkReader(db).readChunk(CancellationSignal(), Long.MIN_VALUE, LegacyEndpointField.NAME),
                LegacyEndpointValueChunkReader(db).readChunk(CancellationSignal(), Long.MIN_VALUE, LegacyEndpointField.ENDPOINT, 1536))
        }
        state = SavedEndpointsState(locked = false, inspection = inspection(chunks.first()))
        render(english = true)
        chunks.forEach { chunk ->
            assertEquals(0, chunk.byteCount)
            compose.runOnIdle { state = state.copy(inspection = inspection(chunk)) }
            val heading = text(R.string.saved_endpoints_record, Long.MIN_VALUE.toString())
            scrollTo(heading)
            compose.onNodeWithText(heading).assertIsDisplayed()
            val empty = text(R.string.saved_endpoints_part_empty, chunk.byteOffset.toString(), chunk.totalByteLength.toString())
            assertTrue(empty.contains("offset ${chunk.byteOffset}"))
            assertTrue(empty.contains("Current size: ${chunk.totalByteLength} bytes"))
            scrollTo(empty)
            compose.onNodeWithText(empty).assertIsDisplayed()
            scrollTo("\"\"")
            compose.onNodeWithTag("saved_endpoint_chunk").assertTextEquals("\"\"")
            scrollTo(text(R.string.saved_endpoints_part_next))
            compose.onNodeWithText(text(R.string.saved_endpoints_part_next)).assertIsNotEnabled()
            scrollTo(text(R.string.saved_endpoints_part_previous))
            if (chunk.byteOffset == 0L) compose.onNodeWithText(text(R.string.saved_endpoints_part_previous)).assertIsNotEnabled()
            else click(R.string.saved_endpoints_part_previous)
        }
        compose.runOnIdle { assertEquals(1, previousInspection); assertEquals(0, nextInspection) }
    }

    @Test fun inspectionLoadingFailureAndMissingRowKeepActionsExplicitWithoutPayload() {
        val missing = withMemoryArchive(emptyList()) {
            LegacyEndpointValueChunkReader(it).readChunk(CancellationSignal(), -11, LegacyEndpointField.ENDPOINT)
        }
        assertEquals(LegacyEndpointChunkState.ROW_ABSENT, missing.state)
        val requested = SavedEndpointInspectionState(-11, LegacyEndpointField.ENDPOINT, byteOffset = 512)
        state = SavedEndpointsState(locked = false, inspection = requested)
        render()
        scrollTo(text(R.string.saved_endpoints_loading))
        compose.onNodeWithText(text(R.string.saved_endpoints_loading)).assertIsDisplayed()
        compose.onNodeWithTag("saved_endpoint_chunk").assertDoesNotExist()
        listOf(R.string.saved_endpoints_part_previous, R.string.saved_endpoints_part_next).forEach { id ->
            scrollTo(text(id)); compose.onNodeWithText(text(id)).assertIsNotEnabled()
        }
        compose.runOnIdle { state = state.copy(inspection = requested.copy(loading = false, readFailed = true)) }
        scrollTo(text(R.string.saved_endpoints_read_failed))
        compose.onNodeWithText(text(R.string.saved_endpoints_read_failed)).assertIsDisplayed()
        click(R.string.common_retry)
        compose.runOnIdle { assertEquals(1, retryInspection); assertEquals(0, retry) }
        compose.onNodeWithTag("saved_endpoint_chunk").assertDoesNotExist()
        compose.runOnIdle { state = state.copy(inspection = inspection(missing)) }
        scrollTo(text(R.string.saved_endpoints_part_unavailable))
        compose.onNodeWithText(text(R.string.saved_endpoints_part_unavailable)).assertIsDisplayed()
        listOf(R.string.saved_endpoints_part_previous, R.string.saved_endpoints_part_next).forEach { id ->
            scrollTo(text(id)); compose.onNodeWithText(text(id)).assertIsNotEnabled()
        }
        click(R.string.saved_endpoints_part_return)
        compose.runOnIdle { assertEquals(1, returnToList) }
    }

    @Test fun unlockedInspectionSystemBackAndReturnButtonInvokeDistinctCallbacks() {
        val chunk = withMemoryArchive(listOf(MemoryArchiveRow(2, "name".toByteArray(), "endpoint".toByteArray()))) {
            LegacyEndpointValueChunkReader(it).readChunk(CancellationSignal(), 2, LegacyEndpointField.NAME)
        }
        state = SavedEndpointsState(locked = false, inspection = inspection(chunk))
        render()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { assertEquals(1, back); assertEquals(0, returnToList) }
        click(R.string.saved_endpoints_part_return)
        compose.runOnIdle { assertEquals(1, back); assertEquals(1, returnToList) }
        click(R.string.wallet_recovery_back)
        compose.runOnIdle { assertEquals(2, back); assertEquals(1, returnToList) }
    }


    @Test fun nativeMalformedByteNeighborsAndStrictUtf8BoundariesRenderExactly() {
        val cases = listOf(
            byteArrayOf(78, -1, 0) to "\"N\\xff\\u0000\"",
            byteArrayOf(-97, -104, -128, 116, 97, 105, 108) to "\"\\x9f\\x98\\x80tail\"",
            byteArrayOf(-64, -128, 65) to "\"\\xc0\\x80A\"",
            byteArrayOf(-19, -96, -128, 66) to "\"\\xed\\xa0\\x80B\"",
            byteArrayOf(-12, -112, -128, -128, 67) to "\"\\xf4\\x90\\x80\\x80C\"",
            ("😀".toByteArray() + byteArrayOf(-1) + "雪".toByteArray()) to "\"😀\\xff雪\"",
            "\\xff".toByteArray() to "\"\\\\xff\""
        )
        val chunks = withMemoryArchive(cases.mapIndexed { index, (bytes, _) ->
            MemoryArchiveRow(-100L - index, "name".toByteArray(), bytes)
        }) { database ->
            cases.mapIndexed { index, _ ->
                LegacyEndpointValueChunkReader(database).readChunk(CancellationSignal(), -100L - index, LegacyEndpointField.ENDPOINT)
            }
        }
        state = SavedEndpointsState(locked = false, inspection = inspection(chunks.first()))
        render(fontScale = 2f)
        cases.forEachIndexed { index, (bytes, expected) ->
            assertEquals(bytes.toList(), checkNotNull(chunks[index].bytes).toList())
            compose.runOnIdle { state = state.copy(inspection = inspection(chunks[index])) }
            compose.onNodeWithTag("saved_endpoints_list").performScrollToNode(hasTestTag("saved_endpoint_chunk"))
            compose.onNodeWithTag("saved_endpoint_chunk").assertTextEquals(expected).assertIsDisplayed()
            compose.onNode(hasText(expected) and hasClickAction()).assertDoesNotExist()
        }
    }

    private fun inspection(chunk: LegacyEndpointValueChunk) = SavedEndpointInspectionState(
        chunk.originalRowId, chunk.field, chunk.byteOffset, chunk = chunk, loading = false
    )

    private data class MemoryArchiveRow(val id: Long, val name: ByteArray, val endpoint: ByteArray)

    /** No named database, application database, dependency injection, schema upgrade, or on-disk fixture. */
    private fun <T> withMemoryArchive(rows: List<MemoryArchiveRow>, read: (SupportSQLiteDatabase) -> T): T {
        assertFalse(Looper.getMainLooper().isCurrentThread)
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(compose.activity)
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE nodes (id INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL, link TEXT NOT NULL, " +
                        "networkType INTEGER NOT NULL, isDefault INTEGER NOT NULL, isActive INTEGER NOT NULL)")
                    rows.forEach { row ->
                        db.execSQL("INSERT INTO nodes(id,name,link,networkType,isDefault,isActive) " +
                            "VALUES (?,CAST(? AS TEXT),CAST(? AS TEXT),77,0,0)", arrayOf(row.id, row.name, row.endpoint))
                    }
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    error("In-memory component fixture must never upgrade")
                }
            }).build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        try {
            assertNull(helper.databaseName)
            val db = helper.writableDatabase
            db.query("PRAGMA database_list").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("main", cursor.getString(1))
                assertEquals("", cursor.getString(2))
                assertFalse(cursor.moveToNext())
            }
            db.execSQL("PRAGMA query_only = ON")
            return read(db)
        } finally {
            helper.close()
        }
    }

    private fun render(fontScale: Float = 1f, english: Boolean = false) {
        val context = if (english) {
            compose.activity.createConfigurationContext(Configuration(compose.activity.resources.configuration).apply {
                setLocales(LocaleList(Locale.ENGLISH))
            })
        } else compose.activity
        renderContext = context
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context,
                LocalConfiguration provides context.resources.configuration,
                LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                FearlessAppTheme {
                    Box(Modifier.requiredWidth(320.dp).requiredHeight(440.dp)) {
                        SavedEndpointsScreen(state, { back++ }, { unlock++ }, { refresh++ }, { retry++ }, { previous++ }, { next++ },
                            { rowId, field -> inspections += rowId to field }, { retryInspection++ }, { previousInspection++ },
                            { nextInspection++ }, { returnToList++ })
                    }
                }
            }
        }
    }

    private fun scrollTo(text: String) {
        compose.onNodeWithTag("saved_endpoints_list").performScrollToNode(hasText(text))
    }
    private fun click(id: Int) {
        scrollTo(text(id))
        compose.onNodeWithText(text(id)).assertIsEnabled().performClick()
    }
    private fun text(id: Int, vararg arguments: Any): String = (renderContext ?: compose.activity).getString(id, *arguments)
    private fun value(text: String) = LegacyEndpointText(LegacyEndpointStorage.TEXT,
        text.toByteArray(Charsets.UTF_8).size.toLong(), LegacyEndpointTextState.AVAILABLE, text)
    private fun record(id: Long, name: LegacyEndpointText = value("test"), endpoint: LegacyEndpointText = value("wss://example.invalid")) =
        LegacyEndpointRecord(id, name, endpoint, LegacyEndpointInteger(LegacyEndpointStorage.INTEGER, null, 77),
            LegacyEndpointInteger(LegacyEndpointStorage.INTEGER, null, 0), LegacyEndpointInteger(LegacyEndpointStorage.INTEGER, null, 0))
    private fun page(record: LegacyEndpointRecord) = LegacyEndpointPage(true, listOf(record), null, null)
}
