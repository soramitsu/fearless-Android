package jp.co.soramitsu.staking.impl.presentation

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.ui.test.onNodeWithText
import java.math.BigDecimal
import jp.co.soramitsu.common.compose.component.AccountInfoViewState
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.FeeInfoViewState
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.setup.pool.join.SetupStakingScreen
import jp.co.soramitsu.staking.impl.presentation.setup.pool.join.SetupStakingScreenViewState
import androidx.compose.runtime.currentComposer
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

/**
 * Every design preview of the staking screens is a fixture state of a real screen: rendering each one, in the
 * restyled and the legacy palette, proves the screens lay out those states and show their texts.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class StakingComposePreviewsTest(private val owner: String, private val preview: String, private val restyled: Boolean) {

    @get:Rule
    val compose = createComposeRule()

    @After
    fun restorePalette() {
        unmockkObject(FwRestyle)
    }

    @Test
    fun previewRendersVisibleText() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        if (!restyled) {
            mockkObject(FwRestyle)
            every { FwRestyle.isEnabled(any()) } returns false
            every { FwRestyle.paletteEnabled } returns false
        }
        if (preview == "JoinPoolFixture") {
            compose.setContent { FearlessAppTheme { joinPoolFixture() } }
            compose.onNodeWithText("Join pool from").assertExists()
            compose.onNodeWithText("My account").assertExists()
            compose.onNodeWithText("0.0051 KSM").assertExists()
        } else {
            val method = Class.forName("$PKG.$owner").getDeclaredMethod(preview, Composer::class.java, Int::class.javaPrimitiveType)
            method.isAccessible = true
            compose.setContent { method.invoke(null, currentComposer, 0) }
        }
        compose.waitForIdle()
        val texts = compose.onAllNodes(hasText("", substring = true), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("$preview shows no text", texts.isNotEmpty())
    }

    // The production preview passes a Painter to Coil, which accepts a drawable/resource instead.
    @Composable
    private fun joinPoolFixture() {
        val state = SetupStakingScreenViewState(
            ToolbarViewState("Join pool", R.drawable.ic_arrow_back_24dp),
            AccountInfoViewState("address", "My account", R.drawable.ic_wallet, "Join pool from"),
            AmountInputViewState("KSM", "", "Balance: 20.0", "$120.0", BigDecimal.ONE),
            FeeInfoViewState(feeAmount = "0.0051 KSM", feeAmountFiat = "$0.0009"),
            ButtonViewState("Join", true)
        )
        SetupStakingScreen(state, false, {}, {}, {}, {})
    }

    companion object {
        private const val PKG = "jp.co.soramitsu.staking.impl.presentation"

        private val previews = listOf(
            "pools.compose.PoolInfoScreenKt" to "PoolInfoScreenPreview",
            "pools.compose.SelectableListItemKt" to "PoolItemPreview",
            "pools.compose.SelectPoolScreenKt" to "SelectPoolScreenPreview",
            "pools.edit.EditPoolScreenKt" to "EditPoolScreenPreview",
            "setup.pool.StartStakingPoolScreenKt" to "SetupStakingPoolScreenPreview",
            "pools.compose.PoolInfoOptionsScreenKt" to "PoolInfoOptionsScreenPreview",
            "setup.pool.create.CreatePoolSetupScreenKt" to "CreatePoolSetupScreenPreview",
            "setup.pool.join.SetupStakingScreenKt" to "JoinPoolFixture",
            "confirm.pool.join.ConfirmJoinPoolScreenKt" to "ConfirmJoinPoolScreenPreview",
            "common.filters.ValidatorsSettingsScreenKt" to "ValidatorsSettingsScreenPreview",
            "staking.balance.compose.ManagePoolStakeScreenKt" to "ManagePoolStakeScreenPreview",
            "staking.main.compose.StakingInfoItemKt" to "StakingInfoItemPreview",
            "staking.main.compose.StakingAssetInfoKt" to "StakingAssetInfoPreview",
            "staking.main.compose.EstimatedEarningsKt" to "EstimatedEarningsPreview",
            "validators.compose.SelectedValidatorsScreenKt" to "SelectedValidatorsScreenScreenPreview",
            "validators.compose.StartSelectValidatorsScreenKt" to "StartSelectValidatorsScreenPreview",
            "validators.compose.SelectValidatorsScreenKt" to "SelectValidatorsScreenPreview"
        )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{1} restyled={2}")
        fun cases(): List<Array<Any>> = previews.flatMap { (owner, name) ->
            listOf(arrayOf<Any>(owner, name, true), arrayOf<Any>(owner, name, false))
        }
    }
}
