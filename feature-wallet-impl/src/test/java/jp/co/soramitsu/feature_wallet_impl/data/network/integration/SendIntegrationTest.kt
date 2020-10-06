package jp.co.soramitsu.feature_wallet_impl.data.network.integration

import com.google.gson.Gson
import jp.co.soramitsu.common.data.network.rpc.RxWebSocket
import jp.co.soramitsu.common.data.network.rpc.pojo
import jp.co.soramitsu.common.data.network.rpc.scale
import jp.co.soramitsu.common.data.network.rpc.scaleCollection
import jp.co.soramitsu.common.data.network.scale.EncodableStruct
import jp.co.soramitsu.common.data.network.scale.invoke
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.KeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.Signer
import jp.co.soramitsu.fearless_utils.encrypt.model.Keypair
import jp.co.soramitsu.fearless_utils.ss58.AddressType
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.account.AccountInfoRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.author.PendingExtrinsicsRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersionRequest
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.extrinsics.TransferRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.extrinsics.signExtrinsic
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.requests.FeeCalculationRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.model.response.RuntimeVersion
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountInfo
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountInfo.nonce
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.Call
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.Call.args
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.Call.callIndex
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.ExtrinsicPayloadValue
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.SignedExtrinsic
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.SubmittableExtrinsic
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.SubmittableExtrinsic.signedExtrinsic
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.TransferArgs
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.TransferArgs.amount
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.TransferArgs.recipientId
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.spongycastle.util.encoders.Hex
import java.math.BigDecimal

private const val PUBLIC_KEY = "fdc41550fb5186d71cae699c31731b3e1baa10680c7bd6b3831a6d222cf4d168"
private const val PRIVATE_KEY = "f3923eea431177cd21906d4308aea61c037055fb00575cae687217c6d8b2397f"
private const val TO_ADDRESS = "5CDayXd3cDCWpBkSXVsVfhE5bWKyTZdD3D1XUinR1ezS1sGn"

private const val URL = "wss://westend-rpc.polkadot.io"

@RunWith(MockitoJUnitRunner::class)
//@Ignore("Manual run only")
class SendIntegrationTest {
    private val sS58Encoder = SS58Encoder()
    private val signer = Signer()

    private val mapper = Gson()

    @Mock private lateinit var resourceManager: ResourceManager

    private lateinit var rxWebSocket: RxWebSocket

    @Before
    fun setup() {
        given(resourceManager.getString(anyInt())).willReturn("Mock")

        rxWebSocket = RxWebSocket(mapper, StdoutLogger(), URL, resourceManager)

        rxWebSocket.connect()
    }

    @After
    fun tearDown() {
        rxWebSocket.disconnect()
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
        val seed = ByteArray(32)
        val keyPair = KeypairFactory().generate(EncryptionType.ED25519, seed, "")

        val submittableExtrinsic = generateExtrinsic(keyPair)

        val feeRequest = FeeCalculationRequest(submittableExtrinsic)

        val result = rxWebSocket.executeRequest(feeRequest).blockingGet().result

        assert(result != null)
    }

    private fun generateExtrinsic(keypair: Keypair): EncodableStruct<SubmittableExtrinsic> {
        val accountId = Hex.decode(PUBLIC_KEY)

        val genesis = Node.NetworkType.WESTEND.genesisHash
        val genesisBytes = Hex.decode(genesis)

        val transferAmount = BigDecimal("0.007").scaleByPowerOfTen(Asset.Token.WND.mantissa)

        val runtimeInfo = rxWebSocket
            .executeRequest(RuntimeVersionRequest(), pojo<RuntimeVersion>())
            .blockingGet().result

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

        val signature = signer.signExtrinsic(payload, keypair, EncryptionType.ED25519)

        assert(signer.verifyEd25519(ExtrinsicPayloadValue.toByteArray(payload), signature, keypair.publicKey))

        val extrinsic = SignedExtrinsic { extrinsic ->
            extrinsic[SignedExtrinsic.accountId] = accountId

            extrinsic[SignedExtrinsic.signature] = signature
            extrinsic[SignedExtrinsic.signatureVersion] = 0.toUByte()
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