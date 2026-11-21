package jp.co.soramitsu.app.root.presentation.main

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.lang.reflect.Proxy
import jp.co.soramitsu.account.impl.domain.recovery.ArchivedWalletRecovery
import jp.co.soramitsu.account.impl.domain.recovery.WalletPinRecovery
import jp.co.soramitsu.account.impl.domain.recovery.WalletRecoveryInventory
import jp.co.soramitsu.account.impl.presentation.appearance.AppearanceScreen
import jp.co.soramitsu.account.impl.presentation.appearance.AppearanceState
import jp.co.soramitsu.account.impl.presentation.mnemonic.backup.BackupMnemonicCallback
import jp.co.soramitsu.account.impl.presentation.mnemonic.backup.BackupMnemonicContent
import jp.co.soramitsu.account.impl.presentation.mnemonic.backup.BackupMnemonicState
import jp.co.soramitsu.account.impl.presentation.mnemonic_agreements.MnemonicAgreementsCallback
import jp.co.soramitsu.account.impl.presentation.mnemonic_agreements.MnemonicAgreementsContent
import jp.co.soramitsu.account.impl.presentation.mnemonic_agreements.MnemonicAgreementsState
import jp.co.soramitsu.account.impl.presentation.pincode.PinCodeViewModel
import jp.co.soramitsu.account.impl.presentation.pincode.PinRecoveryScreen
import jp.co.soramitsu.account.impl.presentation.recovery.ArchivedWalletRecoveryScreen
import jp.co.soramitsu.account.impl.presentation.recovery.ArchivedWalletRecoveryViewModel
import jp.co.soramitsu.account.impl.presentation.recovery.RecoveryCenterScreen
import jp.co.soramitsu.account.impl.presentation.recovery.RecoveryTarget
import jp.co.soramitsu.common.AlertViewState
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.AlertSheet
import jp.co.soramitsu.common.compose.component.BannerWalletRecovery
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.ChainSelectorViewStateWithFilters
import jp.co.soramitsu.common.compose.component.GradientIconState
import jp.co.soramitsu.common.compose.component.InfoItemSetViewState
import jp.co.soramitsu.common.compose.component.InfoItemViewState
import jp.co.soramitsu.common.compose.component.MnemonicWordModel
import jp.co.soramitsu.common.compose.component.MnemonicWords
import jp.co.soramitsu.common.compose.component.SelectorState
import jp.co.soramitsu.common.compose.component.SigningDetailViewState
import jp.co.soramitsu.common.compose.component.SigningSummarySection
import jp.co.soramitsu.common.compose.component.SigningSummaryViewState
import jp.co.soramitsu.common.compose.component.TextSelectableItemState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.component.WalletItem
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.compose.component.WalletNameItemViewState
import jp.co.soramitsu.common.compose.component.WalletSelectorScreen
import jp.co.soramitsu.common.compose.component.WalletSelectorViewState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.fw.FwAppearance
import jp.co.soramitsu.common.compose.theme.fw.FwEffects
import jp.co.soramitsu.common.compose.theme.fw.FwHighContrast
import jp.co.soramitsu.common.compose.theme.fw.FwNotice
import jp.co.soramitsu.common.compose.theme.fw.FwNoticeTone
import jp.co.soramitsu.common.compose.theme.fw.FwStandard
import jp.co.soramitsu.common.compose.theme.fw.FwTier
import jp.co.soramitsu.common.compose.theme.fw.FwTierChoice
import jp.co.soramitsu.common.compose.theme.fw.LocalFwColors
import jp.co.soramitsu.common.compose.theme.fw.LocalFwEffects
import jp.co.soramitsu.common.compose.theme.fw.LocalFwSecureSurface
import jp.co.soramitsu.common.compose.theme.fw.fwPageBackground
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshot
import jp.co.soramitsu.common.presentation.ErrorDialog
import jp.co.soramitsu.common.data.storage.encrypt.WalletRecoveryKind
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope
import jp.co.soramitsu.polkaswap.api.presentation.models.SwapDetailsViewState
import jp.co.soramitsu.polkaswap.impl.presentation.swap_preview.SwapPreviewCallbacks
import jp.co.soramitsu.polkaswap.impl.presentation.swap_preview.SwapPreviewContent
import jp.co.soramitsu.polkaswap.impl.presentation.swap_preview.SwapPreviewState
import jp.co.soramitsu.tonconnect.impl.presentation.tonsignrequest.TonSignPreviewContent
import jp.co.soramitsu.tonconnect.impl.presentation.tonsignrequest.TonSignPreviewScreenInterface
import jp.co.soramitsu.tonconnect.impl.presentation.tonsignrequest.TonSignPreviewViewState
import jp.co.soramitsu.tonconnect.impl.presentation.tonsignrequest.TonSignRequestContent
import jp.co.soramitsu.tonconnect.impl.presentation.tonsignrequest.TonSignRequestScreenInterface
import jp.co.soramitsu.tonconnect.impl.presentation.tonsignrequest.TonSignRequestViewState
import jp.co.soramitsu.wallet.impl.presentation.balance.assetselector.AssetItemState
import jp.co.soramitsu.wallet.impl.presentation.balance.assetselector.AssetSelectContent
import jp.co.soramitsu.wallet.impl.presentation.balance.assetselector.AssetSelectContentInterface
import jp.co.soramitsu.wallet.impl.presentation.balance.assetselector.AssetSelectScreenViewState
import jp.co.soramitsu.wallet.impl.presentation.balance.chainselector.ChainSelectContent
import jp.co.soramitsu.wallet.impl.presentation.balance.chainselector.ChainSelectScreenContract
import jp.co.soramitsu.wallet.impl.presentation.balance.chainselector.toFilteredDecorator
import jp.co.soramitsu.wallet.impl.presentation.balance.walletselector.SelectWalletContent
import jp.co.soramitsu.wallet.impl.presentation.cross_chain.confirm.CrossChainConfirmContent
import jp.co.soramitsu.wallet.impl.presentation.cross_chain.confirm.CrossChainConfirmScreenInterface
import jp.co.soramitsu.wallet.impl.presentation.cross_chain.confirm.CrossChainConfirmViewState
import jp.co.soramitsu.wallet.impl.presentation.manageassets.ManageAssetItemState
import jp.co.soramitsu.wallet.impl.presentation.manageassets.ManageAssetsContent
import jp.co.soramitsu.wallet.impl.presentation.manageassets.ManageAssetsContentInterface
import jp.co.soramitsu.wallet.impl.presentation.manageassets.ManageAssetsScreenViewState
import jp.co.soramitsu.wallet.impl.presentation.send.confirm.ConfirmSendContent
import jp.co.soramitsu.wallet.impl.presentation.send.confirm.ConfirmSendScreenInterface
import jp.co.soramitsu.wallet.impl.presentation.send.confirm.ConfirmSendViewState
import jp.co.soramitsu.walletconnect.impl.presentation.requestpreview.RequestPreviewContent
import jp.co.soramitsu.walletconnect.impl.presentation.requestpreview.RequestPreviewScreenInterface
import jp.co.soramitsu.walletconnect.impl.presentation.requestpreview.RequestPreviewViewState
import jp.co.soramitsu.walletconnect.impl.presentation.sessionproposal.SessionProposalContent
import jp.co.soramitsu.walletconnect.impl.presentation.sessionproposal.SessionProposalScreenInterface
import jp.co.soramitsu.walletconnect.impl.presentation.sessionproposal.SessionProposalViewState
import jp.co.soramitsu.walletconnect.impl.presentation.sessionrequest.SessionRequestContent
import jp.co.soramitsu.walletconnect.impl.presentation.sessionrequest.SessionRequestScreenInterface
import jp.co.soramitsu.walletconnect.impl.presentation.sessionrequest.SessionRequestViewState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The eight surfaces brought into the sun-lit design, rendered from public fixtures: no wallet, no key and
 * no write. Recovery-phrase fixtures use masked words, never a phrase. Each state is saved as a picture for
 * review, and the states an unfunded walk cannot reach (signing, recovery with damaged keys) are covered here.
 */
@RunWith(AndroidJUnit4::class)
class SunlitSurfacesInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<PolkaswapNavigationLayoutTestActivity>()

    private var fixture by mutableStateOf<(@Composable () -> Unit)>({})
    private var fontScale by mutableStateOf(1f)
    private var effects by mutableStateOf<FwEffects?>(null)
    private var secure by mutableStateOf(false)

    private fun host() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                FearlessTheme {
                    val fx = effects ?: LocalFwEffects.current
                    CompositionLocalProvider(
                        LocalFwEffects provides fx,
                        LocalFwColors provides if (fx.highContrast) FwHighContrast else FwStandard,
                        LocalFwSecureSurface provides secure
                    ) {
                        Box(Modifier.requiredWidth(411.dp).requiredHeight(860.dp).fwPageBackground().testTag("surface")) {
                            key(fixture, fontScale) { fixture() }
                        }
                    }
                }
            }
        }
    }

    private fun show(name: String, scale: Float = 1f, fx: FwEffects? = null, content: @Composable () -> Unit) {
        compose.runOnIdle {
            fontScale = scale
            effects = fx
            fixture = content
        }
        compose.waitForIdle()
        save(name)
    }

    private fun save(name: String) {
        write(name, compose.onNodeWithTag("surface").captureToImage().asAndroidBitmap())
    }

    private fun write(name: String, bitmap: Bitmap) {
        val label = InstrumentationRegistry.getArguments().getString("surfacesEvidenceLabel") ?: "default"
        val directory = compose.activity.getExternalFilesDir("sunlit-surfaces-$label")!!.apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    @Test fun pickersAndManageAssetsRenderAtBothTextSizes() {
        host()
        for ((suffix, scale) in listOf("1x" to 1f, "2x" to 2f)) {
            show("pickers-chains-$suffix", scale) { ChainSelectContent(chainPicker, noOp<ChainSelectScreenContract>()) }
            show("pickers-assets-$suffix", scale) { AssetSelectContent(assetPicker, noOp<AssetSelectContentInterface>()) }
            show("pickers-wallets-$suffix", scale) {
                SelectWalletContent(walletPicker, {}, {}, {}, {}, {})
            }
            show("pickers-wallet-sheet-$suffix", scale) { WalletSelectorScreen(walletPicker, {}, {}) }
            show("manage-assets-$suffix", scale) { ManageAssetsContent(manageAssets, noOp<ManageAssetsContentInterface>()) }
        }
    }

    @Test fun chosenWalletAndChainReportTheSelectionAsStateNotOnlyColour() {
        host()
        show("selection-wallet") {
            WalletItem(wallet(1, "Main wallet", selected = true), modifier = Modifier.padding(20.dp).testTag("chosen"))
        }
        compose.onNodeWithTag("chosen").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        show("selection-chain") { ChainSelectContent(chainPicker, noOp<ChainSelectScreenContract>()) }
        compose.onNodeWithTag("ChainItem_image_selected", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun manageGroupCheckReportsMixedStateAndRowsKeepTheirSwitches() {
        host()
        var changed: Boolean? = null
        val callback = proxy<ManageAssetsContentInterface> { name, args ->
            if (name == "onChecked") changed = args?.get(1) as Boolean
        }
        show("manage-assets-group") { ManageAssetsContent(manageAssets, callback) }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("manage_toggle:dot:acala"))
        compose.onNodeWithTag("manage_toggle:dot:acala").assertIsOff().performClick()
        compose.runOnIdle { assertEquals(true, changed) }
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Indeterminate)).assertIsDisplayed()
    }

    @Test fun appearanceChoicesReachTheirCallbacks() {
        host()
        val reduced = mutableListOf<Boolean>()
        val contrast = mutableListOf<Boolean>()
        show("appearance-automatic") {
            AppearanceScreen(AppearanceState(FwAppearance()), rememberScrollState(), {}, { reduced += it }, { contrast += it }, {})
        }
        compose.onNodeWithTag("appearance_effects_reduced").performClick()
        compose.onNodeWithTag("appearance_contrast").assertIsOff().performClick()
        compose.runOnIdle {
            assertEquals(listOf(true), reduced)
            assertEquals(listOf(true), contrast)
        }
        show("appearance-reduced", fx = FwEffects(FwTier.Reduced, highContrast = false, reduceMotion = false)) {
            AppearanceScreen(AppearanceState(FwAppearance(FwTierChoice.Reduced)), rememberScrollState(), {}, {}, {}, {})
        }
        show("appearance-contrast", fx = FwEffects(FwTier.Reduced, highContrast = true, reduceMotion = false)) {
            AppearanceScreen(AppearanceState(FwAppearance(highContrast = true)), rememberScrollState(), {}, {}, {}, {})
        }
        compose.onNodeWithTag("appearance_contrast").assertIsOn()
        show("appearance-kept-reduced", fx = FwEffects(FwTier.Reduced, highContrast = false, reduceMotion = false)) {
            AppearanceScreen(AppearanceState(FwAppearance()), rememberScrollState(), {}, {}, {}, {})
        }
        compose.onNodeWithTag("appearance_kept_reduced").assertIsDisplayed()
        show("appearance-save-failed") {
            AppearanceScreen(AppearanceState(FwAppearance(), saveFailed = true), rememberScrollState(), {}, {}, {}, {})
        }
        compose.onNodeWithTag("appearance_save_failed").assertExists()
        show("appearance-2x", 2f) { AppearanceScreen(AppearanceState(FwAppearance()), rememberScrollState(), {}, {}, {}, {}) }
        compose.onNodeWithTag("appearance_preview").performScrollTo().assertIsDisplayed()
        save("appearance-2x-preview")
    }

    @Test fun recoverySurfacesRenderFlatAtBothTextSizes() {
        host()
        for ((suffix, scale) in listOf("1x" to 1f, "2x" to 2f)) {
            show("recovery-center-$suffix", scale) {
                RecoveryCenterScreen(recoveryCenter, {}, { _, _ -> }, {}, {}, { _, _ -> })
            }
            show("recovery-pin-$suffix", scale) { PinRecoveryScreen(pinRecovery, {}, {}, { _, _ -> }, {}) }
            show("recovery-archived-$suffix", scale) { Archived() }
            show("recovery-banner-$suffix", scale) { Box(Modifier.padding(20.dp)) { BannerWalletRecovery {} } }
        }
    }

    @Test fun pausedAndUnavailableStatesUseStillNotices() {
        host()
        show("unavailable-notices") {
            androidx.compose.foundation.layout.Column(Modifier.padding(20.dp).fillMaxWidth()) {
                FwNotice("Polkamarkt trading is paused in this version. Your positions and balances are unaffected.", tone = FwNoticeTone.Neutral)
                androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
                FwNotice("Sending on Bitcoin is paused right now while we check an issue.", title = "Sending paused")
                androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
                FwNotice("This site is listed as a scam. Nothing can be signed.", title = "Refused", tone = FwNoticeTone.Danger)
            }
        }
        show("unavailable-alert-sheet") {
            AlertSheet(
                AlertViewState("Network unavailable", "The network is unavailable, you can wait or request community support", "Close", 16, R.drawable.ic_alert_16),
                {},
                {}
            )
        }
    }

    @Test fun pausedSheetIsAWindowOnTheSameLitSheet() {
        host()
        compose.waitForIdle()
        compose.runOnUiThread {
            ErrorDialog(
                title = "Sending paused",
                message = "Sending on Bitcoin is paused right now while we check an issue. Your balance is unaffected, and you can still receive.",
                positiveButtonText = "Receive",
                negativeButtonText = "Close"
            ).show(compose.activity.supportFragmentManager)
        }
        compose.waitForIdle()
        // Compose idleness precedes the platform dialog-window animation.
        android.os.SystemClock.sleep(800)
        val window = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        assertTrue("The paused sheet must render", window != null)
        write("unavailable-paused-sheet", window)
    }

    @Test fun signingScreensRenderAndNothingMovesOnThem() {
        host()
        compose.runOnIdle { secure = true }
        fun still(name: String, content: @Composable () -> Unit) {
            show(name, content = content)
            assertStill(name)
        }
        still("signing-confirm-send") { ConfirmSendContent(confirmSend, noOp<ConfirmSendScreenInterface>()) }
        still("signing-confirm-send-loading") {
            ConfirmSendContent(ConfirmSendViewState(null, buttonState = ButtonViewState("Confirm", false)), noOp<ConfirmSendScreenInterface>())
        }
        still("signing-cross-chain") { CrossChainConfirmContent(crossChain, noOp<CrossChainConfirmScreenInterface>()) }
        still("signing-request-preview") { RequestPreviewContent(requestPreview, noOp<RequestPreviewScreenInterface>()) }
        still("signing-session-proposal") { SessionProposalContent(sessionProposal, noOp<SessionProposalScreenInterface>()) }
        still("signing-session-request") { SessionRequestContent(sessionRequest, noOp<SessionRequestScreenInterface>()) }
        still("signing-ton-request") { TonSignRequestContent(tonRequest, noOp<TonSignRequestScreenInterface>()) }
        still("signing-ton-preview") { TonSignPreviewContent(tonPreview, noOp<TonSignPreviewScreenInterface>()) }
        still("signing-swap-preview") { SwapPreviewContent(swapPreview, noOp<SwapPreviewCallbacks>()) }
    }

    @Test fun recoveryPhraseLayoutsUseMaskedFixturesAndStayStill() {
        host()
        compose.runOnIdle { secure = true }
        // Masked on purpose: this fixture shows the layout and never a phrase, not even a sample one.
        val masked = List(12) { MnemonicWordModel((it + 1).toString(), "••••••") }
        for ((suffix, scale) in listOf("1x" to 1f, "2x" to 2f)) {
            show("mnemonic-words-$suffix", scale) { Box(Modifier.padding(20.dp)) { MnemonicWords(masked) } }
            assertEquals(
                "A two-digit number must stay on one line at $suffix",
                compose.onNodeWithText("1").fetchSemanticsNode().size.height,
                compose.onNodeWithText("10").fetchSemanticsNode().size.height
            )
            assertStill("mnemonic-words-$suffix")
            show("mnemonic-backup-$suffix", scale) {
                BackupMnemonicContent(
                    BackupMnemonicState(masked, "SR25519", emptyList(), "", "", false),
                    false,
                    noOp<BackupMnemonicCallback>()
                )
            }
            assertStill("mnemonic-backup-$suffix")
            show("mnemonic-agreements-$suffix", scale) {
                MnemonicAgreementsContent(
                    MnemonicAgreementsState(
                        TextSelectableItemState(true, jp.co.soramitsu.feature_account_impl.R.string.mnemonic_agreements_ageement_1),
                        TextSelectableItemState(false, jp.co.soramitsu.feature_account_impl.R.string.mnemonic_agreements_ageement_2),
                        TextSelectableItemState(false, jp.co.soramitsu.feature_account_impl.R.string.mnemonic_agreements_ageement_3),
                        false
                    ),
                    noOp<MnemonicAgreementsCallback>()
                )
            }
            assertStill("mnemonic-agreements-$suffix")
        }
    }

    /** Stillness: a second frame a full second later must be the same frame, pixel for pixel. */
    private fun assertStill(name: String) {
        compose.mainClock.autoAdvance = false
        try {
            val first = compose.onNodeWithTag("surface").captureToImage().asAndroidBitmap()
            compose.mainClock.advanceTimeBy(1000)
            val second = compose.onNodeWithTag("surface").captureToImage().asAndroidBitmap()
            assertTrue("$name moved on a secure surface", first.sameAs(second))
        } finally {
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
        }
    }

    @Composable
    private fun Archived() {
        ArchivedWalletRecoveryScreen(archived, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {})
    }

    private val chainPicker = ChainSelectScreenContract.State.Impl.FilteringDecorator(
        ChainSelectorViewStateWithFilters.Filter.All,
        ChainSelectorViewStateWithFilters.Filter.All,
        ChainSelectScreenContract.State.Impl(
            chains = listOf(
                ChainSelectScreenContract.State.ItemState.Impl(id = "1", imageUrl = null, title = "Polkadot").toFilteredDecorator(true),
                ChainSelectScreenContract.State.ItemState.Impl(id = "2", imageUrl = null, title = "Kusama").toFilteredDecorator(false),
                ChainSelectScreenContract.State.ItemState.Impl(id = "3", imageUrl = null, title = "Moonbeam").toFilteredDecorator(false)
            ),
            selectedChainId = "2",
            searchQuery = null
        )
    )

    private val assetPicker = AssetSelectScreenViewState(
        assets = listOf(
            AssetItemState("dot", null, "Polkadot Asset Hub", "DOT", "12.5", "$54.12", isSelected = true, chainId = "1"),
            AssetItemState("usdt", null, "Polkadot Asset Hub", "USDT", "7", "$7.00", chainId = "1"),
            AssetItemState("wnd", null, "Westend", "WND", "0", "", chainId = "2", isTestnet = true)
        ),
        hiddenAssets = listOf(AssetItemState("usdc", null, "Polkadot Asset Hub", "USDC", null, "", chainId = "1")),
        isHiddenExpanded = true
    )

    private fun wallet(id: Long, title: String, selected: Boolean = false) = WalletItemViewState(
        id = id,
        balance = "1,250.00",
        assetSymbol = "$",
        title = title,
        walletIcon = R.drawable.ic_wallet,
        isSelected = selected
    )

    private val walletPicker = WalletSelectorViewState(
        wallets = listOf(wallet(1, "Main wallet"), wallet(2, "Savings"), wallet(3, "Staking")),
        selectedWallet = wallet(2, "Savings")
    )

    private fun managed(key: String, symbol: String, chain: String, checked: Boolean, grouped: Boolean = false, amount: String = "0") =
        ManageAssetItemState(
            canonicalAssetKey = key, id = key, imageUrl = null, chainName = chain, assetName = null, symbol = symbol,
            amount = amount, fiatAmount = "$0.00", chainId = key, isChecked = checked, showEdit = false,
            isZeroAmount = amount == "0", isGrouped = grouped
        )

    private val dotRows = listOf(
        managed("dot:polkadot", "DOT", "Polkadot", true, amount = "12.5"),
        managed("dot:hub", "DOT", "Polkadot Asset Hub", true),
        managed("dot:acala", "DOT", "Acala", false).copy(hiddenNote = "Hidden in previous version", preference = "hidden")
    )

    private val manageAssets = ManageAssetsScreenViewState(
        assets = mapOf(
            "solo" to listOf(managed("sol", "SOL", "Solana", true, amount = "0.001"), managed("btc", "BTC", "Bitcoin", true)),
            "group:dot" to dotRows
        ),
        allSectionAssets = mapOf("group:dot" to dotRows),
        selectedChainTitle = "All networks"
    )

    private val recoveryTarget = RecoveryTarget.from(7, WalletSecretScope.Ethereum)

    private val recoveryCenter = WalletRecoveryInventory.State(
        listOf(
            WalletRecoveryInventory.Wallet(
                7,
                "Public recovery fixture",
                listOf(WalletRecoveryInventory.Item(recoveryTarget, WalletRecoveryKind.MISSING_KEY)),
                listOf(WalletRecoveryInventory.Advisory(WalletSecretScope.Ton, true)),
                false
            )
        ),
        0
    )

    private val pinRecovery = PinCodeViewModel.RecoveryState(
        loading = false, strongBiometric = true, deviceCredential = true,
        choices = listOf(WalletPinRecovery.Choice("Public recovery fixture", recoveryTarget))
    )

    private val archived = ArchivedWalletRecoveryViewModel.State(
        records = listOf(
            ArchivedWalletRecovery.Record(
                "wallet_secret_archive:v1:7:ACCESS_SECRETS",
                listOf(ArchivedWalletRecovery.Address(WalletSecretScope.Ethereum, "0x7777777777777777777777777777777777777777")),
                1,
                true,
                EncryptedPreferenceSnapshot.unencrypted("public fixture exact value")
            )
        ),
        busy = false
    )

    private val confirmSend = ConfirmSendViewState(
        chainIconUrl = null,
        networkInfoItem = TitleValueViewState("Network", "Polkadot Asset Hub"),
        fromInfoItem = TitleValueViewState("From", "Main wallet", "12bzRJfh7arnnfPPUZHeJUaE62QLEwhK48QnH9LXeK2m1iZU"),
        toInfoItem = TitleValueViewState("To", "13UVJyLnbVp9RBZYFwFGyDvVd1y27Tt8tkntv6Q7JVPhFsTB"),
        amountInfoItem = TitleValueViewState("Amount", "7 USDT", "$7.00"),
        feeInfoItem = TitleValueViewState("Network fee", "0.01 DOT", "$0.04"),
        buttonState = ButtonViewState("Confirm", true)
    )

    private val crossChain = CrossChainConfirmViewState(
        originChainIcon = null,
        destinationChainIcon = null,
        fromInfoItem = TitleValueViewState("From", "Main wallet"),
        toInfoItem = TitleValueViewState("To", "13UVJyLnbVp9RBZYFwFGyDvVd1y27Tt8tkntv6Q7JVPhFsTB"),
        originNetworkItem = TitleValueViewState("Origin network", "Polkadot"),
        destinationNetworkItem = TitleValueViewState("Destination network", "Acala"),
        amountInfoItem = TitleValueViewState("Amount", "1 DOT", "$4.33"),
        originFeeInfoItem = TitleValueViewState("Origin fee", "0.01 DOT", "$0.04"),
        destinationFeeInfoItem = TitleValueViewState("Destination fee", "0.001 DOT", "$0.01"),
        buttonState = ButtonViewState("Confirm", true)
    )

    private val nameItem = WalletNameItemViewState(id = 1, title = "Main wallet", walletIcon = R.drawable.ic_wallet, isSelected = false)

    private val summary = SigningSummaryViewState(
        headline = "Token approval · Unlimited USDC",
        sections = listOf(
            SigningSummarySection(
                title = null,
                rows = listOf(
                    SigningDetailViewState("Action", "Token approval"),
                    SigningDetailViewState("Token", "USDC · 0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"),
                    SigningDetailViewState("Spender", "0x68b3465833fb72A70ecDF485E0e4C7bD8665Fc45"),
                    SigningDetailViewState("Amount", "Unlimited", isWarning = true)
                )
            )
        ),
        warnings = listOf("The spender can take all of this token from your wallet until you revoke the approval."),
        showsRawData = false
    )

    private val requestTable = listOf(
        TitleValueViewState("dApp", "Public fixture"),
        TitleValueViewState("Host", "app.example.org"),
        TitleValueViewState("Network", "Ethereum")
    )

    private val requestPreview = RequestPreviewViewState(
        chainIcon = GradientIconState.Local(R.drawable.ic_fearless_logo),
        method = "eth_sendTransaction",
        tableItems = requestTable,
        wallet = nameItem,
        loading = false,
        summary = summary,
        originWarning = "This site could not be verified. Continue only if you trust it."
    )

    private val sessionProposal = SessionProposalViewState(
        sessionProposal = null,
        originHost = "app.example.org",
        originWarning = "This site could not be verified. Continue only if you trust it.",
        requiredPermissions = InfoItemSetViewState(
            "Ethereum",
            listOf(InfoItemViewState("Methods", "eth_sendTransaction, personal_sign"), InfoItemViewState("Events", "accountsChanged, chainChanged"))
        ),
        optionalPermissions = null,
        requiredNetworksSelectorState = SelectorState(title = "Required networks", subTitle = "Ethereum", iconUrl = null),
        optionalNetworksSelectorState = null,
        wallets = listOf(nameItem, nameItem.copy(id = 2, title = "Savings", isSelected = true)),
        approving = false,
        rejecting = false
    )

    private val requestWallet = WalletItemViewState(id = 1, title = "Main wallet", walletIcon = R.drawable.ic_wallet, isSelected = false)

    private val sessionRequest = SessionRequestViewState(
        connectionUrl = "app.example.org",
        message = InfoItemViewState("Message", "Sign in to the public fixture", singleLine = true),
        wallet = requestWallet,
        originWarning = "This site could not be verified. Continue only if you trust it."
    )

    private val tonRequest = TonSignRequestViewState(
        connectionUrl = "app.example.org",
        message = InfoItemViewState("Message", "Sign in to the public fixture", singleLine = true),
        wallet = requestWallet
    )

    private val tonPreview = TonSignPreviewViewState(
        chainIcon = GradientIconState.Local(R.drawable.ic_fearless_logo),
        method = "ton_sendMessage",
        tableItems = requestTable,
        wallet = nameItem,
        loading = false,
        summary = summary.copy(warnings = emptyList())
    )

    private val swapPreview = SwapPreviewState(
        swapDetailsViewState = SwapDetailsViewState(
            fromTokenId = "1001",
            toTokenId = "1002",
            fromTokenName = "VAL",
            toTokenName = "XSTUSD",
            fromTokenImage = GradientIconState.Local(R.drawable.ic_fearless_logo),
            toTokenImage = GradientIconState.Local(R.drawable.ic_fearless_logo),
            toTokenMinReceived = "1",
            toFiatMinReceived = "$0.98",
            fromTokenAmount = "1",
            toTokenAmount = "2",
            fromTokenOnToToken = "0.1234",
            toTokenOnFromToken = "12345,0",
            minmaxTitle = "Min received",
            route = "USD -> XOR -> DOT -> VAL"
        ),
        networkFee = SwapDetailsViewState.NetworkFee(tokenAmount = "0.0007", tokenName = "XOR", fiatAmount = "$0.32"),
        isLoading = false
    )

    private inline fun <reified T : Any> noOp(): T = proxy { _, _ -> }

    private inline fun <reified T : Any> proxy(crossinline onCall: (String, Array<out Any?>?) -> Unit): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
            onCall(method.name, args)
            null
        } as T
}
