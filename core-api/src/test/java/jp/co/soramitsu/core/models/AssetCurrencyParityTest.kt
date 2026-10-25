package jp.co.soramitsu.core.models

import java.math.BigInteger
import jp.co.soramitsu.core.utils.isSoraUtilityAsset
import jp.co.soramitsu.core.utils.soraAssetIdCode
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Asset.currency and Asset.typeExtra against Play 4.2.0 (AL-9).
 *
 * prod-parity/13-asset-currency.json was written by the verbatim 4.2.0 Asset constructor
 * (shared-features-android@7508bc83 core/models Asset.kt) for every bundled registry asset and for the
 * inputs the registry lacks today. input.type is the 4.2.0 ChainAssetType name, which is also what the
 * chain_assets.type column holds after 4.2.0 or the RC cached the registry.
 */
class AssetCurrencyParityTest {

    private val cases: List<JsonObject> = Json.parseToJsonElement(
        requireNotNull(javaClass.getResource("/prod-parity/13-asset-currency.json")).readText()
    ).jsonObject.getValue("cases").jsonArray.map { it.jsonObject }

    @Test
    fun `every registry and synthetic asset gets the 4_2_0 type and currency`() {
        val accepted = cases.filter { "error" !in it }
        assertTrue(accepted.size > 350)

        accepted.forEach { case ->
            val label = case.string("label")
            val asset = case.getValue("input").jsonObject.toAsset()

            assertEquals(label, case.string("typeExtra"), asset.typeExtra?.name)
            assertEquals(label, unsignedSoraCode(case.getValue("currency")), describe(asset.currency))
        }
    }

    @Test
    fun `SORA asset id codes are the unsigned bytes 4_2_0 encoded`() {
        val signed = cases.mapNotNull { it["currency"] as? JsonObject }
            .flatMap { it["struct"]?.jsonObject?.get("code")?.jsonObject?.get("list")?.jsonArray.orEmpty() }
            .count { it.jsonObject.getValue("number").jsonPrimitive.content.toInt() < 0 }
        // DEO, CERES, USDT and most other SORA tokens have a byte >= 0x80 in their id.
        assertTrue(signed > 0)

        assertEquals(
            listOf(0, 0xf2, 0xf4, 0xfd).map(Int::toBigInteger),
            soraAssetIdCode("0x00f2f4fd").toList()
        )
        assertEquals(emptyList<BigInteger>(), soraAssetIdCode(null))
    }

    @Test
    fun `an asset type 4_2_0 refused to encode is still refused, never sent as the native token`() {
        val refused = cases.filter { "error" in it }
        assertEquals(listOf("synthetic:unknown"), refused.map { it.string("label") })

        refused.forEach { case ->
            val asset = case.getValue("input").jsonObject.toAsset()

            // 4.2.0 threw from the Asset constructor. The RC model is built lazily so detected assets
            // (Solana, Iroha) can carry the Unknown type, and refuses when the currency is read instead.
            // A null here would make WssSubstrateSource fall back to a Balances transfer of the native token.
            assertThrows(case.string("label"), IllegalStateException::class.java) { asset.currency }
        }
    }

    @Test
    fun `the vectors cover every asset type 4_2_0 knew`() {
        val covered = cases.map { it.getValue("input").jsonObject.string("type") }.toSet()

        assertTrue(null in covered)
        ChainAssetType.entries.forEach { type ->
            assertTrue("no 4.2.0 case for $type", type.name in covered)
        }
    }

    @Test
    fun `SORA XOR on mainnet and testnet is the utility asset whatever the registry type says`() {
        listOf(SoraMainChainId, SoraTestChainId).forEach { chainId ->
            listOf(ChainAssetType.SoraAsset, ChainAssetType.Normal, null).forEach { type ->
                val xor = soraAsset(chainId, isUtility = true, type = type, currencyId = XOR)

                assertEquals(ChainAssetType.SoraUtilityAsset, xor.typeExtra)
                assertTrue(xor.isSoraUtilityAsset())
                assertEquals(type, xor.type)
            }
        }
    }

    @Test
    fun `SORA tokens keep their registry type and encode the asset id as a code struct`() {
        val value = soraAsset(SoraMainChainId, isUtility = false, type = ChainAssetType.SoraAsset, currencyId = VAL)

        assertEquals(ChainAssetType.SoraAsset, value.typeExtra)
        assertFalse(value.isSoraUtilityAsset())
        assertEquals(VAL, value.currencyId)
        assertEquals(
            describe(Struct.Instance(mapOf("code" to listOf(2, 0, 4).map(Int::toBigInteger) + List(29) { BigInteger.ZERO }))),
            describe(value.currency)
        )
    }

    @Test
    fun `the utility asset of other chains is not overridden`() {
        val parachainXor = soraAsset(SORA_POLKADOT_PARACHAIN, isUtility = true, type = ChainAssetType.SoraAsset, currencyId = XOR)

        assertEquals(ChainAssetType.SoraAsset, parachainXor.typeExtra)
        assertFalse(parachainXor.isSoraUtilityAsset())
    }

    @Test
    fun `ORML tokens use the upper-cased currency id or symbol inside a Token entry`() {
        val withId = soraAsset("acala", isUtility = false, type = ChainAssetType.OrmlAsset, currencyId = "kusd")
        val withoutId = soraAsset("acala", isUtility = false, type = ChainAssetType.OrmlAsset, currencyId = null, symbol = "ldot")

        assertEquals(describe(DictEnum.Entry("Token", DictEnum.Entry("KUSD", null))), describe(withId.currency))
        assertEquals(describe(DictEnum.Entry("Token", DictEnum.Entry("LDOT", null))), describe(withoutId.currency))
        assertEquals("kusd", withId.currencyId)
    }

    private fun JsonObject.toAsset(): Asset = Asset(
        id = string("assetId")!!,
        name = string("symbol"),
        symbol = string("symbol")!!,
        iconUrl = "",
        chainId = string("chainId")!!,
        chainName = string("chainName")!!,
        chainIcon = null,
        isTestNet = false,
        priceId = null,
        precision = 0,
        staking = Asset.StakingType.UNSUPPORTED,
        purchaseProviders = null,
        supportStakingPool = false,
        isUtility = getValue("isUtility").jsonPrimitive.booleanOrNull!!,
        // mapChainLocalToChain reads the cached chain_assets.type the same way.
        type = string("type")?.let { ChainAssetType.valueOf(it) },
        currencyId = string("currencyId"),
        existentialDeposit = null,
        color = null,
        isNative = null
    )

    private fun soraAsset(
        chainId: String,
        isUtility: Boolean,
        type: ChainAssetType?,
        currencyId: String?,
        symbol: String = "xor"
    ) = JsonObject(
        mapOf(
            "assetId" to JsonPrimitive("asset"),
            "symbol" to JsonPrimitive(symbol),
            "chainId" to JsonPrimitive(chainId),
            "chainName" to JsonPrimitive("Chain"),
            "isUtility" to JsonPrimitive(isUtility),
            "type" to (type?.name?.let(::JsonPrimitive) ?: JsonNull),
            "currencyId" to (currencyId?.let(::JsonPrimitive) ?: JsonNull)
        )
    ).toAsset()

    /** Same library-neutral rendering the 4.2.0 generator used for its values. */
    private fun describe(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is BigInteger -> JsonObject(mapOf("number" to JsonPrimitive(value.toString())))
        is String -> JsonObject(mapOf("string" to JsonPrimitive(value)))
        is ByteArray -> JsonObject(mapOf("bytes" to JsonPrimitive(value.joinToString("", "0x") { "%02x".format(it) })))
        is DictEnum.Entry<*> -> JsonObject(mapOf("enum" to JsonPrimitive(value.name), "value" to describe(value.value)))
        is Struct.Instance -> JsonObject(mapOf("struct" to JsonObject(value.mapping.mapValues { describe(it.value) })))
        is List<*> -> JsonObject(mapOf("list" to JsonArray(value.map(::describe))))
        else -> error("cannot describe ${value::class}")
    }

    /**
     * 4.2.0 built the SORA code from signed bytes (0xf2 as -14) and its writer kept the low byte. The RC passes the
     * unsigned value, which fearless-utils encodes to the same byte (the runtime parity test checks the bytes).
     */
    private fun unsignedSoraCode(element: JsonElement): JsonElement {
        val code = (element as? JsonObject)?.get("struct")?.jsonObject?.get("code")?.jsonObject?.get("list")?.jsonArray
            ?: return element
        val unsigned = code.map { item ->
            val value = item.jsonObject.getValue("number").jsonPrimitive.content.toInt() and 0xFF
            JsonObject(mapOf("number" to JsonPrimitive(value.toString())))
        }
        return JsonObject(mapOf("struct" to JsonObject(mapOf("code" to JsonObject(mapOf("list" to JsonArray(unsigned)))))))
    }

    private fun JsonObject.string(key: String): String? = get(key)?.takeIf { it !is JsonNull }?.jsonPrimitive?.content

    private companion object {
        const val XOR = "0x0200000000000000000000000000000000000000000000000000000000000000"
        const val VAL = "0x0200040000000000000000000000000000000000000000000000000000000000"
        const val SORA_POLKADOT_PARACHAIN = "e92d165ad41e41e215d09713788173aecfdbe34d3bed29409d33a2ef03980738"
    }
}
