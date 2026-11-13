package jp.co.soramitsu.tonconnect.api.model

import jp.co.soramitsu.coredb.model.ConnectionSource
import org.junit.Assert.assertEquals
import org.junit.Test

class BridgeEventTest {

    @Test
    fun shouldAnswerWithTheDappRequestId_notTheBridgeEventId() {
        val event = BridgeEvent(
            eventId = 1_700_000_000_123_456L,
            message = BridgeEvent.Message(BridgeMethod.SEND_TRANSACTION, listOf("{}"), id = 12),
            connection = TonDappConnection(
                metaId = 1,
                clientId = "a".repeat(64),
                name = "App",
                icon = "https://example.com/icon.png",
                url = "https://example.com",
                source = ConnectionSource.QR
            )
        )

        assertEquals(12L, event.requestId)
    }
}
