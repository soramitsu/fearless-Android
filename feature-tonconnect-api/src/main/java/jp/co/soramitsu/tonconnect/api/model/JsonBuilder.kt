package jp.co.soramitsu.tonconnect.api.model

import jp.co.soramitsu.common.utils.base64
import jp.co.soramitsu.common.utils.tonAccountId
import jp.co.soramitsu.runtime.multiNetwork.chain.ton.V4R2WalletContract
import org.json.JSONArray
import org.json.JSONObject

object JsonBuilder {

    fun responseError(id: Long, error: BridgeError): JSONObject {
        val json = JSONObject()
        json.put("error", error(error))
        json.put("id", id)
        return json
    }

    fun error(error: BridgeError): JSONObject {
        val json = JSONObject()
        json.put("code", error.code)
        json.put("message", error.message)
        return json
    }

    fun connectEventError(error: BridgeError): JSONObject {
        val json = JSONObject()
        json.put("event", "connect_error")
        json.put("id", System.currentTimeMillis())
        json.put("payload", error(error))
        return json
    }

    fun device(maxMessages: Int, appVersion: String): JSONObject {
        val json = JSONObject()
        json.put("platform", "android")
        json.put("appName", "Fearless")
        json.put("appVersion", appVersion)
        json.put("maxProtocolVersion", 2)
        json.put("features", features(maxMessages))
        return json
    }

    private fun features(maxMessages: Int): JSONArray {
        val array = JSONArray()
        array.put("SendTransaction")
        array.put(sendTransactionFeature(maxMessages))
        return array
    }

    private fun sendTransactionFeature(maxMessages: Int): JSONObject {
        val json = JSONObject()
        json.put("name", "SendTransaction")
        json.put("maxMessages", maxMessages)
        return json
    }

    fun responseSendTransaction(id: Long, boc: String): JSONObject {
        val json = JSONObject()
        json.put("result", boc)
        json.put("id", id)
        return json
    }

    @Suppress("UnusedParameter")
    @OptIn(ExperimentalStdlibApi::class)
    fun connectEventSuccess(
        tonPublicKey: ByteArray,
        proof: TONProof.Result?,
        proofError: BridgeError?
    ): JSONObject {
        val senderSmartContract = V4R2WalletContract(tonPublicKey)
        val stateInit = senderSmartContract.stateInitCell().base64()
        val tonAddressItemReply = JSONObject()
        tonAddressItemReply.put("name", "ton_addr")
        val isTestnet = true // to do remove from release
        tonAddressItemReply.put("address", tonPublicKey.tonAccountId(isTestnet))
        val network = if (isTestnet) "-3" else "-239"
        tonAddressItemReply.put("network", network)
        tonAddressItemReply.put("publicKey", tonPublicKey.toHexString())
        tonAddressItemReply.put("walletStateInit", stateInit)

        val payloadItemsJson = JSONArray()
        payloadItemsJson.put(tonAddressItemReply)

        proof?.let {
            val size = proof.domain.value.toByteArray().size
            val domainJson = JSONObject()
            domainJson.put("lengthBytes", size)
            domainJson.put("length_bytes", size)
            domainJson.put("value", proof.domain.value)

            val proofJson = JSONObject()
            proofJson.put("timestamp", proof.timestamp)
            proofJson.put("domain", domainJson)
            proofJson.put("signature", proof.signature)
            proofJson.put("payload", proof.payload)

            val tonProofItemReply = JSONObject()
            tonProofItemReply.put("name", "ton_proof")
            tonProofItemReply.put("proof", proofJson)

            payloadItemsJson.put(tonProofItemReply)
        }

//            proofError?.let {
//                payloadItemsJson.put(tonProofItemReplyError(it))
//            }

        val sendTransactionFeatureJson = JSONObject()
        sendTransactionFeatureJson.put("name", "SendTransaction")
        sendTransactionFeatureJson.put("maxMessages", senderSmartContract.maxMessages)

        val featuresJsonArray = JSONArray()
        featuresJsonArray.put("SendTransaction")
        featuresJsonArray.put(sendTransactionFeatureJson)

        val deviceJson = JSONObject()
        deviceJson.put("platform", "android")
        deviceJson.put("appName", "Tonkeeper")
        deviceJson.put("appVersion", "5.0.12") // BuildConfig.VERSION_NAME)
        deviceJson.put("maxProtocolVersion", 2)
        deviceJson.put("features", featuresJsonArray)

        val payloadJson = JSONObject()
        payloadJson.put("items", payloadItemsJson)
        payloadJson.put("device", deviceJson)

        val json = JSONObject()
        json.put("event", "connect")
        json.put("id", System.currentTimeMillis())
        json.put("payload", payloadJson)
        return json
    }
}
