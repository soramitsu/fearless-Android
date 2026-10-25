package jp.co.soramitsu.wallet.impl.presentation.send.setup

import jp.co.soramitsu.common.data.storage.WalletSelectionEpoch
import java.util.concurrent.atomic.AtomicLong

/** Rejects delayed quick amounts after any input, newer request or wallet/network transition. */
class SendAmountRequestGate {
    private val revision = AtomicLong()

    /** Call synchronously for each amount/recipient edit, including programmatic edits. */
    fun invalidate() { revision.incrementAndGet() }

    /** A new quick input supersedes every previous request. */
    fun begin(sharedRevision: Long): Ticket = Ticket(revision.incrementAndGet(), sharedRevision, WalletSelectionEpoch.snapshot())

    /** Must be checked after every suspension and immediately before applying the result. */
    fun isCurrent(ticket: Ticket, sharedRevision: Long): Boolean = ticket.inputRevision == revision.get() &&
        ticket.sharedRevision == sharedRevision && WalletSelectionEpoch.isCurrent(ticket.walletEpoch)

    data class Ticket(val inputRevision: Long, val sharedRevision: Long, val walletEpoch: Long)
}
