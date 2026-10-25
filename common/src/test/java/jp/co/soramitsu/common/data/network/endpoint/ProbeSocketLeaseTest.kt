package jp.co.soramitsu.common.data.network.endpoint

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

class ProbeSocketLeaseTest {
    @Test fun onlyOnePhysicalSocketIsAdmittedAndReleaseWaitsForWorkerAndClose() {
        val close = mutableListOf<Runnable>(); val released = AtomicInteger()
        val lease = ProbeSocketLease(Executor { close.add(it) }) { released.incrementAndGet() }
        val socket = lease.plainFactory().createSocket()
        assertThrows(IOException::class.java) { lease.plainFactory().createSocket() }
        lease.finished(); assertEquals(0, released.get()); assertFalse(socket.isClosed)
        close.single().run(); assertTrue(socket.isClosed); assertEquals(1, released.get())
        lease.finished(); lease.abort(); assertEquals(1, released.get()); assertEquals(1, close.size)
    }

    @Test fun cancellationBeforeDnsReturnsPreventsAnySocketCreation() {
        val released = AtomicInteger(); val lease = ProbeSocketLease(Executor { it.run() }) { released.incrementAndGet() }
        lease.abort(); assertEquals(0, released.get())
        assertThrows(IOException::class.java) { lease.plainFactory().createSocket() }
        assertThrows(IOException::class.java) { lease.tlsFactory().createSocket() }
        assertThrows(IOException::class.java) { lease.requireActive() }
        lease.finished(); assertEquals(1, released.get())
    }

    @Test fun layeredTlsSocketMustOwnTheRegisteredPlainSocketAndCloseWithLease() {
        val close = mutableListOf<Runnable>()
        val lease = ProbeSocketLease(Executor { close.add(it) }) {}
        val plain = lease.plainFactory().createSocket()
        val tls = object : java.net.Socket() {
            override fun close() { plain.close(); super.close() }
        }
        lease.registerLayeredSocket(plain, tls)
        lease.finished(); assertFalse(tls.isClosed); assertFalse(plain.isClosed)
        close.single().run(); assertTrue(tls.isClosed); assertTrue(plain.isClosed)
    }

    @Test fun cancelledOrWrongOwnerLayeredSocketIsClosedWithoutReplacingTrackedSocket() {
        val lease = ProbeSocketLease(Executor { it.run() }) {}
        val plain = lease.plainFactory().createSocket()
        val wrong = java.net.Socket()
        java.net.Socket().use { other ->
            assertThrows(IOException::class.java) { lease.registerLayeredSocket(other, wrong) }
        }
        assertTrue(wrong.isClosed); assertFalse(plain.isClosed)
        lease.abort(); val late = java.net.Socket()
        assertThrows(IOException::class.java) { lease.registerLayeredSocket(plain, late) }
        assertTrue(late.isClosed); assertTrue(plain.isClosed); lease.finished()
    }

    @Test fun connectedOverloadsCannotBypassRegistration() {
        val lease = ProbeSocketLease(Executor { it.run() }) {}
        assertThrows(IOException::class.java) { lease.plainFactory().createSocket("localhost", 1) }
        assertThrows(IOException::class.java) { lease.tlsFactory().createSocket("localhost", 1) }
        lease.finished()
    }
}
