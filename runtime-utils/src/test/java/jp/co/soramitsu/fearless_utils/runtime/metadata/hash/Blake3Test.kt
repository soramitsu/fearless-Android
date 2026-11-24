package jp.co.soramitsu.fearless_utils.runtime.metadata.hash

import jp.co.soramitsu.fearless_utils.extensions.toHexString
import org.junit.Assert.assertEquals
import org.junit.Test

class Blake3Test {

    // BLAKE3 test-vector inputs (byte i = i mod 251); hashes from the reference `blake3` crate 1.x.
    private val vectors = mapOf(
        0 to "af1349b9f5f9a1a6a0404dea36dcc9499bcb25c9adc112b7cc9a93cae41f3262",
        1 to "2d3adedff11b61f14c886e35afa036736dcd87a74d27b5c1510225d0f592e213",
        64 to "4eed7141ea4a5cd4b788606bd23f46e212af9cacebacdc7d1f4c6dc7f2511b98",
        1024 to "42214739f095a406f3fc83deb889744ac00df831c10daa55189b5d121c855af7",
        1025 to "d00278ae47eb27b34faecf67b4fe263f82d5412916c1ffd97c8cb7fb814b8444"
    )

    @Test
    fun `matches the reference implementation`() {
        vectors.forEach { (length, expected) ->
            val input = ByteArray(length) { (it % 251).toByte() }
            assertEquals("length $length", expected, Blake3.hash256(input).toHexString(false))
        }
    }
}
