package jp.co.soramitsu.runtime.multiNetwork.chain

import com.google.gson.Gson
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainRemote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RemoteChainMappingTest {

    @Test
    fun `an asset without a precision is dropped instead of guessed`() {
        val chain = remote(
            """{"chainId":"0x01","name":"Chain","addressPrefix":42,"ecosystem":"Substrate","assets":[
                {"id":"with","symbol":"dot","precision":10},
                {"id":"without","symbol":"ksm"}
            ]}"""
        ).toChain()

        assertEquals(listOf("with"), chain.assets.map { it.id })
        assertEquals(10, chain.assets.single().precision)
    }

    @Test
    fun `a chain without an address prefix is refused instead of given prefix 0`() {
        val chain = remote("""{"chainId":"0x01","name":"Chain","ecosystem":"Substrate","assets":[]}""")

        assertThrows(IllegalArgumentException::class.java) { chain.toChain() }
    }

    @Test
    fun `an explicit address prefix is kept`() {
        val chain = remote("""{"chainId":"0x01","name":"Chain","addressPrefix":0,"ecosystem":"Substrate","assets":[]}""")

        assertEquals(0, chain.toChain().addressPrefix)
    }

    private fun remote(json: String): ChainRemote = Gson().fromJson(json, ChainRemote::class.java)
}
