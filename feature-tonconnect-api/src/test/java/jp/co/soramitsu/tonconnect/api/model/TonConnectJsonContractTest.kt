package jp.co.soramitsu.tonconnect.api.model

import android.os.Parcel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TonConnectJsonContractTest {
    @Test
    fun deviceAdvertisesProtocolAndTransactionMessageLimit() {
        val device = JsonBuilder.device(4, "4.3.3")
        assertEquals("android", device.getString("platform"))
        assertEquals("Fearless", device.getString("appName"))
        assertEquals("4.3.3", device.getString("appVersion"))
        assertEquals(2, device.getInt("maxProtocolVersion"))
        val features = device.getJSONArray("features")
        assertEquals(2, features.length())
        assertEquals("SendTransaction", features.getString(0))
        assertEquals("SendTransaction", features.getJSONObject(1).getString("name"))
        assertEquals(4, features.getJSONObject(1).getInt("maxMessages"))
    }

    @Test
    fun errorAndTransactionResponsesPreserveDappRequestId() {
        val error = JsonBuilder.responseError(123, BridgeError.BAD_REQUEST)
        assertEquals(123L, error.getLong("id"))
        assertEquals(BridgeError.BAD_REQUEST.code, error.getJSONObject("error").getInt("code"))
        assertEquals(BridgeError.BAD_REQUEST.message, error.getJSONObject("error").getString("message"))
        val result = JsonBuilder.responseSendTransaction(123, "encoded-boc")
        assertEquals(123L, result.getLong("id"))
        assertEquals("encoded-boc", result.getString("result"))
        assertFalse(result.has("error"))
    }

    @Test
    fun failedConnectionUsesEventPayloadAndTimestamp() {
        val before = System.currentTimeMillis()
        val error = JsonBuilder.connectEventError(BridgeError.UNKNOWN_APP)
        assertEquals("connect_error", error.getString("event"))
        assertTrue(error.getLong("id") in before..System.currentTimeMillis())
        assertEquals(BridgeError.UNKNOWN_APP.code, error.getJSONObject("payload").getInt("code"))
    }

    @Test
    fun successfulConnectionContainsAddressStateInitAndOptionalProof() {
        val proof = TONProof.Result(1234, TONProof.Domain("dapp.example"), "challenge", "signature")
        val event = JsonBuilder.connectEventSuccess(ByteArray(32), proof, null)
        assertEquals("connect", event.getString("event"))
        val payload = event.getJSONObject("payload")
        val items = payload.getJSONArray("items")
        assertEquals(2, items.length())
        val address = items.getJSONObject(0)
        assertEquals("ton_addr", address.getString("name"))
        assertEquals("-239", address.getString("network"))
        assertEquals("00".repeat(32), address.getString("publicKey"))
        assertTrue(address.getString("walletStateInit").isNotEmpty())
        assertTrue(address.getString("address").isNotEmpty())
        val proofItem = items.getJSONObject(1)
        assertEquals("ton_proof", proofItem.getString("name"))
        val result = proofItem.getJSONObject("proof")
        assertEquals(1234L, result.getLong("timestamp"))
        assertEquals("challenge", result.getString("payload"))
        assertEquals("signature", result.getString("signature"))
        val domain = result.getJSONObject("domain")
        assertEquals("dapp.example", domain.getString("value"))
        assertEquals(12, domain.getInt("lengthBytes"))
        assertEquals(12, domain.getInt("length_bytes"))
        assertEquals("Fearless", payload.getJSONObject("device").getString("appName"))
    }

    @Test
    fun connectionWithoutProofHasOnlyAddressItem() {
        val event = JsonBuilder.connectEventSuccess(ByteArray(32), null, null)
        assertEquals(1, event.getJSONObject("payload").getJSONArray("items").length())
        assertEquals(2, event.getJSONObject("payload").getJSONObject("device").getInt("maxProtocolVersion"))
    }

    @Test
    fun connectRequestNormalizesManifestAndKeepsRecognizedItems() {
        val request = ConnectRequest.parse(
            """{"manifesturl":"https://DAPP.example/manifest.json","items":[
                {"name":"unknown"},{"name":"ton_addr"},{"name":"ton_proof","payload":"challenge"}]}"""
        )
        assertEquals("https://dapp.example/manifest.json", request.manifestUrl)
        assertEquals(listOf(ConnectRequest.Item.TonAddress, ConnectRequest.Item.TonProof("challenge")), request.items)
        assertEquals("challenge", request.proofPayload)
        val noProof = ConnectRequest.parse(JSONObject("""{"manifestUrl":"https://dapp.example/manifest.json","items":[]}"""))
        assertNull(noProof.proofPayload)
    }

    @Test
    fun invalidConnectRequestsAreReportedAsParsingErrors() {
        listOf(null, "", " ", "not json", "{}", """{"manifestUrl":"file:///tmp/manifest","items":[]}""").forEach {
            assertThrows(TonConnectException.RequestParsingError::class.java) { ConnectRequest.parse(it) }
        }
    }

    @Test
    fun proofParcelRoundTripPreservesAllFields() {
        val expected = TONProof.Result(123, TONProof.Domain("dapp.example"), "challenge", "signature")
        val parcel = Parcel.obtain()
        try {
            expected.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            assertEquals(expected, TONProof.Result.CREATOR.createFromParcel(parcel))
            assertEquals(0, expected.describeContents())
            assertEquals(2, TONProof.Result.CREATOR.newArray(2).size)
            assertNull(TONProof.Result.CREATOR.newArray(1).single())
        } finally {
            parcel.recycle()
        }
    }
}
