package jp.co.soramitsu.xcm

import jp.co.soramitsu.core.extrinsic.ExtrinsicBuilderFactory
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.core.extrinsic.mortality.Mortality
import jp.co.soramitsu.core.extrinsic.mortality.MortalityConstructor
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.core.rpc.RpcCalls
import jp.co.soramitsu.core.runtime.IChainRegistry
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.DynamicTypeResolver
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.v13Preset
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.Type
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.TypeReference
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Era
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Extrinsic
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.SignedExtras
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.tryExtractMultiSignature
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.FixedByteArray
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u8
import jp.co.soramitsu.fearless_utils.runtime.metadata.ExtrinsicMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.FunctionArgument
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.MetadataFunction
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Module
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersion
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.kotlin.mock
import java.math.BigInteger

class XcmFeeQuoteTest {

    @Test
    fun `fee query never reads wallet key material or enters signing or submission`() = runBlocking {
        val runtime = runtime()
        val chain = mock<Chain>()
        `when`(chain.id).thenReturn("origin")
        val rpc = mock<RpcCalls>()
        val factory = mock<ExtrinsicBuilderFactory>()
        val mortality = mock<MortalityConstructor>()
        val accountId = ByteArray(32)
        val nonce = BigInteger.valueOf(64)
        val era = Era.Mortal(64, 12)
        val keypairProvider = object : KeypairProvider {
            override suspend fun getCryptoTypeFor(chain: IChain, accountId: ByteArray) = CryptoType.SR25519
            override suspend fun getKeypairFor(chain: IChain, accountId: ByteArray): Keypair =
                error("Fee queries must never read a wallet private key")
        }
        val encoded = buildXcmFeeQuote(runtime, accountId, CryptoType.SR25519, nonce, era, call())
        `when`(rpc.getRuntime("origin")).thenReturn(runtime)
        `when`(rpc.getAccountNonce(chain, accountId)).thenReturn(nonce)
        `when`(mortality.construct(chain)).thenReturn(Mortality(era, ByteArray(32)))
        `when`(rpc.estimateExtrinsicFee("origin", encoded)).thenReturn(BigInteger.valueOf(123))

        val fee = ExtrinsicServiceXcmSubmitter(rpc, factory, mortality)
            .estimateFee(chain, accountId, keypairProvider, call())

        assertEquals(BigInteger.valueOf(123), fee)
        verifyNoInteractions(factory)
        verify(rpc).getRuntime("origin")
        verify(rpc).getAccountNonce(chain, accountId)
        verify(rpc).estimateExtrinsicFee("origin", encoded)
        verifyNoMoreInteractions(rpc)
    }

    @Test
    fun `placeholder preserves multisignature variant and encoded width`() {
        val runtime = runtime()
        val encoded = CryptoType.entries.associateWith { cryptoType ->
            buildXcmFeeQuote(runtime, ByteArray(32), cryptoType, BigInteger.valueOf(64), Era.Mortal(64, 12), call())
        }
        encoded.forEach { (cryptoType, hex) ->
            val decoded = Extrinsic.fromHex(runtime, hex)
            val signature = requireNotNull(decoded.signature?.tryExtractMultiSignature())
            assertEquals(cryptoType.name.lowercase(), signature.encryptionType.name.lowercase())
            assertArrayEquals(ByteArray(if (cryptoType == CryptoType.ECDSA) 65 else 64), signature.value)
            assertEquals(BigInteger.valueOf(64), decoded.signature!!.signedExtras[SignedExtras.NONCE])
            assertEquals(BigInteger.ZERO, decoded.signature!!.signedExtras[SignedExtras.TIP])
            assertEquals(BigInteger.valueOf(7), decoded.call.arguments["value"])
        }
        assertEquals(encoded.getValue(CryptoType.ED25519).fromHex().size, encoded.getValue(CryptoType.SR25519).fromHex().size)
        assertEquals(encoded.getValue(CryptoType.SR25519).fromHex().size + 1, encoded.getValue(CryptoType.ECDSA).fromHex().size)
    }

    @Test
    fun `ethereum fee shape retains account20 and r s v widths`() {
        val runtime = runtime(ethereum = true)
        val accountId = ByteArray(20) { 3 }
        val encoded = buildXcmFeeQuote(runtime, accountId, CryptoType.ECDSA, BigInteger.ZERO, Era.Immortal, call())
        val decoded = Extrinsic.fromHex(runtime, encoded)
        assertArrayEquals(accountId, decoded.signature!!.accountIdentifier as ByteArray)
        val signature = decoded.signature!!.signature as Struct.Instance
        assertArrayEquals(ByteArray(32), signature["r"])
        assertArrayEquals(ByteArray(32), signature["s"])
        assertEquals(BigInteger.ZERO, signature.get<BigInteger>("v"))
        assertThrows(IllegalArgumentException::class.java) {
            buildXcmFeeQuote(runtime, accountId, CryptoType.SR25519, BigInteger.ZERO, Era.Immortal, call())
        }
    }

    @Test
    fun `fee query rejects an unknown call argument instead of changing call shape`() {
        assertThrows(IllegalArgumentException::class.java) {
            buildXcmFeeQuote(
                runtime(), ByteArray(32), CryptoType.ED25519, BigInteger.ZERO, Era.Immortal,
                call().copy(arguments = mapOf("unknown" to BigInteger.ONE))
            )
        }
    }

    @Test
    fun `authorized builder freezes actual runtime call without reading wallet key`() = runBlocking {
        val runtime = runtime()
        val chain = mock<Chain>()
        `when`(chain.id).thenReturn("01".repeat(32))
        val rpc = mock<RpcCalls>()
        val registry = mock<IChainRegistry>()
        val mortality = mock<MortalityConstructor>()
        val account = ByteArray(32) { 8 }
        `when`(registry.getRuntime(chain.id)).thenReturn(runtime)
        `when`(rpc.getRuntimeVersion(chain.id)).thenReturn(RuntimeVersion(48, 4))
        `when`(rpc.getAccountNonce(chain, account)).thenReturn(BigInteger.valueOf(64))
        `when`(mortality.construct(chain)).thenReturn(Mortality(Era.Mortal(64, 12), ByteArray(32)))
        val provider = object : KeypairProvider {
            override suspend fun getCryptoTypeFor(chain: IChain, accountId: ByteArray) = CryptoType.SR25519
            override suspend fun getKeypairFor(chain: IChain, accountId: ByteArray): Keypair =
                error("Preparation must not access private keys")
        }
        val builder = ExtrinsicBuilderFactory(rpc, registry, mortality)
            .createForAuthorizedSubmit(chain, account, provider, null, null)
        builder.call("XcmFixture", "send", mapOf("value" to BigInteger.valueOf(7)))
        val prepared = builder.prepareSigning()
        org.junit.Assert.assertTrue(prepared.intentBytes().isNotEmpty())
        assertArrayEquals(prepared.intentBytes(), builder.prepareSigning().intentBytes())
    }

    private fun call() = XcmExtrinsicCall("XcmFixture", "send", mapOf("value" to BigInteger.valueOf(7)))

    private fun runtime(ethereum: Boolean = false): RuntimeSnapshot {
        val signatureType: Type<*> = if (ethereum) {
            Struct(
                "ExtrinsicSignature",
                linkedMapOf(
                    "r" to TypeReference(FixedByteArray("r", 32)),
                    "s" to TypeReference(FixedByteArray("s", 32)),
                    "v" to TypeReference(u8)
                )
            )
        } else {
            DictEnum(
                "ExtrinsicSignature",
                listOf(
                    DictEnum.Entry("Ed25519", TypeReference(FixedByteArray("Ed25519", 64))),
                    DictEnum.Entry("Sr25519", TypeReference(FixedByteArray("Sr25519", 64))),
                    DictEnum.Entry("Ecdsa", TypeReference(FixedByteArray("Ecdsa", 65)))
                )
            )
        }
        val registry = TypeRegistry(
            v13Preset() + mapOf(
                "Address" to TypeReference(FixedByteArray("Address", if (ethereum) 20 else 32)),
                "ExtrinsicSignature" to TypeReference(signatureType)
            ),
            DynamicTypeResolver.defaultCompoundResolver()
        )
        val function = MetadataFunction("send", listOf(FunctionArgument("value", u8)), emptyList(), 1 to 0)
        val module = Module("XcmFixture", null, mapOf("send" to function), emptyMap(), emptyMap(), emptyMap(), BigInteger.ONE)
        return RuntimeSnapshot(
            registry,
            RuntimeMetadata(
                BigInteger.ONE,
                mapOf(module.name to module),
                ExtrinsicMetadata(
                    BigInteger.valueOf(4), listOf(SignedExtras.ERA, SignedExtras.NONCE, SignedExtras.TIP, SignedExtras.ASSET_TX_PAYMENT)
                )
            )
        )
    }
}
