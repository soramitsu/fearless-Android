package jp.co.soramitsu.walletconnect.impl.presentation

import co.jp.soramitsu.walletconnect.model.WalletConnectEvent
import co.jp.soramitsu.walletconnect.model.WalletConnectMetadata
import jp.co.soramitsu.walletconnect.impl.protocol.WalletConnectClient
import jp.co.soramitsu.walletconnect.impl.protocol.WalletConnectClientConfig
import jp.co.soramitsu.walletconnect.impl.protocol.WcMemoryStorage
import jp.co.soramitsu.walletconnect.impl.protocol.WcTestClock
import jp.co.soramitsu.walletconnect.impl.protocol.WcTestDapp
import jp.co.soramitsu.walletconnect.impl.protocol.WcTestRelay
import jp.co.soramitsu.walletconnect.impl.protocol.eventually
import jp.co.soramitsu.walletconnect.impl.protocol.storedSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class WCDelegateTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val relay = WcTestRelay()
    private val clock = WcTestClock()

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `a proposal that arrived before the root screen listened is replayed to it`() {
        val client = client()
        WCDelegate.attach(client)
        val dapp = WcTestDapp(relay, clock)
        runBlocking { client.pair(dapp.uri()) }
        val id = dapp.propose()
        eventually { WCDelegate.sessionProposalEvent?.first?.id == id }

        val replayed = runBlocking { withTimeout(5_000) { WCDelegate.walletEvents.first() } }

        assertEquals(id, (replayed as WalletConnectEvent.SessionProposal).proposal.id)
        assertTrue(WCDelegate.registerDelegatesIfReady().isSuccess)
        assertEquals(emptyList<Any>(), WCDelegate.getPendingListOfSessionRequests("unknown"))
        WCDelegate.refreshConnections()
        assertEquals(emptyList<Any>(), runBlocking { withTimeout(5_000) { WCDelegate.activeSessionFlow.first() } })
    }

    @Test
    fun `the delegate listens before attach returns, so an expiry found at start reaches the app`() {
        val topic = "cd".repeat(32)
        val client = client(WcMemoryStorage(storedSessionState(topic, expiry = clock.nowSeconds() - 1)), start = false)
        val received = CopyOnWriteArrayList<WalletConnectEvent>()
        scope.launch(start = CoroutineStart.UNDISPATCHED) { WCDelegate.walletEvents.collect { received += it } }

        WCDelegate.attach(client)
        assertTrue(client.hasCollector)
        client.start()

        eventually { received.any { it is WalletConnectEvent.SessionDeleted && it.topic == topic } }
    }

    private fun client(storage: WcMemoryStorage = WcMemoryStorage(), start: Boolean = true) = WalletConnectClient(
        storage = storage,
        transport = relay,
        verifyKeySource = { error("offline") },
        config = WalletConnectClientConfig(
            "0123456789abcdef0123456789abcdef",
            null,
            WalletConnectMetadata("w", "w", "https://w", emptyList()),
            setOf("personal_sign"),
            "ua"
        ),
        clock = clock,
        scope = scope
    ).also { if (start) it.start() }
}
