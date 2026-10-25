package jp.co.soramitsu.xcm

import com.google.gson.JsonParser
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.network.JsonFactory
import jp.co.soramitsu.core.runtime.RuntimeFactory
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Era
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Extrinsic
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.xcm.domain.XcmArgumentShape
import jp.co.soramitsu.xcm.domain.XcmDestinationFeeMode
import jp.co.soramitsu.xcm.domain.XcmDestinationFeeSpec
import jp.co.soramitsu.xcm.domain.XcmExecutionSpec
import jp.co.soramitsu.xcm.domain.XcmMultiLocationParser
import jp.co.soramitsu.xcm.domain.XcmMultiLocationSpec
import jp.co.soramitsu.xcm.domain.XcmTransferType
import jp.co.soramitsu.xcm.domain.XcmWeightLimitSpec
import jp.co.soramitsu.xcm.domain.XcmWeightLimitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.math.BigInteger
import java.util.zip.GZIPInputStream

/**
 * Encodes calls produced by the real [SubstrateXcmTransferEngine] against real runtimes (Polkadot Asset Hub 2005000
 * and Acala, the snapshots the wallet's parity tests already use), then decodes the extrinsic bytes back and checks
 * what they mean.
 *
 * The other engine tests compare the engine's logical map with strings, so they could not notice that no reviewed call
 * could be encoded at all: the recipient junction was a string, the V3 asset id was wrapped like a versioned
 * location, V3 `X1` was an array, and chain metadata names calls in snake_case while the specs use the JavaScript
 * camelCase.
 */
class SubstrateXcmTransferEngineRealMetadataTest {

    private val assetHub: RuntimeSnapshot by lazy { runtime("asset-hub-polkadot") }
    private val acala: RuntimeSnapshot by lazy { runtime("acala") }
    private val engine = SubstrateXcmTransferEngine(NoSubmitter)

    @Test
    fun `teleport encodes for every accepted XCM version and decodes to the reviewed meaning`() {
        VERSIONS.forEach { version ->
            val call = decode(assetHub, engine.buildTransferCall(teleportSpec(version), RECIPIENT, AMOUNT))
            val variant = version.uppercase()

            assertEquals(version, "limited_teleport_assets", call.name)
            assertEquals(version, mapOf(variant to location(1, here())), call.arguments["dest"])
            assertEquals(version, mapOf(variant to location(0, singleJunction(version, account()))), call.arguments["beneficiary"])
            assertEquals(version, mapOf(variant to listOf(asset(version, location(1, here())))), call.arguments["assets"])
            assertEquals(version, 0L, call.arguments["fee_asset_item"])
            assertEquals(version, mapOf("Unlimited" to null), call.arguments["weight_limit"])
        }
    }

    @Test
    fun `reserve transfer to a parachain encodes for every accepted XCM version`() {
        VERSIONS.forEach { version ->
            val call = decode(assetHub, engine.buildTransferCall(reserveSpec(version), RECIPIENT, AMOUNT))
            val variant = version.uppercase()

            assertEquals(version, "limited_reserve_transfer_assets", call.name)
            assertEquals(
                version,
                mapOf(variant to location(1, singleJunction(version, mapOf("Parachain" to 2000L)))),
                call.arguments["dest"]
            )
            assertEquals(version, mapOf(variant to location(0, singleJunction(version, account()))), call.arguments["beneficiary"])
        }
    }

    @Test
    fun `a multi junction asset location keeps its pallet instance and general index`() {
        // The USDT asset on Asset Hub, as seen from Asset Hub itself.
        val usdt = parsed(0, "X2(PalletInstance(50), GeneralIndex(1984))")
        VERSIONS.forEach { version ->
            val spec = reserveSpec(version).copy(assetLocation = usdt, feeAssetLocation = usdt)
            val call = decode(assetHub, engine.buildTransferCall(spec, RECIPIENT, AMOUNT))

            val expectedLocation = location(
                0,
                mapOf("X2" to listOf(mapOf("PalletInstance" to 50L), mapOf("GeneralIndex" to 1984L)))
            )
            assertEquals(
                version,
                mapOf(version.uppercase() to listOf(asset(version, expectedLocation))),
                call.arguments["assets"]
            )
        }
    }

    @Test
    fun `a general key junction fails closed because its runtime shape is not modelled`() {
        // GeneralKey is a { length, data } struct on live runtimes; a bare string must not be guessed into it.
        val keyed = parsed(1, "X1(GeneralKey(0x0001))")
        VERSIONS.forEach { version ->
            val call = engine.buildTransferCall(
                reserveSpec(version).copy(assetLocation = keyed, feeAssetLocation = keyed),
                RECIPIENT,
                AMOUNT
            )

            assertThrows(version, RuntimeException::class.java) { encode(assetHub, call) }
        }
    }

    @Test
    fun `a limited weight decodes to the reviewed reference time and proof size`() {
        val spec = teleportSpec("v5").copy(
            weightLimit = XcmWeightLimitSpec(
                type = XcmWeightLimitType.LIMITED,
                refTime = BigInteger("5000000000"),
                proofSize = BigInteger("65536")
            )
        )

        val call = decode(assetHub, engine.buildTransferCall(spec, RECIPIENT, AMOUNT))

        assertEquals(
            mapOf("Limited" to mapOf("refTime" to 5_000_000_000L, "proofSize" to 65_536L)),
            call.arguments["weight_limit"]
        )
    }

    @Test
    fun `an XTokens transfer encodes against the Acala runtime`() {
        VERSIONS.forEach { version ->
            val call = decode(acala, engine.buildTransferCall(xTokensSpec(version), RECIPIENT, AMOUNT))
            val variant = version.uppercase()

            assertEquals(version, "transfer_multiasset", call.name)
            assertEquals(version, mapOf("Unlimited" to null), call.arguments["dest_weight_limit"])
            assertEquals(version, mapOf(variant to asset(version, location(1, here()))), call.arguments["asset"])
            assertEquals(
                version,
                mapOf(variant to location(1, mapOf("X2" to listOf(mapOf("Parachain" to 1000L), account())))),
                call.arguments["dest"]
            )
        }
    }

    @Test
    fun `a recipient that is not a 32 byte account never reaches an encoded extrinsic`() {
        listOf("0x1234", "0x" + "11".repeat(20), "0x" + "11".repeat(33), "not-an-address", "0x" + "zz".repeat(32))
            .forEach { recipient ->
                val call = engine.buildTransferCall(teleportSpec("v5"), recipient, AMOUNT)

                assertThrows(recipient, RuntimeException::class.java) { encode(assetHub, call) }
            }
        listOf("", "   ").forEach { recipient ->
            assertThrows(IllegalArgumentException::class.java) {
                engine.buildTransferCall(teleportSpec("v5"), recipient, AMOUNT)
            }
        }
    }

    @Test
    fun `XCM versions that live runtimes no longer decode are refused`() {
        listOf("v1", "v2", "v6", "V3", " v3", "three", "").forEach { version ->
            assertThrows(version, IllegalArgumentException::class.java) {
                engine.buildTransferCall(teleportSpec(version), RECIPIENT, AMOUNT)
            }
        }
    }

    @Test
    fun `a zero or negative amount is refused`() {
        listOf(BigInteger.ZERO, BigInteger.valueOf(-1)).forEach { amount ->
            assertThrows(IllegalArgumentException::class.java) {
                engine.buildTransferCall(teleportSpec("v5"), RECIPIENT, amount)
            }
        }
    }

    @Test
    fun `call lookup accepts the JavaScript and the metadata spelling and fails closed otherwise`() {
        val xcm = assetHub.metadata.module("PolkadotXcm")

        assertEquals("limited_teleport_assets", xcm.resolveXcmCall("limitedTeleportAssets").name)
        assertEquals("limited_teleport_assets", xcm.resolveXcmCall("limited_teleport_assets").name)
        assertEquals("transfer_assets_using_type_and_then", xcm.resolveXcmCall("transferAssetsUsingTypeAndThen").name)
        assertThrows(NoSuchElementException::class.java) { xcm.resolveXcmCall("doesNotExist") }
        assertThrows(NoSuchElementException::class.java) { xcm.resolveXcmCall("") }
    }

    @Test
    fun `a call missing from the runtime fails closed instead of encoding a guess`() {
        val call = engine.buildTransferCall(teleportSpec("v5"), RECIPIENT, AMOUNT).copy(callName = "doesNotExist")

        assertThrows(NoSuchElementException::class.java) { encode(assetHub, call) }
    }

    @Test
    fun `an argument the runtime does not define fails closed`() {
        val call = engine.buildTransferCall(teleportSpec("v5"), RECIPIENT, AMOUNT)
        val extra = call.copy(arguments = call.arguments + ("surprise" to BigInteger.ONE))

        assertThrows(IllegalArgumentException::class.java) { encode(assetHub, extra) }
    }

    // ---- decoding helpers -------------------------------------------------------------------

    private class DecodedCall(val name: String, val arguments: Map<String, Any?>)

    private fun encode(runtime: RuntimeSnapshot, call: XcmExtrinsicCall): String = buildXcmFeeQuote(
        runtime = runtime,
        accountId = ByteArray(32) { 7 },
        cryptoType = CryptoType.SR25519,
        nonce = BigInteger.ZERO,
        era = Era.Immortal,
        call = call
    )

    private fun decode(runtime: RuntimeSnapshot, call: XcmExtrinsicCall): DecodedCall {
        val extrinsic = Extrinsic.fromHex(runtime, encode(runtime, call))

        return DecodedCall(
            name = extrinsic.call.function.name,
            arguments = extrinsic.call.arguments.mapValues { (_, value) -> normalize(value) }
        )
    }

    // Plain maps, lists and longs, with 20 and 32 byte arrays turned into hex, so decoded calls compare with literals.
    private fun normalize(value: Any?): Any? = when (value) {
        is DictEnum.Entry<*> -> mapOf(value.name to normalize(value.value))
        is Struct.Instance -> value.mapping.mapValues { normalize(it.value) }
        is ByteArray -> hex(value.map { it.toLong() and 0xff })
        is List<*> -> normalizeList(value.map(::normalize))
        is Number -> value.toLong()
        else -> value
    }

    private fun normalizeList(items: List<Any?>): Any {
        val isAccountBytes = items.size in setOf(20, 32) && items.all { it is Long && it in 0..255 }
        return if (isAccountBytes) hex(items.map { it as Long }) else items
    }

    private fun hex(bytes: List<Long>) = bytes.joinToString(separator = "", prefix = "0x") { "%02x".format(it) }

    // ---- expected values --------------------------------------------------------------------

    private fun here(): Map<String, Any?> = mapOf("Here" to null)

    private fun location(parents: Long, interior: Map<String, Any?>): Map<String, Any?> =
        mapOf("parents" to parents, "interior" to interior)

    // V3 defines X1 as a single junction; V4 and newer as an array.
    private fun singleJunction(version: String, junction: Map<String, Any?>): Map<String, Any?> =
        mapOf("X1" to if (version == "v3") junction else listOf(junction))

    // Beneficiary junction as it decodes: no network restriction (None) and the 32 recipient bytes.
    private fun account(): Map<String, Any?> = mapOf("AccountId32" to mapOf("network" to null, "id" to ALICE_HEX))

    private fun asset(version: String, id: Map<String, Any?>): Map<String, Any?> = mapOf(
        "id" to if (version == "v3") mapOf("Concrete" to id) else id,
        "fun" to mapOf("Fungible" to AMOUNT.toLong())
    )

    // ---- fixtures ---------------------------------------------------------------------------

    private fun teleportSpec(version: String) = spec(
        version = version,
        transferType = XcmTransferType.LIMITED_TELEPORT_ASSETS,
        callName = "limitedTeleportAssets",
        destination = parsed(1, "Here"),
        asset = parsed(1, "Here")
    )

    private fun reserveSpec(version: String) = spec(
        version = version,
        transferType = XcmTransferType.LIMITED_RESERVE_TRANSFER_ASSETS,
        callName = "limitedReserveTransferAssets",
        destination = parsed(1, "X1(Parachain(2000))"),
        asset = parsed(1, "Here")
    )

    // xTokens takes one destination that already contains the account, so the beneficiary location is the whole path.
    private fun xTokensSpec(version: String) = spec(
        version = version,
        transferType = XcmTransferType.X_TOKENS_TRANSFER_MULTIASSET,
        callName = "transferMultiasset",
        destination = parsed(1, "X1(Parachain(1000))"),
        asset = parsed(1, "Here")
    ).copy(
        palletName = "XTokens",
        argumentShape = XcmArgumentShape.X_TOKENS_TRANSFER_MULTIASSET,
        beneficiaryLocation = parsed(1, "X2(Parachain(1000), AccountId32({network: Any, id: <account>}))")
    )

    private fun spec(
        version: String,
        transferType: XcmTransferType,
        callName: String,
        destination: XcmMultiLocationSpec,
        asset: XcmMultiLocationSpec
    ) = XcmExecutionSpec(
        palletName = "PolkadotXcm",
        callName = callName,
        transferType = transferType,
        argumentShape = XcmArgumentShape.POLKADOT_XCM_TRANSFER_ASSETS,
        xcmVersion = version,
        destinationLocation = destination,
        assetLocation = asset,
        beneficiaryLocation = parsed(0, ACCOUNT_INTERIOR),
        feeAssetLocation = asset,
        feeAssetItem = 0,
        weightLimit = XcmWeightLimitSpec(type = XcmWeightLimitType.UNLIMITED, refTime = null, proofSize = null),
        destinationFee = XcmDestinationFeeSpec(mode = XcmDestinationFeeMode.INCLUDED, assetSymbol = "DOT", amount = null),
        bridge = null
    )

    private fun parsed(parents: Int, interior: String) = XcmMultiLocationSpec(
        parents = parents,
        interior = interior,
        junctions = XcmMultiLocationParser.requireValidInterior(interior, "test")
    )

    private fun runtime(name: String): RuntimeSnapshot {
        val file = listOf(
            File("../feature-wallet-impl/src/test/resources/prod-parity/runtime/$name.json.gz"),
            File("feature-wallet-impl/src/test/resources/prod-parity/runtime/$name.json.gz")
        ).firstOrNull(File::isFile) ?: error("prod-parity runtime snapshot $name.json.gz not found from ${File(".").absolutePath}")
        val snapshot = GZIPInputStream(file.inputStream()).use { String(it.readBytes()) }
            .let { JsonParser.parseString(it).asJsonObject }

        return RuntimeFactory(JsonFactory.create()).constructRuntime(
            metadataRaw = snapshot.get("metadata").asString,
            ownTypesRaw = snapshot.get("types").asString,
            runtimeVersion = snapshot.get("specVersion").asInt
        ).runtime
    }

    private object NoSubmitter : XcmExtrinsicSubmitter {
        override suspend fun submit(
            chain: jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain,
            accountId: ByteArray,
            keypairProvider: jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider,
            call: XcmExtrinsicCall
        ): String = error("not used")

        override suspend fun estimateFee(
            chain: jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain,
            accountId: ByteArray,
            keypairProvider: jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider,
            call: XcmExtrinsicCall
        ): BigInteger = error("not used")
    }

    private companion object {
        val VERSIONS = listOf("v3", "v4", "v5")
        const val ACCOUNT_INTERIOR = "X1(AccountId32({network: Any, id: <account>}))"

        // //Alice in Polkadot SS58 form and her public key, which is what must come out of the decoded call.
        const val RECIPIENT = "15oF4uVJwmo4TdGW7VfQxNLavjCXviqxT9S1MgbjMNHr6Sp5"
        const val ALICE_HEX = "0xd43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d"
        val AMOUNT: BigInteger = BigInteger("10000000000")
    }
}
