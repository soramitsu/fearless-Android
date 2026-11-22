package jp.co.soramitsu.irohaconnect.impl.review

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import jp.co.soramitsu.irohaconnect.impl.WebVectors
import jp.co.soramitsu.irohaconnect.impl.WebVectors.array
import jp.co.soramitsu.irohaconnect.impl.WebVectors.bytes
import jp.co.soramitsu.irohaconnect.impl.WebVectors.obj
import jp.co.soramitsu.irohaconnect.impl.WebVectors.objects
import jp.co.soramitsu.irohaconnect.impl.WebVectors.str
import jp.co.soramitsu.irohaconnect.impl.WebVectors.strOrNull
import jp.co.soramitsu.irohaconnect.impl.protocol.IrohaConnectEncoding
import jp.co.soramitsu.irohaconnect.impl.protocol.IrohaConnectProfiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Each payload was built by the iroha-js 0.0.3 codec (or hand-encoded around it) and reviewed by the browser wallet's
 * `reviewIrohaConnectTransaction`; the Android review must show the same fields or refuse with the same code.
 */
class IrohaConnectTransactionReviewVectorsTest {

    private val expectation = IrohaConnectTransactionExpectation(
        networkIdLiteral = IrohaConnectProfiles.TAIRA_NETWORK_ID,
        networkPrefix = IrohaConnectProfiles.taira.networkPrefix,
        authority = WebVectors.accountAddress,
        signingPublicKeyHex = WebVectors.accountPublicKeyHex
    )

    private fun JsonObject.nullableString(name: String): String? =
        get(name)?.takeUnless(JsonElement::isJsonNull)?.asString

    private fun asset(json: JsonObject) = IrohaConnectReviewAsset(json.str("definitionId"), json.strOrNull("symbol"))

    private fun expectedReview(json: JsonObject) = IrohaConnectTransactionReview(
        kind = IrohaConnectTransactionReview.Kind.entries.single { it.wireName == json.str("kind") },
        authority = json.str("authority"),
        networkId = json.str("networkId"),
        payloadBytes = json.get("payloadBytes").asInt,
        payloadHashHex = json.str("payloadHashHex"),
        creationTimeMs = json.str("creationTimeMs"),
        ttlMs = json.nullableString("ttlMs"),
        nonce = json.nullableString("nonce"),
        fee = json.obj("fee").let { fee ->
            IrohaConnectReviewFee(
                payer = fee.str("payer"),
                program = fee.nullableString("program"),
                programRevision = fee.nullableString("programRevision"),
                chargeLimits = fee.array("chargeLimits").objects().map {
                    IrohaConnectReviewFeeLimit(it.str("kind"), asset(it.obj("asset")), it.str("maxAmount"))
                },
                gasLimit = fee.nullableString("gasLimit")
            )
        },
        metadata = json.nullableString("metadata"),
        transfer = json.get("transfer")?.takeUnless(JsonElement::isJsonNull)?.asJsonObject?.let {
            IrohaConnectReviewTransfer(asset(it.obj("asset")), it.nullableString("scope"), it.str("quantity"), it.str("destination"))
        },
        calls = json.get("calls")?.takeUnless(JsonElement::isJsonNull)?.asJsonArray?.objects()?.map {
            IrohaConnectReviewContractCall(
                contractAddress = it.str("contractAddress"),
                entrypoint = it.str("entrypoint"),
                codeHashHex = it.str("codeHashHex"),
                argumentBytes = it.get("argumentBytes")?.takeUnless(JsonElement::isJsonNull)?.asInt,
                argumentsSha256 = it.nullableString("argumentsSha256")
            )
        }
    )

    @Test
    fun shouldReviewOrRefuseEveryPayloadLikeTheBrowserWallet_whenGivenWebVectors() {
        val vectors = WebVectors.root.array("reviews").objects()
        assertTrue(vectors.size >= 70)
        vectors.forEach { vector ->
            val name = vector.str("name")
            val payload = vector.bytes("payloadHex")
            val expectedError = vector.strOrNull("error")
            if (expectedError != null) {
                val error = assertThrows(name, IrohaConnectTransactionException::class.java) {
                    IrohaConnectTransactionReviewer.review(payload, expectation)
                }
                assertEquals(name, expectedError.uppercase(), error.code.name)
                return@forEach
            }
            val reviewed = IrohaConnectTransactionReviewer.review(payload, expectation)
            assertEquals(name, expectedReview(vector.obj("review")), reviewed.review)
            assertEquals(name, vector.str("payloadHashHex"), IrohaConnectEncoding.hex(reviewed.payloadHash))
        }
    }

    @Test
    fun shouldRefuseAPayloadForAnotherSigningKey_whenTheAuthorityDoesNotMatchTheKey() {
        val payload = WebVectors.root.array("reviews").objects().first().bytes("payloadHex")
        val error = assertThrows(IrohaConnectTransactionException::class.java) {
            IrohaConnectTransactionReviewer.review(payload, expectation.copy(signingPublicKeyHex = "11".repeat(32)))
        }
        assertEquals(IrohaConnectTransactionErrorCode.INVALID_TRANSACTION, error.code)
        val wrongNetwork = assertThrows(IrohaConnectTransactionException::class.java) {
            IrohaConnectTransactionReviewer.review(payload, expectation.copy(networkIdLiteral = "not a network id"))
        }
        assertEquals(IrohaConnectTransactionErrorCode.INVALID_TRANSACTION, wrongNetwork.code)
    }

    @Test
    fun shouldEscapeControlFormatAndSeparatorCharacters_whenSanitizingReviewText() {
        assertEquals("pay\\u{202E}evil\\u{2028}", IrohaConnectTransactionReviewer.sanitize("pay‮evil "))
        assertEquals("a\\u{0}b\\u{7F}", IrohaConnectTransactionReviewer.sanitize("a\u0000b\u007f"))
        assertEquals("tag\\u{E0001}", IrohaConnectTransactionReviewer.sanitize("tag󠀁"))
        assertEquals("abcd…", IrohaConnectTransactionReviewer.sanitize("abcdefgh", 5))
        assertEquals("ご注文", IrohaConnectTransactionReviewer.sanitize("ご注文"))
    }
}
