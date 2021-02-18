@file:Suppress("EXPERIMENTAL_API_USAGE")

package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain

import jp.co.soramitsu.common.data.network.runtime.binding.EventRecord
import jp.co.soramitsu.common.data.network.runtime.binding.ExtrinsicStatusEvent
import jp.co.soramitsu.common.data.network.runtime.binding.Phase
import jp.co.soramitsu.common.data.network.runtime.binding.bindExtrinsicStatusEventRecords
import jp.co.soramitsu.common.data.network.runtime.calls.SubstrateCalls
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.common.utils.preBinder
import jp.co.soramitsu.core.model.CryptoType
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.KeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.model.Keypair
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.executeAsync
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.nonNull
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.pojo
import jp.co.soramitsu.fearless_utils.wsrpc.request.DeliveryType
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.account.AccountInfoRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.GetStorageRequest
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.extrinsics.TransferRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.requests.FeeCalculationRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.requests.GetBlockRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.response.FeeResponse
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.response.SignedBlock
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.account.AccountInfoFactory
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.account.AccountInfoSchema
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.extrinsic.EncodeExtrinsicParams
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.extrinsic.TransferExtrinsicFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.util.encoders.Hex

class WssSubstrateSource(
    private val socketService: SocketService,
    private val keypairFactory: KeypairFactory,
    private val accountInfoFactory: AccountInfoFactory,
    private val extrinsicFactory: TransferExtrinsicFactory,
    private val runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
    private val substrateCalls: SubstrateCalls
) : SubstrateRemoteSource {

    override suspend fun fetchAccountInfo(
        address: String,
        networkType: Node.NetworkType
    ): EncodableStruct<AccountInfoSchema> {
        val publicKeyBytes = address.toAccountId()
        val request = AccountInfoRequest(publicKeyBytes)

        val response = socketService.executeAsync(request)
        val accountInfo = (response.result as? String)?.let { accountInfoFactory.decode(it) }

        return accountInfo ?: accountInfoFactory.createEmpty()
    }

    override suspend fun getTransferFee(account: Account, transfer: Transfer): FeeResponse {
        val keypair = generateFakeKeyPair(account)
        val extrinsic = buildSubmittableExtrinsic(account, transfer, keypair)

        val request = FeeCalculationRequest(extrinsic)

        return socketService.executeAsync(request, mapper = pojo<FeeResponse>().nonNull())
    }

    override suspend fun performTransfer(
        account: Account,
        transfer: Transfer,
        keypair: Keypair
    ): String {
        val extrinsic = buildSubmittableExtrinsic(account, transfer, keypair)
        val transferRequest = TransferRequest(extrinsic)

        return socketService.executeAsync(
            transferRequest,
            mapper = pojo<String>().nonNull(),
            deliveryType = DeliveryType.AT_MOST_ONCE
        )
    }

    override suspend fun fetchAccountTransfersInBlock(blockHash: String, account: Account): Result<List<TransferExtrinsicWithStatus>> = runCatching {
        val blockRequest = GetBlockRequest(blockHash)

        val block = socketService.executeAsync(blockRequest, mapper = pojo<SignedBlock>().nonNull())

        val runtime = runtimeProperty.get()

        val eventsKey = runtime.metadata.module("System").storage("Events").storageKey()
        val eventsRequest = GetStorageRequest(listOf(eventsKey, blockHash))

        val rawResponse = socketService.executeAsync(eventsRequest, mapper = preBinder())

        val statusesByExtrinsicId = bindExtrinsicStatusEventRecords(rawResponse, runtime)
            .filter { it.phase is Phase.ApplyExtrinsic }
            .associateBy { (it.phase as Phase.ApplyExtrinsic).extrinsicId.toInt() }

        filterAccountTransactions(account, block.block.extrinsics, statusesByExtrinsicId)
    }

    private suspend fun buildSubmittableExtrinsic(
        account: Account,
        transfer: Transfer,
        keypair: Keypair
    ): String = withContext(Dispatchers.Default) {
        val runtimeVersion = substrateCalls.getRuntimeVersion()
        val cryptoType = mapCryptoTypeToEncryption(account.cryptoType)
        val accountIdValue = account.address.toAccountId()

        val currentNonce = substrateCalls.getNonce(account)
        val genesis = account.network.type.runtimeConfiguration.genesisHash
        val genesisBytes = Hex.decode(genesis)

        val params = EncodeExtrinsicParams(
            senderId = accountIdValue,
            recipientId = transfer.recipient.toAccountId(),
            amountInPlanks = transfer.amountInPlanks,
            nonce = currentNonce,
            runtimeVersion = runtimeVersion,
            networkType = account.network.type,
            encryptionType = cryptoType,
            genesis = genesisBytes
        )

        extrinsicFactory.createEncodedExtrinsic(params, keypair)
    }

    private suspend fun generateFakeKeyPair(account: Account) = withContext(Dispatchers.Default) {
        val cryptoType = mapCryptoTypeToEncryption(account.cryptoType)
        val emptySeed = ByteArray(32) { 1 }

        keypairFactory.generate(cryptoType, emptySeed, "")
    }

    private fun mapCryptoTypeToEncryption(cryptoType: CryptoType): EncryptionType {
        return when (cryptoType) {
            CryptoType.SR25519 -> EncryptionType.SR25519
            CryptoType.ED25519 -> EncryptionType.ED25519
            CryptoType.ECDSA -> EncryptionType.ECDSA
        }
    }

    private suspend fun filterAccountTransactions(
        account: Account,
        extrinsics: List<String>,
        statuesByExtrinsicIndex: Map<Int, EventRecord<ExtrinsicStatusEvent>>
    ): List<TransferExtrinsicWithStatus> {
        return withContext(Dispatchers.Default) {
            val currentPublicKey = account.address.toAccountId()
            val transfersPalette = account.network.type.runtimeConfiguration.pallets.transfers

            extrinsics.mapIndexed { index, hex ->
                val transferExtrinsic = extrinsicFactory.decode(hex)

                transferExtrinsic?.let {
                    val status = statuesByExtrinsicIndex[index]?.event

                    TransferExtrinsicWithStatus(transferExtrinsic, status)
                }
            }
                .filterNotNull()
                .filter { transferWithStatus ->
                    val extrinsic = transferWithStatus.extrinsic

                    if (extrinsic.index !in transfersPalette) return@filter false

                    extrinsic.senderId.contentEquals(currentPublicKey) || extrinsic.recipientId.contentEquals(currentPublicKey)
                }
        }
    }
}