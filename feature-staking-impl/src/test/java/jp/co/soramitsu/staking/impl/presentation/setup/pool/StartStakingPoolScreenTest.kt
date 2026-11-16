package jp.co.soramitsu.staking.impl.presentation.setup.pool

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.StakingComposeTest
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test

class StartStakingPoolScreenTest : StakingComposeTest() {

    private val state = SetupStakingPoolViewState(
        toolbarViewState = ToolbarViewState("Pool staking", R.drawable.ic_arrow_back_24dp),
        assetName = "dot",
        rewardsPayoutDelay = "Every 6 hours",
        yearlyEstimatedEarnings = "14.2%",
        unstakingPeriod = "28 days"
    )

    private fun screenForwardsActionsToTheViewModel(legacy: Boolean) {
        val viewModel = mockk<StartStakingPoolViewModel>(relaxed = true)
        every { viewModel.state } returns MutableStateFlow(state)
        render(legacy) { StartStakingPoolScreen(viewModel) }

        compose.onNodeWithText("Pool staking").assertExists()
        compose.onNodeWithText("14.2%", substring = true).assertExists()
        compose.onNodeWithText("28 days", substring = true).assertExists()
        compose.onNodeWithText(str(R.string.common_watch).uppercase()).performScrollTo().performClick()
        tick()
        compose.onNodeWithText(str(R.string.staking_pool_start_join_button_title)).performScrollTo().performClick()
        tick()
        compose.onNodeWithText(str(R.string.staking_pool_start_create_button_title)).assertDoesNotExist()
        compose.onNodeWithText(str(R.string.ux_pool_operator_options)).performScrollTo().performClick()
        tick()
        compose.onNodeWithText(str(R.string.ux_pool_operator_description)).assertExists()
        compose.onNodeWithText(str(R.string.staking_pool_start_create_button_title)).performScrollTo().performClick()
        compose.waitForIdle()

        verify { viewModel.onInstructionsClick() }
        verify { viewModel.onJoinPool() }
        verify { viewModel.onCreatePool() }
    }

    @Test
    fun restyledScreenOpensJoinCreateAndInstructions() = screenForwardsActionsToTheViewModel(legacy = false)

    @Test
    fun legacyScreenOpensJoinCreateAndInstructions() = screenForwardsActionsToTheViewModel(legacy = true)
}
