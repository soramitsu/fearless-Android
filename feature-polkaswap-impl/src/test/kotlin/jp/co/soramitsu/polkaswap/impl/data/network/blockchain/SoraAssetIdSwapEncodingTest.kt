package jp.co.soramitsu.polkaswap.impl.data.network.blockchain

import java.io.File
import java.math.BigInteger
import java.util.zip.GZIPInputStream
import jp.co.soramitsu.core.network.JsonFactory
import jp.co.soramitsu.core.runtime.RuntimeFactory
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Era
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.instances.AddressInstanceConstructor
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersion
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SORA asset ids with a byte >= 0x80 (USDT, DEO, CERES and most bridged tokens) must still encode in a swap.
 * 4.2.0 passed those bytes signed; fearless-utils refuses negative u8 values, so a signed id failed to encode.
 * The runtime is the SORA mainnet snapshot the AL-9 parity vectors use.
 */
class SoraAssetIdSwapEncodingTest {

    @Test
    fun `swap between XOR and a high-byte SORA asset encodes both ids byte for byte`() {
        val runtime = soraRuntime()
        val keypair = SubstrateKeypairFactory.generate(EncryptionType.ED25519, ByteArray(32) { 7 })

        listOf(USDT, DEO).forEach { assetId ->
            val extrinsic = ExtrinsicBuilder(
                runtime = runtime,
                keypair = keypair,
                nonce = BigInteger.ZERO,
                runtimeVersion = RuntimeVersion(SPEC_VERSION, TRANSACTION_VERSION),
                genesisHash = ByteArray(32),
                multiChainEncryption = MultiChainEncryption.Substrate(EncryptionType.ED25519),
                accountIdentifier = AddressInstanceConstructor.constructInstance(runtime.typeRegistry, keypair.publicKey),
                era = Era.Immortal
            ).swap(
                dexId = 0,
                inputAssetId = XOR,
                outputAssetId = assetId,
                amount = BigInteger.TEN,
                limit = BigInteger.ONE,
                filter = "Disabled",
                markets = emptyList(),
                desired = WithDesired.INPUT
            ).build()

            // LiquidityProxy.swap: dex_id (u32), input_asset_id, output_asset_id.
            assertTrue(extrinsic, extrinsic.contains("00000000" + XOR.removePrefix("0x") + assetId.removePrefix("0x")))
        }
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
        const val USDT = "0x0083a6b3fbc6edae06f115c8953ddd7cbfba0b74579d6ea190f96853073b76f4"
        const val DEO = "0x00f2f4fda40a4bf1fc3769d156fa695532eec31e265d75068524462c0b80f674"
        const val SPEC_VERSION = 133
        const val TRANSACTION_VERSION = 131
    }
}
