package jp.co.soramitsu.runtime.multiNetwork.connection

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

internal const val CONVERGENCE_MILLIS = 10_000L
private const val POLL_MILLIS = 25L

/** Polls until [condition] holds. False once [timeoutMillis] have passed without it. */
internal fun eventually(timeoutMillis: Long = CONVERGENCE_MILLIS, condition: () -> Boolean): Boolean {
    val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
    while (true) {
        if (condition()) return true
        if (System.nanoTime() >= deadline) return false
        Thread.sleep(POLL_MILLIS)
    }
}

/**
 * One EVM JSON-RPC node on 127.0.0.1, reached by web3j's HttpService over a real socket.
 *
 * Each node answers eth_blockNumber with its own block, so a read through the pool shows which node
 * served it. Every request closes its connection, so [requests] counts every read that arrived.
 * A node can hold one request unanswered, which keeps a pool probe in flight while a test changes
 * the node list, and can answer with HTTP 500, which web3j raises as a failed read.
 */
internal class LoopbackEvmNode(
    private val blockNumber: Long,
    private val evmChainId: Long
) : AutoCloseable {
    private val server = ServerSocket(0, BACKLOG, InetAddress.getByName("127.0.0.1"))

    val url = "http://127.0.0.1:${server.localPort}/"

    /** JSON-RPC requests that reached this node, counted on arrival and before any hold. */
    val requests = AtomicInteger()

    /** When false, every answer is HTTP 500. Read when the answer is written, so a held request follows it. */
    @Volatile
    var healthy = true

    private val settled = AtomicInteger()
    private val openSockets = ConcurrentHashMap.newKeySet<Socket>()
    private val pendingHold = AtomicReference<Hold?>()
    private val malformed = AtomicReference<Throwable?>()

    @Volatile
    private var lastHold: Hold? = null

    @Volatile
    private var closed = false

    private val acceptor = thread(isDaemon = true, name = "loopback-evm-${server.localPort}") {
        while (true) {
            val socket = try {
                server.accept()
            } catch (_: IOException) {
                break
            }
            openSockets += socket
            thread(isDaemon = true) { serve(socket) }
        }
    }

    /** Leaves the next request unanswered until [release]. Requests after it are answered at once. */
    fun holdNextRequest() {
        val hold = Hold()
        lastHold = hold
        pendingHold.set(hold)
    }

    fun awaitHeldRequest(timeoutMillis: Long = CONVERGENCE_MILLIS): Boolean =
        checkNotNull(lastHold) { "holdNextRequest was not called" }.arrived.await(timeoutMillis, TimeUnit.MILLISECONDS)

    fun release() {
        lastHold?.released?.countDown()
    }

    fun awaitRequests(count: Int, timeoutMillis: Long): Boolean = eventually(timeoutMillis) { requests.get() >= count }

    /** True once [count] requests are finished with: answered, or dropped because the client had closed its socket. */
    fun awaitSettled(count: Int, timeoutMillis: Long = CONVERGENCE_MILLIS): Boolean =
        eventually(timeoutMillis) { settled.get() >= count }

    /** Takes the node offline. Later connections are refused. */
    override fun close() {
        if (closed) return
        closed = true
        server.close()
        release()
        openSockets.forEach { runCatching { it.close() } }
        acceptor.join(JOIN_MILLIS)
        malformed.get()?.let { throw AssertionError("The loopback EVM node received a malformed request", it) }
    }

    private fun serve(socket: Socket) {
        var arrived = false
        try {
            socket.use {
                it.soTimeout = SOCKET_TIMEOUT_MILLIS
                val request = readJsonRpcRequest(it)
                requests.incrementAndGet()
                arrived = true
                pendingHold.getAndSet(null)?.let { hold ->
                    hold.arrived.countDown()
                    hold.released.await(HOLD_LIMIT_SECONDS, TimeUnit.SECONDS)
                }
                respond(it, request)
            }
        } catch (_: IOException) {
            // The client went away, or this node was closed while it served the request.
        } catch (error: Throwable) {
            malformed.compareAndSet(null, error)
        } finally {
            openSockets -= socket
            if (arrived) settled.incrementAndGet()
        }
    }

    private fun readJsonRpcRequest(socket: Socket): String {
        val input = socket.getInputStream()
        val head = ByteArrayOutputStream()
        while (!head.toString("US-ASCII").endsWith("\r\n\r\n")) {
            val byte = input.read()
            if (byte < 0) throw EOFException("The request ended inside its headers")
            check(head.size() < MAX_HEAD_BYTES) { "The request headers are too large" }
            head.write(byte)
        }
        val headers = head.toString("US-ASCII")
        check(headers.startsWith("POST ")) { "Unexpected request: ${headers.substringBefore("\r\n")}" }
        val length = headers.split("\r\n")
            .firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
            ?.substringAfter(':')?.trim()?.toInt()
        check(length != null && length in 1..MAX_BODY_BYTES) { "Unexpected JSON-RPC body length: $length" }
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val count = input.read(body, read, length - read)
            if (count < 0) throw EOFException("The request ended inside its body")
            read += count
        }
        return body.toString(Charsets.UTF_8)
    }

    private fun answer(request: String): String {
        val id = checkNotNull(REQUEST_ID.find(request)) { "JSON-RPC request without a numeric id" }.groupValues[1]
        val method = checkNotNull(REQUEST_METHOD.find(request)) { "JSON-RPC request without a method" }.groupValues[1]
        val result = when (method) {
            "eth_blockNumber" -> quantity(blockNumber)
            // Identity reads, in case the pool checks a node before using it.
            "eth_chainId" -> quantity(evmChainId)
            "net_version" -> evmChainId.toString()
            "web3_clientVersion" -> "Loopback/1.0"
            else -> return """{"jsonrpc":"2.0","id":$id,"error":{"code":-32601,"message":"Method not found"}}"""
        }
        return """{"jsonrpc":"2.0","id":$id,"result":"$result"}"""
    }

    private fun quantity(value: Long) = "0x" + value.toString(HEX)

    private fun respond(socket: Socket, request: String) {
        val (status, body) = if (healthy) "200 OK" to answer(request) else "500 Internal Server Error" to "{}"
        val bytes = body.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $status\r\nContent-Type: application/json\r\n" +
            "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().apply {
            write(head.toByteArray(Charsets.US_ASCII))
            write(bytes)
            flush()
        }
    }

    private class Hold {
        val arrived = CountDownLatch(1)
        val released = CountDownLatch(1)
    }

    private companion object {
        const val BACKLOG = 50
        const val HEX = 16
        const val SOCKET_TIMEOUT_MILLIS = 5_000
        const val HOLD_LIMIT_SECONDS = 60L
        const val JOIN_MILLIS = 3_000L
        const val MAX_HEAD_BYTES = 16_384
        const val MAX_BODY_BYTES = 65_536
        val REQUEST_ID = Regex("\"id\"\\s*:\\s*(\\d+)")
        val REQUEST_METHOD = Regex("\"method\"\\s*:\\s*\"([^\"]+)\"")
    }
}
