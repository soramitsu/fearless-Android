package jp.co.soramitsu.common.data.network.endpoint

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

internal object GenesisProbeProtocol {
    const val RESPONSE_BYTES = 16 * 1024
    const val STREAM_BYTES = 64 * 1024
    private const val BASE58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

    fun request(family: EndpointProbeFamily): String = when (family) {
        EndpointProbeFamily.SUBSTRATE -> "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"chain_getBlockHash\",\"params\":[0]}"
        EndpointProbeFamily.SOLANA -> "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"getGenesisHash\"}"
    }

    fun decodeIdentity(family: EndpointProbeFamily, text: String): ByteArray = when (family) {
        EndpointProbeFamily.SUBSTRATE -> {
            require(text.length == 66 && text.startsWith("0x") && text.drop(2).all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
                "Invalid genesis identity"
            }
            ByteArray(32) { text.substring(2 + it * 2, 4 + it * 2).toInt(16).toByte() }
        }
        EndpointProbeFamily.SOLANA -> {
            require(text.length in 32..44 && text.all { it in BASE58 }) { "Invalid genesis identity" }
            var value = BigInteger.ZERO
            text.forEach { value = value.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(BASE58.indexOf(it).toLong())) }
            val digits = if (value.signum() == 0) byteArrayOf() else value.toByteArray().let {
                if (it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it
            }
            val zeros = text.takeWhile { it == '1' }.length
            require(zeros + digits.size == 32) { "Invalid genesis identity" }
            ByteArray(zeros) + digits
        }
    }

    fun inspect(request: EndpointProbeRequest, bytes: ByteArray): EndpointProbeOutcome {
        if (bytes.size > RESPONSE_BYTES) return EndpointProbeOutcome.RESPONSE_LIMIT
        return try {
            val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val text = decoder.decode(ByteBuffer.wrap(bytes)).toString()
            require(!text.startsWith('\uFEFF'))
            var result: String? = null
            val seen = mutableSetOf<String>()
            JsonReader(StringReader(text)).use { reader ->
                reader.isLenient = false
                reader.beginObject()
                while (reader.hasNext()) {
                    val key = reader.nextName()
                    require(seen.add(key) && seen.size <= 3)
                    when (key) {
                        "jsonrpc" -> require(reader.peek() == JsonToken.STRING && reader.nextString() == "2.0")
                        "id" -> require(reader.peek() == JsonToken.NUMBER && reader.nextString() == "1")
                        "result" -> {
                            require(reader.peek() == JsonToken.STRING)
                            result = reader.nextString()
                        }
                        else -> throw IllegalArgumentException("Unexpected response field")
                    }
                }
                reader.endObject()
                require(reader.peek() == JsonToken.END_DOCUMENT && seen == setOf("jsonrpc", "id", "result"))
            }
            val observed = decodeIdentity(request.family, requireNotNull(result))
            if (MessageDigest.isEqual(request.expectedBytes, observed)) EndpointProbeOutcome.MATCH else EndpointProbeOutcome.MISMATCH
        } catch (_: Exception) {
            EndpointProbeOutcome.MALFORMED_RESPONSE
        }
    }
}
