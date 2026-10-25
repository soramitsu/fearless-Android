package jp.co.soramitsu.common.data.network.endpoint

import org.junit.Assert.*
import org.junit.Test

class GenesisProbeProtocolTest {
    private val hash = "0x" + "12".repeat(32)
    private val request = EndpointProbeRequest(EndpointProbeFamily.SUBSTRATE, "ws://127.0.0.1/", hash)
    private fun inspect(text: String) = GenesisProbeProtocol.inspect(request, text.toByteArray())
    private fun response(value: String) = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"$value\"}"

    @Test fun exactSubstrateIdentityAndSingleFixedRequest() {
        assertEquals(EndpointProbeOutcome.MATCH, inspect(response(hash.uppercase().replace("0X", "0x"))))
        assertEquals("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"chain_getBlockHash\",\"params\":[0]}", GenesisProbeProtocol.request(EndpointProbeFamily.SUBSTRATE))
    }

    @Test fun differentValidIdentityIsMismatch() {
        assertEquals(EndpointProbeOutcome.MISMATCH, inspect(response("0x" + "13".repeat(32))))
    }

    @Test fun malformedOrWrongLengthIdentitiesAreRejected() {
        listOf("", hash.drop(1), hash + "00", "0x" + "zz".repeat(32)).forEach {
            assertEquals(EndpointProbeOutcome.MALFORMED_RESPONSE, inspect(response(it)))
        }
    }

    @Test fun exactSolanaIdentityAndNoFallbackMethod() {
        val genesis = "5eykt4UsFv8P8NJdTREpY1vzqKqZKvdpKuc147dw2N9d"
        val request = EndpointProbeRequest(EndpointProbeFamily.SOLANA, "https://example.invalid/", genesis)
        assertEquals(32, request.expectedBytes.size)
        assertEquals(EndpointProbeOutcome.MATCH, GenesisProbeProtocol.inspect(request, response(genesis).toByteArray()))
        assertEquals("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"getGenesisHash\"}", GenesisProbeProtocol.request(EndpointProbeFamily.SOLANA))
    }

    @Test fun base58LeadingZeroAndInvalidAlphabetBounds() {
        assertArrayEquals(ByteArray(32), GenesisProbeProtocol.decodeIdentity(EndpointProbeFamily.SOLANA, "1".repeat(32)))
        listOf("1".repeat(31), "1".repeat(33), "0".repeat(32), "z".repeat(45)).forEach {
            assertThrows(IllegalArgumentException::class.java) { GenesisProbeProtocol.decodeIdentity(EndpointProbeFamily.SOLANA, it) }
        }
    }

    @Test fun versionAndIdMustMatchExactTypesAndLexicalValue() {
        listOf("\"1\"", "1.0", "1e0", "2", "null", "true", "[]").forEach {
            assertEquals(EndpointProbeOutcome.MALFORMED_RESPONSE, inspect(response(hash).replace("\"id\":1", "\"id\":$it")))
        }
        assertEquals(EndpointProbeOutcome.MALFORMED_RESPONSE, inspect(response(hash).replace("2.0", "1.0")))
    }

    @Test fun duplicateMissingUnknownAndErrorFieldsAreRejected() {
        listOf(response(hash).dropLast(1) + ",\"id\":1}", response(hash).replace("\"id\":1,", ""),
            response(hash).dropLast(1) + ",\"error\":null}", response(hash).dropLast(1) + ",\"extra\":[]}").forEach {
            assertEquals(EndpointProbeOutcome.MALFORMED_RESPONSE, inspect(it))
        }
    }

    @Test fun batchesTrailingDocumentsDeepValuesAndLenientJsonAreRejected() {
        listOf("[${response(hash)}]", response(hash) + "{}", "{id:1}", "{\"result\":" + "[".repeat(8000),
            response(hash).replace("\"$hash\"", "null"), response(hash).replace("\"$hash\"", "{}"), response(hash) + "//comment").forEach {
            assertEquals(EndpointProbeOutcome.MALFORMED_RESPONSE, inspect(it))
        }
    }

    @Test fun strictUtf8AndResponseByteCap() {
        assertEquals(EndpointProbeOutcome.MALFORMED_RESPONSE, GenesisProbeProtocol.inspect(request, byteArrayOf(0xc0.toByte(), 0xaf.toByte())))
        assertEquals(EndpointProbeOutcome.MALFORMED_RESPONSE, inspect("\uFEFF" + response(hash)))
        assertEquals(EndpointProbeOutcome.RESPONSE_LIMIT, GenesisProbeProtocol.inspect(request, ByteArray(16385)))
        assertEquals(EndpointProbeOutcome.MATCH, inspect(response(hash).padEnd(16384)))
    }

    @Test fun candidateMustAlreadyHaveExactCanonicalTransportForm() {
        listOf("ws://example.invalid", " ws://example.invalid/", "ws://user:pass@example.invalid/", "ws://example.invalid/#x",
            "WS://example.invalid/", "ws://EXAMPLE.invalid/", "ws://example.invalid:80/", "ws://example.invalid/é", "file:///x",
            "ws://example.invalid/" + "x".repeat(8192)).forEach {
            assertThrows(IllegalArgumentException::class.java) { EndpointProbeRequest(EndpointProbeFamily.SUBSTRATE, it, hash) }
        }
    }

    @Test fun familySchemeAdmissionAndDiagnosticsNeverEchoPayload() {
        assertThrows(IllegalArgumentException::class.java) { EndpointProbeRequest(EndpointProbeFamily.SUBSTRATE, "https://example.invalid/", hash) }
        val candidate = EndpointProbeRequest(EndpointProbeFamily.SUBSTRATE, "wss://example.invalid/private?token=synthetic", hash)
        assertFalse(candidate.toString().contains("synthetic")); assertFalse(candidate.toString().contains(hash))
        EndpointProbeOutcome.entries.forEach { assertFalse(it.toString().contains("synthetic")) }
    }
}
