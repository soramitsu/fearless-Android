@file:Suppress("EXPERIMENTAL_API_USAGE")

package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain

import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.KeypairFactory
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
import jp.co.soramitsu.fearless_utils.wsrpc.request.DeliveryType
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.account.AccountInfoRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersionRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.GetStorageRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.SubscribeStorageRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.storageChange
import jp.co.soramitsu.fearless_utils.wsrpc.subscription.response.SubscriptionChange
import jp.co.soramitsu.fearless_utils.wsrpc.subscriptionFlow
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.extrinsics.TransferRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.requests.FeeCalculationRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.requests.GetBlockRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.requests.NextAccountIndexRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.response.BalanceChange
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.response.FeeResponse
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.response.RuntimeVersion
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.response.SignedBlock
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.AccountId
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.ActiveEraInfo
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.StakingLedger
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.account.AccountData
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.account.AccountInfoFactory
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.account.AccountInfoSchema
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.account.AccountInfoSchemaV28
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.extrinsic.EncodeExtrinsicParams
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.extrinsic.TransferExtrinsic
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.extrinsic.TransferExtrinsicFactory
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
    private val keypairFactory: KeypairFactory,
    private val accountInfoFactory: AccountInfoFactory,
    private val extrinsicFactory: TransferExtrinsicFactory,
    private val sS58Encoder: SS58Encoder
) : SubstrateRemoteSource {

    override suspend fun fetchAccountInfo(
        address: String,
        networkType: Node.NetworkType
    ): EncodableStruct<AccountInfoSchema> {
        val publicKeyBytes = getAccountId(address)
        val request = AccountInfoRequest(publicKeyBytes)

        val response = socketService.executeAsync(request)
        val accountInfo = (response.result as? String)?.let { accountInfoFactory.decode(it) }

        return accountInfo ?: emptyAccountInfo()
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

    override suspend fun listenForAccountUpdates(address: String): Flow<BalanceChange> {
        val key = Module.System.Account.storageKey(getAccountId(address))
        val request = SubscribeStorageRequest(key)

        return socketService.subscriptionFlow(request)
            .map(::buildBalanceChange)
    }

    override suspend fun fetchAccountTransfersInBlock(blockHash: String, account: Account): List<TransferExtrinsic> {
        val request = GetBlockRequest(blockHash)

        val block = socketService.executeAsync(request, mapper = pojo<SignedBlock>().nonNull())

        return filterAccountTransactions(account, block.block.extrinsics)
    }

    override suspend fun listenStakingLedger(stashAddress: String): Flow<EncodableStruct<StakingLedger>> {
        val key = Module.Staking.Bonded.storageKey(getAccountId(stashAddress))
        val request = SubscribeStorageRequest(key)

        return socketService.subscriptionFlow(request)
            .map { it.storageChange().getSingleChange() }
            .distinctUntilChanged()
            .flatMapLatest { controllerId ->
                if (controllerId != null) {
                    subscribeToLedger(stashAddress, controllerId)
                } else {
                    flowOf(createEmptyLedger(stashAddress))
                }
            }
    }

    override suspend fun getActiveEra(): EncodableStruct<ActiveEraInfo> {
        val key = Module.Staking.ActiveEra.storageKey()
        val request = GetStorageRequest(listOf(key))

        return socketService.executeAsync(request, mapper = scale(ActiveEraInfo).nonNull())
    }

    private fun subscribeToLedger(stashAddress: String, controllerId: String): Flow<EncodableStruct<StakingLedger>> {
        val accountId = AccountId.read(controllerId)
        val bytes = AccountId.toByteArray(accountId)

        val key = Module.Staking.Ledger.storageKey(bytes)
        val request = SubscribeStorageRequest(key)

        return socketService.subscriptionFlow(request)
            .map { it.storageChange().getSingleChange() }
            .map { change ->
                if (change != null) {
                    StakingLedger.read(change)
                } else {
                    createEmptyLedger(stashAddress)
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

    private suspend fun buildBalanceChange(subscriptionChange: SubscriptionChange): BalanceChange {
        val storageChange = subscriptionChange.storageChange()

        val block = storageChange.block

        val change = storageChange.getSingleChange()
        val accountInfo = readAccountInfo(change)

        return BalanceChange(block, accountInfo)
    }

    private suspend fun getAccountId(address: String) = withContext(Dispatchers.Default) {
        sS58Encoder.decode(address)
    }

    private suspend fun buildSubmittableExtrinsic(
        account: Account,
        transfer: Transfer,
        keypair: Keypair
    ): String = withContext(Dispatchers.Default) {
        val runtimeVersion = getRuntimeVersion()
        val cryptoType = mapCryptoTypeToEncryption(account.cryptoType)
        val accountIdValue = getAccountId(account.address)

        val currentNonce = getNonce(account)
        val genesis = account.network.type.runtimeConfiguration.genesisHash
        val genesisBytes = Hex.decode(genesis)

        val params = EncodeExtrinsicParams(
            senderId = accountIdValue,
            recipientId = getAccountId(transfer.recipient),
            amountInPlanks = transfer.amountInPlanks,
            nonce = currentNonce,
            runtimeVersion = runtimeVersion,
            networkType = account.network.type,
            encryptionType = cryptoType,
            genesis = genesisBytes
        )

        extrinsicFactory.createEncodedExtrinsic(params, keypair)
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

    private suspend fun readAccountInfo(hex: String?): EncodableStruct<AccountInfoSchema> {
        return hex?.let { accountInfoFactory.decode(it) } ?: emptyAccountInfo()
    }

    private fun emptyAccountInfo(): EncodableStruct<AccountInfoSchema> = AccountInfoSchemaV28 { info ->
        info[AccountInfoSchemaV28.nonce] = 0.toUInt()
        info[AccountInfoSchemaV28.providers] = 0.toUInt()
        info[AccountInfoSchemaV28.consumers] = 0.toUInt()

        info[AccountInfoSchemaV28.data] = AccountData { data ->
            data[AccountData.free] = 0.toBigInteger()
            data[AccountData.reserved] = 0.toBigInteger()
            data[AccountData.miscFrozen] = 0.toBigInteger()
            data[AccountData.feeFrozen] = 0.toBigInteger()
        }
    }

    private fun mapCryptoTypeToEncryption(cryptoType: CryptoType): EncryptionType {
        return when (cryptoType) {
            CryptoType.SR25519 -> EncryptionType.SR25519
            CryptoType.ED25519 -> EncryptionType.ED25519
            CryptoType.ECDSA -> EncryptionType.ECDSA
        }
    }

    private suspend fun filterAccountTransactions(account: Account, extrinsics: List<String>): List<TransferExtrinsic> {
        return withContext(Dispatchers.Default) {
            val currentPublicKey = getAccountId(account.address)
            val transfersPalette = account.network.type.runtimeConfiguration.pallets.transfers

            extrinsics.mapNotNull { hex -> extrinsicFactory.decode(hex) }
                .filter { transfer ->
                    if (transfer.index !in transfersPalette) return@filter false

                    transfer.senderId.contentEquals(currentPublicKey) || transfer.recipientId.contentEquals(currentPublicKey)
                }
        }
    }
}