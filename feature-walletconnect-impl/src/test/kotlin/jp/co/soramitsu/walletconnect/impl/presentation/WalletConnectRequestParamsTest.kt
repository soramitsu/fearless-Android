package jp.co.soramitsu.walletconnect.impl.presentation

import com.reown.walletkit.client.Wallet
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WalletConnectRequestParamsTest {

    private val evmAddress = "0x8ba1f109551bd432803012645ac136ddd64dba72"

    private fun request(method: String, params: String) =
        Wallet.Model.SessionRequest.JSONRPCRequest(id = 1, method = method, params = params)

    @Test
    fun `malformed hex in a personal message yields no message instead of throwing`() {
        assertNull(request("personal_sign", """["0xzz", "$evmAddress"]""").message)
        assertNull(request("personal_sign", """["0xabc", "$evmAddress"]""").message)
        assertNull(request("eth_sign", """["$evmAddress", "not hex"]""").message)
    }

    @Test
    fun `personal message hex is decoded and its account read`() {
        val personalSign = request("personal_sign", """["0x68656c6c6f", "$evmAddress"]""")
        val ethSign = request("eth_sign", """["$evmAddress", "0x68656c6c6f"]""")

        assertEquals("hello", personalSign.message)
        assertEquals(evmAddress, personalSign.address)
        assertEquals("hello", ethSign.message)
        assertEquals(evmAddress, ethSign.address)
    }

    @Test
    fun `params of the wrong shape yield null instead of throwing`() {
        assertNull(request("personal_sign", "{}").message)
        assertNull(request("personal_sign", "{}").address)
        assertNull(request("personal_sign", """["0x68656c6c6f"]""").address)
        assertNull(request("polkadot_signMessage", "[]").message)
        assertNull(request("polkadot_signMessage", """{"message": "0x01"}""").address)
        assertNull(request("polkadot_signMessage", """{"address": 5, "message": "0x01"}""").address)
        assertNull(request("eth_sendTransaction", """["$evmAddress"]""").address)
        assertNull(request("eth_signTypedData_v4", "not json").message)
        assertNull(request("eth_signTypedData_v4", "not json").address)
    }

    @Test
    fun `polkadot params are read`() {
        val signMessage = request("polkadot_signMessage", """{"address": "5Grw", "message": "0x0102"}""")
        val signTransaction = request(
            "polkadot_signTransaction",
            """{"address": "5Grw", "transactionPayload": {"genesisHash": "0x91b1", "nonce": "0x00"}}"""
        )

        assertEquals("0x0102", signMessage.message)
        assertEquals("5Grw", signMessage.address)
        assertEquals("5Grw", signTransaction.address)
        assertEquals(
            Json.parseToJsonElement("""{"genesisHash": "0x91b1", "nonce": "0x00"}"""),
            Json.parseToJsonElement(requireNotNull(signTransaction.message))
        )
    }

    @Test
    fun `ethereum transaction and typed data params are read`() {
        val transaction = request("eth_sendTransaction", """[{"from": "$evmAddress", "to": "0x01", "value": "0x10"}]""")
        val typedDataAsString = request("eth_signTypedData_v4", """["$evmAddress", "{\"primaryType\": \"Mail\"}"]""")
        val typedDataAsObject = request("eth_signTypedData", """["$evmAddress", {"primaryType": "Mail"}]""")

        assertEquals(evmAddress, transaction.address)
        assertEquals(
            Json.parseToJsonElement("""{"from": "$evmAddress", "to": "0x01", "value": "0x10"}"""),
            Json.parseToJsonElement(requireNotNull(transaction.message))
        )
        assertEquals("""{"primaryType": "Mail"}""", typedDataAsString.message)
        assertEquals(evmAddress, typedDataAsString.address)
        assertEquals(Json.parseToJsonElement("""{"primaryType": "Mail"}"""), Json.parseToJsonElement(requireNotNull(typedDataAsObject.message)))
    }

    @Test
    fun `unsupported methods name no account`() {
        val request = request("wallet_switchEthereumChain", """[{"chainId": "0x1"}]""")

        assertNull(request.address)
        assertEquals("wallet_switchEthereumChain's params: [{\"chainId\": \"0x1\"}]", request.message)
    }
}
