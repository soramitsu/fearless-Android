package jp.co.soramitsu.fearless_utils.runtime.extrinsic

import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.keypair.BaseKeypair
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.runtime.RealRuntimeProvider
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Era
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.multiAddressFromId
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersion
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigInteger

private val KEYPAIR = BaseKeypair(
    publicKey = "fdc41550fb5186d71cae699c31731b3e1baa10680c7bd6b3831a6d222cf4d168".fromHex(),
    privateKey = "f3923eea431177cd21906d4308aea61c037055fb00575cae687217c6d8b2397f".fromHex()
)

class ExtrinsicBuilderTestV14 {

    val runtime = RealRuntimeProvider.buildRuntime("westend", "_v14")

    @Test
    fun `polkatrain test`() {
        val pr = RealRuntimeProvider.buildRuntime("polkatrain", "")
        val eventsType = runtime.metadata.module("System").storage("Events").type.value!!
        val hex = "0x1400000000000000b0338609000000000200000001000000000080b2e60e000000000200000002000000130674489913000000000000000000000000000002000000050474dbd6b95909e4ab120120ba7cf48c28e5008137a41f2b9b3d31e5a65c49e8381d52e6040000000000000000000000000000020000000000301b0f0000000000000000"
        val decoded = eventsType.fromHex(pr, hex) as List<*>
        assertEquals(5, decoded.size)
    }

    @Test
    fun `should build single transfer extrinsic`() {
        val extrinsicInHex =
            "0x41028400fdc41550fb5186d71cae699c31731b3e1baa10680c7bd6b3831a6d222cf4d16800080bfe8bc67f44b498239887dc5679523cfcb1d20fd9ec9d6bae0a385cca118d2cb7ef9f4674d52a810feb32932d7c6fe3e05ce9e06cd72cf499c8692206410ab5038800040000340a806419d5e278172e45cb0e50da1b031795366c99ddfe0a680bd53b142c630700e40b5402"

        val builder = ExtrinsicBuilder(
            runtime = runtime,
            keypair = KEYPAIR,
            nonce = 34.toBigInteger(),
            runtimeVersion = RuntimeVersion(48, 4),
            genesisHash = "e143f23803ac50e8f6f8e62695d1ce9e4e1d68aa36c1cd2cfd15340213f3423e".fromHex(),
            multiChainEncryption = MultiChainEncryption.Substrate(EncryptionType.ED25519),
            accountIdentifier = multiAddressFromId(KEYPAIR.publicKey),
            era = Era.Mortal(64, 59),
            blockHash = "0x1b876104c68b4a8924c098d61d2ad798761bb6fff55cca2885939ffc27ef5ecb".fromHex()
        )

        builder.transfer(
            recipientAccountId = "340a806419d5e278172e45cb0e50da1b031795366c99ddfe0a680bd53b142c63".fromHex(),
            amount = BigInteger("10000000000")
        )

        val encoded = builder.build()

        assertEquals(extrinsicInHex, encoded)
    }

    @Test
    fun `polkadot v14 test`() {
        val runtime = RealRuntimeProvider.buildRuntime("polkadot", "_v14")
        val storageType = runtime.metadata.module("Crowdloan").storage("Funds").type.value!!
        val hex = "0x3a2d163712bfa3a894b26009f3e8c092d5b99da1375a06f4036e93eadcebcc65010116732d1a045c9351606743bf786aad1db344e5dd51e15d6417deb3828044080e005039278c04000000000000000000003cec986eae72f6040000000000000000ffcd7c00000064a7b3b6e00d000000000000000002b7407700060000000d00000002000000"
        val decoded = storageType.fromHex(runtime, hex)!!
        assertEquals(true, storageType is Struct)
    }

    @Test
    fun `should build single transfer extrinsic statemine`() {
        val curruntime = RealRuntimeProvider.buildRuntime("statemine", "_v14")
        val extrinsicInHex =
            "0x45028400fdc41550fb5186d71cae699c31731b3e1baa10680c7bd6b3831a6d222cf4d1680045ba1f9d291fff7dddf36f7ec060405d5e87ac8fab8832cfcc66858e6975141748ce89c41bda6c3a84204d3c6f929b928702168ca38bbed69b172044b599a10ab5038800000a0000bcc5ecf679ebd776866a04c212a4ec5dc45cefab57d7aa858c389844e212693f0700e40b5402"

        val builder = ExtrinsicBuilder(
            runtime = curruntime,
            keypair = KEYPAIR,
            nonce = 34.toBigInteger(),
            runtimeVersion = RuntimeVersion(601, 4),
            genesisHash = "48239ef607d7928874027a43a67689209727dfb3d3dc5e5b03a39bdc2eda771a".fromHex(),
            multiChainEncryption = MultiChainEncryption.Substrate(EncryptionType.ED25519),
            accountIdentifier = multiAddressFromId(KEYPAIR.publicKey),
            era = Era.Mortal(64, 59),
            blockHash = "0xdd7532c5c01242696001e57cded1bc1326379059300287552a9c344e5bea1070".fromHex()
        )

        builder.transfer(
            recipientAccountId = "GqqKJJZ1MtiWiC6CzNg3g8bawriq6HZioHW1NEpxdf6Q6P5".toAccountId(),
            amount = BigInteger("10000000000")
        )

        val encoded = builder.build()

        assertEquals(extrinsicInHex, encoded)
    }

    @Test
    fun `should build batch extrinsic`() {

        val extrinsicInHex =
            "0xf1028400fdc41550fb5186d71cae699c31731b3e1baa10680c7bd6b3831a6d222cf4d168005b94d4436372ba74895936695e97d543358219e77f3e827f77b2e26f53413363a5dd098e172a51308e7d35aa6c03c5f171c4b43732db61c3d86b62d83e626b07b5038800100008040000340a806419d5e278172e45cb0e50da1b031795366c99ddfe0a680bd53b142c630700e40b5402040000340a806419d5e278172e45cb0e50da1b031795366c99ddfe0a680bd53b142c630700e40b5402"

        val builder = ExtrinsicBuilder(
            runtime = runtime,
            keypair = KEYPAIR,
            nonce = 34.toBigInteger(),
            runtimeVersion = RuntimeVersion(48, 4),
            genesisHash = "e143f23803ac50e8f6f8e62695d1ce9e4e1d68aa36c1cd2cfd15340213f3423e".fromHex(),
            multiChainEncryption = MultiChainEncryption.Substrate(EncryptionType.ED25519),
            accountIdentifier = multiAddressFromId(KEYPAIR.publicKey),
            era = Era.Mortal(64, 59),
            blockHash = "0x1b876104c68b4a8924c098d61d2ad798761bb6fff55cca2885939ffc27ef5ecb".fromHex()
        )

        repeat(2) {
            builder.transfer(
                recipientAccountId = "340a806419d5e278172e45cb0e50da1b031795366c99ddfe0a680bd53b142c63".fromHex(),
                amount = BigInteger("10000000000")
            )
        }

        val encoded = builder.build()

        assertEquals(extrinsicInHex, encoded)
    }
}