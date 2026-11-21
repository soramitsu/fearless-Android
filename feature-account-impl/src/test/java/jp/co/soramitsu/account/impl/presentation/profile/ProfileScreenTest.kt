package jp.co.soramitsu.account.impl.presentation.profile

import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import jp.co.soramitsu.account.impl.testutil.string
import jp.co.soramitsu.account.impl.testutil.tap
import jp.co.soramitsu.common.compose.component.SettingsItemAction
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.feature_account_impl.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2600dp")
class ProfileScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private val callback = object : ProfileScreenInterface {
        override fun onWalletOptionsClick(item: WalletItemViewState) { calls += "options" }
        override fun walletsClicked() { calls += "wallets" }
        override fun onWalletConnectClick() { calls += "walletconnect" }
        override fun onTonConnectClick() { calls += "tonconnect" }
        override fun onManageAssetsClick() { calls += "manageAssets" }
        override fun currencyClicked() { calls += "currency" }
        override fun languagesClicked() { calls += "languages" }
        override fun appearanceClicked() { calls += "appearance" }
        override fun onNomisMultichainScoreContainerClick() { calls += "nomis" }
        override fun polkaswapDisclaimerClicked() { calls += "polkaswap" }
        override fun changePinCodeClicked() { calls += "pin" }
        override fun recoveryClicked() { calls += "recovery" }
        override fun aboutClicked() { calls += "about" }
        override fun onScoreClick(item: WalletItemViewState) { calls += "score" }
    }

    private fun wallet(ecosystems: Set<WalletEcosystem>) = WalletItemViewState(
        id = 7,
        balance = "120.5",
        assetSymbol = "$",
        title = "Main wallet",
        walletIcon = jp.co.soramitsu.common.R.drawable.ic_wallet,
        isSelected = true,
        supportedEcosystems = ecosystems
    )

    private fun render(ecosystems: Set<WalletEcosystem>, nomisChecked: Boolean = true) {
        val state = ProfileScreenState(
            walletState = wallet(ecosystems),
            walletsItemAction = SettingsItemAction.Transition,
            currency = "USD",
            language = "English",
            nomisChecked = nomisChecked
        )
        compose.setContent {
            FearlessAppTheme { ProfileScreen(scrollState = rememberScrollState(), state = state, callback = callback) }
        }
    }

    @Test
    fun everySettingsRowReachesItsCallback() {
        render(setOf(WalletEcosystem.Substrate, WalletEcosystem.Ethereum, WalletEcosystem.Ton))

        compose.onNodeWithText(string(R.string.profile_settings_title)).assertIsDisplayed()
        compose.onNodeWithText("Main wallet").assertIsDisplayed()
        compose.onNodeWithText("USD").assertIsDisplayed()
        compose.onNodeWithText("English").assertIsDisplayed()

        compose.onNodeWithContentDescription(string(R.string.ux_more)).tap()
        listOf(
            R.string.profile_wallets_title,
            R.string.settings_manage_assets,
            R.string.common_currency,
            R.string.profile_tonconnect_title,
            R.string.profile_walletconnect_title,
            R.string.wallet_recovery_center,
            R.string.profile_pincode_change_title,
            R.string.profile_language_title,
            R.string.appearance_title,
            R.string.profile_account_score_title,
            R.string.polkaswap_disclaimer_settings_item
        ).forEach { compose.onNodeWithText(string(it)).tap() }
        // "About" is both the section heading and the row; the row is the second node.
        compose.onAllNodesWithText(string(R.string.about_title))[1].tap()
        compose.waitForIdle()

        assertEquals(
            listOf(
                "options", "wallets", "manageAssets", "currency", "tonconnect", "walletconnect", "recovery", "pin",
                "languages", "appearance", "nomis", "polkaswap", "about"
            ),
            calls
        )
        // A wallet with TON and signing accounts offers both connections without an "add account" hint.
        compose.onNodeWithText(string(R.string.connection_add_ton_account)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.connection_add_signing_account)).assertDoesNotExist()
    }

    @Test
    fun walletWithoutTonAsksForATonAccountBeforeTonConnect() {
        render(setOf(WalletEcosystem.Substrate), nomisChecked = false)

        compose.onNodeWithText(string(R.string.connection_add_ton_account)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.connection_add_signing_account)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.profile_account_score_title)).assertIsDisplayed()
    }

    @Test
    fun tonOnlyWalletHidesScoreAndPolkaswapAndAsksForASigningAccount() {
        render(setOf(WalletEcosystem.Ton))

        compose.onNodeWithText(string(R.string.connection_add_signing_account)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.connection_add_ton_account)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.profile_account_score_title)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.polkaswap_disclaimer_settings_item)).assertDoesNotExist()
        // A TON-only wallet has no wallet options here.
        compose.onNodeWithContentDescription(string(R.string.ux_more)).assertDoesNotExist()
    }

    @Test
    fun previewShowsTheSampleWallet() {
        compose.setContent { ProfileScreenPreview() }

        compose.onNodeWithText("My Wallet").assertIsDisplayed()
        compose.onNodeWithText("USD").assertIsDisplayed()
    }
}
