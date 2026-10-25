package jp.co.soramitsu.fearless_utils.encrypt.junction

import jp.co.soramitsu.fearless_utils.common.assertThrows
import jp.co.soramitsu.fearless_utils.encrypt.junction.BIP32JunctionDecoder.DecodingError.InvalidBIP32Junction
import jp.co.soramitsu.fearless_utils.encrypt.junction.JunctionDecoder.DecodingError.InvalidStart
import org.junit.Test

// trick compiler to not to complain about 0x80 being too large to fit into Byte
private val HARD_BYTE = 0x80u.toByte()

class BIP32JunctionDecoderTest : JunctionTest() {

    override val decoder: JunctionDecoder = BIP32JunctionDecoder

    @Test
    fun `single soft`() = performTest(
        path = "/1",
        expectedPassword = null,
        Junction(
            type = JunctionType.SOFT,
            chaincode = byteArrayOf(0, 0, 0, 0x01)
        )
    )

    @Test
    fun `single hard`() = performTest(
        path = "//1",
        expectedPassword = null,
        Junction(
            type = JunctionType.HARD,
            chaincode = byteArrayOf(HARD_BYTE, 0, 0, 0x01)
        )
    )

    @Test
    fun `hard and soft`() = performTest(
        path = "//1/2",
        expectedPassword = null,
        Junction(
            type = JunctionType.HARD,
            chaincode = byteArrayOf(HARD_BYTE, 0, 0, 0x01)
        ),
        Junction(
            type = JunctionType.SOFT,
            chaincode = byteArrayOf(0, 0, 0, 0x02)
        )
    )

    @Test
    fun `soft and biggest hard`() {
        val maxUByte = 0xFFu.toByte()

        performTest(
            path = "/0//2147483647",
            expectedPassword = null,
            Junction(
                type = JunctionType.SOFT,
                chaincode = byteArrayOf(0, 0, 0, 0)
            ),
            Junction(
                type = JunctionType.HARD,
                chaincode = byteArrayOf(maxUByte, maxUByte, maxUByte, maxUByte)
            )
        )
    }

    @Test
    fun `soft hard password`() {
        performTest(
            path = "//1/2///pass",
            expectedPassword = "pass",
            Junction(
                type = JunctionType.HARD,
                chaincode = byteArrayOf(HARD_BYTE, 0, 0, 0x01)
            ),
            Junction(
                type = JunctionType.SOFT,
                chaincode = byteArrayOf(0, 0, 0, 0x02)
            )
        )
    }

    @Test
    fun `missing prefix`() {
        assertThrows<InvalidStart> { decoder.decode("1/2") }
        assertThrows<InvalidStart> { decoder.decode("hello") }
    }

    @Test
    fun `wrong bip32 junction`() {
        assertThrows<InvalidBIP32Junction> { decoder.decode("/1/5000000000") }
        assertThrows<InvalidBIP32Junction> { decoder.decode("/hello") }
    }
}