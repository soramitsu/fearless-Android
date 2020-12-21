package jp.co.soramitsu.feature_wallet_impl.data.network.integration

import com.google.gson.Gson
import com.neovisionaries.ws.client.WebSocketFactory
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.scale.invoke
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.KeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.Signer
import jp.co.soramitsu.fearless_utils.encrypt.model.Keypair
import jp.co.soramitsu.fearless_utils.ss58.AddressType
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.nonNull
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.pojo
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.scale
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.scaleCollection
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.account.AccountInfoRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.author.PendingExtrinsicsRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersionRequest
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.extrinsics.TransferRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.extrinsics.signExtrinsic
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.requests.FeeCalculationRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.requests.NextAccountIndexRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.response.RuntimeVersion
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.AccountInfo
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.AccountInfo.nonce
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.Call
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.Call.args
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.Call.callIndex
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.ExtrinsicPayloadValue
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.Signature
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.SignedExtrinsic
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.SubmittableExtrinsic
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.SubmittableExtrinsic.signedExtrinsic
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.TransferArgs
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.TransferArgs.amount
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.TransferArgs.recipientId
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
import java.math.BigInteger

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

    private lateinit var rxWebSocket: SocketService

    @Before
    fun setup() {
        given(resourceManager.getString(anyInt())).willReturn("Mock")

        rxWebSocket = SocketService(mapper, StdoutLogger(), WebSocketFactory())

        rxWebSocket.start(URL)
    }

    @After
    fun tearDown() {
        rxWebSocket.stop()
    }

    @Test
    fun `should perform transfer`() {
        val keyPair = Keypair(Hex.decode(PRIVATE_KEY), Hex.decode(PUBLIC_KEY))

        val submittableExtrinsic = generateExtrinsic(keyPair)

        val feeRequest = TransferRequest(submittableExtrinsic)

        val result = rxWebSocket.executeRequest(feeRequest).blockingGet().result

        assert(result != null)
    }

    @Test
    fun `should calculate fee`() {
        val seed = ByteArray(32) { 1 }
        val keyPair = KeypairFactory().generate(EncryptionType.ECDSA, seed, "")

        val submittableExtrinsic = generateExtrinsic(keyPair)

        val feeRequest = FeeCalculationRequest(submittableExtrinsic)

        val result = rxWebSocket.executeRequest(feeRequest).blockingGet().result

        assert(result != null)
    }

    private fun generateExtrinsic(keypair: Keypair): EncodableStruct<SubmittableExtrinsic> {
        val accountId = Hex.decode(PUBLIC_KEY)

        val genesis = Node.NetworkType.WESTEND.runtimeConfiguration.genesisHash
        val genesisBytes = Hex.decode(genesis)

        val transferAmount = BigDecimal("0.001").scaleByPowerOfTen(Token.Type.WND.mantissa)

        val runtimeInfo = rxWebSocket
            .executeRequest(RuntimeVersionRequest(), pojo<RuntimeVersion>().nonNull())
            .blockingGet()

        val specVersion = runtimeInfo.specVersion
        val transactionVersion = runtimeInfo.transactionVersion

        val pendingExtrinsics = rxWebSocket
            .executeRequest(PendingExtrinsicsRequest(), scaleCollection(SubmittableExtrinsic))
            .blockingGet().result!!

        val pendingForCurrent = pendingExtrinsics.count { it[signedExtrinsic][SignedExtrinsic.accountId].contentEquals(accountId) }

        val accountInfo = rxWebSocket
            .executeRequest(AccountInfoRequest(accountId), scale(AccountInfo))
            .blockingGet().result!!

        val nonce = accountInfo[nonce] + pendingForCurrent.toUInt()
        val nonceBigInt = nonce.toLong().toBigInteger()

        val receiverPublicKey = sS58Encoder.decode(TO_ADDRESS, AddressType.WESTEND)

        val callStruct = Call { call ->
            call[callIndex] = Pair(4.toUByte(), 0.toUByte())

            call[args] = TransferArgs { args ->
                args[recipientId] = receiverPublicKey
                args[amount] = transferAmount.toBigIntegerExact()
            }
        }

        val payload = ExtrinsicPayloadValue { payload ->
            payload[ExtrinsicPayloadValue.call] = callStruct
            payload[ExtrinsicPayloadValue.nonce] = nonceBigInt
            payload[ExtrinsicPayloadValue.specVersion] = specVersion.toUInt()
            payload[ExtrinsicPayloadValue.transactionVersion] = transactionVersion.toUInt()

            payload[ExtrinsicPayloadValue.genesis] = genesisBytes
            payload[ExtrinsicPayloadValue.blockHash] = genesisBytes
        }

        val signature = Signature(
            encryptionType = EncryptionType.ECDSA,
            value =  signer.signExtrinsic(payload, keypair, EncryptionType.ECDSA)
        )

        val extrinsic = SignedExtrinsic { extrinsic ->
            extrinsic[SignedExtrinsic.accountId] = accountId
            extrinsic[SignedExtrinsic.signature] = signature
            extrinsic[SignedExtrinsic.nonce] = nonceBigInt
            extrinsic[SignedExtrinsic.call] = callStruct
        }

        val extrinsicBytes = SignedExtrinsic.toByteArray(extrinsic)
        val byteLength = extrinsicBytes.size.toBigInteger()

        return SubmittableExtrinsic { struct ->
            struct[SubmittableExtrinsic.byteLength] = byteLength
            struct[signedExtrinsic] = extrinsic
        }
    }
}