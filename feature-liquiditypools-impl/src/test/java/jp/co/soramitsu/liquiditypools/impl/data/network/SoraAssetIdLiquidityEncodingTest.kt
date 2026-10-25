package jp.co.soramitsu.liquiditypools.impl.data.network

import jp.co.soramitsu.core.network.JsonFactory
import jp.co.soramitsu.core.runtime.RuntimeFactory
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Era
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.instances.AddressInstanceConstructor
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersion
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.math.BigInteger
import java.util.zip.GZIPInputStream

/**
 * SORA asset ids with a byte >= 0x80 must encode in pool calls and pool storage keys. 4.2.0 passed those
 * bytes signed; fearless-utils refuses negative u8 values. Runtime: the AL-9 SORA mainnet snapshot.
 */
class SoraAssetIdLiquidityEncodingTest {

    private val runtime = soraRuntime()

    @Test
    fun `asset id code is the unsigned value of every byte`() {
        val code = DEO.mapAssetId()

        assertEquals(32, code.size)
        assertEquals(listOf(0x00, 0xf2, 0xf4, 0xfd).map(Int::toBigInteger), code.take(4))
        assertTrue(code.all { it >= BigInteger.ZERO && it <= BigInteger.valueOf(0xFF) })
    }

    @Test
    fun `deposit liquidity with a high-byte asset encodes both ids`() {
        val keypair = SubstrateKeypairFactory.generate(EncryptionType.ED25519, ByteArray(32) { 7 })
        val extrinsic = ExtrinsicBuilder(
            runtime = runtime,
            keypair = keypair,
            nonce = BigInteger.ZERO,
            runtimeVersion = RuntimeVersion(SPEC_VERSION, TRANSACTION_VERSION),
            genesisHash = ByteArray(32),
            multiChainEncryption = MultiChainEncryption.Substrate(EncryptionType.ED25519),
            accountIdentifier = AddressInstanceConstructor.constructInstance(runtime.typeRegistry, keypair.publicKey),
            era = Era.Immortal
        ).depositLiquidity(
            dexId = 0,
            baseAssetId = XOR,
            targetAssetId = DEO,
            baseAssetAmount = BigInteger.TEN,
            targetAssetAmount = BigInteger.TEN,
            amountFromMin = BigInteger.ONE,
            amountToMin = BigInteger.ONE
        ).build()

        // PoolXYK.deposit_liquidity: dex_id (u32), input_asset_a, input_asset_b.
        assertTrue(extrinsic, extrinsic.contains("00000000" + XOR.removePrefix("0x") + DEO.removePrefix("0x")))
    }

    @Test
    fun `pool reserves key for a high-byte asset ends with both ids`() {
        val key = runtime.metadata.module("PoolXYK").storage("Reserves")
            .storageKey(runtime, XOR.mapCodeToken(), DEO.mapCodeToken())

        assertTrue(key, key.endsWith(DEO.removePrefix("0x")))
        assertTrue(key, key.contains(XOR.removePrefix("0x")))
    }

    private fun soraRuntime(): RuntimeSnapshot {
        val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "settings.gradle").isFile }
        val snapshot = File(root, "feature-wallet-impl/src/test/resources/prod-parity/runtime/sora.json.gz")
            .inputStream().let(::GZIPInputStream).use { Json.parseToJsonElement(it.readBytes().decodeToString()).jsonObject }
        return RuntimeFactory(JsonFactory.create()).constructRuntime(
            metadataRaw = snapshot.getValue("metadata").jsonPrimitive.content,
            ownTypesRaw = snapshot.getValue("types").jsonPrimitive.content,
            runtimeVersion = snapshot.getValue("specVersion").jsonPrimitive.int
        ).runtime
    }

    private companion object {
        const val XOR = "0x0200000000000000000000000000000000000000000000000000000000000000"
        const val DEO = "0x00f2f4fda40a4bf1fc3769d156fa695532eec31e265d75068524462c0b80f674"
        const val SPEC_VERSION = 133
        const val TRANSACTION_VERSION = 131
    }
}
