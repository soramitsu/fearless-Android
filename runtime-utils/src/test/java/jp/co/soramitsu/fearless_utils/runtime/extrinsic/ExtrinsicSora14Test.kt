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
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Extrinsic
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersion
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigInteger

class ExtrinsicSora14Test {

    private val soraRuntime = RealRuntimeProvider.buildRuntime("sora2", "_v14")

    private val soraKeypair = BaseKeypair(
        privateKey = "fb981a1fe2aeb2c6f3eaf50af5ac2408b6f5ab4b04ae124aa4546d71014d6e0d".fromHex(),
        publicKey = "bcc5ecf679ebd776866a04c212a4ec5dc45cefab57d7aa858c389844e212693f".fromHex()
    )

    @Test
    fun `sora parse storage to get events for transfer extrinsic`() {
        val extrinsic =
            "0xe10284bcc5ecf679ebd776866a04c212a4ec5dc45cefab57d7aa858c389844e212693f0188d5209090f899c8745ce97fa7a0ac7a1dd4863a7cb78370e9f5d8b89d1c081543bb2c78d88eb05fc240b4e0238645f580d82ca7e5dba01a692c9461eda7398795033c00150102000400000000000000000000000000000000000000000000000000000000008c7eef30ac094c2b3ad9c1297dc1d2c8a2bb4d9085c7a7bff6f82cc4b9eae6420000f444829163450000000000000000"
        val encoded = Extrinsic.fromHex(soraRuntime, extrinsic)

        val address = (encoded.signature?.accountIdentifier as? ByteArray)?.toAddress(69)
        assertEquals("cnVkoGs3rEMqLqY27c2nfVXJRGdzNJk2ns78DcqtppaSRe8qm", address)
        assertEquals("Assets", encoded.call.module.name)
        assertEquals("transfer", encoded.call.function.name)
        assertEquals(3, encoded.call.arguments.size)
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
                "asset_id" to Struct.Instance(
                    mapOf("code" to asset.fromHex().toList().map { it.toInt().toBigInteger() })
                ),
                "to" to to.toAccountId(),
                "amount" to BigInteger("1000000000000000000")
            )
        )

        val encoded = builder.build()

        assertEquals(extrinsicInHex, encoded)
    }

    @Test
    fun `should build single sora swap extrinsic`() {
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
                "input_asset_id" to Struct.Instance(
                    mapOf("code" to asset.fromHex().toList().map { it.toInt().toBigInteger() })
                ),
                "output_asset_id" to Struct.Instance(
                    mapOf("code" to asset2.fromHex().toList().map { it.toInt().toBigInteger() })
                ),
                "swap_amount" to DictEnum.Entry(
                    "WithDesiredInput",
                    Struct.Instance(
                        mapOf(
                            "desired_amount_in" to BigInteger("1"),
                            "min_amount_out" to BigInteger("2")
                        )
                    )
                ),
                "selected_source_types" to listOf(DictEnum.Entry("XYKPool", null)),
                "filter_mode" to DictEnum.Entry(
                    name = "Disabled",
                    value = null
                )
            )
        )

        val encoded = builder.build()

        assertEquals(extrinsicInHex, encoded)
    }

    @Test
    fun `should build single sora swap smart market extrinsic`() {
        val from = "cnVkoGs3rEMqLqY27c2nfVXJRGdzNJk2ns78DcqtppaSRe8qm"
        val asset = "0200000000000000000000000000000000000000000000000000000000000000"
        val asset2 = "0200040000000000000000000000000000000000000000000000000000000000"
        val extrinsicInHex =
            "0x3d0384bcc5ecf679ebd776866a04c212a4ec5dc45cefab57d7aa858c389844e212693f00b40f20a6501d61cae0e014fd8e1298fe8c6627b0afe37f916dc3dc20257692b5bc0c21cb8c3a310e72e8fed5d016376f05bc5e0a399be29a6a0d306bd0e8a106250004001a0000000000020000000000000000000000000000000000000000000000000000000000000002000400000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000020000000000000000000000000000000000"

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
                "input_asset_id" to Struct.Instance(
                    mapOf("code" to asset.fromHex().toList().map { it.toInt().toBigInteger() })
                ),
                "output_asset_id" to Struct.Instance(
                    mapOf("code" to asset2.fromHex().toList().map { it.toInt().toBigInteger() })
                ),
                "swap_amount" to DictEnum.Entry(
                    "WithDesiredInput",
                    Struct.Instance(
                        mapOf(
                            "desired_amount_in" to BigInteger("1"),
                            "min_amount_out" to BigInteger("2")
                        )
                    )
                ),
                "selected_source_types" to emptyList<String>(),
                "filter_mode" to DictEnum.Entry(
                    name = "Disabled",
                    value = null
                )
            )
        )

        val encoded = builder.build()

        assertEquals(extrinsicInHex, encoded)
    }
}
