package jp.co.soramitsu.common.data.network.endpoint

import java.io.IOException
import java.io.InputStream

internal class ProbeByteLimitException : IOException("Probe byte limit exceeded")

/** Bounds actual post-TLS HTTP plaintext, including headers and chunk extensions. */
internal class ProbeByteBudgetInputStream(private val input: InputStream, private val maximum: Int) : InputStream() {
    private var consumed = 0

    init { require(maximum >= 0) }

    private fun remaining(): Int {
        if (consumed >= maximum) throw ProbeByteLimitException()
        return maximum - consumed
    }

    override fun read(): Int {
        remaining()
        return input.read().also { if (it >= 0) consumed++ }
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        if (offset < 0 || length < 0 || offset > bytes.size - length) throw IndexOutOfBoundsException()
        if (length == 0) return 0
        return input.read(bytes, offset, minOf(length, remaining())).also { if (it > 0) consumed += it }
    }

    override fun skip(count: Long): Long {
        if (count <= 0) return 0
        return input.skip(minOf(count, remaining().toLong())).also { consumed += it.toInt() }
    }

    override fun available(): Int = minOf(input.available(), maximum - consumed)
    override fun close() = input.close()
    override fun markSupported(): Boolean = false
}
