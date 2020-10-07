@file:Suppress("EXPERIMENTAL_API_USAGE")

package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain

import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.common.data.network.rpc.Mapped
import jp.co.soramitsu.common.data.network.rpc.RxWebSocket
import jp.co.soramitsu.common.data.network.rpc.RxWebSocketCreator
import jp.co.soramitsu.common.data.network.rpc.SocketSingleRequestExecutor
import jp.co.soramitsu.common.data.network.rpc.pojo
import jp.co.soramitsu.common.data.network.rpc.provideLifecycleFor
import jp.co.soramitsu.common.data.network.rpc.scale
import jp.co.soramitsu.common.data.network.rpc.scaleCollection
import jp.co.soramitsu.common.data.network.scale.EncodableStruct
import jp.co.soramitsu.common.data.network.scale.invoke
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.KeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.Signer
import jp.co.soramitsu.fearless_utils.encrypt.model.Keypair
import jp.co.soramitsu.fearless_utils.ss58.AddressType
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.account.AccountInfoRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.author.PendingExtrinsicsRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersionRequest
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.extrinsics.TransferRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.extrinsics.signExtrinsic
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.requests.FeeCalculationRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.model.response.FeeRemote
import jp.co.soramitsu.feature_wallet_impl.data.network.model.response.FeeResponse
import jp.co.soramitsu.feature_wallet_impl.data.network.model.response.RuntimeVersion
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountData
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountData.feeFrozen
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountData.free
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountData.miscFrozen
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountData.reserved
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountInfo
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountInfo.data
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountInfo.nonce
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountInfo.refCount
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.Call
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.ExtrinsicPayloadValue
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.SignedExtrinsic
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.SignedExtrinsic.accountId
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.SignedExtrinsic.call
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.SignedExtrinsic.signature
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.SignedExtrinsic.signatureVersion
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.SubmittableExtrinsic
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.SubmittableExtrinsic.byteLength
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.SubmittableExtrinsic.signedExtrinsic
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.TransferArgs
import org.spongycastle.util.encoders.Hex
import java.math.BigInteger

class WssSubstrateSource(
    private val socketRequestExecutor: SocketSingleRequestExecutor,
    private val rxWebSocketCreator: RxWebSocketCreator,
    private val signer: Signer,
    private val keypairFactory: KeypairFactory,
    private val sS58Encoder: SS58Encoder
) : SubstrateRemoteSource {

    override fun fetchAccountInfo(account: Account, node: Node): Single<EncodableStruct<AccountInfo>> {
        val socket = rxWebSocketCreator.createSocket(node.link)

        return socket.connect()
            .andThen(fetchAccountInfo(socket, account))
            .provideLifecycleFor(socket)
    }

    override fun getTransferFee(account: Account, node: Node, transfer: Transfer): Single<FeeResponse> {
        val socket = rxWebSocketCreator.createSocket(node.link)

        val calculateFee = Single.fromCallable {
            val cryptoType = mapCryptoTypeToEncryption(account.cryptoType)
            val emptySeed = ByteArray(32)
            val keypair = keypairFactory.generate(cryptoType, emptySeed, "")

            val (extrinsic, newAccountInfo) = buildSubmittableExtrinsic(account, transfer, keypair, socket)

            val request = FeeCalculationRequest(extrinsic)

            val resultHolder = socket.executeRequest(request, pojo<FeeRemote>()).blockingGet()

            FeeResponse(resultHolder.result, newAccountInfo)
        }

        return socket.connect()
            .andThen(calculateFee)
            .provideLifecycleFor(socket)
    }

    override fun performTransfer(
        account: Account,
        node: Node,
        transfer: Transfer,
        keypair: Keypair
    ): Completable {
        val socket = rxWebSocketCreator.createSocket(node.link)

        val performTransfer = Single.fromCallable {
            val (extrinsic, accountInfo) = buildSubmittableExtrinsic(account, transfer, keypair, socket)

            TransferRequest(extrinsic)
        }
            .flatMap { socket.executeRequest(it) }
            .doOnSuccess { if (it.result == null) throw BlockChainException() }
            .ignoreElement()

        return socket.connect()
            .andThen(performTransfer)
            .provideLifecycleFor(socket)
    }

    private fun buildSubmittableExtrinsic(
        account: Account,
        transfer: Transfer,
        keypair: Keypair,
        socket: RxWebSocket
    ): Pair<EncodableStruct<SubmittableExtrinsic>, EncodableStruct<AccountInfo>> {
        val cryptoType = mapCryptoTypeToEncryption(account.cryptoType)
        val accountIdValue = Hex.decode(account.publicKey)

        val runtimeInfo = getRuntimeVersion(socket).blockingGet().result
        val (currentNonce, newAccountInfo) = getNonce(socket, account).blockingGet()

        val genesis = account.network.type.genesisHash
        val genesisBytes = Hex.decode(genesis)

        val callStruct = createTransferCall(account.network.type, transfer.recipient, transfer.amountInPlanks)

        val payload = ExtrinsicPayloadValue { payload ->
            payload[ExtrinsicPayloadValue.call] = callStruct
            payload[ExtrinsicPayloadValue.nonce] = currentNonce
            payload[ExtrinsicPayloadValue.specVersion] = runtimeInfo.specVersion.toUInt()
            payload[ExtrinsicPayloadValue.transactionVersion] = runtimeInfo.transactionVersion.toUInt()

            payload[ExtrinsicPayloadValue.genesis] = genesisBytes
            payload[ExtrinsicPayloadValue.blockHash] = genesisBytes
        }

        val signatureValue = signer.signExtrinsic(payload, keypair, cryptoType)

        val extrinsic = SignedExtrinsic { extrinsic ->
            extrinsic[accountId] = accountIdValue
            extrinsic[signature] = signatureValue
            extrinsic[signatureVersion] = cryptoType.signatureVersion.toUByte()
            extrinsic[SignedExtrinsic.nonce] = currentNonce
            extrinsic[call] = callStruct
        }

        val extrinsicBytes = SignedExtrinsic.toByteArray(extrinsic)
        val byteLengthValue = extrinsicBytes.size.toBigInteger()

        val submittableExtrinsic = SubmittableExtrinsic { struct ->
            struct[byteLength] = byteLengthValue
            struct[signedExtrinsic] = extrinsic
        }

        return submittableExtrinsic to newAccountInfo
    }

    private fun fetchAccountInfo(rxWebSocket: RxWebSocket, account: Account): Single<EncodableStruct<AccountInfo>> {
        val publicKey = account.publicKey

        val publicKeyBytes = Hex.decode(publicKey)
        val request = AccountInfoRequest(publicKeyBytes)

        return rxWebSocket.executeRequest(request, scale(AccountInfo))
            .map { response -> response.result ?: emptyAccountInfo() }
    }

    private fun createTransferCall(networkType: Node.NetworkType, recipientAddress: String, amount: BigInteger): EncodableStruct<Call> {
        val addressType = mapNetworkTypeToAddressType(networkType)

        return Call { call ->
            call[Call.callIndex] = Pair(4.toUByte(), 0.toUByte())

            call[Call.args] = TransferArgs { args ->
                args[TransferArgs.recipientId] = sS58Encoder.decode(recipientAddress, addressType)
                args[TransferArgs.amount] = amount
            }
        }
    }

    private fun getNonce(socket: RxWebSocket, account: Account): Single<Pair<BigInteger, EncodableStruct<AccountInfo>>> {
        return Single.fromCallable {
            val accountInfo = fetchAccountInfo(socket, account).blockingGet()
            val accountNonce = accountInfo[nonce]

            val pendingExtrinsics = getPendingExtrinsicsCount(socket, account).blockingGet()

            val result = accountNonce + pendingExtrinsics.toUInt()

            result.toLong().toBigInteger() to accountInfo
        }
    }

    private fun getPendingExtrinsicsCount(socket: RxWebSocket, account: Account): Single<Int> {
        val request = PendingExtrinsicsRequest()

        return socket.executeRequest(request, scaleCollection(SubmittableExtrinsic))
            .map { it.result ?: throw IllegalArgumentException("Result is null") }
            .map { countUserExtrinsics(account, it) }
    }

    private fun countUserExtrinsics(account: Account, list: List<EncodableStruct<SubmittableExtrinsic>>): Int {
        val publicKeyBytes = Hex.decode(account.publicKey)

        return list.count { it[signedExtrinsic][accountId].contentEquals(publicKeyBytes) }
    }

    private fun getRuntimeVersion(socket: RxWebSocket): Single<Mapped<RuntimeVersion>> {
        val request = RuntimeVersionRequest()

        return socket.executeRequest(request, pojo())
    }

    private fun emptyAccountInfo() = AccountInfo { info ->
        info[nonce] = 0.toUInt()
        info[refCount] = 0.toUByte()

        info[data] = AccountData { data ->
            data[free] = 0.toBigInteger()
            data[reserved] = 0.toBigInteger()
            data[miscFrozen] = 0.toBigInteger()
            data[feeFrozen] = 0.toBigInteger()
        }
    }

    private fun mapCryptoTypeToEncryption(cryptoType: CryptoType): EncryptionType {
        return when (cryptoType) {
            CryptoType.SR25519 -> EncryptionType.SR25519
            CryptoType.ED25519 -> EncryptionType.ED25519
            CryptoType.ECDSA -> EncryptionType.ECDSA
        }
    }

    private fun mapNetworkTypeToAddressType(networkType: Node.NetworkType): AddressType {
        return when (networkType) {
            Node.NetworkType.KUSAMA -> AddressType.KUSAMA
            Node.NetworkType.POLKADOT -> AddressType.POLKADOT
            Node.NetworkType.WESTEND -> AddressType.WESTEND
        }
    }
}