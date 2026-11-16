package jp.co.soramitsu.common.compose.component

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.time.Duration
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
class BannerInteractionsRobolectricTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun advanceClickClock() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    @After
    fun restorePalette() {
        unmockkObject(FwRestyle)
    }

    @Test
    fun shouldOfferBackupAndDismissSeparately() {
        verifyBanner(R.string.common_backup_your_wallet, R.string.backup_now) { action, close -> BannerBackup(action, close) }
    }

    @Test
    fun shouldOpenLiquidityPoolDetailsAndDismiss() {
        verifyBanner(R.string.banners_liquidity_pools_title, R.string.common_show_details) { action, close -> BannerLiquidityPools(action, close) }
    }

    @Test
    fun shouldOpenDemeterAndDismiss() {
        verifyBanner(R.string.banners_demeter_title, R.string.common_show_more) { action, close -> BannerDemeter(action, close) }
    }

    @Test
    fun shouldOfferSubstrateAndEvmWalletCreation() {
        verifyBanner(R.string.banner_addwallet_regular_title, R.string.banner_addwallet_regular_button_title) { action, close ->
            BannerJoinSubstrateEvm(action, close)
        }
        compose.onNodeWithText(context.getString(R.string.banner_addwallet_regular_subtitle)).assertIsDisplayed()
    }

    @Test
    fun shouldOfferTonWalletCreation() {
        verifyBanner(R.string.banner_addwallet_ton_title, R.string.banner_addwallet_ton_button_title) { action, close -> BannerJoinTon(action, close) }
    }

    @Test
    fun shouldPresentScopedRecoveryAndRouteItsAction() {
        var clicks = 0
        compose.setContent { FearlessAppTheme { BannerWalletRecovery { clicks++ } } }
        compose.onNodeWithText(context.getString(R.string.wallet_recovery_scoped_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.wallet_recovery_fix_now)).performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun shouldExplainViewOnlyRecoveryInTheLegacyPalette() {
        mockkObject(FwRestyle)
        every { FwRestyle.isEnabled(any()) } returns false
        var clicks = 0
        compose.setContent { FearlessAppTheme { BannerWalletRecovery(viewOnly = true) { clicks++ } } }
        compose.onNodeWithText(context.getString(R.string.pin_recovery_view_only_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.pin_recovery_reset)).performClick()
        assertEquals(1, clicks)
    }

    private fun verifyBanner(title: Int, action: Int, content: @Composable (() -> Unit, () -> Unit) -> Unit) {
        val events = mutableListOf<String>()
        compose.setContent { FearlessAppTheme { content({ events += "action" }, { events += "close" }) } }
        compose.onNodeWithText(context.getString(title), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(context.getString(action)).performClick()
        compose.onNodeWithContentDescription(context.getString(toolbarActionLabel(R.drawable.ic_cross_32))).performClick()
        assertEquals(listOf("action", "close"), events)
    }
}
