package jp.co.soramitsu.fearless_utils.encrypt.junction

import jp.co.soramitsu.fearless_utils.extensions.fromHex
import org.junit.Test

class SubstrateJunctionDecoderTest : JunctionTest() {

    override val decoder: JunctionDecoder = SubstrateJunctionDecoder

    @Test
    fun `single soft`() = performTest(
        path = "/1",
        expectedPassword=null,
        Junction(
            JunctionType.SOFT,
            "0100000000000000000000000000000000000000000000000000000000000000".fromHex()
        )
    )

    @Test
    fun `single hard`() = performTest(
        path = "//2",
        expectedPassword=null,
        Junction(
            JunctionType.HARD,
            "0200000000000000000000000000000000000000000000000000000000000000".fromHex()
        )
    )

    @Test
    fun `soft and hard`() = performTest(
        path = "//2/3",
        expectedPassword=null,
        Junction(
            JunctionType.HARD,
            "0200000000000000000000000000000000000000000000000000000000000000".fromHex()
        ),
        Junction(
            JunctionType.SOFT,
            "0300000000000000000000000000000000000000000000000000000000000000".fromHex()
        )
    )
}