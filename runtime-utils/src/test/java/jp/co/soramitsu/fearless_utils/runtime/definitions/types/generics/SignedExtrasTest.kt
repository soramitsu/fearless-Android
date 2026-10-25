package jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics

import jp.co.soramitsu.fearless_utils.common.assertThrows
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.errors.EncodeDecodeException
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.toHex
import jp.co.soramitsu.fearless_utils.runtime.metadata.ExtrinsicMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigInteger

@RunWith(MockitoJUnitRunner::class)
class SignedExtrasTest {

    @Mock
    lateinit var runtime: RuntimeSnapshot

    @Mock
    lateinit var metadata: RuntimeMetadata

    @Mock
    lateinit var extrinsicMetadata: ExtrinsicMetadata

    @Before
    fun setup() {
        `when`(runtime.metadata).thenReturn(metadata)
        `when`(metadata.extrinsic).thenReturn(extrinsicMetadata)
    }

    @Test
    fun `should encode full set of extras`() {
        extrinsicContainsExtras(SignedExtras.ERA, SignedExtras.NONCE, SignedExtras.TIP)

        val extras = mapOf(
            SignedExtras.TIP to BigInteger.ONE,
            SignedExtras.NONCE to BigInteger.TEN,
            SignedExtras.ERA to Era.Immortal
        )

        val encoded = SignedExtras.toHex(runtime, extras)

        assertEquals("0x002804", encoded)
    }

    @Test
    fun `should encode partial set of extras and ignore unused arguments`() {
        extrinsicContainsExtras(SignedExtras.NONCE, SignedExtras.TIP)

        val extras = mapOf(
            SignedExtras.TIP to BigInteger.ONE,
            SignedExtras.NONCE to BigInteger.TEN,
            SignedExtras.ERA to Era.Immortal // CheckMortality is unused
        )

        val encoded = SignedExtras.toHex(runtime, extras)

        assertEquals("0x2804", encoded)
    }

    @Test
    fun `should encode empty set of extras and ignore unused arguments`() {
        extrinsicContainsExtras()

        // all are unused
        val extras = mapOf(
            SignedExtras.TIP to BigInteger.ONE,
            SignedExtras.NONCE to BigInteger.TEN,
            SignedExtras.ERA to Era.Immortal
        )

        val encoded = SignedExtras.toHex(runtime, extras)

        assertEquals("0x", encoded)
    }

    @Test
    fun `should require used extras`() {
        extrinsicContainsExtras(SignedExtras.NONCE, SignedExtras.TIP)

        val extras = mapOf(
            SignedExtras.TIP to BigInteger.ONE,
        )

        assertThrows<EncodeDecodeException> {
            SignedExtras.toHex(runtime, extras)
        }
    }

    @Test
    fun `should decode full set of extras`() {
        extrinsicContainsExtras(SignedExtras.ERA, SignedExtras.NONCE, SignedExtras.TIP)

        val inHex = "0x002804"

        val decoded = SignedExtras.fromHex(runtime, inHex)

        assertEquals(decoded.size, 3)
    }

    @Test
    fun `should decode partial set of extras`() {
        extrinsicContainsExtras(SignedExtras.NONCE, SignedExtras.TIP)

        val inHex = "0x2804"

        val decoded = SignedExtras.fromHex(runtime, inHex)

        assertEquals(decoded.size, 2)
    }

    @Test
    fun `should decode empty set of extras`() {
        extrinsicContainsExtras()

        val inHex = "0x"

        val decoded = SignedExtras.fromHex(runtime, inHex)

        assertEquals(decoded.size, 0)
    }

    @Test
    fun `should encode CheckAppId and CheckMetadataHash mode`() {
        extrinsicContainsExtras(SignedExtras.ERA, SignedExtras.NONCE, SignedExtras.TIP, SignedExtras.APP_ID, SignedExtras.CHECK_METADATA_HASH)

        val extras = mapOf(
            SignedExtras.TIP to BigInteger.ONE,
            SignedExtras.NONCE to BigInteger.TEN,
            SignedExtras.ERA to Era.Immortal,
            SignedExtras.APP_ID to BigInteger.valueOf(5),
            SignedExtras.CHECK_METADATA_HASH to BigInteger.ZERO
        )

        val encoded = SignedExtras.toHex(runtime, extras)

        assertEquals("0x0028041400", encoded)
    }

    @Test
    fun `should default CheckAppId and CheckMetadataHash when a caller-built map omits them`() {
        extrinsicContainsExtras(SignedExtras.ERA, SignedExtras.NONCE, SignedExtras.TIP, SignedExtras.APP_ID, SignedExtras.CHECK_METADATA_HASH)

        // e.g. fee-quote extrinsics assembled by the app without the builder
        val extras = mapOf(
            SignedExtras.TIP to BigInteger.ONE,
            SignedExtras.NONCE to BigInteger.TEN,
            SignedExtras.ERA to Era.Immortal
        )

        val encoded = SignedExtras.toHex(runtime, extras)

        assertEquals("0x0028040000", encoded)
    }

    @Test
    fun `should not emit CheckMetadataHash or CheckAppId for runtimes that do not list them`() {
        extrinsicContainsExtras(SignedExtras.ERA, SignedExtras.NONCE, SignedExtras.TIP)

        val extras = mapOf(
            SignedExtras.TIP to BigInteger.ONE,
            SignedExtras.NONCE to BigInteger.TEN,
            SignedExtras.ERA to Era.Immortal,
            SignedExtras.APP_ID to BigInteger.valueOf(5),
            SignedExtras.CHECK_METADATA_HASH to BigInteger.ZERO
        )

        val encoded = SignedExtras.toHex(runtime, extras)

        assertEquals("0x002804", encoded)
    }

    @Test
    fun `should encode asset tx payment tip with native fee asset`() {
        extrinsicContainsExtras(SignedExtras.ERA, SignedExtras.NONCE, SignedExtras.ASSET_TX_PAYMENT, SignedExtras.CHECK_METADATA_HASH)

        val extras = mapOf(
            SignedExtras.NONCE to BigInteger.TEN,
            SignedExtras.ERA to Era.Immortal,
            SignedExtras.ASSET_TX_PAYMENT to listOf(BigInteger.ONE, null)
        )

        val encoded = SignedExtras.toHex(runtime, extras)

        assertEquals("0x0028040000", encoded)
    }

    @Test
    fun `should decode CheckMetadataHash without shifting the call`() {
        extrinsicContainsExtras(SignedExtras.ERA, SignedExtras.NONCE, SignedExtras.TIP, SignedExtras.CHECK_METADATA_HASH)

        val decoded = SignedExtras.fromHex(runtime, "0x00280400")

        assertEquals(4, decoded.size)
        assertEquals(BigInteger.ZERO, decoded[SignedExtras.CHECK_METADATA_HASH])
        assertEquals(BigInteger.TEN, decoded[SignedExtras.NONCE])
    }

    @Test
    fun `should decode CheckAppId`() {
        extrinsicContainsExtras(SignedExtras.ERA, SignedExtras.NONCE, SignedExtras.TIP, SignedExtras.APP_ID)

        val decoded = SignedExtras.fromHex(runtime, "0x00280414")

        assertEquals(BigInteger.valueOf(5), decoded[SignedExtras.APP_ID])
    }

    @Test
    fun `should encode CheckMetadataHash None in additional signed data`() {
        extrinsicContainsExtras(
            AdditionalExtras.SPEC_VERSION,
            AdditionalExtras.TX_VERSION,
            AdditionalExtras.GENESIS,
            AdditionalExtras.BLOCK_HASH,
            AdditionalExtras.CHECK_METADATA_HASH
        )
        val additional = mapOf(
            AdditionalExtras.SPEC_VERSION to BigInteger.ONE,
            AdditionalExtras.TX_VERSION to BigInteger.valueOf(2),
            AdditionalExtras.GENESIS to ByteArray(32) { 4 },
            AdditionalExtras.BLOCK_HASH to ByteArray(32) { 5 },
            AdditionalExtras.CHECK_METADATA_HASH to BigInteger.ZERO
        )
        val expected = "0x" + "01000000" + "02000000" + "04".repeat(32) + "05".repeat(32) + "00"

        assertEquals(expected, AdditionalExtras.toHex(runtime, additional))
        assertEquals(expected, AdditionalExtras.toHex(runtime, additional - AdditionalExtras.CHECK_METADATA_HASH))
    }

    @Test
    fun `should still require additional data without a default`() {
        extrinsicContainsExtras(AdditionalExtras.SPEC_VERSION, AdditionalExtras.CHECK_METADATA_HASH)

        assertThrows<EncodeDecodeException> {
            AdditionalExtras.toHex(runtime, mapOf(AdditionalExtras.CHECK_METADATA_HASH to BigInteger.ZERO))
        }
    }

    private fun extrinsicContainsExtras(vararg extras: String) {
        `when`(extrinsicMetadata.signedExtensions).thenReturn(extras.toList())
    }
}