package jp.co.soramitsu.irohaconnect.impl.presentation

import jp.co.soramitsu.common.R
import jp.co.soramitsu.irohaconnect.impl.domain.IrohaConnectAccount
import jp.co.soramitsu.irohaconnect.impl.domain.IrohaConnectActionError
import jp.co.soramitsu.irohaconnect.impl.domain.IrohaConnectFailure
import jp.co.soramitsu.irohaconnect.impl.domain.IrohaConnectNotice
import jp.co.soramitsu.irohaconnect.impl.domain.IrohaConnectNoticeCode
import jp.co.soramitsu.irohaconnect.impl.domain.IrohaConnectPhase
import jp.co.soramitsu.irohaconnect.impl.domain.IrohaConnectSessionInfo
import jp.co.soramitsu.irohaconnect.impl.domain.IrohaConnectSessionPermissions
import jp.co.soramitsu.irohaconnect.impl.domain.IrohaConnectSigningRequest
import jp.co.soramitsu.irohaconnect.impl.domain.IrohaConnectSnapshot
import jp.co.soramitsu.irohaconnect.impl.review.IrohaConnectReviewAsset
import jp.co.soramitsu.irohaconnect.impl.review.IrohaConnectReviewContractCall
import jp.co.soramitsu.irohaconnect.impl.review.IrohaConnectReviewFee
import jp.co.soramitsu.irohaconnect.impl.review.IrohaConnectReviewFeeLimit
import jp.co.soramitsu.irohaconnect.impl.review.IrohaConnectReviewTransfer
import jp.co.soramitsu.irohaconnect.impl.review.IrohaConnectTransactionReview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IrohaConnectViewStatesTest {

    private val session = IrohaConnectSessionInfo(
        appName = "Hanami Market",
        appUrl = "https://hanami.example",
        chainId = "iroha3-taira",
        networkName = "SORA Taira",
        networkId = "hash:…",
        permissions = IrohaConnectSessionPermissions(listOf("sign_transaction"), emptyList(), null),
        toriiBaseUrl = "https://taira.sora.org"
    )
    private val xor = IrohaConnectReviewAsset("6TEAJqbb8oEPmLncoNiMRbLEK6tw", "XOR")
    private val review = IrohaConnectTransactionReview(
        kind = IrohaConnectTransactionReview.Kind.TRANSFER,
        authority = "testu…",
        networkId = "hash:…",
        payloadBytes = 300,
        payloadHashHex = "ab".repeat(32),
        creationTimeMs = "1",
        ttlMs = "100000",
        nonce = "42",
        fee = IrohaConnectReviewFee("sponsor", "sponsor/program", "3", listOf(IrohaConnectReviewFeeLimit("nexus", xor, "0.25")), "1000"),
        metadata = "{\"memo\":\"hanami\"}",
        transfer = IrohaConnectReviewTransfer(xor, "dataspace:7", "1.5", "testu…dest")
    )

    @Test
    fun shouldOfferTheAccountsAndPermissions_whenTheDappAwaitsApproval() {
        val snapshot = IrohaConnectSnapshot(
            phase = IrohaConnectPhase.SESSION_APPROVAL,
            session = session,
            accounts = listOf(IrohaConnectAccount(1, "a", "Main", "00"), IrohaConnectAccount(2, "b", "Second", "11"))
        )
        val state = IrohaConnectViewStates.build(snapshot, selectedAccount = null, linkFailure = null, busy = false)
        assertEquals("Hanami Market", state.appName)
        assertEquals(listOf(true, false), state.accounts.map { it.selected })
        assertEquals(listOf(R.string.common_network, R.string.iroha_connect_relay), state.sessionRows.map { it.label })
        assertEquals(R.string.iroha_connect_permissions, state.permissions?.title)
        assertEquals("sign_transaction", state.permissions?.rows?.first()?.value)
        assertEquals("—", state.permissions?.rows?.get(1)?.value)
        assertEquals(listOf(false, true), IrohaConnectViewStates.build(snapshot, "b", null, false).accounts.map { it.selected })
        assertNull(state.message)
    }

    @Test
    fun shouldShowEverySignatureBoundField_whenATransferAwaitsReview() {
        val snapshot = IrohaConnectSnapshot(
            phase = IrohaConnectPhase.REQUEST_APPROVAL,
            session = session,
            selectedAccountId = "testu…",
            request = IrohaConnectSigningRequest("r", "testu…", 0, 120_000, review)
        )
        val state = IrohaConnectViewStates.build(snapshot, null, null, busy = true)
        assertTrue(state.busy)
        assertEquals(R.string.iroha_connect_review_transfer, state.requestTitle)
        assertEquals(R.string.iroha_connect_connected_account, state.sessionRows.last().label)
        val rows = state.requestSections.flatMap { it.rows }
        assertEquals("1.5 XOR", rows.single { it.label == R.string.common_amount }.value)
        assertEquals("testu…dest", rows.single { it.label == R.string.signing_field_recipient }.value)
        assertEquals("dataspace:7", rows.single { it.label == R.string.iroha_connect_review_scope }.value)
        assertEquals("0.25 XOR", rows.single { it.label == R.string.iroha_connect_review_max_fee }.value)
        assertEquals("sponsor/program", rows.single { it.label == R.string.iroha_connect_review_fee_sponsor }.value)
        assertEquals(listOf("100"), rows.single { it.label == R.string.iroha_connect_review_valid_for }.valueArgs)
        assertEquals("ab".repeat(32), rows.single { it.label == R.string.iroha_connect_review_payload_hash }.value)
    }

    @Test
    fun shouldListEachContractCall_whenABatchAwaitsReview() {
        val calls = review.copy(
            kind = IrohaConnectTransactionReview.Kind.CONTRACT_CALLS,
            transfer = null,
            calls = listOf(
                IrohaConnectReviewContractCall("irohac1…", "place_order", "ab", 3, "cd"),
                IrohaConnectReviewContractCall("irohac1…", "cancel", "ef", null, null)
            )
        )
        val sections = IrohaConnectViewStates.reviewSections(calls)
        assertEquals(3, sections.size)
        val first = sections[0].rows.single { it.label == R.string.iroha_connect_review_arguments }
        assertEquals(R.string.iroha_connect_review_arguments_value, first.valueRes)
        assertEquals(listOf("3", "cd"), first.valueArgs)
        val second = sections[1].rows.single { it.label == R.string.iroha_connect_review_arguments }
        assertEquals(R.string.iroha_connect_review_no_arguments, second.valueRes)
    }

    @Test
    fun shouldTranslateEveryFailureNoticeAndActionError() {
        IrohaConnectFailure.entries.forEach { assertTrue(IrohaConnectViewStates.failureMessage(it) != 0) }
        IrohaConnectNoticeCode.entries.forEach { assertTrue(IrohaConnectViewStates.noticeMessage(it) != 0) }
        IrohaConnectActionError.entries.forEach { assertTrue(IrohaConnectViewStates.actionMessage(it) != 0) }
        val failed = IrohaConnectSnapshot(phase = IrohaConnectPhase.ERROR, failure = IrohaConnectFailure.OPEN_TIMEOUT)
        assertEquals(R.string.iroha_connect_error_open_timeout, IrohaConnectViewStates.build(failed, null, null, false).message)
        assertEquals(
            R.string.iroha_connect_error_invalid_link,
            IrohaConnectViewStates.build(failed, null, IrohaConnectFailure.INVALID_LINK, false).message
        )
        val closed = IrohaConnectSnapshot(notice = IrohaConnectNotice(0, IrohaConnectNoticeCode.DAPP_CLOSED))
        assertEquals(R.string.iroha_connect_notice_dapp_closed, IrohaConnectViewStates.build(closed, null, null, false).message)
    }
}
