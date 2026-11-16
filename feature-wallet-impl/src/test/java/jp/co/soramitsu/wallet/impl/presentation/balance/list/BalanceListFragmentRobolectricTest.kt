package jp.co.soramitsu.wallet.impl.presentation.balance.list

import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.MutableLiveData
import coil.ImageLoader
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.UniversalWalletPasswordNotice.Outcome
import jp.co.soramitsu.account.api.domain.UniversalWalletPasswordNotice.Row
import jp.co.soramitsu.account.api.domain.UniversalWalletSlot
import jp.co.soramitsu.common.PLAY_MARKET_APP_URI
import jp.co.soramitsu.common.compose.component.AssetBalanceViewState
import jp.co.soramitsu.common.compose.component.ChainSelectorViewStateWithFilters
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.MainToolbarViewStateWithFilters
import jp.co.soramitsu.common.data.network.coingecko.FiatChooserEvent
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.common.presentation.FiatCurrenciesChooserBottomSheetDialog
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.view.PrimaryButton
import jp.co.soramitsu.common.view.bottomSheet.AlertBottomSheet
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.feature_wallet_impl.BuildConfig
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures.string
import jp.co.soramitsu.wallet.impl.presentation.HiltFragmentHost
import jp.co.soramitsu.wallet.impl.presentation.StubHiltActivity
import jp.co.soramitsu.wallet.impl.presentation.StubHiltApplication
import jp.co.soramitsu.wallet.impl.presentation.balance.assetActions.buy.BuyMixin
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowToast

/** The wallet home wires its toolbar, notices, sheets, toasts and store link to its view model. */
@RunWith(RobolectricTestRunner::class)
@Config(application = StubHiltApplication::class, qualifiers = "w420dp-h2400dp")
class BalanceListFragmentRobolectricTest {

    @get:Rule
    val compose = createEmptyComposeRule().apply { mainClock.autoAdvance = false }

    private val fiatChooser = MutableLiveData<FiatChooserEvent>()
    private val unsupportedChain = MutableLiveData<Event<Unit>>()
    private val securityNotice = MutableLiveData<Event<Unit>>()
    private val passwordNotice = MutableLiveData<Event<UniversalPasswordNoticeState>>()
    private val passwordResult = MutableLiveData<Event<UniversalPasswordNoticeState>>()
    private val playMarket = MutableLiveData<Event<Unit>>()
    private val tokensArrived = MutableLiveData<Event<List<String>>>()
    private val viewModel = mockk<BalanceListViewModel>(relaxed = true)
    private val imageLoader = mockk<ImageLoader>(relaxed = true)
    private var host: ActivityController<StubHiltActivity>? = null

    @Before
    fun setUp() {
        ComposeFixtures.prepareUiTest()
        every { viewModel.state } returns MutableStateFlow(walletState())
        every { viewModel.arrivalPulse } returns MutableStateFlow(0)
        every { viewModel.toolbarState } returns MutableStateFlow(
            MainToolbarViewStateWithFilters(
                title = WALLET_NAME,
                selectorViewState = ChainSelectorViewStateWithFilters(selectedChainName = CHAIN_NAME, allowChainSelection = true)
            )
        )
        every { viewModel.showFiatChooser } returns fiatChooser
        every { viewModel.showUnsupportedChainAlert } returns unsupportedChain
        every { viewModel.showUpdateSecurityNotice } returns securityNotice
        every { viewModel.showUniversalPasswordNotice } returns passwordNotice
        every { viewModel.universalPasswordSwitchResult } returns passwordResult
        every { viewModel.openPlayMarket } returns playMarket
        every { viewModel.tokensArrived } returns tokensArrived
        every { viewModel.showProviderChooserEvent } returns MutableLiveData<Event<BuyMixin.ProviderChooserPayload>>()
        every { viewModel.integrateWithBuyProviderEvent } returns MutableLiveData<Event<BuyMixin.IntegrationPayload>>()
    }

    @After
    fun tearDown() {
        HiltFragmentHost.close(host)
    }

    @Test
    fun resumedWalletRendersAndReportsItsVisibility() {
        host = hostList()

        compose.onNodeWithText(WALLET_NAME).assertExists()
        compose.onNodeWithText(string(R.string.wallet_all_assets_hidden)).assertExists()
        verify { viewModel.onResume() }
        verify { viewModel.onScreenVisibilityChanged(true) }

        HiltFragmentHost.close(host)
        host = null

        verify { viewModel.onScreenVisibilityChanged(false) }
    }

    @Test
    fun toolbarOpensSearchWalletsAndNetworks() {
        host = hostList()

        compose.onNodeWithContentDescription(string(R.string.ux_search)).performClick()
        ComposeFixtures.passDoubleTapGuard()
        compose.onNodeWithContentDescription(string(R.string.ux_switch_wallet)).performClick()
        ComposeFixtures.passDoubleTapGuard()
        compose.onNodeWithText(CHAIN_NAME).performClick()
        settle()

        verify { viewModel.openSearchAssets() }
        verify { viewModel.openWalletSelector() }
        verify { viewModel.openSelectChain() }
    }

    @Test
    fun fiatChooserReportsTheChosenCurrency() {
        host = hostList()
        val usd = FiatCurrency("usd", "$", "US Dollar", "https://icons/usd.svg")
        val eur = FiatCurrency("eur", "€", "Euro", "https://icons/eur.svg")

        fiatChooser.value = Event(DynamicListBottomSheet.Payload(listOf(usd, eur), usd))
        ShadowLooper.idleMainLooper()
        val dialog = ShadowDialog.getLatestDialog() as FiatCurrenciesChooserBottomSheetDialog

        assertEquals(
            string(jp.co.soramitsu.common.R.string.common_currency),
            dialog.findViewById<TextView>(jp.co.soramitsu.common.R.id.dynamicListSheetTitle)!!.text.toString()
        )
        dialog.itemClicked(eur)
        ShadowLooper.idleMainLooper()

        verify { viewModel.onFiatSelected(eur) }
        assertFalse(dialog.isShowing)
    }

    @Test
    fun unsupportedChainAlertOffersTheUpdate() {
        host = hostList()

        unsupportedChain.value = Event(Unit)
        ShadowLooper.idleMainLooper()
        val alert = ShadowDialog.getLatestDialog() as AlertBottomSheet

        assertEquals(string(R.string.update_needed_text), alert.textOf(jp.co.soramitsu.common.R.id.alertTitle))
        alert.findViewById<View>(jp.co.soramitsu.common.R.id.alertConfirmButton)!!.performClick()
        ShadowLooper.idleMainLooper()

        verify { viewModel.updateAppClicked() }
        assertFalse(alert.isShowing)
    }

    @Test
    fun updateSecurityNoticeIsAcknowledgedOnOk() {
        host = hostList()

        securityNotice.value = Event(Unit)
        ShadowLooper.idleMainLooper()
        val alert = ShadowDialog.getLatestDialog() as AlertBottomSheet

        assertEquals(string(R.string.update_security_notice_title), alert.textOf(jp.co.soramitsu.common.R.id.alertTitle))
        alert.findViewById<View>(jp.co.soramitsu.common.R.id.alertConfirmButton)!!.performClick()
        ShadowLooper.idleMainLooper()

        verify { viewModel.onUpdateSecurityNoticeClosed() }
    }

    @Test
    fun universalPasswordNoticeSwitchesShowsTheResultAndReportsItsClose() {
        host = hostList()
        val state = UniversalPasswordNoticeState(META_ID, listOf(Row(UniversalWalletSlot.SOLANA, "OldSol", "NewSol")))

        passwordNotice.value = Event(state)
        ShadowLooper.idleMainLooper()
        val sheet = ShadowDialog.getLatestDialog() as UniversalPasswordNoticeSheet
        // A second notice while one is open is ignored.
        passwordNotice.value = Event(state)
        ShadowLooper.idleMainLooper()
        assertSame(sheet, ShadowDialog.getLatestDialog())

        sheet.findViewById<PrimaryButton>(R.id.universalPasswordSwitch)!!.performClick()
        verify { viewModel.onUniversalPasswordSwitch(META_ID) }

        passwordResult.value = Event(
            UniversalPasswordNoticeState(META_ID, listOf(Row(UniversalWalletSlot.SOLANA, "OldSol", "NewSol", outcome = Outcome.SWITCHED)))
        )
        ShadowLooper.idleMainLooper()
        assertEquals(string(R.string.common_ok), sheet.findViewById<PrimaryButton>(R.id.universalPasswordSwitch)!!.text.toString())

        sheet.findViewById<Button>(R.id.universalPasswordLater)!!.performClick()
        ShadowLooper.idleMainLooper()

        assertFalse(sheet.isShowing)
        verify { viewModel.onUniversalPasswordNoticeClosed(META_ID) }
    }

    @Test
    fun playMarketEventOpensTheStoreListing() {
        host = hostList()

        playMarket.value = Event(Unit)
        ShadowLooper.idleMainLooper()
        val intent = shadowOf(host!!.get()).nextStartedActivity

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(Uri.parse(PLAY_MARKET_APP_URI), intent.data)
    }

    @Test
    fun arrivedTokensAreAnnouncedInAToast() {
        host = hostList()

        tokensArrived.value = Event(listOf("DOT", "KSM"))
        ShadowLooper.idleMainLooper()

        assertEquals(
            string(jp.co.soramitsu.common.R.string.fw_feedback_tokens_received, "DOT, KSM"),
            ShadowToast.getTextOfLatestToast()
        )
    }

    @Test
    fun debugBroadcastsDriveTheFeedbackSimulator() {
        // The simulator is registered only in debug builds, which the unit tests run against.
        assumeTrue(BuildConfig.DEBUG)
        host = hostList()
        val activity = host!!.get()
        val prefix = activity.packageName + ".debug."

        activity.sendBroadcast(Intent(prefix + "FEEDBACK_ARRIVAL"))
        activity.sendBroadcast(Intent(prefix + "FEEDBACK_SENT"))
        activity.sendBroadcast(Intent(prefix + "SEAL").putExtra("stage", "ready"))
        ShadowLooper.idleMainLooper()

        verify { viewModel.debugSimulateFeedback(false) }
        verify { viewModel.debugSimulateFeedback(true) }
        verify { viewModel.debugSeal("ready") }
    }

    private fun hostList(): ActivityController<StubHiltActivity> {
        val controller = HiltFragmentHost.host(
            BalanceListFragment(),
            mapOf(BalanceListViewModel::class.java to viewModel),
            inject = { it.imageLoader = imageLoader }
        )
        settle()
        return controller
    }

    private fun settle() {
        ShadowLooper.idleMainLooper()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun AlertBottomSheet.textOf(id: Int): String = findViewById<TextView>(id)!!.text.toString()

    private fun walletState() = WalletState.default.copy(
        assetsState = WalletAssetsState.Assets(AssetsLoadingState.Loaded(emptyList(), allAssetsHidden = true), isHideVisible = true),
        balance = AssetBalanceViewState("$85.00", "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY", true, ChangeBalanceViewState("+1%", "+$1"))
    )

    private companion object {
        const val WALLET_NAME = "Main wallet"
        const val CHAIN_NAME = "Kusama Relay"
        const val META_ID = 42L
    }
}
