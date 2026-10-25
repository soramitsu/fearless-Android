package jp.co.soramitsu.app.root.presentation.main

import android.content.Context
import android.content.res.Configuration
import android.os.CancellationSignal
import android.os.LocaleList
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.account.impl.presentation.recovery.SavedEndpointAttachmentStatus
import jp.co.soramitsu.account.impl.presentation.recovery.SavedEndpointDecisionActions
import jp.co.soramitsu.account.impl.presentation.recovery.SavedEndpointDecisionForm
import jp.co.soramitsu.account.impl.presentation.recovery.SavedEndpointsScreen
import jp.co.soramitsu.account.impl.presentation.recovery.SavedEndpointsState
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.coredb.recovery.EndpointInventorySource
import jp.co.soramitsu.coredb.recovery.EndpointRecordRef
import jp.co.soramitsu.coredb.recovery.decision.EndpointDecisionTargetCatalog
import jp.co.soramitsu.coredb.recovery.decision.EndpointDecisionTargetPage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/** Public synthetic component state only; actual PIN, SQL saves and upgrade are qualified separately. */
@RunWith(AndroidJUnit4::class)
class SavedEndpointAttachmentUiInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<PolkaswapNavigationLayoutTestActivity>()
    private var state by mutableStateOf(SavedEndpointsState())
    private var locale by mutableStateOf(Locale.ENGLISH)
    private var context: Context? = null
    private var retryLoad = 0
    private var retryTargets = 0
    private var reload = 0
    private var returned = 0
    private val pages = mutableListOf<Int>()
    private val saves = mutableListOf<SavedEndpointDecisionForm>()
    private val adds = mutableListOf<SavedEndpointDecisionForm>()
    private val restores = mutableListOf<SavedEndpointDecisionForm>()
    private val identities = mutableListOf<Any>()

    @Test fun explicitAddAndRestoreUseExactRenderedSnapshotInAllEightLocalesAtDoubleTextSize() {
        val ready = form().let { it.copy(url = "wss://public.invalid/", target = it.targets!!.options.first().target,
            choosingTarget = false, attachmentReady = true) }
        state = SavedEndpointsState(locked = false, decisionForm = ready)
        render(2f)
        listOf("en", "ja", "zh", "ru", "pt", "in", "tr", "vi").forEach { language ->
            compose.runOnIdle { locale = Locale(language) }
            compose.waitForIdle()
            compose.runOnIdle {
                assertEquals("The rendered locale must follow the requested locale", locale, checkNotNull(context).resources.configuration.locales[0])
            }
            scrollTag("saved_endpoint_attachment_action")
            compose.onNodeWithTag("saved_endpoint_attachment_action").assertIsDisplayed().assertIsEnabled().performClick()
            scrollTag("saved_endpoint_attachment_restore")
            compose.onNodeWithTag("saved_endpoint_attachment_restore").assertIsDisplayed().assertIsEnabled().performClick()
        }
        compose.runOnIdle {
            assertEquals(8, adds.size); assertEquals(8, restores.size)
            assertTrue(adds.all { it === ready }); assertTrue(restores.all { it === ready })
            assertTrue(saves.isEmpty())
        }
    }

    @Test fun unsavedUnsupportedAndNonOwnedRecordsDoNotExposeInapplicableActions() {
        val ready = form().let { it.copy(url = "wss://public.invalid/", target = it.targets!!.options.first().target,
            choosingTarget = false) }
        state = SavedEndpointsState(locked = false, decisionForm = ready)
        render()
        scrollTag(SAVE)
        compose.onNodeWithTag("saved_endpoint_attachment_action").assertDoesNotExist()
        compose.runOnIdle { state = state.copy(decisionForm = ready.copy(attachmentReady = true,
            target = ready.targets!!.options[1].target)) }
        scrollTag(SAVE)
        compose.onNodeWithTag("saved_endpoint_attachment_action").assertDoesNotExist()
        compose.runOnIdle { state = state.copy(decisionForm = ready.copy(attachmentReady = true,
            ref = EndpointRecordRef(EndpointInventorySource.CURRENT, 7))) }
        scrollTag("saved_endpoint_attachment_action")
        compose.onNodeWithTag("saved_endpoint_attachment_action").assertIsEnabled()
        compose.onNodeWithTag("saved_endpoint_attachment_restore").assertDoesNotExist()
        compose.runOnIdle { assertTrue(adds.isEmpty()); assertTrue(restores.isEmpty()) }
    }

    @Test fun verifyingDisablesBothActionsAndEditsWhileReturnRemainsReachable() {
        val busy = form().let { it.copy(url = "wss://public.invalid/", target = it.targets!!.options.first().target,
            choosingTarget = false, attachmentReady = true, attaching = true, saved = true) }
        state = SavedEndpointsState(locked = false, decisionForm = busy)
        render()
        scrollTag(NAME); compose.onNodeWithTag(NAME).assertIsNotEnabled()
        scrollTag(SAVE); compose.onNodeWithTag(SAVE).assertIsNotEnabled()
        compose.onNodeWithTag("saved_endpoint_choice_saved").assertDoesNotExist()
        scrollTag("saved_endpoint_attachment_action"); compose.onNodeWithTag("saved_endpoint_attachment_action").assertIsNotEnabled()
        scrollTag("saved_endpoint_attachment_restore"); compose.onNodeWithTag("saved_endpoint_attachment_restore").assertIsNotEnabled()
        click(R.string.saved_endpoints_part_return)
        compose.runOnIdle { assertEquals(1, returned); assertTrue(adds.isEmpty()); assertTrue(restores.isEmpty()) }
    }

    @Test fun transientResultsShowCorrectMessageWithoutStalePendingLabelOrImplicitSelection() {
        val ready = form().let { it.copy(url = "wss://public.invalid/", target = it.targets!!.options.first().target,
            choosingTarget = false, attachmentReady = true, saved = true) }
        state = SavedEndpointsState(locked = false, decisionForm = ready)
        render(2f)
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
        messages.forEach { (status, message) ->
            compose.runOnIdle { state = state.copy(decisionForm = ready.copy(attachmentStatus = status,
                attachmentRequiresRefresh = status == SavedEndpointAttachmentStatus.REATTACHED_INACTIVE)) }
            see(message)
            compose.onNodeWithTag("saved_endpoint_attachment_status").assertTextEquals(text(message))
            compose.onNodeWithTag("saved_endpoint_choice_saved").assertDoesNotExist()
            if (status in setOf(SavedEndpointAttachmentStatus.ADDED_INACTIVE, SavedEndpointAttachmentStatus.REATTACHED_INACTIVE,
                    SavedEndpointAttachmentStatus.PRESENT_ACTIVE, SavedEndpointAttachmentStatus.PRESENT_INACTIVE)) {
                compose.onNodeWithTag("saved_endpoint_attachment_action").assertDoesNotExist()
            }
            if (status == SavedEndpointAttachmentStatus.REATTACHED_INACTIVE) {
                scrollTag(NAME); compose.onNodeWithTag(NAME).assertIsNotEnabled()
                scrollTag(SAVE); compose.onNodeWithTag(SAVE).assertIsNotEnabled()
            }
        }
        compose.runOnIdle { assertTrue(adds.isEmpty()); assertTrue(restores.isEmpty()); assertTrue(saves.isEmpty()) }
    }

    @Test fun lockedOrRevokedReadyFormExposesNoAttachmentActionOrStatus() {
        val ready = form().let { it.copy(url = "wss://public.invalid/", target = it.targets!!.options.first().target,
            choosingTarget = false, attachmentReady = true, attachmentStatus = SavedEndpointAttachmentStatus.MISMATCH) }
        state = SavedEndpointsState(locked = true, decisionForm = ready)
        render()
        compose.onNodeWithTag("saved_endpoint_attachment_action").assertDoesNotExist()
        compose.onNodeWithTag("saved_endpoint_attachment_status").assertDoesNotExist()
        compose.runOnIdle { state = state.copy(locked = false) }
        scrollTag("saved_endpoint_attachment_action"); compose.onNodeWithTag("saved_endpoint_attachment_action").assertIsDisplayed()
        compose.runOnIdle { state = state.copy(locked = true) }
        compose.onNodeWithTag("saved_endpoint_attachment_action").assertDoesNotExist()
        compose.onNodeWithTag("saved_endpoint_attachment_status").assertDoesNotExist()
    }

    @Test fun staleSourceKeepsChoiceEditableAndRequiresSaveWithoutAnAttachmentButton() {
        val stale = form().let { it.copy(name = "Preserved choice", url = "wss://public.invalid/",
            target = it.targets!!.options.first().target, choosingTarget = false,
            attachmentReady = false, attachmentSourceChanged = true) }
        state = SavedEndpointsState(locked = false, decisionForm = stale)
        render(2f)
        see(R.string.saved_endpoint_attachment_source_changed)
        compose.onNodeWithTag("saved_endpoint_attachment_source_changed")
            .assertTextEquals(text(R.string.saved_endpoint_attachment_source_changed))
        compose.onNodeWithTag("saved_endpoint_attachment_action").assertDoesNotExist()
        scrollTag(NAME); compose.onNodeWithTag(NAME).assertTextContains(stale.name).assertIsEnabled()
        scrollTag(SAVE); compose.onNodeWithTag(SAVE).assertIsEnabled().performClick()
        compose.runOnIdle { assertSame(stale, saves.single()); assertTrue(adds.isEmpty()); assertTrue(restores.isEmpty()) }
    }

    private fun render(fontScale: Float = 1f, registry: SaveableStateRegistry? = null) {
        compose.setContent {
            val requestedLocale = this@SavedEndpointAttachmentUiInstrumentedTest.locale
            val local = compose.activity.createConfigurationContext(Configuration(compose.activity.resources.configuration).apply {
                setLocales(LocaleList(requestedLocale))
            })
            assertEquals("The context must use the requested locale", requestedLocale, local.resources.configuration.locales[0])
            context = local
            CompositionLocalProvider(LocalContext provides local, LocalConfiguration provides local.resources.configuration,
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
                LocalSaveableStateRegistry provides (registry ?: LocalSaveableStateRegistry.current)) {
                if (registry != null) rememberSaveable { "saveable-control" }
                FearlessAppTheme {
                    Box(Modifier.requiredWidth(320.dp).requiredHeight(440.dp)) {
                        SavedEndpointsScreen(state, {}, {}, {}, {}, {}, {}, returnToList = { returned++ },
                            decisionActions = SavedEndpointDecisionActions(
                                retryLoad = { identities += it; retryLoad++ },
                                name = { identity, value -> identities += identity; update { it.copy(name = value) } },
                                url = { identity, value -> identities += identity; update { it.copy(url = value) } },
                                choose = { identity, option ->
                                    identities += identity
                                    update { it.copy(target = option.target, targetName = option.name, choosingTarget = false) }
                                },
                                page = { identity, direction ->
                                    identities += identity; pages += direction
                                    update { it.copy(choosingTarget = true) }
                                },
                                retryTargets = { identities += it; retryTargets++ },
                                reload = { identities += it; reload++ },
                                save = { saves += it }, attach = { adds += it }, restore = { restores += it }))
                    }
                }
            }
        }
    }

    private fun update(change: (SavedEndpointDecisionForm) -> SavedEndpointDecisionForm) {
        state = state.copy(decisionForm = change(checkNotNull(state.decisionForm)))
    }
    private fun form() = SavedEndpointDecisionForm(Any(), EndpointRecordRef(EndpointInventorySource.OWNED, 7, 3),
        targets = catalog(), loading = false)
    private fun catalog(): EndpointDecisionTargetPage {
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(compose.activity)
            .name(null).callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE chains(id TEXT PRIMARY KEY,name TEXT,ecosystem TEXT,isEthereumBased INTEGER," +
                        "isEthereumChain INTEGER,isTestNet INTEGER,history_type TEXT)")
                    listOf("1".padStart(64, '0'), "unsupported-local-network", "invalid").forEachIndexed { index, id ->
                        db.execSQL("INSERT INTO chains VALUES(?,?,?,0,0,0,NULL)",
                            arrayOf<Any?>(id, "Public network ${index + 1}", if (index == 2) null else "Substrate"))
                    }
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        return try { EndpointDecisionTargetCatalog(helper.writableDatabase).readPage(CancellationSignal()) }
        finally { helper.close() }
    }
    private fun scrollTag(tag: String) = compose.onNodeWithTag("saved_endpoints_list").performScrollToNode(hasTestTag(tag))
    private fun text(id: Int) = checkNotNull(context).getString(id)
    private fun see(id: Int) {
        compose.onNodeWithTag("saved_endpoints_list").performScrollToNode(hasText(text(id)))
        compose.onNodeWithText(text(id)).assertIsDisplayed()
    }
    private fun click(id: Int) {
        see(id)
        compose.onNodeWithText(text(id)).assertIsEnabled().performClick()
    }

    companion object {
        private const val NAME = "saved_endpoint_choice_name"
        private const val URL = "saved_endpoint_choice_url"
        private const val SAVE = "saved_endpoint_choice_save"
    }
}
