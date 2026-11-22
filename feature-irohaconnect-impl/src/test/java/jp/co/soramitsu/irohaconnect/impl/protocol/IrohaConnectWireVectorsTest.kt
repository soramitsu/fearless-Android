package jp.co.soramitsu.irohaconnect.impl.protocol

import jp.co.soramitsu.irohaconnect.impl.WebVectors
import jp.co.soramitsu.irohaconnect.impl.WebVectors.array
import jp.co.soramitsu.irohaconnect.impl.WebVectors.bytes
import jp.co.soramitsu.irohaconnect.impl.WebVectors.obj
import jp.co.soramitsu.irohaconnect.impl.WebVectors.objects
import jp.co.soramitsu.irohaconnect.impl.WebVectors.str
import jp.co.soramitsu.irohaconnect.impl.WebVectors.strOrNull
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every vector here was produced by the browser wallet's `wire.ts`; the Android port must agree byte for byte. */
class IrohaConnectWireVectorsTest {

    private val hex = IrohaConnectEncoding::hex

    @Test
    fun shouldComputeNoritoCrc64_whenGivenWebVectors() {
        WebVectors.root.array("crc64").objects().forEach { vector ->
            assertEquals(vector.str("crcHex"), java.lang.Long.toHexString(IrohaConnectCrypto.crc64Xz(vector.bytes("inputHex"))).padStart(16, '0'))
        }
        assertEquals(0x995dc9bbdf1939faUL.toLong(), IrohaConnectCrypto.crc64Xz("123456789".toByteArray()))
    }

    @Test
    fun shouldParseAndPrintNetworkIds_whenLiteralsAreCanonical() {
        WebVectors.root.array("networkIds").objects().forEach { vector ->
            val parsed = requireNotNull(IrohaNetworkId.parse(vector.str("literal")))
            assertEquals(vector.str("bytesHex"), hex(parsed.toBytes()))
            assertEquals(vector.str("literal"), IrohaNetworkId.fromBytes(vector.bytes("bytesHex")).literal)
        }
        val taira = IrohaConnectProfiles.TAIRA_NETWORK_ID
        assertNull(IrohaNetworkId.parse(taira.lowercase()))
        assertNull(IrohaNetworkId.parse(taira.replace("#6774", "#6775")))
        assertNull(IrohaNetworkId.parse(" $taira"))
        assertNull(IrohaNetworkId.parse(taira.dropLast(1)))
        // The marker bit must be set: an even last byte is not an Iroha hash.
        assertNull(IrohaNetworkId.parse("hash:" + "00".repeat(32) + "#0000"))
    }

    @Test
    fun shouldParseLinksExactlyLikeTheBrowserWallet_whenGivenWebVectors() {
        val links = WebVectors.root.array("links").objects()
        assertTrue(links.size >= 25)
        links.forEach { vector ->
            val uri = vector.str("uri")
            val expectedError = vector.strOrNull("error")
            if (expectedError != null) {
                val error = assertThrows(uri, IrohaConnectException::class.java) { IrohaConnectLink.parse(uri) }
                assertEquals(uri, expectedError.removePrefix("iroha_connect_").uppercase(), error.code.name)
                return@forEach
            }
            val link = IrohaConnectLink.parse(uri)
            assertEquals(uri, vector.str("scheme"), link.scheme)
            assertEquals(vector.str("sidHex"), hex(link.sidBytes))
            assertEquals(vector.str("appPublicKeyHex"), hex(link.appPublicKey))
            assertEquals(vector.str("nonceHex"), hex(link.nonce))
            assertEquals(vector.str("networkIdHex"), hex(link.networkIdBytes))
            assertEquals(vector.str("token"), link.token)
            assertEquals(vector.str("relayToken"), link.relayToken)
            assertEquals(vector.str("webSocketUrl"), IrohaConnectRelay.webSocketUrl(link))
            assertEquals(vector.str("protocol"), IrohaConnectRelay.tokenProtocol(link.token))
        }
    }

    @Test
    fun shouldRefuseLinksWithControlCharactersOrBadEscapes_whenStricterThanAUrlParser() {
        val good = WebVectors.root.array("links").objects().first().str("uri")
        listOf(
            good.replace("&v=1", "&v=1\n"),
            good.replace("&v=1", "&v=1\t"),
            good.replace("&v=1", "&v=%"),
            good.replace("&v=1", "&v=%G1"),
            good.replace("&v=1", "&v=%C3%28"),
            good + "&x".repeat(2100)
        ).forEach { uri ->
            val error = assertThrows(IrohaConnectException::class.java) { IrohaConnectLink.parse(uri) }
            assertEquals(IrohaConnectErrorCode.INVALID_LINK, error.code)
        }
    }

    @Test
    fun shouldBuildApprovalAndPlainFrames_whenGivenWebVectors() {
        val frames = WebVectors.root.obj("frames")
        val keys = WebVectors.root.obj("keys")
        val sid = frames.bytes("sidHex")
        val walletPublicKey = frames.bytes("walletPublicKeyHex")
        val networkId = requireNotNull(IrohaNetworkId.parse(IrohaConnectProfiles.TAIRA_NETWORK_ID)).toBytes()
        assertEquals(frames.str("relayAuthHex"), hex(IrohaConnectWire.relayAuthHash(sid, frames.str("relayToken"))))
        assertEquals(
            frames.str("preimageNoPermissionsHex"),
            hex(
                IrohaConnectWire.approvalPreimage(
                    sidBytes = sid,
                    networkIdBytes = networkId,
                    appPublicKey = keys.bytes("appPublicHex"),
                    walletPublicKey = walletPublicKey,
                    accountId = WebVectors.accountAddress,
                    permissionsField = byteArrayOf(0),
                    relayToken = frames.str("relayToken")
                )
            )
        )
        assertEquals(
            frames.str("approveNoPermissionsHex"),
            hex(IrohaConnectWire.encodeApproveFrame(sid, 1, walletPublicKey, WebVectors.accountAddress, byteArrayOf(0), frames.bytes("signatureHex")))
        )
        assertEquals(frames.str("rejectHex"), hex(IrohaConnectWire.encodeRejectFrame(sid, 2, 4001, "user_rejected", "The wallet user rejected the connection.")))
        assertEquals(frames.str("pingHex"), hex(IrohaConnectWire.encodePingFrame(sid, 3, -2L)))
        assertEquals(frames.str("pongHex"), hex(IrohaConnectWire.encodePongFrame(sid, 4, 5L)))
        // A wallet-originated ping decodes with the wallet direction and keeps the full u64 nonce.
        val ping = IrohaConnectWire.decodeFrame(frames.bytes("pingHex"), sid) as IrohaConnectFrame.Control
        assertEquals(IrohaConnectDirection.WALLET_TO_APP, ping.direction)
        assertEquals(3L, ping.sequence)
        assertEquals(IrohaConnectControl.Ping(-2L), ping.control)
        assertThrows(IrohaConnectException::class.java) {
            IrohaConnectWire.encodeApproveFrame(sid, 1, walletPublicKey, WebVectors.accountAddress, byteArrayOf(7), frames.bytes("signatureHex"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            IrohaConnectWire.encodeApproveFrame(sid, 1, walletPublicKey, WebVectors.accountAddress, byteArrayOf(0), ByteArray(63))
        }
        assertThrows(IllegalArgumentException::class.java) {
            IrohaConnectWire.encodeRejectFrame(sid, 2, 4001, "1bad", "reason")
        }
        assertThrows(IllegalArgumentException::class.java) { IrohaConnectWire.encodePingFrame(sid, 0, 1) }
    }

    @Test
    fun shouldDeriveDirectionKeysAndSealEnvelopes_whenGivenWebVectors() {
        val keys = WebVectors.root.obj("keys")
        val sid = keys.bytes("sidHex")
        val derived = IrohaConnectWire.deriveDirectionKeys(keys.bytes("walletPrivateHex"), keys.bytes("appPublicHex"), sid)
        assertEquals(keys.str("appKeyHex"), hex(derived.appKey))
        assertEquals(keys.str("walletKeyHex"), hex(derived.walletKey))

        WebVectors.root.array("envelopes").objects().forEach { vector ->
            val payload = vector.obj("payload")
            val walletPayload = when (payload.str("type")) {
                "sign_result_ok" -> IrohaConnectWalletPayload.SignResultOk(payload.bytes("signatureHex"))
                "sign_result_err" -> IrohaConnectWalletPayload.SignResultErr(payload.str("code"), payload.str("message"))
                else -> IrohaConnectWalletPayload.ControlClose(payload.get("code").asInt, payload.str("reason"))
            }
            val sequence = vector.get("sequence").asLong
            val sealed = IrohaConnectWire.sealEnvelope(derived.walletKey, sid, sequence, walletPayload)
            assertEquals(vector.str("frameHex"), hex(sealed))
            val frame = IrohaConnectWire.decodeFrame(sealed, sid) as IrohaConnectFrame.Ciphertext
            assertEquals(IrohaConnectDirection.WALLET_TO_APP, frame.direction)
            assertEquals(sequence, frame.sequence)
            // The wallet opens only app ciphertext, and the wallet key never opens it as app traffic.
            val wrongKey = assertThrows(IrohaConnectException::class.java) {
                IrohaConnectWire.openEnvelope(derived.walletKey, sid, frame)
            }
            assertEquals(IrohaConnectErrorCode.INVALID_FRAME, wrongKey.code)
            val forged = IrohaConnectFrame.Ciphertext(IrohaConnectDirection.APP_TO_WALLET, frame.sequence, frame.aead)
            val wrongDirection = assertThrows(IrohaConnectException::class.java) {
                IrohaConnectWire.openEnvelope(derived.appKey, sid, forged)
            }
            assertEquals(IrohaConnectErrorCode.INVALID_FRAME, wrongDirection.code)
        }
    }

    @Test
    fun shouldDecodeTheOpenFrameCapturedFromTheLiveTairaRelay() {
        val capture = WebVectors.openCapture
        val sid = requireNotNull(IrohaConnectEncoding.fromCanonicalBase64Url(capture.str("sid")))
        val frame = IrohaConnectWire.decodeFrame(capture.bytes("frameHex"), sid) as IrohaConnectFrame.Control
        assertEquals(IrohaConnectDirection.APP_TO_WALLET, frame.direction)
        assertEquals(1L, frame.sequence)
        val open = frame.control as IrohaConnectControl.Open
        assertEquals(capture.str("appPublicKeyHex"), hex(open.appPublicKey))
        assertEquals(capture.str("networkIdHex"), hex(open.networkIdBytes))
        assertEquals(IrohaConnectAppMeta("Fearless capture", "https://example.org", null), open.appMeta)
        assertEquals(IrohaConnectPermissions(listOf("sign_transaction", "sign_raw"), emptyList(), null), open.permissions)
        assertThrows(IrohaConnectException::class.java) {
            IrohaConnectWire.decodeFrame(capture.bytes("frameHex"), ByteArray(32) { 1 })
        }
        assertThrows(IrohaConnectException::class.java) {
            IrohaConnectWire.decodeFrame(capture.bytes("frameHex").copyOf(900), sid)
        }
        assertThrows(IrohaConnectException::class.java) {
            IrohaConnectWire.decodeFrame(capture.bytes("frameHex") + byteArrayOf(0), sid)
        }
        assertThrows(IrohaConnectException::class.java) { IrohaConnectWire.decodeFrame(ByteArray(0), sid) }
    }

    @Test
    fun shouldOpenEveryAppEnvelopeTheUpstreamSdkSealed_whenReplayingTheTranscript() {
        val transcript = WebVectors.root.array("transcripts").objects().first()
        val sid = transcript.bytes("sidHex")
        val keys = WebVectors.root.obj("keys")
        val derived = IrohaConnectWire.deriveDirectionKeys(keys.bytes("walletPrivateHex"), keys.bytes("appPublicHex"), sid)
        val payloads = transcript.array("events").objects()
            .filter { it.str("type") == "appFrame" }
            .map { IrohaConnectWire.decodeFrame(it.bytes("hex"), sid) }
            .filterIsInstance<IrohaConnectFrame.Ciphertext>()
            .map { IrohaConnectWire.openEnvelope(derived.appKey, sid, it) }
        assertEquals(4, payloads.count { it is IrohaConnectAppPayload.SignRequestTx })
        val raw = payloads.filterIsInstance<IrohaConnectAppPayload.SignRequestRaw>().single()
        assertEquals("iroha:torii:canonical-request:v1", raw.domainTag)
        assertArrayEquals(byteArrayOf(1, 2, 3), raw.bytes)
        assertTrue(payloads.last() is IrohaConnectAppPayload.ControlClose)
    }
}
