package jp.co.soramitsu.wallet.impl.presentation.send.confirm

import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import org.junit.Assert.assertEquals
import org.junit.Test

class ConfirmSendViewStateTest {
    @Test fun `network is the first confirmation fact before sender recipient and amount`() {
        val network = TitleValueViewState("Network", "Polkadot Asset Hub")
        val from = TitleValueViewState("From", "wallet")
        val state = ConfirmSendViewState("", networkInfoItem = network, fromInfoItem = from,
            amountInfoItem = TitleValueViewState("Amount", "7 USDT"), buttonState = ButtonViewState("Confirm", true))
        assertEquals(network, state.tableItems.first())
        assertEquals(from, state.tableItems[1])
    }
}
