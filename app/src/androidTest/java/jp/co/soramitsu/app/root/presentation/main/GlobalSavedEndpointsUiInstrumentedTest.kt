package jp.co.soramitsu.app.root.presentation.main

import android.content.Context
import android.content.res.Configuration
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
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.account.impl.presentation.recovery.SavedEndpointRecordInspectionState
import jp.co.soramitsu.account.impl.presentation.recovery.SavedEndpointsScreen
import jp.co.soramitsu.account.impl.presentation.recovery.SavedEndpointsState
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.coredb.recovery.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/** Component rendering with public, non-secret data; authentication is qualified by the real PIN journey. */
@RunWith(AndroidJUnit4::class)
class GlobalSavedEndpointsUiInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<PolkaswapNavigationLayoutTestActivity>()
    private var state by mutableStateOf(SavedEndpointsState())
    private var locale by mutableStateOf(Locale.ENGLISH)
    private var context: Context? = null
    private val selected = mutableListOf<EndpointInventorySource>()
    private val inspected = mutableListOf<Pair<EndpointRecordRef, EndpointField>>()
    private var retry = 0
    private var previous = 0
    private var next = 0
    private var returned = 0

    @Test fun lockedAndRevokedStatesHideGlobalRecordsChunksAndCollectionPicker() {
        val selectedChunk = bytesChunk("wss://public.invalid/".toByteArray())
        state = SavedEndpointsState(locked = true, source = EndpointInventorySource.OWNED,
            inventoryPage = page(), inventoryInspection = inspection(selectedChunk))
        render()
        compose.onNodeWithTag("saved_endpoint_collection").assertDoesNotExist()
        compose.onNodeWithTag("saved_endpoint_inventory_chunk").assertDoesNotExist()
        compose.onNodeWithText("\"wss://public.invalid/\"").assertDoesNotExist()
        compose.runOnIdle { state = state.copy(locked = false) }
        scrollToTag("saved_endpoint_inventory_chunk")
        compose.onNodeWithTag("saved_endpoint_inventory_chunk").assertTextEquals("\"wss://public.invalid/\"")
        compose.runOnIdle { state = state.copy(locked = true) }
        compose.onNodeWithTag("saved_endpoint_inventory_chunk").assertDoesNotExist()
        compose.onNodeWithTag("saved_endpoint_collection").assertDoesNotExist()
    }

    @Test fun collectionPickerCarriesAllFiveExactSourcesAtDoubleTextSizeInTurkish() {
        state = SavedEndpointsState(locked = false)
        locale = Locale("tr")
        render(2f)
        EndpointInventorySource.values().forEach { source ->
            scrollToTag("saved_endpoint_collection")
            compose.onNodeWithTag("saved_endpoint_collection").performClick()
            compose.onNodeWithTag("saved_endpoint_source_${source.name.lowercase(Locale.ROOT)}")
                .performScrollTo().assertIsDisplayed().performClick()
        }
        compose.runOnIdle { assertEquals(EndpointInventorySource.values().toList(), selected) }
    }

    @Test fun absentAndPresentEmptyCollectionsRemainDistinctAndWrongSourcePagesStayHidden() {
        state = SavedEndpointsState(locked = false, source = EndpointInventorySource.OWNED,
            inventoryPage = EndpointPage(EndpointInventorySource.OWNED, false, null, emptyList(), null, null))
        render()
        see(R.string.saved_endpoint_source_absent)
        compose.runOnIdle { state = state.copy(inventoryPage = state.inventoryPage!!.copy(present = true)) }
        see(R.string.saved_endpoints_empty)
        compose.runOnIdle { state = state.copy(inventoryPage = page(EndpointInventorySource.CURRENT)) }
        compose.onNodeWithText("\"public name\"").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.saved_endpoint_historical_notice)).assertDoesNotExist()
    }

    @Test fun ownershipLabelsDistinguishPendingAttachedAndRemovedWithoutSelectionClaims() {
        state = SavedEndpointsState(locked = false, source = EndpointInventorySource.OWNED, inventoryPage = page())
        render()
        listOf(
            EndpointOwnership(false, false, null) to R.string.saved_endpoint_pending,
            EndpointOwnership(true, false, 0) to R.string.saved_endpoint_attached,
            EndpointOwnership(false, true, 0) to R.string.saved_endpoint_deleted
        ).forEach { (ownership, label) ->
            compose.runOnIdle { state = state.copy(inventoryPage = page(ownership = ownership)) }
            see(label)
            compose.onNodeWithText(text(R.string.saved_endpoint_selection_value)).assertDoesNotExist()
        }
    }

    @Test fun historicalRawFlagsAndNetworkReferenceRemainVisibleWithoutBooleanCoercion() {
        val record = record(EndpointInventorySource.CURRENT).let {
            it.copy(values = it.values + mapOf(EndpointField.IS_DEFAULT to EndpointValue.Integer(-9),
                EndpointField.IS_ACTIVE to EndpointValue.Real(0.75), EndpointField.CHAIN_REFERENCE to EndpointValue.Null))
        }
        state = SavedEndpointsState(locked = false, source = EndpointInventorySource.CURRENT,
            inventoryPage = page(EndpointInventorySource.CURRENT).copy(records = listOf(record)))
        render(2f)
        see(R.string.saved_endpoint_historical_notice)
        see(R.string.saved_endpoints_not_saved)
        scrollTo("-9"); compose.onNodeWithText("-9").assertIsDisplayed()
        scrollTo("0.75"); compose.onNodeWithText("0.75").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.saved_endpoint_attached)).assertDoesNotExist()
    }

    @Test fun anomalousValuesOfferExactSourceRowRevisionAndFieldInspection() {
        val opaque = record().let {
            it.copy(values = mapOf(
                EndpointField.NAME to EndpointValue.Text(2049, EndpointTextState.OVERSIZED, null),
                EndpointField.ENDPOINT to EndpointValue.Blob(3),
                EndpointField.CHAIN_REFERENCE to EndpointValue.Text(1, EndpointTextState.INVALID_UTF8, null)))
        }
        state = SavedEndpointsState(locked = false, source = EndpointInventorySource.OWNED,
            inventoryPage = page().copy(records = listOf(opaque)))
        render()
        listOf(EndpointField.NAME to R.string.saved_endpoints_name, EndpointField.ENDPOINT to R.string.saved_endpoints_url,
            EndpointField.CHAIN_REFERENCE to R.string.saved_endpoint_chain_reference).forEach { (_, label) ->
            val action = text(R.string.saved_endpoint_inspect_field, text(label))
            scrollTo(action); compose.onNodeWithText(action).assertIsEnabled().performClick()
        }
        compose.runOnIdle {
            assertEquals(listOf(EndpointField.NAME, EndpointField.ENDPOINT, EndpointField.CHAIN_REFERENCE)
                .map { ref() to it }, inspected)
        }
    }

    @Test fun maximumTextIsFullyEscapedAndNeverMadeClickableAtDoubleTextSize() {
        val value = "x".repeat(2044) + "\u0000雪"
        assertEquals(2048, value.toByteArray().size)
        val updated = record().let { it.copy(values = it.values + (EndpointField.ENDPOINT to textValue(value))) }
        state = SavedEndpointsState(locked = false, source = EndpointInventorySource.OWNED,
            inventoryPage = page().copy(records = listOf(updated)))
        render(2f)
        val expected = "\"" + "x".repeat(2044) + "\\u0000雪\""
        scrollTo(expected); compose.onNodeWithText(expected).assertExists()
        compose.onNode(hasText(expected) and hasClickAction()).assertDoesNotExist()
    }

    @Test fun invalidBytesAndSplitUnicodePartsDisplayExactBytesAndCurrentRanges() {
        val first = ByteArray(511) { 'a'.code.toByte() } + byteArrayOf(0xf0.toByte())
        val second = byteArrayOf(0x9f.toByte(), 0x98.toByte(), 0x80.toByte(), 0, 0xff.toByte())
        val chunks = listOf(bytesChunk(first, total = 517), bytesChunk(second, offset = 512, total = 517))
        val expected = listOf("\"" + "a".repeat(511) + "\\xf0\"", "\"\\x9f\\x98\\x80\\u0000\\xff\"")
        state = SavedEndpointsState(locked = false, source = EndpointInventorySource.OWNED,
            inventoryInspection = inspection(chunks.first()))
        render(2f)
        chunks.forEachIndexed { index, chunk ->
            compose.runOnIdle { state = state.copy(inventoryInspection = inspection(chunk)) }
            scrollToTag("saved_endpoint_inventory_chunk")
            compose.onNodeWithTag("saved_endpoint_inventory_chunk").assertTextEquals(expected[index])
            compose.onNode(hasText(expected[index]) and hasClickAction()).assertDoesNotExist()
            val range = text(R.string.saved_endpoints_part_range, (chunk.byteOffset + 1).toString(),
                (chunk.byteOffset + chunk.bytes!!.size).toString(), "517")
            scrollTo(range); compose.onNodeWithText(range).assertIsDisplayed()
        }
    }

    @Test fun staleRevisionStopsPartNavigationAndOffersReturnWithoutRetryingNewPayload() {
        val stale = EndpointValueChunk(ref(), EndpointField.NAME, 512, EndpointChunkState.STALE_REVISION,
            null, null, null, observedRevision = 4)
        state = SavedEndpointsState(locked = false, source = EndpointInventorySource.OWNED,
            inventoryInspection = inspection(stale))
        render()
        see(R.string.saved_endpoint_changed)
        listOf(R.string.saved_endpoints_part_previous, R.string.saved_endpoints_part_next).forEach {
            scrollTo(text(it)); compose.onNodeWithText(text(it)).assertIsNotEnabled()
        }
        compose.onNodeWithTag("saved_endpoint_inventory_chunk").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.common_retry)).assertDoesNotExist()
        click(R.string.saved_endpoints_part_return)
        compose.runOnIdle { assertEquals(1, returned); assertEquals(0, next); assertEquals(0, previous); assertEquals(0, retry) }
    }

    @Test fun nullNumericAndUnavailablePartsNeverInventBytePayloads() {
        val rawRef = EndpointRecordRef(EndpointInventorySource.CURRENT, -17)
        context = compose.activity.createConfigurationContext(Configuration(compose.activity.resources.configuration).apply {
            setLocales(LocaleList(Locale.ENGLISH))
        })
        val cases = listOf(
            EndpointValueChunk(rawRef, EndpointField.IS_ACTIVE, 0, EndpointChunkState.NULL_VALUE,
                EndpointStorage.NULL, null, null) to text(R.string.saved_endpoints_not_saved),
            EndpointValueChunk(rawRef, EndpointField.IS_ACTIVE, 0, EndpointChunkState.NON_BYTE_STORAGE,
                EndpointStorage.INTEGER, null, null, integerValue = Long.MIN_VALUE) to Long.MIN_VALUE.toString(),
            EndpointValueChunk(rawRef, EndpointField.IS_ACTIVE, 0, EndpointChunkState.NON_BYTE_STORAGE,
                EndpointStorage.REAL, null, null, realValue = -0.5) to "-0.5",
            EndpointValueChunk(rawRef, EndpointField.IS_ACTIVE, 0, EndpointChunkState.ROW_ABSENT,
                null, null, null) to text(R.string.saved_endpoints_part_unavailable),
            EndpointValueChunk(rawRef, EndpointField.IS_ACTIVE, 0, EndpointChunkState.SOURCE_ABSENT,
                null, null, null) to text(R.string.saved_endpoints_part_unavailable)
        )
        state = SavedEndpointsState(locked = false, source = EndpointInventorySource.CURRENT,
            inventoryInspection = inspection(cases.first().first))
        render()
        cases.forEach { (chunk, message) ->
            compose.runOnIdle { state = state.copy(inventoryInspection = inspection(chunk)) }
            scrollTo(message); compose.onNodeWithText(message).assertIsDisplayed()
            compose.onNodeWithTag("saved_endpoint_inventory_chunk").assertDoesNotExist()
        }
    }

    @Test fun partControlsUseTheirOwnCallbacksAndLoadingDisablesBoth() {
        state = SavedEndpointsState(locked = false, source = EndpointInventorySource.OWNED,
            inventoryInspection = inspection(bytesChunk(ByteArray(512) { 65 }, offset = 512, total = 1536)))
        render()
        click(R.string.saved_endpoints_part_previous); click(R.string.saved_endpoints_part_next)
        compose.runOnIdle { state = state.copy(inventoryInspection = state.inventoryInspection!!.copy(loading = true)) }
        listOf(R.string.saved_endpoints_part_previous, R.string.saved_endpoints_part_next).forEach {
            scrollTo(text(it)); compose.onNodeWithText(text(it)).assertIsNotEnabled()
        }
        compose.runOnIdle { state = state.copy(inventoryInspection =
            state.inventoryInspection!!.copy(loading = false, chunk = null, readFailed = true)) }
        click(R.string.common_retry)
        compose.runOnIdle { assertEquals(1, previous); assertEquals(1, next); assertEquals(1, retry) }
    }

    @Test fun allShippedLocalesRenderStatusAndInspectActionsAtNarrowDoubleTextSize() {
        val opaque = record().let { it.copy(values = it.values + (EndpointField.ENDPOINT to EndpointValue.Blob(4))) }
        state = SavedEndpointsState(locked = false, source = EndpointInventorySource.OWNED,
            inventoryPage = page().copy(records = listOf(opaque)))
        render(2f)
        listOf("en", "ja", "zh", "ru", "pt", "in", "tr", "vi").forEach { language ->
            compose.runOnIdle { locale = Locale(language) }
            compose.waitForIdle()
            compose.runOnIdle {
                assertEquals("The rendered locale must follow the requested locale", locale, checkNotNull(context).resources.configuration.locales[0])
            }
            see(R.string.saved_endpoint_pending)
            val label = text(R.string.saved_endpoint_inspect_field, text(R.string.saved_endpoints_url))
            scrollTo(label); compose.onNodeWithText(label).assertIsDisplayed().assertIsEnabled().performClick()
        }
        compose.runOnIdle { assertEquals(List(8) { ref() to EndpointField.ENDPOINT }, inspected) }
    }

    private fun render(fontScale: Float = 1f) {
        compose.setContent {
            val requestedLocale = this@GlobalSavedEndpointsUiInstrumentedTest.locale
            val local = compose.activity.createConfigurationContext(Configuration(compose.activity.resources.configuration).apply {
                setLocales(LocaleList(requestedLocale))
            })
            assertEquals("The context must use the requested locale", requestedLocale, local.resources.configuration.locales[0])
            context = local
            CompositionLocalProvider(LocalContext provides local, LocalConfiguration provides local.resources.configuration,
                LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                FearlessAppTheme {
                    Box(Modifier.requiredWidth(320.dp).requiredHeight(440.dp)) {
                        SavedEndpointsScreen(state, {}, {}, {}, {}, {}, {},
                            selectSource = { selected += it },
                            inspectRecord = { ref, field -> inspected += ref to field },
                            retryRecordInspection = { retry++ }, previousRecordInspection = { previous++ },
                            nextRecordInspection = { next++ }, returnToList = { returned++ })
                    }
                }
            }
        }
    }
    private fun scrollTo(value: String) = compose.onNodeWithTag("saved_endpoints_list").performScrollToNode(hasText(value))
    private fun scrollToTag(tag: String) = compose.onNodeWithTag("saved_endpoints_list").performScrollToNode(hasTestTag(tag))
    private fun text(id: Int, vararg args: Any) = (context ?: compose.activity).getString(id, *args)
    private fun see(id: Int) { scrollTo(text(id)); compose.onNodeWithText(text(id)).assertIsDisplayed() }
    private fun click(id: Int) { scrollTo(text(id)); compose.onNodeWithText(text(id)).assertIsEnabled().performClick() }
    private fun ref() = EndpointRecordRef(EndpointInventorySource.OWNED, 7, 3)
    private fun textValue(value: String) = EndpointValue.Text(value.toByteArray().size.toLong(), EndpointTextState.AVAILABLE, value)
    private fun record(source: EndpointInventorySource = EndpointInventorySource.OWNED,
                       ownership: EndpointOwnership = EndpointOwnership(false, false, null)): EndpointRecord {
        val values = linkedMapOf<EndpointField, EndpointValue>(EndpointField.NAME to textValue("public name"),
            EndpointField.ENDPOINT to textValue("wss://public.invalid/"), EndpointField.CHAIN_REFERENCE to textValue("public-chain"))
        if (source != EndpointInventorySource.OWNED) {
            values[EndpointField.IS_DEFAULT] = EndpointValue.Integer(0)
            values[EndpointField.IS_ACTIVE] = EndpointValue.Integer(0)
        }
        return EndpointRecord(if (source == EndpointInventorySource.OWNED) ref() else EndpointRecordRef(source, -17),
            values, ownership.takeIf { source == EndpointInventorySource.OWNED })
    }
    private fun page(source: EndpointInventorySource = EndpointInventorySource.OWNED,
                     ownership: EndpointOwnership = EndpointOwnership(false, false, null)) =
        EndpointPage(source, true, EndpointSourceProvenance(81, 81).takeIf { source != EndpointInventorySource.OWNED },
            listOf(record(source, ownership)), null, null)
    private fun bytesChunk(bytes: ByteArray, offset: Long = 0, total: Long = bytes.size.toLong()) =
        EndpointValueChunk(ref(), EndpointField.ENDPOINT, offset, EndpointChunkState.AVAILABLE, EndpointStorage.TEXT,
            total, bytes, observedRevision = 3, ownership = EndpointOwnership(false, false, null))
    private fun inspection(chunk: EndpointValueChunk) =
        SavedEndpointRecordInspectionState(chunk.ref, chunk.field, chunk.byteOffset, chunk, loading = false)
}
