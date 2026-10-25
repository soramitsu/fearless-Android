package jp.co.soramitsu.common.data.network.endpoint

import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.SocketFactory
import javax.net.ssl.SSLSocketFactory

/** Registers the sole raw socket before connect; cancellation never waits for DNS or close. */
internal class ProbeSocketLease(private val closer: Executor, private val release: () -> Unit) {
    private val lock = Any()
    private var socket: Socket? = null
    private var stopped = false
    private var socketReserved = false
    private var workerDone = false
    private var closeDone = false
    private val released = AtomicBoolean()

    fun requireActive() = synchronized(lock) { if (stopped) throw IOException("Probe cancelled") }

    private fun create(factory: () -> Socket): Socket {
        synchronized(lock) {
            if (stopped || socketReserved) throw IOException("Probe socket unavailable")
            socketReserved = true
        }
        val created = factory()
        val accepted = synchronized(lock) {
            if (stopped) false else { socket = created; true }
        }
        if (!accepted) {
            created.close()
            throw IOException("Probe cancelled")
        }
        return created
    }

    fun registerLayeredSocket(plain: Socket, tls: Socket) {
        val accepted = synchronized(lock) {
            if (stopped || socket !== plain) false else { socket = tls; true }
        }
        if (!accepted) {
            tls.close()
            throw IOException("Probe cancelled")
        }
    }

    fun abort() {
        val toClose: Socket?
        synchronized(lock) {
            if (stopped) return
            stopped = true
            toClose = socket
        }
        closer.execute {
            try { toClose?.close() } catch (_: IOException) { /* No payload diagnostics. */ }
            finally {
                synchronized(lock) { closeDone = true }
                releaseIfFinished()
            }
        }
    }

    fun finished() {
        abort()
        synchronized(lock) { workerDone = true }
        releaseIfFinished()
    }

    private fun releaseIfFinished() {
        val complete = synchronized(lock) { workerDone && closeDone }
        if (complete && released.compareAndSet(false, true)) release()
    }

    fun plainFactory(): SocketFactory = object : SocketFactory() {
        override fun createSocket(): Socket = create { Socket() }
        override fun createSocket(host: String, port: Int): Socket = unsupported()
        override fun createSocket(host: String, port: Int, local: InetAddress, localPort: Int): Socket = unsupported()
        override fun createSocket(host: InetAddress, port: Int): Socket = unsupported()
        override fun createSocket(host: InetAddress, port: Int, local: InetAddress, localPort: Int): Socket = unsupported()
    }

    fun tlsFactory(): SSLSocketFactory {
        val delegate = SSLSocketFactory.getDefault() as SSLSocketFactory
        return object : SSLSocketFactory() {
            override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
            override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites
            override fun createSocket(): Socket = create { delegate.createSocket() }
            override fun createSocket(socket: Socket, host: String, port: Int, autoClose: Boolean): Socket = unsupported()
            override fun createSocket(host: String, port: Int): Socket = unsupported()
            override fun createSocket(host: String, port: Int, local: InetAddress, localPort: Int): Socket = unsupported()
            override fun createSocket(host: InetAddress, port: Int): Socket = unsupported()
            override fun createSocket(host: InetAddress, port: Int, local: InetAddress, localPort: Int): Socket = unsupported()
        }
    }

    private fun unsupported(): Nothing = throw IOException("Unsupported probe socket operation")
}
