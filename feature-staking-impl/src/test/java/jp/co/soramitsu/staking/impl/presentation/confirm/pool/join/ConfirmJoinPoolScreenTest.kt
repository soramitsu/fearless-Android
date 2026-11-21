package jp.co.soramitsu.staking.impl.presentation.confirm.pool.join

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import jp.co.soramitsu.common.compose.component.GradientIconState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.data.repository.PoolJoinJournal
import jp.co.soramitsu.staking.impl.presentation.StakingComposeTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ConfirmJoinPoolScreenTest : StakingComposeTest() {

    private val review = ConfirmJoinPoolScreenViewState(
        toolbarViewState = ToolbarViewState("Join pool review", R.drawable.ic_arrow_back_24dp),
        amount = "10 KSM",
        address = TitleValueViewState("From", "My account"),
        selectedPool = TitleValueViewState("Selected Pool", "Pool #1", "id: 1"),
        networkFee = TitleValueViewState("Network Fee", "0.0051 KSM", "$0.32"),
        assetIcon = GradientIconState.Local(R.drawable.ic_arrow_back_24dp),
        isLoading = false
    )

    private val recovery = PoolJoinRecoveryViewState(
        message = "A previous join is still pending",
        amount = "5 KSM",
        poolId = "Pool #7",
        network = "Kusama",
        address = "HNZata7iMYWmk5RvZRTiAsSDhV8366zq2YGb3tLH5Upf74F",
        transactionHash = "0xabc123",
        canCheck = true,
        canCheckOlder = true,
        finalized = false,
        create = PoolJoinJournal.CreateDetails("Sunrise pool", "Nominator wallet", "Toggler wallet")
    )

    private fun reviewShowsTheJoinAndConfirms(legacy: Boolean) {
        val events = mutableListOf<String>()
        render(legacy) {
            ConfirmJoinPoolScreen(
                state = review.copy(additionalMessage = "Rewards start next era"),
                onNavigationClick = { events += "back" },
                onConfirm = { events += "confirm" }
            )
        }

        compose.onNodeWithText("Join pool review").assertExists()
        compose.onNodeWithText(str(R.string.pool_staking_confirm_join_title)).assertExists()
        compose.onNodeWithText("10 KSM").assertExists()
        compose.onNodeWithText("Pool #1").assertExists()
        compose.onNodeWithText("Rewards start next era").assertExists()
        compose.onNodeWithContentDescription(str(R.string.ux_back)).performClick()
        tick()
        compose.onNodeWithText(str(R.string.common_confirm)).performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(listOf("back", "confirm"), events)
    }

    @Test
    fun restyledReviewShowsTheJoinAndConfirms() = reviewShowsTheJoinAndConfirms(legacy = false)

    @Test
    fun legacyReviewShowsTheJoinAndConfirms() = reviewShowsTheJoinAndConfirms(legacy = true)

    @Test
    fun pendingRecoveryListsEveryIdentifierAndChecksRecentAndOlderBlocks() {
        val checks = mutableListOf<Boolean>()
        var returned = 0
        render {
            ConfirmJoinPoolScreen(review.copy(recovery = recovery), {}, {}, { checks += it }, { returned++ })
        }

        compose.onNodeWithText(str(R.string.pool_join_recovery_title)).assertExists()
        compose.onNodeWithText(str(R.string.pool_staking_confirm_join_title)).assertDoesNotExist()
        val labels = listOf(
            R.string.common_network, R.string.transaction_details_from, R.string.common_amount,
            R.string.pool_staking_selected_pool, R.string.pool_create_title, R.string.pool_staking_pool_name,
            R.string.pool_staking_nominator, R.string.pool_recovery_state_control, R.string.pool_join_recovery_hash
        )
        labels.forEach { compose.onNodeWithText(str(it)).assertExists() }
        listOf(recovery.message, "5 KSM", "Pool #7", "Kusama", recovery.address, "0xabc123", "Sunrise pool",
            "Nominator wallet", "Toggler wallet").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText(str(R.string.pool_join_recovery_return)).assertDoesNotExist()

        compose.onNodeWithText(str(R.string.pool_join_recovery_recent)).performScrollTo().performClick()
        tick()
        compose.onNodeWithText(str(R.string.pool_join_recovery_older)).performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(listOf(false, true), checks)
        assertEquals(0, returned)
    }

    @Test
    fun finalizedRecoveryOffersOnlyTheReturnAction() {
        val checks = mutableListOf<Boolean>()
        var returned = 0
        val finalized = recovery.copy(
            amount = null, poolId = null, network = "", address = "", transactionHash = null, create = null,
            finalized = true, canReturn = true
        )
        render(legacy = true) {
            ConfirmJoinPoolScreen(review.copy(recovery = finalized), {}, {}, { checks += it }, { returned++ })
        }

        compose.onNodeWithText(recovery.message).assertExists()
        listOf(R.string.common_network, R.string.transaction_details_from, R.string.common_amount,
            R.string.pool_join_recovery_hash, R.string.pool_join_recovery_recent, R.string.pool_join_recovery_older)
            .forEach { compose.onNodeWithText(str(it)).assertDoesNotExist() }
        compose.onNodeWithText(str(R.string.pool_join_recovery_return)).performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(1, returned)
        assertEquals(emptyList<Boolean>(), checks)
    }

    @Test
    fun recoveryWithoutCheckCapacityDisablesTheRecentCheck() {
        val checks = mutableListOf<Boolean>()
        render {
            ConfirmJoinPoolScreen(
                review.copy(recovery = recovery.copy(canCheck = false, canCheckOlder = false)), {}, {}, { checks += it }
            )
        }

        compose.onNodeWithText(str(R.string.pool_join_recovery_older)).assertDoesNotExist()
        val recent = compose.onNodeWithText(str(R.string.pool_join_recovery_recent))
        recent.performScrollTo().assertIsNotEnabled().performClick()
        compose.waitForIdle()

        assertEquals(emptyList<Boolean>(), checks)
    }
}
