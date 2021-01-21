@file:Suppress("EXPERIMENTAL_API_USAGE")

package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain

import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.KeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.Signer
import jp.co.soramitsu.fearless_utils.encrypt.model.Keypair
import jp.co.soramitsu.fearless_utils.runtime.Module
import jp.co.soramitsu.fearless_utils.runtime.storageKey
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.scale.invoke
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.executeAsync
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.nonNull
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.pojo
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.scale
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.string
import jp.co.soramitsu.fearless_utils.wsrpc.request.DeliveryType
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.account.AccountInfoRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersionRequest
import jp.co.soramitsu.fearless_utils.wsrpc.subscription.response.SubscriptionChange
import jp.co.soramitsu.fearless_utils.wsrpc.subscriptionFlow
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.extrinsics.TransferRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.extrinsics.signExtrinsic
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.requests.FeeCalculationRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.requests.GetBlockRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.requests.GetStorageRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.requests.NextAccountIndexRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.requests.SubscribeStorageRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.response.BalanceChange
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.response.FeeResponse
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.response.RuntimeVersion
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.response.SignedBlock
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.AccountData
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.AccountData.feeFrozen
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.AccountData.free
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.AccountData.miscFrozen
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.AccountData.reserved
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.AccountId
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.AccountInfo
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.AccountInfo.data
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.AccountInfo.nonce
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.AccountInfo.refCount
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.ActiveEraInfo
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.Call
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.Call.args
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.Call.callIndex
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.ExtrinsicPayloadValue
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.Signature
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.SignedExtrinsic
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.SignedExtrinsic.accountId
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.SignedExtrinsic.call
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.SignedExtrinsic.signature
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.StakingLedger
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.SubmittableExtrinsic
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.SubmittableExtrinsic.byteLength
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.SubmittableExtrinsic.signedExtrinsic
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.TransferArgs
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.TransferArgs.recipientId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.bouncycastle.util.encoders.Hex
import java.math.BigInteger

class WssSubstrateSource(
    private val socketService: SocketService,
    private val signer: Signer,
    private val keypairFactory: KeypairFactory,
    private val sS58Encoder: SS58Encoder
) : SubstrateRemoteSource {

    override suspend fun fetchAccountInfo(
        address: String,
        networkType: Node.NetworkType
    ): EncodableStruct<AccountInfo> {
        val publicKeyBytes = getAccountId(address)
        val request = AccountInfoRequest(publicKeyBytes)

        val response = socketService.executeAsync(request, mapper = scale(AccountInfo))

        return response.result ?: emptyAccountInfo()
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
            mapper = string().nonNull(),
            deliveryType = DeliveryType.AT_MOST_ONCE
        )
    }

    override suspend fun listenForAccountUpdates(address: String): Flow<BalanceChange> {
        val key = Module.System.Account.storageKey(getAccountId(address))
        val request = SubscribeStorageRequest(key)

        return socketService.subscriptionFlow(request)
            .map(::buildBalanceChange)
    }

    override suspend fun fetchAccountTransactionInBlock(blockHash: String, account: Account): List<EncodableStruct<SubmittableExtrinsic>> {
        val request = GetBlockRequest(blockHash)

        val block = socketService.executeAsync(request, mapper = pojo<SignedBlock>().nonNull())

        return filterAccountTransactions(account, block.block.extrinsics)
    }

    override suspend fun listenStakingLedger(stashAddress: String): Flow<EncodableStruct<StakingLedger>> {
        val key = Module.Staking.Bonded.storageKey(getAccountId(stashAddress))
        val request = SubscribeStorageRequest(key)

        return socketService.subscriptionFlow(request)
            .map { it.params.result.getSingleChange() }
            .distinctUntilChanged()
            .flatMapLatest { change ->
                val controllerId = change.value

                if (controllerId != null) {
                    subscribeToLedger(stashAddress, controllerId)
                } else {
                    flowOf(createEmptyLedger(stashAddress))
                }
            }
    }

    override suspend fun getActiveEra(): EncodableStruct<ActiveEraInfo> {
        val key = Module.Staking.ActiveEra.storageKey()
        val request = GetStorageRequest(key)

        return socketService.executeAsync(request, mapper = scale(ActiveEraInfo).nonNull())
    }

    private fun subscribeToLedger(stashAddress: String, controllerId: String): Flow<EncodableStruct<StakingLedger>> {
        val accountId = AccountId.read(controllerId)
        val bytes = AccountId.toByteArray(accountId)

        val key = Module.Staking.Ledger.storageKey(bytes)
        val request = SubscribeStorageRequest(key)

        return socketService.subscriptionFlow(request)
            .map { it.params.result.getSingleChange() }
            .map { change ->
                if (change.value.isNullOrBlank()) {
                    createEmptyLedger(stashAddress)
                } else {
                    StakingLedger.read(change.value!!)
                }
            }
    }

    private fun createEmptyLedger(address: String): EncodableStruct<StakingLedger> {
        return StakingLedger { ledger ->
            ledger[StakingLedger.stash] = sS58Encoder.decode(address)
            ledger[StakingLedger.active] = BigInteger.ZERO
            ledger[StakingLedger.claimedRewards] = emptyList()
            ledger[StakingLedger.total] = BigInteger.ZERO
            ledger[StakingLedger.unlocking] = emptyList()
        }
    }

    private fun buildBalanceChange(subscriptionChange: SubscriptionChange): BalanceChange {
        val block = subscriptionChange.params.result.block

        val change = subscriptionChange.params.result.getSingleChange()

        val accountInfo = if (change.value != null) AccountInfo.read(change.value!!) else emptyAccountInfo()

        return BalanceChange(block, accountInfo)
    }

    private suspend fun getAccountId(address: String) = withContext(Dispatchers.Default) {
        sS58Encoder.decode(address)
    }

    private suspend fun buildSubmittableExtrinsic(
        account: Account,
        transfer: Transfer,
        keypair: Keypair
    ): EncodableStruct<SubmittableExtrinsic> = withContext(Dispatchers.Default) {
        val runtimeInfo = getRuntimeVersion()
        val cryptoType = mapCryptoTypeToEncryption(account.cryptoType)
        val accountIdValue = getAccountId(account.address)

        val currentNonce = getNonce(account)
        val genesis = account.network.type.runtimeConfiguration.genesisHash
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

        val signatureValue = Signature(
            encryptionType = cryptoType,
            value = signer.signExtrinsic(payload, keypair, cryptoType)
        )

        val extrinsic = SignedExtrinsic { extrinsic ->
            extrinsic[accountId] = accountIdValue
            extrinsic[signature] = signatureValue
            extrinsic[SignedExtrinsic.nonce] = currentNonce
            extrinsic[call] = callStruct
        }

        val extrinsicBytes = SignedExtrinsic.toByteArray(extrinsic)
        val byteLengthValue = extrinsicBytes.size.toBigInteger()

        val submittableExtrinsic = SubmittableExtrinsic { struct ->
            struct[byteLength] = byteLengthValue
            struct[signedExtrinsic] = extrinsic
        }

        submittableExtrinsic
    }

    private fun createTransferCall(
        networkType: Node.NetworkType,
        recipientAddress: String,
        amount: BigInteger
    ): EncodableStruct<Call> {
        return Call { call ->
            call[Call.callIndex] = networkType.runtimeConfiguration.pallets.transfers.transfer.index

            call[Call.args] = TransferArgs { args ->
                args[TransferArgs.recipientId] = sS58Encoder.decode(recipientAddress)
                args[TransferArgs.amount] = amount
            }
        }
    }

    private suspend fun getNonce(account: Account): BigInteger {
        val nonceRequest = NextAccountIndexRequest(account.address)

        val response = socketService.executeAsync(nonceRequest)
        val doubleResult = response.result as Double

        return doubleResult.toInt().toBigInteger()
    }

    private suspend fun generateFakeKeyPair(account: Account) = withContext(Dispatchers.Default) {
        val cryptoType = mapCryptoTypeToEncryption(account.cryptoType)
        val emptySeed = ByteArray(32) { 1 }

        keypairFactory.generate(cryptoType, emptySeed, "")
    }

    private suspend fun getRuntimeVersion(): RuntimeVersion {
        val request = RuntimeVersionRequest()

        return socketService.executeAsync(request, mapper = pojo<RuntimeVersion>().nonNull())
    }

    private fun emptyAccountInfo() = AccountInfo { info ->
        info[nonce] = 0.toUInt()
        info[refCount] = 0.toUInt()

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

    private suspend fun filterAccountTransactions(account: Account, extrinsics: List<String>): List<EncodableStruct<SubmittableExtrinsic>> {
        return withContext(Dispatchers.Default) {
            val currentPublicKey = getAccountId(account.address)
            val transfersPalette = account.network.type.runtimeConfiguration.pallets.transfers

            extrinsics.filter { hex ->
                val stub = SubmittableExtrinsic.readOrNull(hex) ?: return@filter false

                val callIndex = stub[signedExtrinsic][call][callIndex]

                callIndex in transfersPalette
            }
                .map(SubmittableExtrinsic::read)
                .filter { transfer ->
                    val signed = transfer[signedExtrinsic]
                    val sender = signed[accountId]
                    val receiver = signed[call][args][recipientId]

                    sender.contentEquals(currentPublicKey) || receiver.contentEquals(currentPublicKey)
                }
        }
    }
}