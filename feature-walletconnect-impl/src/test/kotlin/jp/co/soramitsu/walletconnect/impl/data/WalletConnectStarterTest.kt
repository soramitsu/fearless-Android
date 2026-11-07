package jp.co.soramitsu.walletconnect.impl.data

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import co.jp.soramitsu.walletconnect.model.WalletConnectMetadata
import jp.co.soramitsu.walletconnect.impl.presentation.WCDelegate
import jp.co.soramitsu.walletconnect.impl.protocol.WalletConnectClient
import jp.co.soramitsu.walletconnect.impl.protocol.WalletConnectClientConfig
import jp.co.soramitsu.walletconnect.impl.protocol.WcMemoryStorage
import jp.co.soramitsu.walletconnect.impl.protocol.WcTestClock
import jp.co.soramitsu.walletconnect.impl.protocol.WcTestRelay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class WalletConnectStarterTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `start attaches the client once and follows the process lifecycle`() {
        val client = WalletConnectClient(
            storage = WcMemoryStorage(),
            transport = WcTestRelay(),
            verifyKeySource = { error("offline") },
            config = WalletConnectClientConfig(
                "0123456789abcdef0123456789abcdef",
                null,
                WalletConnectMetadata("w", "w", "https://w", emptyList()),
                emptySet(),
                "ua"
            ),
            clock = WcTestClock(),
            scope = scope
        )
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry.createUnsafe(this)
            override val lifecycle: Lifecycle get() = registry
        }
        val starter = WalletConnectStarter(client) { owner.registry }

        starter.start()
        starter.start()
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)

        assertSame(client, WCDelegate.client)
        assertEquals(1, owner.registry.observerCount)
    }
}
