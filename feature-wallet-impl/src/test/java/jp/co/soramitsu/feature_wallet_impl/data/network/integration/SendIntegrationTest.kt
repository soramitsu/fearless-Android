package jp.co.soramitsu.feature_wallet_impl.data.network.integration

import com.google.gson.Gson
import com.neovisionaries.ws.client.WebSocketFactory
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.KeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.Signer
import jp.co.soramitsu.fearless_utils.encrypt.model.Keypair
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.scale.invoke
import jp.co.soramitsu.fearless_utils.scale.toHexString
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.executeAsync
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.nonNull
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.pojo
import jp.co.soramitsu.fearless_utils.wsrpc.recovery.Reconnector
import jp.co.soramitsu.fearless_utils.wsrpc.request.RequestExecutor
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersionRequest
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.extrinsics.TransferRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.extrinsics.signExtrinsic
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.requests.FeeCalculationRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.requests.NextAccountIndexRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.response.RuntimeVersion
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.Signature
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.extrinsic.ExtrinsicPayloadValue
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.extrinsic.MultiAddress
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.extrinsic.SignedExtrinsicV28
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.extrinsic.SubmittableExtrinsicV28
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.extrinsic.TransferArgsV28
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.extrinsic.TransferCallV28
import kotlinx.coroutines.runBlocking
import org.bouncycastle.util.encoders.Hex
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal

private const val PUBLIC_KEY = "f65a7d560102f2019da9b9d8993f53f51cc38d50cdff3d0b8e71997d7f911ff1"
private const val PRIVATE_KEY = "ae4093af3c40f2ecc32c14d4dada9628a4a42b28ca1a5b200b89321cbc883182"
private const val TO_ADDRESS = "5DEwU2U97RnBHCpfwHMDfJC7pqAdfWaPFib9wiZcr2ephSfT"

private const val URL = "wss://westend-rpc.polkadot.io"

@RunWith(MockitoJUnitRunner::class)
@Ignore("Manual run only")
class SendIntegrationTest {
    private val sS58Encoder = SS58Encoder()
    private val signer = Signer()

    private val mapper = Gson()

    @Mock private lateinit var resourceManager: ResourceManager

    private lateinit var socketService: SocketService

    @Before
    fun setup() {
        given(resourceManager.getString(anyInt())).willReturn("Mock")

        socketService = SocketService(mapper, StdoutLogger(), WebSocketFactory(), Reconnector(), RequestExecutor())

        socketService.start(URL)
    }

    @After
    fun tearDown() {
        socketService.stop()
    }

    @Test
    fun `should perform transfer`() = runBlocking {
        val keyPair = Keypair(Hex.decode(PRIVATE_KEY), Hex.decode(PUBLIC_KEY))

        val submittableExtrinsic = generateExtrinsic(keyPair)

        val feeRequest = TransferRequest(submittableExtrinsic.toHexString())

        val result = socketService.executeAsync(feeRequest).result

        assert(result != null)
    }

    @Test
    fun `should calculate fee`() = runBlocking {
        val seed = ByteArray(32) { 1 }
        val keyPair = KeypairFactory().generate(EncryptionType.ECDSA, seed, "")

        val submittableExtrinsic = generateExtrinsic(keyPair)

        val feeRequest = FeeCalculationRequest(submittableExtrinsic.toHexString())

        val result = socketService.executeAsync(feeRequest).result

        assert(result != null)
    }

    private suspend fun generateExtrinsic(keypair: Keypair): EncodableStruct<SubmittableExtrinsicV28> {
        val accountId = Hex.decode(PUBLIC_KEY)

        val westendRuntime = Node.NetworkType.WESTEND.runtimeConfiguration

        val genesis = westendRuntime.genesisHash
        val addressByte = westendRuntime.addressByte
        val genesisBytes = Hex.decode(genesis)

        val transferAmount = BigDecimal("0.001").scaleByPowerOfTen(Token.Type.WND.mantissa)

        val runtimeInfo = socketService
            .executeAsync(RuntimeVersionRequest(), mapper = pojo<RuntimeVersion>().nonNull())

        val specVersion = runtimeInfo.specVersion
        val transactionVersion = runtimeInfo.transactionVersion

        val address = sS58Encoder.encode(accountId, addressByte)

        val nonce = socketService.executeAsync(NextAccountIndexRequest(address), mapper = pojo<Double>().nonNull())
        val nonceBigInt = nonce.toInt().toBigInteger()

        val receiverPublicKey = sS58Encoder.decode(TO_ADDRESS)

        val callStruct = TransferCallV28 { call ->
            call[TransferCallV28.callIndex] = Pair(4.toUByte(), 0.toUByte())

            call[TransferCallV28.args] = TransferArgsV28 { args ->
                args[TransferArgsV28.recipientId] = MultiAddress.Id(receiverPublicKey)
                args[TransferArgsV28.amount] = transferAmount.toBigIntegerExact()
            }
        }

        val callBytes = TransferCallV28.toByteArray(callStruct)

        val payload = ExtrinsicPayloadValue { payload ->
            payload[ExtrinsicPayloadValue.call] = callBytes
            payload[ExtrinsicPayloadValue.nonce] = nonceBigInt
            payload[ExtrinsicPayloadValue.specVersion] = specVersion.toUInt()
            payload[ExtrinsicPayloadValue.transactionVersion] = transactionVersion.toUInt()

            payload[ExtrinsicPayloadValue.genesis] = genesisBytes
            payload[ExtrinsicPayloadValue.blockHash] = genesisBytes
        }

        val signature = Signature(
            encryptionType = EncryptionType.ECDSA,
            value = signer.signExtrinsic(payload, keypair, EncryptionType.ECDSA)
        )

        val extrinsic = SignedExtrinsicV28 { extrinsic ->
            extrinsic[SignedExtrinsicV28.accountId] = MultiAddress.Id(accountId)
            extrinsic[SignedExtrinsicV28.signature] = signature
            extrinsic[SignedExtrinsicV28.nonce] = nonceBigInt
            extrinsic[SignedExtrinsicV28.call] = callStruct
        }

        val extrinsicBytes = SignedExtrinsicV28.toByteArray(extrinsic)
        val byteLength = extrinsicBytes.size.toBigInteger()

        return SubmittableExtrinsicV28 { struct ->
            struct[SubmittableExtrinsicV28.byteLength] = byteLength
            struct[SubmittableExtrinsicV28.signedExtrinsic] = extrinsic
        }
    }
}