package jp.co.soramitsu.walletconnect.impl.presentation

import jp.co.soramitsu.core.network.JsonFactory
import jp.co.soramitsu.core.runtime.RuntimeFactory
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericCall
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.toByteArray
import jp.co.soramitsu.fearless_utils.runtime.metadata.call
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.math.BigInteger
import java.util.zip.GZIPInputStream

/**
 * The runtime-utils parity fixtures: live V14 metadata of Polkadot 2005000, Kusama 2003002, Statemint 2005000 and
 * Westend 1025001, with signing payloads that polkadot.js 16.5.6 rebuilt byte for byte (see `provenance` in
 * `extrinsic-vectors.json`). All of these runtimes list CheckMetadataHash.
 */
internal object ParityRuntimes {
    private val resources = File("../runtime-utils/src/test/resources")
    private val runtimes = mutableMapOf<String, RuntimeSnapshot>()

    val vectors: JsonObject by lazy {
        Json.parseToJsonElement(File(resources, "parity/extrinsic-vectors.json").readText()).jsonObject
    }

    val chains: List<JsonObject>
        get() = vectors.getValue("chains").jsonArray.map { it.jsonObject }

    /** The vectors' public Ed25519 test key; never a wallet key. */
    val keypairPublicKey: ByteArray
        get() = vectors.getValue("keypair").jsonObject.text("publicKey").fromHex()

    val keypairPrivateKey: ByteArray
        get() = vectors.getValue("keypair").jsonObject.text("privateKey").fromHex()

    fun chain(name: String): JsonObject = chains.single { it.text("name") == name }

    fun runtime(name: String): RuntimeSnapshot = synchronized(runtimes) {
        runtimes.getOrPut(name) {
            val chain = chain(name)
            val metadataHex = GZIPInputStream(File(resources, chain.text("metadata")).inputStream())
                .bufferedReader().use { it.readText() }.trim()
            val types = File(resources, chain.text("types")).readText()
            RuntimeFactory(JsonFactory.create()).constructRuntime(metadataHex, types, chain.number("specVersion")).runtime
        }
    }

    /** The SCALE call of a parity case: one `transfer_keep_alive`, or a `batch_all` of them. */
    fun callOf(runtime: RuntimeSnapshot, case: JsonObject): ByteArray {
        val transfers = case.getValue("calls").jsonArray.map { it.jsonObject }.map { transfer ->
            val module = runtime.metadata.module(transfer.text("module"))
            GenericCall.Instance(
                module,
                module.call(transfer.text("call")),
                mapOf(
                    "dest" to DictEnum.Entry("Id", transfer.text("dest").fromHex()),
                    "value" to BigInteger(transfer.text("value"))
                )
            )
        }
        val call = if (case.getValue("batchAll").jsonPrimitive.content.toBoolean()) {
            val utility = runtime.metadata.module("Utility")
            GenericCall.Instance(utility, utility.call("batch_all"), mapOf("calls" to transfers))
        } else {
            transfers.single()
        }
        return GenericCall.toByteArray(runtime, call)
    }

    /**
     * The `transactionPayload` polkadot.js `SignerPayloadJSON` would send for a parity case: numbers as big-endian hex
     * of their type's width, CheckMetadataHash mode 0 with no hash.
     */
    fun signerPayload(
        chain: JsonObject,
        case: JsonObject,
        method: ByteArray,
        address: String
    ): JsonObject {
        val era = case["era"]?.takeIf { it !is JsonNull }?.jsonArray?.let { (period, phase) ->
            mortalEra(period.jsonPrimitive.int, phase.jsonPrimitive.int)
        } ?: byteArrayOf(0)
        return buildJsonObject {
            put("address", address)
            put("blockHash", case["blockHash"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content ?: chain.text("genesisHash"))
            put("blockNumber", "0x00000000")
            put("era", era.toHexString(withPrefix = true))
            put("genesisHash", chain.text("genesisHash"))
            put("metadataHash", JsonNull)
            put("method", method.toHexString(withPrefix = true))
            put("mode", 0)
            put("nonce", BigInteger.valueOf(case.number("nonce").toLong()).fixedHex(4))
            put("signedExtensions", chain.getValue("signedExtensions"))
            put("specVersion", BigInteger.valueOf(chain.number("specVersion").toLong()).fixedHex(4))
            put("tip", BigInteger(case.text("tip")).fixedHex(16))
            put("transactionVersion", BigInteger.valueOf(chain.number("transactionVersion").toLong()).fixedHex(4))
            put("version", 4)
        }
    }

    /** A mortal era as Substrate encodes it. */
    fun mortalEra(period: Int, phase: Int): ByteArray {
        val quantizeFactor = maxOf(period shr 12, 1)
        val encoded = minOf(maxOf(Integer.numberOfTrailingZeros(period) - 1, 1), 15) or (phase / quantizeFactor shl 4)
        return byteArrayOf((encoded and 0xff).toByte(), (encoded shr 8).toByte())
    }

    fun BigInteger.fixedHex(bytes: Int): String = "0x" + toString(16).padStart(bytes * 2, '0')

    fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.content

    fun JsonObject.number(key: String): Int = getValue(key).jsonPrimitive.int

    fun JsonObject.cases(): List<JsonObject> = (getValue("cases") as JsonArray).map { it.jsonObject }
}
