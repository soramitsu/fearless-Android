package jp.co.soramitsu.wallet.impl.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.onRoot
import java.math.BigDecimal
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.data.network.send.PendingWalletSend
import jp.co.soramitsu.common.data.network.send.ReviewedWalletSend
import jp.co.soramitsu.common.data.network.send.SolanaSendNetworkIdentity
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.data.repository.tranfser.NativeSendRecoverySnapshot
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures.string
import jp.co.soramitsu.wallet.impl.presentation.balance.detail.frozen.FrozenAssetPayload
import jp.co.soramitsu.wallet.impl.presentation.balance.detail.frozen.FrozenTokensContent
import jp.co.soramitsu.wallet.impl.presentation.balance.detail.frozen.FrozenTokensContentViewState
import jp.co.soramitsu.wallet.impl.presentation.balance.detail.legacy.LegacyCrowdloanContent
import jp.co.soramitsu.wallet.impl.presentation.balance.detail.legacy.LegacyCrowdloanScreenState
import jp.co.soramitsu.wallet.impl.presentation.balance.list.PullRefreshBox
import jp.co.soramitsu.wallet.impl.presentation.send.NativeSendRecoveryDialog
import jp.co.soramitsu.wallet.impl.presentation.send.NativeSendRecoveryUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w420dp-h1600dp")
class SmallWalletSheetsRobolectricTest {

    @Before
    fun prepareUiTest() {
        ComposeFixtures.prepareUiTest()
    }

    @get:Rule
    val compose = createWalletComposeRule()

    private val calls = mutableListOf<String>()

    @Before
    fun setUp() {
        ComposeFixtures.passDoubleTapGuard()
    }

    @Test
    fun frozenTokensListEveryNonEmptyPart() {
        compose.setContent {
            FearlessAppTheme {
                FrozenTokensContent(
                    FrozenTokensContentViewState(
                        FrozenAssetPayload(
                            locked = BigDecimal.ONE, staked = BigDecimal.TEN, reserved = BigDecimal("2"),
                            redeemable = BigDecimal("3"), unstaking = BigDecimal("4"), assetSymbol = "dot"
                        )
                    )
                )
            }
        }

        compose.onNodeWithText(string(R.string.common_title_frozen_token, "DOT")).assertIsDisplayed()
        listOf(
            R.string.assetdetails_balance_locked, R.string.wallet_balance_bonded, R.string.wallet_balance_reserved,
            R.string.wallet_balance_redeemable, R.string.wallet_balance_unbonding_v1_9_0
        ).forEach { compose.onNodeWithText(string(it)).assertIsDisplayed() }
    }

    @Test
    fun frozenTokensWithOnlyALockShowOneRow() {
        compose.setContent {
            FearlessAppTheme { FrozenTokensContent(FrozenTokensContentViewState(FrozenAssetPayload(locked = BigDecimal.ONE, assetSymbol = "ksm"))) }
        }

        compose.onNodeWithText(string(R.string.assetdetails_balance_locked)).assertIsDisplayed()
        assertEquals(0, compose.onAllNodes(hasText(string(R.string.wallet_balance_bonded))).fetchSemanticsNodes().size)
    }

    @Test
    fun legacyCrowdloanShowsEvidenceAndOpensTheLockedBalance() {
        compose.setContent {
            FearlessAppTheme {
                LegacyCrowdloanContent(
                    LoadingState.Loaded(
                        LegacyCrowdloanScreenState(
                            hasEvidence = true,
                            description = "Your contribution is locked",
                            evidenceRows = listOf(TitleValueViewState("Parachain", "Acala")),
                            canOpenLockedBalance = true
                        )
                    )
                ) { calls += "open" }
            }
        }

        compose.onNodeWithText("Your contribution is locked").assertIsDisplayed()
        compose.onNodeWithText("Acala").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.legacy_crowdloan_view_locked)).performClick()
        compose.waitForIdle()

        assertEquals(listOf("open"), calls)
    }

    @Test
    fun legacyCrowdloanWithoutEvidenceHasNoButton() {
        compose.setContent {
            FearlessAppTheme {
                LegacyCrowdloanContent(LoadingState.Loaded(LegacyCrowdloanScreenState(false, "Nothing found", emptyList(), false))) {}
            }
        }

        compose.onNodeWithText("Nothing found").assertIsDisplayed()
        assertEquals(0, compose.onAllNodes(hasText(string(R.string.legacy_crowdloan_view_locked))).fetchSemanticsNodes().size)
    }

    @Test
    fun legacyCrowdloanLoadingShowsProgressOnly() {
        compose.setContent { FearlessAppTheme { LegacyCrowdloanContent(LoadingState.Loading()) {} } }

        assertEquals(0, compose.onAllNodes(hasText("", substring = true)).fetchSemanticsNodes().size)
    }

    @Test
    fun pullingDownRefreshes() {
        compose.setContent {
            FearlessAppTheme {
                PullRefreshBox(onRefresh = { calls += "refresh" }) {
                    androidx.compose.foundation.lazy.LazyColumn {
                        items(30) { androidx.compose.material.Text("Row $it") }
                    }
                }
            }
        }

        compose.onRoot().performTouchInput { swipeDown(startY = top + 10f, endY = bottom) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()

        assertEquals(listOf("refresh"), calls)
    }

    @Test
    fun pendingRecoveryOffersCheckRetryExplorerAndClose() {
        render(snapshot(PendingWalletSend.State.Pending, rentUnits = "2039280", kind = PendingWalletSend.Kind.Solana, network = true))

        compose.onNodeWithText(string(R.string.send_recent_payment)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.send_pending_message)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.common_amount) + ": 1.5 SOL").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.common_network_fee) + ": 0.000005 SOL").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.send_account_creation) + ": 0.00203928 SOL").assertIsDisplayed()
        compose.onNodeWithText("Earlier failure").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.send_check_status)).performClick()
        compose.onNodeWithText(string(R.string.send_retry_same_payment)).performClick()
        compose.onNodeWithText(string(R.string.send_view_explorer)).performClick()
        compose.onNodeWithText(string(R.string.common_close)).performClick()
        compose.waitForIdle()

        assertEquals(listOf("check", "retry", "explorer", "close"), calls)
    }

    @Test
    fun recoveryMessagesFollowTheSnapshotState() {
        val cases = listOf(
            snapshot(PendingWalletSend.State.Pending, expired = true) to R.string.send_pending_expired,
            snapshot(PendingWalletSend.State.Confirmed) to R.string.send_confirmed_message,
            snapshot(PendingWalletSend.State.Failed) to R.string.send_failed_message,
            snapshot(PendingWalletSend.State.Pending, notSent = true) to R.string.send_solana_expired_not_sent,
            snapshot(PendingWalletSend.State.Pending, unverified = true) to R.string.send_solana_payment_unverified,
            snapshot(PendingWalletSend.State.Pending, kind = PendingWalletSend.Kind.Solana, network = false) to R.string.send_pending_network_unavailable
        )
        var index by androidx.compose.runtime.mutableStateOf(0)
        compose.setContent {
            FearlessAppTheme {
                NativeSendRecoveryDialog(NativeSendRecoveryUiState(cases[index].first), {}, {}, {}, {})
            }
        }

        cases.forEachIndexed { i, (_, message) ->
            compose.runOnIdle { index = i }
            compose.waitForIdle()
            compose.onNodeWithText(string(message)).assertIsDisplayed()
        }
        compose.runOnIdle { index = 3 }
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodes(hasText(string(R.string.send_check_status))).fetchSemanticsNodes().size)
    }

    @Test
    fun busyRecoveryDisablesItsActions() {
        render(snapshot(PendingWalletSend.State.Pending), busy = true)

        compose.onNodeWithText(string(R.string.send_check_status)).assertIsNotEnabled()
        compose.onNodeWithText(string(R.string.common_close)).assertIsNotEnabled()
        assertTrue(calls.isEmpty())
    }

    private fun render(snapshot: NativeSendRecoverySnapshot, busy: Boolean = false) {
        compose.setContent {
            FearlessAppTheme {
                NativeSendRecoveryDialog(
                    NativeSendRecoveryUiState(snapshot, busy = busy, error = "Earlier failure"),
                    close = { calls += "close" },
                    check = { calls += "check" },
                    retry = { calls += "retry" },
                    explorer = { calls += "explorer" }
                )
            }
        }
        compose.waitForIdle()
    }

    private fun snapshot(
        state: PendingWalletSend.State,
        expired: Boolean = false,
        notSent: Boolean = false,
        unverified: Boolean = false,
        rentUnits: String = "0",
        kind: PendingWalletSend.Kind = PendingWalletSend.Kind.Bitcoin,
        network: Boolean = true
    ): NativeSendRecoverySnapshot {
        val identity = if (kind == PendingWalletSend.Kind.Solana) io.mockk.mockk<SolanaSendNetworkIdentity> {
            io.mockk.every { registryGenesisHash } returns "genesis"
        } else null
        return NativeSendRecoverySnapshot(
            walletId = 1, chainId = "chain", sender = "me", selectionEpoch = 1, transactionId = "tx-1", state = state,
            expired = expired, notSent = notSent, unverified = unverified,
            reviewed = ReviewedWalletSend("chain", "Solana", "sol", null, if (kind == PendingWalletSend.Kind.Solana) "SOL" else "BTC", 9, "me", "you", "1500000000"),
            feeUnits = "5000", accountRentUnits = rentUnits, kind = kind,
            solanaNetwork = identity, currentSolanaGenesisHash = if (network) "genesis" else "other"
        )
    }
}
