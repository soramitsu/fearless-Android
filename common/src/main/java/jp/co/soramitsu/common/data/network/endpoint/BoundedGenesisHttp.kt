package jp.co.soramitsu.common.data.network.endpoint

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

internal class ProbeProtocolException : IOException("Invalid probe response framing")

/** Dedicated one-request HTTP/1 exchange; input budgets wrap plaintext after the TLS handshake. */
internal object BoundedGenesisHttp {
    fun exchange(request: EndpointProbeRequest, lease: ProbeSocketLease, dns: Dns, timeout: Int): EndpointProbeOutcome {
        val url = request.candidateUrl.toHttpUrl()
        lease.requireActive()
        val address = dns.lookup(url.host).firstOrNull() ?: throw IOException("Probe host unavailable")
        lease.requireActive()
        val plain = lease.plainFactory().createSocket()
        plain.soTimeout = timeout
        plain.connect(InetSocketAddress(address, url.port), timeout)
        val socket = if (url.isHttps) {
            lease.requireActive()
            val tls = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(plain, url.host, url.port, true) as SSLSocket
            lease.registerLayeredSocket(plain, tls)
            tls.soTimeout = timeout
            tls.sslParameters = tls.sslParameters.apply {
                endpointIdentificationAlgorithm = "HTTPS"
                if (!url.host.contains(':') && !url.host.all { it in '0'..'9' || it == '.' }) {
                    serverNames = listOf(SNIHostName(url.host))
                }
            }
            tls.startHandshake()
            tls
        } else plain
        lease.requireActive()
        val body = GenesisProbeProtocol.request(request.family).toByteArray(Charsets.US_ASCII)
        val host = if (url.host.contains(':')) "[${url.host}]" else url.host
        val authority = host + if (url.port == if (url.isHttps) 443 else 80) "" else ":${url.port}"
        val target = url.encodedPath + (url.encodedQuery?.let { "?$it" } ?: "")
        val header = "POST $target HTTP/1.1\r\nHost: $authority\r\nContent-Type: application/json\r\n" +
            "Accept: application/json\r\nAccept-Encoding: identity\r\nConnection: close\r\nContent-Length: ${body.size}\r\n\r\n"
        socket.getOutputStream().apply { write(header.toByteArray(Charsets.US_ASCII)); write(body); flush() }
        lease.requireActive()
        val input = ProbeByteBudgetInputStream(socket.getInputStream(), GenesisProbeProtocol.STREAM_BYTES)
        val status = line(input, 1024)
        if (!status.matches(Regex("HTTP/1\\.[01] [0-9]{3}( [\\x20-\\x7e]*)?"))) throw ProbeProtocolException()
        if (status.substring(9, 12) != "200") return EndpointProbeOutcome.TRANSPORT_FAILURE
        val headers = headers(input)
        if (headers["content-encoding"]?.equals("identity", true) == false) throw ProbeProtocolException()
        if (headers.containsKey("content-length") && headers.containsKey("transfer-encoding")) throw ProbeProtocolException()
        val bytes = when {
            headers.containsKey("transfer-encoding") -> {
                if (!headers.getValue("transfer-encoding").equals("chunked", true)) throw ProbeProtocolException()
                chunks(input)
            }
            headers.containsKey("content-length") -> {
                val text = headers.getValue("content-length")
                if (!text.matches(Regex("[0-9]{1,10}"))) throw ProbeProtocolException()
                val size = text.toLong()
                if (size > GenesisProbeProtocol.RESPONSE_BYTES) throw ProbeByteLimitException()
                ByteArray(size.toInt()).also { exact(input, it, 0, it.size) }
            }
            else -> {
                val buffer = ByteArray(GenesisProbeProtocol.RESPONSE_BYTES + 1)
                var count = 0
                while (count < buffer.size) {
                    val read = input.read(buffer, count, buffer.size - count)
                    if (read < 0) break
                    count += read
                }
                buffer.copyOf(count)
            }
        }
        return GenesisProbeProtocol.inspect(request, bytes)
    }

    private fun line(input: InputStream, maximum: Int): String {
        val bytes = ByteArray(maximum)
        var count = 0
        while (true) {
            val value = input.read()
            if (value == -1) throw EOFException("Incomplete probe response")
            if (value == 13) {
                if (input.read() != 10) throw ProbeProtocolException()
                return String(bytes, 0, count, Charsets.US_ASCII)
            }
            if (value != 9 && value !in 32..126) throw ProbeProtocolException()
            if (count == maximum) throw ProbeByteLimitException()
            bytes[count++] = value.toByte()
        }
    }

    private fun headers(input: InputStream): Map<String, String> {
        val values = mutableMapOf<String, String>()
        var bytes = 0
        while (true) {
            val header = line(input, 2048)
            bytes += header.length + 2
            if (bytes > 16384) throw ProbeByteLimitException()
            if (header.isEmpty()) return values
            if (values.size == 64 || header[0] == ' ' || header[0] == '\t') throw ProbeProtocolException()
            val separator = header.indexOf(':')
            if (separator <= 0) throw ProbeProtocolException()
            val key = header.substring(0, separator).lowercase(java.util.Locale.ROOT)
            if (!key.matches(Regex("[a-z0-9!#$%&'*+.^_`|~-]+")) || key in values) throw ProbeProtocolException()
            values[key] = header.substring(separator + 1).trim(' ', '\t')
        }
    }

    private fun chunks(input: InputStream): ByteArray {
        val bytes = ByteArray(GenesisProbeProtocol.RESPONSE_BYTES)
        var count = 0
        while (true) {
            val header = line(input, 1024)
            val hex = header.substringBefore(';')
            if (!hex.matches(Regex("[0-9a-fA-F]{1,8}"))) throw ProbeProtocolException()
            requireChunkExtensions(header, hex.length)
            val size = hex.toLong(16)
            if (size > bytes.size - count) throw ProbeByteLimitException()
            if (size == 0L) {
                val trailers = headers(input)
                if (trailers.keys.any { it in setOf("content-length", "transfer-encoding", "content-encoding") }) throw ProbeProtocolException()
                return bytes.copyOf(count)
            }
            exact(input, bytes, count, size.toInt()); count += size.toInt()
            if (input.read() != 13 || input.read() != 10) throw ProbeProtocolException()
        }
    }

    private fun requireChunkExtensions(header: String, start: Int) {
        var index = start
        fun token(character: Char) = character in 'a'..'z' || character in 'A'..'Z' || character in '0'..'9' ||
            character in "!#$%&'*+-.^_`|~"
        while (index < header.length) {
            if (header[index++] != ';') throw ProbeProtocolException()
            val nameStart = index
            while (index < header.length && token(header[index])) index++
            if (index == nameStart) throw ProbeProtocolException()
            if (index < header.length && header[index] == '=') {
                index++
                if (index < header.length && header[index] == '"') {
                    index++
                    var closed = false
                    while (index < header.length) {
                        val character = header[index++]
                        if (character == '"') { closed = true; break }
                        if (character == '\\') {
                            if (index == header.length) throw ProbeProtocolException()
                            index++ // The bounded ASCII line reader already rejects controls except HTAB.
                        }
                    }
                    if (!closed) throw ProbeProtocolException()
                } else {
                    val valueStart = index
                    while (index < header.length && token(header[index])) index++
                    if (index == valueStart) throw ProbeProtocolException()
                }
            }
        }
    }

    private fun exact(input: InputStream, bytes: ByteArray, offset: Int, length: Int) {
        var read = 0
        while (read < length) {
            val count = input.read(bytes, offset + read, length - read)
            if (count == -1) throw EOFException("Incomplete probe response")
            read += count
        }
    }
}
