package jp.co.soramitsu.walletconnect.impl.presentation

import com.reown.walletkit.client.Wallet
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.Signer
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.useScaleWriter
import jp.co.soramitsu.fearless_utils.scale.utils.directWrite
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

/**
 * The payload alignment attack from the 2026-10-28 review, reproduced against live Polkadot 2005000 metadata (the
 * runtime-utils parity fixture): WalletConnect fields whose raw hex, joined as the wallet used to join it, spells the
 * exact 140-byte signing payload of `XcmPallet.limited_teleport_assets` moving 1,000 DOT to an attacker's account on
 * Asset Hub. The request itself names no such structure: its era carries the nonce and its block hash carries the
 * CheckMetadataHash `None` byte.
 */
class WalletConnectPayloadAlignmentTest {

    private val runtime = ParityRuntimes.runtime("polkadot")
    private val keypair = SubstrateKeypairFactory.generate(EncryptionType.ED25519, ByteArray(32) { (it + 1).toByte() })
    private val address = SS58Encoder.encode(keypair.publicKey, 0)
    private val keypairProvider = mockk<KeypairProvider>()
    private val sessionClient = FakeSessionClient().apply { activeSession = session("${polkadotChain.caip2id}:$address") }

    // The attacker's call, as the review built it with polkadot.js: XcmPallet (99) limited_teleport_assets (9) of
    // 1,000 DOT to AccountId32 0xabab… on Parachain(1000), all V5 locations, fee item 0, Unlimited weight. 62 bytes.
    private val teleport: ByteArray = (
        "6309" + "05000100a10f" + "0500010100" + "ab".repeat(32) + "0504000000" + "0b00a0724e1809" + "00000000" + "00"
        ).fromHex()

    // What Polkadot verifies a signature of this teleport against (CheckMetadataHash mode 0, mortal era, nonce 7).
    private val target: ByteArray
        get() = teleport + ERA + byteArrayOf(0x1c, 0x00, 0x00) + "08981e00".fromHex() + "1a000000".fromHex() +
            GENESIS.fromHex() + CHECKPOINT.fromHex() + byteArrayOf(0x00)

    // The review's crafted request fields.
    private val crafted: JsonObject
        get() = buildJsonObject {
            put("address", address)
            put("method", teleport.toHexString(withPrefix = true))
            put("era", (ERA + byteArrayOf(0x1c)).toHexString(withPrefix = true)) // era || compact(nonce 7)
            put("nonce", "0x00") // compact(0) stands in for compact(tip)
            put("tip", "0x00") // compact(0) stands in for the CheckMetadataHash mode byte
            put("specVersion", "0x001e9808")
            put("transactionVersion", "0x0000001a")
            put("genesisHash", "0x$GENESIS")
            put("blockHash", "0x${CHECKPOINT}00") // checkpoint || None(metadata hash)
        }

    // The same transaction asked for honestly: every field holds exactly its own value.
    private val honest: Map<String, kotlinx.serialization.json.JsonElement>
        get() = crafted + mapOf(
            "era" to Json.parseToJsonElement("\"${ERA.toHexString(withPrefix = true)}\""),
            "nonce" to Json.parseToJsonElement("\"0x00000007\""),
            "tip" to Json.parseToJsonElement("\"0x00000000000000000000000000000000\""),
            "blockHash" to Json.parseToJsonElement("\"0x$CHECKPOINT\""),
            "mode" to Json.parseToJsonElement("0")
        )

    @Test
    fun `the crafted fields spell the teleport payload when joined as raw hex`() {
        // The call is exactly one teleport under Polkadot 2005000.
        val call = requireNotNull(PolkadotTransaction.decodeCall(teleport, runtime))
        assertEquals("XcmPallet.limited_teleport_assets", "${call.module.name}.${call.function.name}")
        assertEquals(140, target.size)

        assertArrayEquals(target, concatenatedAsBefore(crafted))
    }

    @Test
    fun `the crafted request is refused before any key is read`() {
        val outcome = signTransaction(crafted)

        assertTrue(outcome is WalletConnectRequestRejectedException)
        assertTrue(sessionClient.responses.single() is Wallet.Model.JsonRpcResponse.JsonRpcError)
        coVerify(exactly = 0) { keypairProvider.getKeypairFor(any(), any()) }
    }

    @Test
    fun `no request field can carry the bytes of another`() {
        for ((field, value) in listOf("era" to "0xb5031c", "blockHash" to "0x${CHECKPOINT}00", "genesisHash" to "0x${GENESIS}00")) {
            val shifted = JsonObject(honest + (field to Json.parseToJsonElement("\"$value\"")))
            assertTrue(field, signTransaction(shifted) is WalletConnectRequestRejectedException)
        }
        // Bytes appended to the call do not decode as one call, so they are never signed either.
        val padded = JsonObject(honest + ("method" to Json.parseToJsonElement("\"${(teleport + ERA).toHexString(withPrefix = true)}\"")))
        assertTrue(signTransaction(padded) is PolkadotTransactionRefusedException)
    }

    @Test
    fun `the only request that signs the target is the honest one, shown as the teleport it is`() {
        val payload = PolkadotSignPayload.parse(JsonObject(honest).toString())
        val transaction = PolkadotTransaction.read(polkadotChain, payload, runtime)

        assertArrayEquals(target, transaction.signingPayload)
        assertEquals("XcmPallet.limited_teleport_assets", "${transaction.call.module.name}.${transaction.call.function.name}")
        // The preview shows it as the cross-chain transfer it is: destination, beneficiary and amount.
        val teleportShown = transaction.crossChainCalls.single()
        assertEquals(BigInteger.valueOf(1000), teleportShown.destination?.paraId)
        assertEquals(SS58Encoder.encode(ByteArray(32) { 0xab.toByte() }, 0), teleportShown.beneficiary)
        assertEquals(BigInteger.TEN.pow(13), teleportShown.amounts.single().planks)
    }

    @Test
    fun `a message holding the target payload is never a valid signature of it`() {
        approveKey()
        val request = sessionRequest(
            id = 71,
            method = "polkadot_signMessage",
            params = buildJsonObject {
                put("address", address)
                put("message", target.toHexString(withPrefix = true))
            }.toString(),
            chainId = polkadotChain.caip2id
        )

        runBlocking { interactor().handleSignAction(polkadotChain, TEST_TOPIC, request, { throw it }, { _, _ -> }, {}) }

        val result = (sessionClient.responses.single() as Wallet.Model.JsonRpcResponse.JsonRpcResult).result
        val signature = Json.parseToJsonElement(requireNotNull(result)).jsonObject["signature"]?.jsonPrimitive?.content.orEmpty().fromHex()
        assertFalse(Signer.verifyEd25519(target, signature, keypair.publicKey))
        assertTrue(Signer.verifyEd25519("<Bytes>".encodeToByteArray() + target + "</Bytes>".encodeToByteArray(), signature, keypair.publicKey))
    }

    private fun approveKey() {
        coEvery { keypairProvider.getKeypairFor(polkadotChain, any()) } returns keypair
        coEvery { keypairProvider.getCryptoTypeFor(polkadotChain, any()) } returns CryptoType.ED25519
    }

    // Returns the signing error, or null when the request was signed.
    private fun signTransaction(payload: JsonObject): Exception? {
        var error: Exception? = null
        val request = sessionRequest(
            id = 70,
            method = "polkadot_signTransaction",
            params = buildJsonObject {
                put("address", address)
                put("transactionPayload", payload)
            }.toString(),
            chainId = polkadotChain.caip2id
        )
        sessionClient.responses.clear()
        runBlocking { interactor().handleSignAction(polkadotChain, TEST_TOPIC, request, { error = it }, { _, _ -> }, {}) }
        return error
    }

    private fun interactor() = WalletConnectInteractorImpl(
        chainsRepository = mockk(),
        ethereumSource = mockk(),
        accountRepository = mockk(),
        keypairProvider = keypairProvider,
        runtimeSource = { runtime },
        transactionSender = mockk(),
        sessionClient = sessionClient,
        signedRequests = WalletConnectSignedRequests()
    )

    // The builder this module used before: raw dApp hex joined field after field.
    private fun concatenatedAsBefore(fields: JsonObject): ByteArray {
        fun bytes(key: String) = fields.getValue(key).jsonPrimitive.content.fromHex()
        fun number(key: String) = BigInteger(1, bytes(key)).toInt()
        return useScaleWriter {
            directWrite(bytes("method"))
            directWrite(bytes("era"))
            writeCompact(number("nonce"))
            writeCompact(number("tip"))
            writeUint32(number("specVersion"))
            writeUint32(number("transactionVersion"))
            directWrite(bytes("genesisHash"))
            directWrite(bytes("blockHash"))
        }
    }

    private companion object {
        const val GENESIS = "91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3"
        const val CHECKPOINT = "1b876104c68b4a8924c098d61d2ad798761bb6fff55cca2885939ffc27ef5ecb"
        val ERA = "b503".fromHex()
    }
}
