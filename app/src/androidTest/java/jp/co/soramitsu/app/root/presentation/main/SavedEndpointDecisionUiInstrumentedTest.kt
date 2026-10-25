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
class SavedEndpointDecisionUiInstrumentedTest {
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
    private val identities = mutableListOf<Any>()

    @Test fun lockedAndRevokedFormsHideFieldsAndActionsEvenWithRetainedSyntheticState() {
        state = SavedEndpointsState(locked = true, decisionForm = form().copy(name = "private fixture name"))
        render()
        compose.onNodeWithTag(NAME).assertDoesNotExist()
        compose.onNodeWithTag(URL).assertDoesNotExist()
        compose.onNodeWithTag(SAVE).assertDoesNotExist()
        compose.runOnIdle { state = state.copy(locked = false) }
        scrollTag(NAME)
        compose.onNodeWithTag(NAME).assertTextContains("private fixture name")
        compose.runOnIdle { state = state.copy(locked = true) }
        compose.onNodeWithTag(NAME).assertDoesNotExist()
        compose.onNodeWithText("private fixture name").assertDoesNotExist()
        compose.onNodeWithTag(SAVE).assertDoesNotExist()
    }

    @Test fun editsAndChoiceCarryExactIdentityAndSaveUsesTheRenderedSnapshot() {
        val initial = form()
        state = SavedEndpointsState(locked = false, decisionForm = initial)
        render()
        scrollTag(SAVE)
        compose.onNodeWithTag(SAVE).assertIsNotEnabled()
        val name = "  exact 雪 name  "
        val url = "https://public.invalid/path?text=%E9%9B%AA"
        scrollTag(NAME)
        compose.onNodeWithTag(NAME).performTextReplacement(name)
        scrollTag(URL)
        compose.onNodeWithTag(URL).performTextReplacement(url)
        scrollTag("saved_endpoint_choice_target_1")
        compose.onNodeWithTag("saved_endpoint_choice_target_1").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("saved_endpoint_choice_target_1").assertDoesNotExist()
        scrollTag("saved_endpoint_choice_selected")
        compose.onNodeWithTag("saved_endpoint_choice_selected").assertTextEquals(
            context!!.getString(R.string.saved_endpoint_choice_selected, "\"${"1".padStart(64, '0')}\"")
        )
        lateinit var expected: SavedEndpointDecisionForm
        compose.runOnIdle {
            expected = state.decisionForm!!
            assertEquals(name, expected.name)
            assertEquals(url, expected.url)
            assertSame(initial.targets!!.options.first().target, expected.target)
        }
        scrollTag(SAVE)
        compose.onNodeWithTag(SAVE).assertIsEnabled().performClick()
        compose.runOnIdle {
            assertSame(expected, saves.single())
            assertTrue(identities.all { it === initial.identity })
            state = state.copy(decisionForm = expected.copy(saved = true))
        }
        see(R.string.saved_endpoint_choice_saved)
    }

    @Test fun unavailableTargetHasNoChoiceAndUnsupportedTargetRemainsExplicitlyPending() {
        state = SavedEndpointsState(locked = false, decisionForm = form().copy(url = "wss://public.invalid/"))
        render()
        see(R.string.saved_endpoint_choice_unavailable)
        compose.onNodeWithTag("saved_endpoint_choice_target_3").assertDoesNotExist()
        scrollTag("saved_endpoint_choice_target_2")
        compose.onNodeWithTag("saved_endpoint_choice_target_2").assertIsEnabled().performClick()
        see(R.string.saved_endpoint_choice_unsupported)
        scrollTag(SAVE)
        compose.onNodeWithTag(SAVE).assertIsEnabled()
        click(R.string.common_choose_network)
        compose.runOnIdle { assertEquals(listOf(0), pages) }
    }

    @Test fun loadingFailureAndCatalogRetryNeverEnableAnIncompleteSave() {
        val initial = form().copy(loading = true, targets = null)
        state = SavedEndpointsState(locked = false, decisionForm = initial)
        render()
        scrollTag(NAME); compose.onNodeWithTag(NAME).assertIsNotEnabled()
        scrollTag(URL); compose.onNodeWithTag(URL).assertIsNotEnabled()
        scrollTag(SAVE); compose.onNodeWithTag(SAVE).assertIsNotEnabled()
        compose.runOnIdle {
            state = state.copy(decisionForm = initial.copy(loading = false, loadFailed = true))
        }
        click(R.string.common_retry)
        compose.runOnIdle {
            assertEquals(1, retryLoad)
            state = state.copy(decisionForm = initial.copy(loading = false, targetsFailed = true))
        }
        click(R.string.common_retry)
        compose.runOnIdle {
            assertEquals(1, retryTargets)
            assertTrue(identities.all { it === initial.identity })
        }
        scrollTag(SAVE); compose.onNodeWithTag(SAVE).assertIsNotEnabled()
    }

    @Test fun savingDisablesEditsAndFailureRetainsTextWithReloadAndReturnActions() {
        val ready = form().let { it.copy(name = "retained name", url = "wss://public.invalid/",
            target = it.targets!!.options.first().target, choosingTarget = false, saving = true) }
        state = SavedEndpointsState(locked = false, decisionForm = ready)
        render()
        scrollTag(NAME); compose.onNodeWithTag(NAME).assertIsNotEnabled()
        scrollTag(URL); compose.onNodeWithTag(URL).assertIsNotEnabled()
        scrollTag(SAVE); compose.onNodeWithTag(SAVE).assertIsNotEnabled()
        compose.runOnIdle { state = state.copy(decisionForm = ready.copy(saving = false, saveFailed = true)) }
        click(R.string.saved_endpoint_choice_reload)
        scrollTag(NAME); compose.onNodeWithTag(NAME).assertTextContains(ready.name)
        scrollTag(URL); compose.onNodeWithTag(URL).assertTextContains(ready.url)
        click(R.string.saved_endpoints_part_return)
        compose.runOnIdle { assertEquals(1, reload); assertEquals(1, returned); assertTrue(saves.isEmpty()) }
    }

    @Test fun allEightLocalesKeepActionsReachableAtNarrowDoubleTextSize() {
        val ready = form().let { it.copy(url = "wss://public.invalid/", target = it.targets!!.options.first().target,
            choosingTarget = false, saveFailed = true, saved = true) }
        state = SavedEndpointsState(locked = false, decisionForm = ready)
        render(2f)
        listOf("en", "ja", "zh", "ru", "pt", "in", "tr", "vi").forEach { language ->
            compose.runOnIdle { locale = Locale(language) }
            compose.waitForIdle()
            compose.runOnIdle {
                assertEquals("The rendered locale must follow the requested locale", locale, checkNotNull(context).resources.configuration.locales[0])
            }
            see(R.string.saved_endpoint_choice_saved)
            click(R.string.saved_endpoint_choice_reload)
            scrollTag(SAVE)
            compose.onNodeWithTag(SAVE).assertIsDisplayed().assertIsEnabled().performClick()
            click(R.string.saved_endpoints_part_return)
        }
        compose.runOnIdle {
            assertEquals(8, reload); assertEquals(8, returned); assertEquals(8, saves.size)
            assertTrue(saves.all { it === ready })
        }
    }

    @Test fun focusedTextFieldsDoNotRegisterCandidateTextForInstanceStateSaving() {
        val registry = SaveableStateRegistry(restoredValues = null, canBeSaved = { true })
        val ready = form().copy(name = "unique private candidate 947", url = "https://public.invalid/private-238")
        state = SavedEndpointsState(locked = false, decisionForm = ready)
        render(registry = registry)
        scrollTag(NAME); compose.onNodeWithTag(NAME).performClick()
        scrollTag(URL); compose.onNodeWithTag(URL).performClick()
        compose.runOnIdle {
            val saved = registry.performSave().toString()
            assertTrue("Control proves the supplied saveable registry is active", saved.contains("saveable-control"))
            assertFalse(saved.contains(ready.name))
            assertFalse(saved.contains(ready.url))
        }
    }

    private fun render(fontScale: Float = 1f, registry: SaveableStateRegistry? = null) {
        compose.setContent {
            val requestedLocale = this@SavedEndpointDecisionUiInstrumentedTest.locale
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
                                save = { saves += it }))
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
