package jp.co.soramitsu.fearless_utils.runtime.extrinsic

import jp.co.soramitsu.fearless_utils.encrypt.Signer
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Extrinsic
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.SignedExtras
import jp.co.soramitsu.fearless_utils.runtime.metadata.ExtrinsicMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.security.MessageDigest

/**
 * AL-6: Polkadot, Kusama, Polkadot Asset Hub and Westend list CheckMetadataHash. Without it the RC encoded neither the mode
 * byte nor the additional-signed None, so every send, staking call and fee query on those chains was rejected.
 */
class ExtrinsicParityTest {

    private val runtimes = mutableMapOf<String, RuntimeSnapshot>()

    private fun runtime(chain: ParityFixtures.Chain) = runtimes.getOrPut(chain.name) { ParityFixtures.runtime(chain) }

    @Test
    fun `fixtures are the captured metadata of chains that list CheckMetadataHash`() {
        assertEquals(listOf("polkadot", "kusama", "statemint", "westend"), ParityFixtures.vectors.chains.map { it.name })
        ParityFixtures.vectors.chains.forEach { chain ->
            val sha256 = MessageDigest.getInstance("SHA-256").digest(ParityFixtures.metadataHex(chain).toByteArray())
            assertEquals(chain.name, chain.metadataSha256, sha256.toHexString())
            assertEquals(chain.name, chain.signedExtensions, runtime(chain).metadata.extrinsic.signedExtensions)
            assertTrue(chain.name, SignedExtras.CHECK_METADATA_HASH in chain.signedExtensions)
        }
    }

    @Test
    fun `build is byte-equal to production 4_2_0 and polkadot-js on metadata-hash chains`() {
        forEachCase { chain, case ->
            val built = ParityFixtures.builder(runtime(chain), chain, case).build(useBatchAll = case.batchAll)

            assertEquals("${chain.name} / ${case.name}", case.extrinsic, built)
        }
    }

    @Test
    fun `prepared signing payload matches the payload production signed`() {
        forEachCase { chain, case ->
            val label = "${chain.name} / ${case.name}"
            val prepared = ParityFixtures.builder(runtime(chain), chain, case).prepareSigning(useBatchAll = case.batchAll)
            var signedMessage: ByteArray? = null

            val signed = prepared.sign(ParityFixtures.keypair) { encryption, message, keypair ->
                signedMessage = message.copyOf()
                Signer.sign(encryption, message, keypair)
            }

            assertEquals(label, case.extrinsic, signed)
            assertEquals(label, case.signedMessage, signedMessage!!.toHexString(withPrefix = true))
            val payload = case.signingPayload.fromHex()
            val intent = prepared.intentBytes()
            assertArrayEquals(label, payload, intent.copyOfRange(intent.size - payload.size, intent.size))
        }
    }

    @Test
    fun `chain extrinsics decode aligned once CheckMetadataHash is known`() {
        forEachCase { chain, case ->
            val label = "${chain.name} / ${case.name}"
            val decoded = Extrinsic.fromHex(runtime(chain), case.extrinsic)
            val extras = decoded.signature!!.signedExtras

            assertEquals(label, BigInteger.ZERO, extras[SignedExtras.CHECK_METADATA_HASH])
            assertEquals(label, case.nonce.toBigInteger(), extras[SignedExtras.NONCE])
            val expectedCall = if (case.calls.size == 1) case.calls.single().call else "batch_all"
            assertEquals(label, expectedCall, decoded.call.function.name)
            if (case.calls.size == 1) {
                val dest = decoded.call.arguments["dest"] as DictEnum.Entry<*>
                assertEquals(label, "Id", dest.name)
                assertArrayEquals(label, case.calls.single().dest.fromHex(), dest.value as ByteArray)
                assertEquals(label, BigInteger(case.calls.single().value), decoded.call.arguments["value"])
            }
        }
    }

    @Test
    fun `asset hub keeps the requested tip in ChargeAssetTxPayment and pays fees in the native asset`() {
        val chain = ParityFixtures.chain("statemint")
        val case = chain.cases.single { it.tip != "0" }

        val built = ParityFixtures.builder(runtime(chain), chain, case).build()
        val assetTxPayment = Extrinsic.fromHex(runtime(chain), built).signature!!.signedExtras[SignedExtras.ASSET_TX_PAYMENT] as List<*>

        assertEquals(BigInteger(case.tip), assetTxPayment[0])
        assertNull(assetTxPayment[1])
        // Play 4.2.0 hard-coded the tip to zero there; the RC signs what the user confirmed.
        assertNotEquals(case.production!!.extrinsic, built)
    }

    @Test
    fun `app id goes to CheckAppId and never into the asset slot`() {
        val chain = ParityFixtures.chain("statemint")
        val case = chain.cases.first()
        val runtime = withSignedExtensions(
            runtime(chain),
            listOf("CheckSpecVersion", "CheckTxVersion", "CheckGenesis", "CheckMortality", "CheckNonce", "ChargeAssetTxPayment", "CheckAppId", "CheckMetadataHash")
        )

        val withAppId = ParityFixtures.builder(runtime, chain, case, appId = BigInteger.valueOf(5)).build()
        val withoutAppId = ParityFixtures.builder(runtime, chain, case, appId = null).build()

        val extras = Extrinsic.fromHex(runtime, withAppId).signature!!.signedExtras
        assertEquals(BigInteger.valueOf(5), extras[SignedExtras.APP_ID])
        assertEquals(listOf(BigInteger.ZERO, null), extras[SignedExtras.ASSET_TX_PAYMENT])
        assertEquals(BigInteger.ZERO, Extrinsic.fromHex(runtime, withoutAppId).signature!!.signedExtras[SignedExtras.APP_ID])
    }

    @Test
    fun `prepared and direct signing stay identical with CheckAppId`() {
        val chain = ParityFixtures.chain("polkadot")
        val runtime = withSignedExtensions(runtime(chain), runtime(chain).metadata.extrinsic.signedExtensions + "CheckAppId")
        chain.cases.forEach { case ->
            val builder = ParityFixtures.builder(runtime, chain, case, appId = BigInteger.valueOf(7))

            assertEquals(case.name, builder.build(case.batchAll), builder.prepareSigning(case.batchAll).sign(ParityFixtures.keypair))
        }
    }

    private fun forEachCase(action: (ParityFixtures.Chain, ParityFixtures.Case) -> Unit) {
        ParityFixtures.vectors.chains.forEach { chain -> chain.cases.forEach { case -> action(chain, case) } }
    }

    private fun withSignedExtensions(runtime: RuntimeSnapshot, signedExtensions: List<String>) = RuntimeSnapshot(
        runtime.typeRegistry,
        RuntimeMetadata(
            runtime.metadata.runtimeVersion,
            runtime.metadata.modules,
            ExtrinsicMetadata(runtime.metadata.extrinsic.version, signedExtensions)
        )
    )
}
