package jp.co.soramitsu.app.root.presentation.main

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.CancellationSignal
import android.os.LocaleList
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.Locale
import java.util.UUID
import jp.co.soramitsu.account.impl.presentation.recovery.SavedEndpointDecisionActions
import jp.co.soramitsu.account.impl.presentation.recovery.SavedEndpointDecisionForm
import jp.co.soramitsu.account.impl.presentation.recovery.SavedEndpointSelectionState
import jp.co.soramitsu.account.impl.presentation.recovery.SavedEndpointsScreen
import jp.co.soramitsu.account.impl.presentation.recovery.SavedEndpointsState
import jp.co.soramitsu.app.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.core.runtime.EndpointRuntimeStatus
import jp.co.soramitsu.coredb.recovery.EndpointInventorySource
import jp.co.soramitsu.coredb.recovery.EndpointRecordRef
import jp.co.soramitsu.coredb.recovery.decision.EndpointDecisionTarget
import jp.co.soramitsu.coredb.recovery.decision.EndpointDecisionTargetCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic presentation only; reflection creates observations, never authentication or mutation grants. */
@RunWith(AndroidJUnit4::class)
class SavedEndpointSelectionUiInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<PolkaswapNavigationLayoutTestActivity>()
    private var state by mutableStateOf(SavedEndpointsState())
    private var locale by mutableStateOf(Locale.ENGLISH)
    private var scale by mutableStateOf(1f)
    private var context: Context? = null
    private val selections = mutableListOf<SavedEndpointDecisionForm>()
    private val refreshes = mutableListOf<SavedEndpointDecisionForm>()
    private val node = ChainNode("wss://public.invalid/selected", "Public selected", true, false)
    private val captures by lazy {
        File(compose.activity.cacheDir, "endpoint-selection-layout-${UUID.randomUUID()}").also { check(it.mkdir()) }
    }

    @Test
    fun allSelectionMessagesAndActionsFitEightLocalesAtNormalAndDoubleText() {
        val ready = form()
        render()
        for (language in listOf("en", "ja", "zh", "ru", "pt", "in", "tr", "vi")) {
            for (fontScale in listOf(1f, 2f)) {
                compose.runOnIdle {
                    locale = Locale(language)
                    scale = fontScale
                }
                compose.waitForIdle()
                compose.runOnIdle {
                    assertEquals("The rendered locale must follow the requested locale", locale, checkNotNull(context).resources.configuration.locales[0])
                }
                if (language != "en") {
                    val english = compose.activity.createConfigurationContext(
                        Configuration(compose.activity.resources.configuration).apply { setLocales(LocaleList(Locale.ENGLISH)) }
                    )
                    for (id in messages.values + runtimeMessages.values + listOf(R.string.saved_endpoint_select, R.string.saved_endpoint_selection_refresh)) {
                        assertNotEquals("Locale must resolve its own translated message", english.getString(id), text(id))
                    }
                }
                for ((phase, id) in messages) {
                    val displayed = ready.copy(selection = selection(phase))
                    compose.runOnIdle { state = SavedEndpointsState(locked = false, decisionForm = displayed) }
                    scroll("saved_endpoint_selection_status")
                    compose.onNodeWithTag("saved_endpoint_selection_status").assertIsDisplayed().assertTextEquals(text(id))
                    if (phase == SavedEndpointSelectionState.Phase.AVAILABLE) {
                        click("saved_endpoint_select")
                        compose.runOnIdle { assertSame(displayed, selections.last()) }
                    } else compose.onNodeWithTag("saved_endpoint_select").assertDoesNotExist()
                    if (phase in setOf(SavedEndpointSelectionState.Phase.FAILED, SavedEndpointSelectionState.Phase.STALE)) {
                        click("saved_endpoint_selection_refresh")
                        compose.runOnIdle { assertSame(displayed, refreshes.last()) }
                    } else compose.onNodeWithTag("saved_endpoint_selection_refresh").assertDoesNotExist()
                    if (fontScale == 2f && phase in setOf(SavedEndpointSelectionState.Phase.AVAILABLE, SavedEndpointSelectionState.Phase.STALE)) {
                        capture("${language}-${phase.name}")
                    }
                }
                for ((runtime, id) in runtimeMessages) {
                    val status = if (runtime == SavedEndpointSelectionState.Runtime.PREFERRED) null
                        else observedRuntime(EndpointRuntimeStatus.Phase.valueOf(runtime.name), true, node, true)
                    val family = if (runtime == SavedEndpointSelectionState.Runtime.PREFERRED) EndpointDecisionTarget.Family.SOLANA
                        else EndpointDecisionTarget.Family.SUBSTRATE
                    compose.runOnIdle {
                        state = SavedEndpointsState(locked = false, decisionForm = ready.copy(
                            selection = selection(SavedEndpointSelectionState.Phase.SAVED, family, status)
                        ))
                    }
                    scroll("saved_endpoint_runtime_status")
                    compose.onNodeWithTag("saved_endpoint_runtime_status").assertIsDisplayed().assertTextEquals(text(id))
                    if (fontScale == 2f && runtime in setOf(SavedEndpointSelectionState.Runtime.PREFERRED, SavedEndpointSelectionState.Runtime.CONNECTED)) {
                        capture("${language}-${runtime.name}")
                    }
                }
            }
        }
        compose.runOnIdle {
            assertEquals(16, selections.size)
            assertEquals(32, refreshes.size)
        }
    }

    @Test
    fun busyAndLockedFormsCannotSelectOrRefresh() {
        val ready = form().copy(selection = selection(SavedEndpointSelectionState.Phase.AVAILABLE))
        state = SavedEndpointsState(locked = false, decisionForm = ready.copy(saving = true))
        render()
        scroll("saved_endpoint_select")
        compose.onNodeWithTag("saved_endpoint_select").assertIsNotEnabled()
        compose.runOnIdle {
            state = state.copy(decisionForm = ready.copy(
                attaching = true, selection = selection(SavedEndpointSelectionState.Phase.STALE)
            ))
        }
        scroll("saved_endpoint_selection_refresh")
        compose.onNodeWithTag("saved_endpoint_selection_refresh").assertIsNotEnabled()
        compose.runOnIdle { state = state.copy(locked = true) }
        for (tag in listOf("saved_endpoint_select", "saved_endpoint_selection_refresh", "saved_endpoint_selection_status", "saved_endpoint_runtime_status")) {
            compose.onNodeWithTag(tag).assertDoesNotExist()
        }
        assertTrue(selections.isEmpty())
        assertTrue(refreshes.isEmpty())
    }

    @Test
    fun connectedTextRequiresMatchingCurrentAppliedAcknowledgment() {
        val ready = form()
        render()
        val rejected = listOf(
            observedRuntime(EndpointRuntimeStatus.Phase.CONNECTED, false, node, true),
            observedRuntime(EndpointRuntimeStatus.Phase.CONNECTED, true, node.copy(url = "wss://public.invalid/other"), true),
            observedRuntime(EndpointRuntimeStatus.Phase.CONNECTED, true, node, false)
        )
        for (status in rejected) {
            compose.runOnIdle {
                state = SavedEndpointsState(locked = false, decisionForm = ready.copy(
                    selection = selection(SavedEndpointSelectionState.Phase.SAVED, runtime = status)
                ))
            }
            scroll("saved_endpoint_runtime_status")
            compose.onNodeWithTag("saved_endpoint_runtime_status")
                .assertIsDisplayed().assertTextEquals(text(R.string.saved_endpoint_runtime_pending))
        }
        assertFalse(selections.isNotEmpty() || refreshes.isNotEmpty())
    }

    private fun selection(
        phase: SavedEndpointSelectionState.Phase,
        family: EndpointDecisionTarget.Family = EndpointDecisionTarget.Family.SUBSTRATE,
        runtime: EndpointRuntimeStatus? = null
    ): SavedEndpointSelectionState = SavedEndpointSelectionState::class.java.getDeclaredConstructor(
        SavedEndpointSelectionState.Phase::class.java, EndpointDecisionTarget.Family::class.java,
        ChainNode::class.java, EndpointRuntimeStatus::class.java, Function0::class.java
    ).newInstance(phase, family, node, runtime, { true })

    private fun observedRuntime(
        phase: EndpointRuntimeStatus.Phase,
        applied: Boolean,
        selected: ChainNode,
        current: Boolean
    ): EndpointRuntimeStatus = EndpointRuntimeStatus::class.java.getDeclaredConstructor(
        EndpointRuntimeStatus.Phase::class.java, Boolean::class.javaPrimitiveType,
        ChainNode::class.java, Function0::class.java, Int::class.javaPrimitiveType
    ).newInstance(phase, applied, selected, { current }, 0)

    private fun form(): SavedEndpointDecisionForm {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(compose.activity).name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE chains(id TEXT PRIMARY KEY,name TEXT,ecosystem TEXT,isEthereumBased INTEGER," +
                            "isEthereumChain INTEGER,isTestNet INTEGER,history_type TEXT)")
                        db.execSQL("INSERT INTO chains VALUES(?,?,'Substrate',0,0,0,NULL)",
                            arrayOf<Any?>("1".padStart(64, '0'), "Public network"))
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build()
        )
        val targets = try { EndpointDecisionTargetCatalog(helper.writableDatabase).readPage(CancellationSignal()) }
            finally { helper.close() }
        return SavedEndpointDecisionForm(
            Any(), EndpointRecordRef(EndpointInventorySource.OWNED, 7, 3), targets = targets, loading = false,
            url = node.url, name = node.name, target = targets.options.first().target, choosingTarget = false, attachmentReady = true
        )
    }

    private fun render() {
        compose.setContent {
            val requestedLocale = this@SavedEndpointSelectionUiInstrumentedTest.locale
            val local = compose.activity.createConfigurationContext(
                Configuration(compose.activity.resources.configuration).apply { setLocales(LocaleList(requestedLocale)) }
            )
            assertEquals("The context must use the requested locale", requestedLocale, local.resources.configuration.locales[0])
            context = local
            CompositionLocalProvider(
                LocalContext provides local, LocalConfiguration provides local.resources.configuration,
                LocalDensity provides Density(LocalDensity.current.density, scale)
            ) {
                FearlessAppTheme {
                    Box(Modifier.requiredWidth(320.dp).requiredHeight(440.dp)) {
                        SavedEndpointsScreen(
                            state, {}, {}, {}, {}, {}, {}, returnToList = {},
                            decisionActions = SavedEndpointDecisionActions(
                                select = { selections += it }, refreshSelection = { refreshes += it }
                            )
                        )
                    }
                }
            }
        }
    }

    private fun scroll(tag: String) = compose.onNodeWithTag("saved_endpoints_list").performScrollToNode(hasTestTag(tag))
    private fun click(tag: String) {
        scroll(tag)
        compose.onNodeWithTag(tag).assertIsDisplayed().assertIsEnabled().performClick()
    }
    private fun text(id: Int) = checkNotNull(context).getString(id)
    private fun capture(name: String) {
        val file = File(captures, "$name.png")
        check(file.createNewFile())
        file.outputStream().use { output ->
            check(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }

    private val messages = linkedMapOf(
        SavedEndpointSelectionState.Phase.CHECKING to R.string.saved_endpoint_selection_checking,
        SavedEndpointSelectionState.Phase.AVAILABLE to R.string.saved_endpoint_selection_available,
        SavedEndpointSelectionState.Phase.NOT_ATTACHED to R.string.saved_endpoint_selection_unattached,
        SavedEndpointSelectionState.Phase.SAVING to R.string.saved_endpoint_selection_saving,
        SavedEndpointSelectionState.Phase.SAVED to R.string.saved_endpoint_selection_saved,
        SavedEndpointSelectionState.Phase.FAILED to R.string.saved_endpoint_selection_failed,
        SavedEndpointSelectionState.Phase.STALE to R.string.saved_endpoint_selection_stale
    )
    private val runtimeMessages = linkedMapOf(
        SavedEndpointSelectionState.Runtime.PREFERRED to R.string.saved_endpoint_runtime_preferred,
        SavedEndpointSelectionState.Runtime.PENDING to R.string.saved_endpoint_runtime_pending,
        SavedEndpointSelectionState.Runtime.NOT_IN_USE to R.string.saved_endpoint_runtime_not_in_use,
        SavedEndpointSelectionState.Runtime.UNAVAILABLE to R.string.saved_endpoint_runtime_unavailable,
        SavedEndpointSelectionState.Runtime.PAUSED to R.string.saved_endpoint_runtime_paused,
        SavedEndpointSelectionState.Runtime.CONNECTING to R.string.saved_endpoint_runtime_connecting,
        SavedEndpointSelectionState.Runtime.CONNECTED to R.string.saved_endpoint_runtime_connected
    )
}

