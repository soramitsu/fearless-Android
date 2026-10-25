package jp.co.soramitsu.wallet.impl.presentation.send.setup

import jp.co.soramitsu.common.data.storage.WalletSelectionEpoch
import jp.co.soramitsu.wallet.impl.presentation.send.SendSharedState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.Assert.*
import org.junit.Test

class SendAmountRequestGateTest {
    @Test fun `delayed Max cannot overwrite manual amount or recipient edits even when values return`() = runTest {
        for (edit in listOf("amount", "recipient")) {
            val gate = SendAmountRequestGate(); val request = gate.begin(0)
            val response = CompletableDeferred<Unit>()
            val old = async { response.await(); gate.isCurrent(request, 0) }
            gate.invalidate(); gate.invalidate() // edit away, then return to original value
            response.complete(Unit)
            assertFalse(edit, old.await())
        }
    }

    @Test fun `newer quick input supersedes older response and reset delay rechecks request`() = runTest {
        val gate = SendAmountRequestGate(); val old = gate.begin(0)
        val delayed = async { delay(70); gate.isCurrent(old, 0) }
        runCurrent(); advanceTimeBy(35)
        val latest = gate.begin(0)
        assertFalse(delayed.await()); assertTrue(gate.isCurrent(latest, 0))
        gate.invalidate(); assertFalse(gate.isCurrent(latest, 0))
    }

    @Test fun `exact asset and wallet A B A cannot revive suspended Max`() = runTest {
        val shared = SendSharedState(); shared.update("a", "1")
        val gate = SendAmountRequestGate(); val request = gate.begin(shared.revision)
        shared.update("b", "1"); shared.update("a", "1")
        assertFalse(gate.isCurrent(request, shared.revision))
        val asset = gate.begin(shared.revision)
        shared.update("a", "2"); shared.update("a", "1")
        assertFalse(gate.isCurrent(asset, shared.revision))
        val wallet = gate.begin(shared.revision)
        repeat(2) { WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange() }
        assertFalse(gate.isCurrent(wallet, shared.revision))
        val recipient = gate.begin(shared.revision)
        shared.updateAddress("different"); shared.clearAddress()
        assertFalse(gate.isCurrent(recipient, shared.revision))
        val cleared = gate.begin(shared.revision); shared.clear()
        assertFalse(gate.isCurrent(cleared, shared.revision))
    }
}
