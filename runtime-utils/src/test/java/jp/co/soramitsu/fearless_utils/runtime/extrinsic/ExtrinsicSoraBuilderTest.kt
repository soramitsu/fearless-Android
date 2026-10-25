package jp.co.soramitsu.fearless_utils.runtime.extrinsic

import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.keypair.BaseKeypair
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.runtime.RealRuntimeProvider
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Era
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersion
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigInteger

class ExtrinsicSoraBuilderTest {

    private val soraRuntime = RealRuntimeProvider.buildRuntime("sora2")

    private val soraKeypair = BaseKeypair(
        privateKey = "fb981a1fe2aeb2c6f3eaf50af5ac2408b6f5ab4b04ae124aa4546d71014d6e0d".fromHex(),
        publicKey = "bcc5ecf679ebd776866a04c212a4ec5dc45cefab57d7aa858c389844e212693f".fromHex()
    )

    @Test
    fun `sora parse storage to get events for transfer extrinsic`(){
        val storage = "0x28000000000000005095a2090000000002000000010000000202bcc5ecf679ebd776866a04c212a4ec5dc45cefab57d7aa858c389844e212693f84bdc405d139399bba3ccea5d3de23316c9deeab661f57e2f4d1720cc6649859000064a7b3b6e00d000000000000000000000100000013000200000000000000000000000000000000000000000000000000000000000000bcc5ecf679ebd776866a04c212a4ec5dc45cefab57d7aa858c389844e212693f84bdc405d139399bba3ccea5d3de23316c9deeab661f57e2f4d1720cc6649859000064a7b3b6e00d00000000000000000000010000001501bcc5ecf679ebd776866a04c212a4ec5dc45cefab57d7aa858c389844e212693f84bdc405d139399bba3ccea5d3de23316c9deeab661f57e2f4d1720cc66498590200000000000000000000000000000000000000000000000000000000000000000064a7b3b6e00d00000000000000000000010000000900bcc5ecf679ebd776866a04c212a4ec5dc45cefab57d7aa858c389844e212693f00c05773a57c0200000000000000000000000100000000001804b7040000000000000000020000000000f01385a4040000000000000003000000020654734f90f971a02c609b2d684e61b557cb8494021fd01de35d3cae2f0670cb9c000064a7b3b6e00d00000000000000000000030000001f014795f18c566f19aafc780d956fc16fe4a15df28b4c552abdda187ca372075b070000030000000000f01385a404000000000000"
        val eventType =
            soraRuntime.metadata.module("System").storage("Events").type.value!!
        val eventsRaw = eventType.fromHex(soraRuntime, storage)
        assertEquals(10, (eventsRaw as List<*>).size)
    }

    @Test
    fun `should build single sora transfer extrinsic`() {
        val from = "cnVkoGs3rEMqLqY27c2nfVXJRGdzNJk2ns78DcqtppaSRe8qm"
        val to = "cnUVLAjzRsrXrzEiqjxMpBwvb6YgdBy8DKibonvZgtcQY5ZKe"
        val asset = "0200000000000000000000000000000000000000000000000000000000000000"
        val extrinsicInHex =
            "0xe10284bcc5ecf679ebd776866a04c212a4ec5dc45cefab57d7aa858c389844e212693f0002776ae977d3d32ca34b3d38455f634a9596094726b5bc09b2ce873cd5e5c075009c8644b00e3f45e8224239833425325080a7e869261c78ffdb8e567e4b61029502d4001501020000000000000000000000000000000000000000000000000000000000000084bdc405d139399bba3ccea5d3de23316c9deeab661f57e2f4d1720cc6649859000064a7b3b6e00d0000000000000000"

        val builder = ExtrinsicBuilder(
            runtime = soraRuntime,
            keypair = soraKeypair,
            nonce = 53.toBigInteger(),
            runtimeVersion = RuntimeVersion(32, 32),
            genesisHash = "20461f752519ab163c3b0daac99fd8a27b5157c607666712641b1d510bee04f3".fromHex(),
            multiChainEncryption = MultiChainEncryption.Substrate(EncryptionType.ED25519),
            accountIdentifier = from.toAccountId(),
            era = Era.getEraFromBlockPeriod(12905, 64),
            blockHash = "0x45af4531f27b22ec5e46cd30ae7c7dc14ef1b2af7952389d3e4f0dfe7658fb91".fromHex()
        )

        builder.call(
            "Assets",
            "transfer",
            mapOf(
                "asset_id" to asset.fromHex(),
                "to" to to.toAccountId(),
                "amount" to BigInteger("1000000000000000000")
            )
        )

        val encoded = builder.build()

        assertEquals(extrinsicInHex, encoded)
    }

    @Test
    fun `sora swap extrinsic`() {
        val from = "cnVkoGs3rEMqLqY27c2nfVXJRGdzNJk2ns78DcqtppaSRe8qm"
        val asset = "0200000000000000000000000000000000000000000000000000000000000000"
        val asset2 = "0200040000000000000000000000000000000000000000000000000000000000"
        val extrinsicInHex =
            "0x410384bcc5ecf679ebd776866a04c212a4ec5dc45cefab57d7aa858c389844e212693f0052435b29ae1d0a1306afc9ea6d0946748ec1dfb253292dab3e29f969295f23a6a2a87a2688379e69316674aba139a060b5199717f1bd7840ca4cefc8b8d21409250004001a000000000002000000000000000000000000000000000000000000000000000000000000000200040000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000002000000000000000000000000000000040000"

        val builder = ExtrinsicBuilder(
            runtime = soraRuntime,
            keypair = soraKeypair,
            nonce = 1.toBigInteger(),
            runtimeVersion = RuntimeVersion(1, 1),
            genesisHash = "0f751ca2d30efe3385a4001d0bfa1548471babf5095f6fe88ee4813cf724fafc".fromHex(),
            multiChainEncryption = MultiChainEncryption.Substrate(EncryptionType.ED25519),
            accountIdentifier = from.toAccountId(),
            era = Era.getEraFromBlockPeriod(44866, 64),
            blockHash = "0xa532ea14451c9b4e1a9ed1c75ab67d8be659362c9d8f2206009ae8d62faf9fca".fromHex()
        )

        builder.call(
            "LiquidityProxy",
            "swap",
            mapOf(
                "dex_id" to BigInteger("0"),
                "input_asset_id" to asset.fromHex(),
                "output_asset_id" to asset2.fromHex(),
                "swap_amount" to DictEnum.Entry("WithDesiredInput", Struct.Instance(mapOf("desired_amount_in" to BigInteger("1"), "min_amount_out" to BigInteger("2")))),
                "selected_source_types" to listOf("XYKPool"),
                "filter_mode" to "Disabled"
            )
        )

        val encoded = builder.build()

        assertEquals(extrinsicInHex, encoded)
    }
}