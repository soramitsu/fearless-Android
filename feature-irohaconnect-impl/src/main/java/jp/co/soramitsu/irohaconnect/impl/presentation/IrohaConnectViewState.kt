package jp.co.soramitsu.irohaconnect.impl.presentation

import jp.co.soramitsu.common.R
import jp.co.soramitsu.irohaconnect.impl.domain.IrohaConnectActionError
import jp.co.soramitsu.irohaconnect.impl.domain.IrohaConnectFailure
import jp.co.soramitsu.irohaconnect.impl.domain.IrohaConnectNoticeCode
import jp.co.soramitsu.irohaconnect.impl.domain.IrohaConnectPhase
import jp.co.soramitsu.irohaconnect.impl.domain.IrohaConnectSnapshot
import jp.co.soramitsu.irohaconnect.impl.review.IrohaConnectReviewAsset
import jp.co.soramitsu.irohaconnect.impl.review.IrohaConnectTransactionReview

/** One labelled value: plain [value] text, or the string resource [valueRes] formatted with [valueArgs]. */
data class IrohaConnectRow(
    val label: Int,
    val value: String? = null,
    val valueRes: Int? = null,
    val valueArgs: List<String> = emptyList()
)

data class IrohaConnectSection(val title: Int?, val rows: List<IrohaConnectRow>)

data class IrohaConnectAccountItem(val address: String, val name: String, val selected: Boolean)

data class IrohaConnectViewState(
    val phase: IrohaConnectPhase = IrohaConnectPhase.IDLE,
    val appName: String? = null,
    val appUrl: String? = null,
    val sessionRows: List<IrohaConnectRow> = emptyList(),
    val permissions: IrohaConnectSection? = null,
    val accounts: List<IrohaConnectAccountItem> = emptyList(),
    val requestTitle: Int? = null,
    val requestSections: List<IrohaConnectSection> = emptyList(),
    /** A failure or notice to show, as a string resource. */
    val message: Int? = null,
    val busy: Boolean = false
)

internal object IrohaConnectViewStates {
    private const val MILLIS_DIGITS = 3

    fun build(
        snapshot: IrohaConnectSnapshot,
        selectedAccount: String?,
        linkFailure: IrohaConnectFailure?,
        busy: Boolean
    ): IrohaConnectViewState {
        val session = snapshot.session
        val sessionRows = buildList {
            session?.let {
                add(IrohaConnectRow(R.string.common_network, it.networkName))
                add(IrohaConnectRow(R.string.iroha_connect_relay, it.toriiBaseUrl))
            }
            if (snapshot.phase == IrohaConnectPhase.CONNECTED || snapshot.phase == IrohaConnectPhase.REQUEST_APPROVAL) {
                snapshot.selectedAccountId?.let { add(IrohaConnectRow(R.string.iroha_connect_connected_account, it)) }
            }
        }
        val permissions = session?.permissions?.let { granted ->
            IrohaConnectSection(
                R.string.iroha_connect_permissions,
                buildList {
                    add(IrohaConnectRow(R.string.connection_methods, granted.methods.joinToString(", ").ifEmpty { "—" }))
                    add(IrohaConnectRow(R.string.connection_events, granted.events.joinToString(", ").ifEmpty { "—" }))
                    granted.resources?.let { add(IrohaConnectRow(R.string.iroha_connect_resources, it.joinToString(", ").ifEmpty { "—" })) }
                }
            )
        }
        val chosen = selectedAccount ?: snapshot.accounts.firstOrNull()?.address
        val message = linkFailure?.let(::failureMessage)
            ?: snapshot.failure?.let(::failureMessage)
            ?: snapshot.notice?.code?.let(::noticeMessage)
        val review = snapshot.request?.review
        return IrohaConnectViewState(
            phase = snapshot.phase,
            appName = session?.appName,
            appUrl = session?.appUrl,
            sessionRows = sessionRows,
            permissions = permissions,
            accounts = snapshot.accounts.map { IrohaConnectAccountItem(it.address, it.name, it.address == chosen) },
            requestTitle = review?.let {
                if (it.kind == IrohaConnectTransactionReview.Kind.TRANSFER) R.string.iroha_connect_review_transfer else R.string.iroha_connect_review_contract_calls
            },
            requestSections = review?.let(::reviewSections).orEmpty(),
            message = message,
            busy = busy
        )
    }

    private fun assetText(asset: IrohaConnectReviewAsset) = asset.symbol ?: asset.definitionId

    fun reviewSections(review: IrohaConnectTransactionReview): List<IrohaConnectSection> {
        val main = buildList {
            review.transfer?.let { transfer ->
                add(IrohaConnectRow(R.string.common_amount, "${transfer.quantity} ${assetText(transfer.asset)}"))
                add(IrohaConnectRow(R.string.signing_field_recipient, transfer.destination))
                transfer.scope?.let { add(IrohaConnectRow(R.string.iroha_connect_review_scope, it)) }
            }
        }
        val calls = review.calls.orEmpty().map { call ->
            IrohaConnectSection(
                null,
                listOf(
                    IrohaConnectRow(R.string.iroha_connect_review_entrypoint, call.entrypoint),
                    IrohaConnectRow(R.string.iroha_connect_review_contract, call.contractAddress),
                    if (call.argumentBytes == null) {
                        IrohaConnectRow(R.string.iroha_connect_review_arguments, valueRes = R.string.iroha_connect_review_no_arguments)
                    } else {
                        IrohaConnectRow(
                            R.string.iroha_connect_review_arguments,
                            valueRes = R.string.iroha_connect_review_arguments_value,
                            valueArgs = listOf(call.argumentBytes.toString(), call.argumentsSha256.orEmpty())
                        )
                    },
                    IrohaConnectRow(R.string.iroha_connect_review_code_hash, call.codeHashHex)
                )
            )
        }
        val fee = buildList {
            val limits = review.fee.chargeLimits.joinToString(", ") { "${it.maxAmount} ${assetText(it.asset)}" }
            if (limits.isNotEmpty()) add(IrohaConnectRow(R.string.iroha_connect_review_max_fee, limits))
            review.fee.program?.let { add(IrohaConnectRow(R.string.iroha_connect_review_fee_sponsor, it)) }
            review.fee.gasLimit?.let { add(IrohaConnectRow(R.string.iroha_connect_review_gas_limit, it)) }
            review.ttlMs?.let {
                val seconds = it.toBigDecimal().movePointLeft(MILLIS_DIGITS).stripTrailingZeros().toPlainString()
                add(IrohaConnectRow(R.string.iroha_connect_review_valid_for, valueRes = R.string.iroha_connect_review_seconds, valueArgs = listOf(seconds)))
            }
            review.nonce?.let { add(IrohaConnectRow(R.string.iroha_connect_review_nonce, it)) }
            review.metadata?.let { add(IrohaConnectRow(R.string.iroha_connect_review_metadata, it)) }
            add(IrohaConnectRow(R.string.iroha_connect_review_payload_hash, review.payloadHashHex))
        }
        val mainSection = main.takeIf { it.isNotEmpty() }?.let { IrohaConnectSection(null, it) }
        return listOfNotNull(mainSection) + calls + IrohaConnectSection(null, fee)
    }

    fun failureMessage(failure: IrohaConnectFailure): Int = when (failure) {
        IrohaConnectFailure.INVALID_LINK -> R.string.iroha_connect_error_invalid_link
        IrohaConnectFailure.RETIRED_LINK -> R.string.iroha_connect_error_retired_link
        IrohaConnectFailure.UNKNOWN_NETWORK -> R.string.iroha_connect_error_unknown_network
        IrohaConnectFailure.NETWORK_UNAVAILABLE -> R.string.iroha_connect_error_network_unavailable
        IrohaConnectFailure.WRONG_NODE -> R.string.iroha_connect_error_wrong_node
        IrohaConnectFailure.RELAY_UNREACHABLE,
        IrohaConnectFailure.SESSION_ENDED,
        IrohaConnectFailure.KEEPALIVE_FAILED -> R.string.iroha_connect_error_connection_lost
        IrohaConnectFailure.OPEN_TIMEOUT -> R.string.iroha_connect_error_open_timeout
        IrohaConnectFailure.PROTOCOL_ERROR -> R.string.iroha_connect_error_protocol
    }

    fun noticeMessage(code: IrohaConnectNoticeCode): Int = when (code) {
        IrohaConnectNoticeCode.RAW_SIGNING_UNAVAILABLE -> R.string.iroha_connect_notice_raw_signing
        IrohaConnectNoticeCode.TRANSACTION_UNSUPPORTED -> R.string.iroha_connect_notice_unsupported
        IrohaConnectNoticeCode.TRANSACTION_INVALID -> R.string.iroha_connect_notice_invalid
        IrohaConnectNoticeCode.TRANSACTION_WRONG_ACCOUNT -> R.string.iroha_connect_notice_wrong_account
        IrohaConnectNoticeCode.PERMISSION_DENIED -> R.string.iroha_connect_notice_permission
        IrohaConnectNoticeCode.REQUEST_IN_PROGRESS -> R.string.iroha_connect_notice_in_progress
        IrohaConnectNoticeCode.REQUEST_EXPIRED -> R.string.iroha_connect_notice_expired
        IrohaConnectNoticeCode.DAPP_CLOSED -> R.string.iroha_connect_notice_dapp_closed
    }

    fun actionMessage(error: IrohaConnectActionError): Int = when (error) {
        IrohaConnectActionError.WALLET_LOCKED -> R.string.iroha_connect_action_locked
        IrohaConnectActionError.CANNOT_SIGN,
        IrohaConnectActionError.KEY_MISMATCH,
        IrohaConnectActionError.ACCOUNT_UNAVAILABLE -> R.string.iroha_connect_action_cannot_sign
        IrohaConnectActionError.REQUEST_EXPIRED -> R.string.iroha_connect_notice_expired
        IrohaConnectActionError.NOT_AVAILABLE,
        IrohaConnectActionError.RELAY_DISCONNECTED -> R.string.iroha_connect_action_unavailable
    }
}
