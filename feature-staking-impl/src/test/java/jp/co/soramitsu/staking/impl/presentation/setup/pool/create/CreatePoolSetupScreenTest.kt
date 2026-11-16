package jp.co.soramitsu.staking.impl.presentation.setup.pool.create

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import java.math.BigDecimal
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.FeeInfoViewState
import jp.co.soramitsu.common.compose.component.TextInputViewState
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.StakingComposeTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreatePoolSetupScreenTest : StakingComposeTest() {

    private class Recorder : CreatePoolSetupScreenInterface {
        val events = mutableListOf<String>()
        override fun onNavigationClick() { events += "back" }
        override fun onPoolNameInput(text: String) { events += "name:$text" }
        override fun onTokenAmountInput(value: BigDecimal?) { events += "amount:$value" }
        override fun onNominatorClick() { events += "nominator" }
        override fun onStateTogglerClick() { events += "toggler" }
        override fun onCreateClick() { events += "create" }
        override fun onQuickAmountInput(value: Double) { events += "quick:$value" }
    }

    private val state = CreatePoolSetupViewState(
        poolNameInputViewState = TextInputViewState("Sunrise pool", "Pool name"),
        amountInputViewState = AmountInputViewState(
            tokenName = "KSM", tokenImage = "", totalBalance = "Balance: 20.0", fiatAmount = "$120.0",
            tokenAmount = BigDecimal.ONE
        ),
        poolId = "7",
        depositor = "Depositor wallet",
        root = "Root wallet",
        nominator = "Nominator wallet",
        stateToggler = "Toggler wallet",
        feeInfoViewState = FeeInfoViewState(feeAmount = "0.0051 KSM", feeAmountFiat = "$0.0009"),
        createButtonViewState = ButtonViewState("Create", true)
    )

    private fun advancedRolesOpenTheirPickers(legacy: Boolean) {
        val recorder = Recorder()
        render(legacy) { CreatePoolSetupScreen(state, false, recorder) }

        compose.onNodeWithText(str(R.string.pool_create_title)).assertExists()
        compose.onNodeWithText("Depositor wallet").assertDoesNotExist()
        compose.onNodeWithContentDescription(str(R.string.ux_back)).performClick()
        tick()
        compose.onNodeWithText(str(R.string.common_advanced)).performScrollTo().performClick()
        compose.waitForIdle()

        listOf(R.string.pool_staking_pool_id, R.string.pool_staking_depositor, R.string.pool_staking_root,
            R.string.pool_staking_nominator, R.string.pool_staking_bouncer)
            .forEach { compose.onNodeWithText(str(it)).assertExists() }
        listOf("7", "Depositor wallet", "Root wallet").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("Nominator wallet").performScrollTo().performClick()
        tick()
        compose.onNodeWithText("Toggler wallet").performScrollTo().performClick()
        tick()
        compose.onNodeWithText("Create").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(listOf("back", "nominator", "toggler", "create"), recorder.events)
    }

    @Test
    fun restyledAdvancedRolesOpenTheirPickers() = advancedRolesOpenTheirPickers(legacy = false)

    @Test
    fun legacyAdvancedRolesOpenTheirPickers() = advancedRolesOpenTheirPickers(legacy = true)

    @Test
    fun poolNameEditsReachTheScreenInterface() {
        val recorder = Recorder()
        render { CreatePoolSetupScreen(state, false, recorder) }

        compose.onNode(hasSetTextAction() and hasText("Sunrise pool")).performTextReplacement("Moonrise pool")
        compose.waitForIdle()

        assertTrue(recorder.events.toString(), "name:Moonrise pool" in recorder.events)
        compose.onNodeWithText("75%").assertDoesNotExist()
    }

    @Test
    fun openKeyboardShowsQuickAmountsThatReachTheScreenInterface() {
        val recorder = Recorder()
        render { CreatePoolSetupScreen(state, true, recorder) }

        compose.onNodeWithText("25%").assertExists()
        compose.onNodeWithText("50%").assertExists()
        compose.onNodeWithText("75%").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(listOf("quick:0.75"), recorder.events)
    }
}
