package jp.co.soramitsu.wallet.impl.domain.beacon

import android.net.Uri
import com.google.gson.Gson
import io.emeraldpay.polkaj.scale.ScaleCodecReader
import it.airgap.beaconsdk.blockchain.substrate.data.SubstrateAccount
import it.airgap.beaconsdk.blockchain.substrate.data.SubstrateAppMetadata
import it.airgap.beaconsdk.blockchain.substrate.data.SubstratePermission
import it.airgap.beaconsdk.blockchain.substrate.data.SubstrateSignerPayload
import it.airgap.beaconsdk.blockchain.substrate.message.request.PermissionSubstrateRequest
import it.airgap.beaconsdk.blockchain.substrate.message.request.SignPayloadSubstrateRequest
import it.airgap.beaconsdk.blockchain.substrate.message.response.PermissionSubstrateResponse
import it.airgap.beaconsdk.blockchain.substrate.message.response.SignPayloadSubstrateResponse
import it.airgap.beaconsdk.blockchain.substrate.substrate
import it.airgap.beaconsdk.client.wallet.BeaconWalletClient
import it.airgap.beaconsdk.core.data.BeaconError
import it.airgap.beaconsdk.core.data.Origin
import it.airgap.beaconsdk.core.data.P2pPeer
import it.airgap.beaconsdk.core.message.BeaconRequest
import it.airgap.beaconsdk.core.message.ErrorBeaconResponse
import it.airgap.beaconsdk.core.message.PermissionBeaconResponse
import it.airgap.beaconsdk.transport.p2p.matrix.p2pMatrix
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.account.api.extrinsic.ExtrinsicService
import jp.co.soramitsu.common.data.Keypair
import jp.co.soramitsu.common.data.mappers.mapCryptoTypeToEncryption
import jp.co.soramitsu.common.data.network.runtime.binding.bindNumber
import jp.co.soramitsu.common.data.network.runtime.binding.cast
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.utils.Base58Ext.fromBase58Check
import jp.co.soramitsu.common.utils.decodeToInt
import jp.co.soramitsu.common.utils.isTransfer
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.fromUnsignedBytes
import jp.co.soramitsu.fearless_utils.extensions.requireHexPrefix
import jp.co.soramitsu.fearless_utils.extensions.toHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.hash.Hasher.blake2b256
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.AdditionalExtras
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Era
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericCall
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.SignedExtras
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.useScaleWriter
import jp.co.soramitsu.fearless_utils.scale.dataType.byte
import jp.co.soramitsu.fearless_utils.scale.utils.directWrite
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import jp.co.soramitsu.runtime.multiNetwork.getRuntime
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private class TransactionRawData(
    val module: String,
    val call: String,
    val args: Map<String, Any?>
)

class BeaconInteractor(
    private val gson: Gson,
    private val accountRepository: AccountRepository,
    private val chainRegistry: ChainRegistry,
    private val preferences: Preferences,
    private val extrinsicService: ExtrinsicService,
    private val beaconSharedState: BeaconSharedState
) {

    companion object {
        private const val REGISTERED_CHAINS_KEY = "BEACON_REGISTERED_NETWORK_CHAIN_ID"
        const val BEACON_CONNECTED_KEY = "IS_BEACON_CONNECTED"
    }

    private val beaconClient by lazy {
        GlobalScope.async {
            BeaconWalletClient("Fearless Wallet") {
                support(substrate())
                use(p2pMatrix())
                ignoreUnsupportedBlockchains = true
            }
        }
    }

    fun isConnected(): Boolean {
        return preferences.getBoolean(BEACON_CONNECTED_KEY, true)
    }

    private suspend fun beaconClient() = beaconClient.await()

    suspend fun connectFromQR(qrCode: String): Result<Pair<P2pPeer, Flow<BeaconRequest?>>> = withContext(Dispatchers.Default) {
        runCatching {
            val qrUri = Uri.parse(qrCode)

            val encodedPeer = qrUri.getQueryParameter("data")!!
            val jsonContent = encodedPeer.fromBase58Check().decodeToString()
            val peer = gson.fromJson(jsonContent, P2pPeer::class.java)
            val beaconClient = beaconClient()

            beaconClient.addPeers(peer)

            val requestsFlow = beaconClient.connect()
                .map {
                    if (it.isSuccess) {
                        preferences.putBoolean(BEACON_CONNECTED_KEY, true)
                    }
                    it.getOrNull()
                }

            peer to requestsFlow
        }
    }

    suspend fun initWithoutQr(): Result<Pair<P2pPeer, Flow<BeaconRequest?>>> {
        val peers = beaconClient().getPeers()
        if (peers.isEmpty()) {
            return Result.failure(BeaconConnectionHasNoPeerException())
        }
        val peer = peers.last() as P2pPeer
        return kotlin.runCatching {
            val beaconClient = beaconClient()
            val requestsFlow = beaconClient.connect()
                .map {
                    if (it.isSuccess) {
                        preferences.putBoolean(BEACON_CONNECTED_KEY, true)
                    }
                    it.getOrNull()
                }
            peer to requestsFlow
        }
    }

    suspend fun hasPeers(): Boolean {
        val client = beaconClient()
        val peers = client.getPeers()
        if (peers.isEmpty()) return false
        if (peers.size > 1) {
            val peersToRemove = peers.subList(0, peers.size - 2)
            client.removePeers(peersToRemove)
        }
        return true
    }

    suspend fun decodeOperation(operation: String): Result<SignableOperation> = runCatching {
        val currentRegisteredNetwork = getBeaconRegisteredNetwork()
        requireNotNull(currentRegisteredNetwork)
        val runtime = chainRegistry.getRuntime(currentRegisteredNetwork)
        val chain = chainRegistry.getChain(currentRegisteredNetwork)
        val call = GenericCall.fromHex(runtime, operation)
        mapCallToSignableOperation(call, chain.addressPrefix)
    }

    suspend fun decodeOperation(payload: SubstrateSignerPayload.Json): Result<SignableOperation> {
        return runCatching {
            val currentRegisteredNetwork = getBeaconRegisteredNetwork()
            requireNotNull(currentRegisteredNetwork)
            val runtime = chainRegistry.getRuntime(currentRegisteredNetwork)
            val chain = chainRegistry.getChain(currentRegisteredNetwork)
            val call = GenericCall.fromHex(runtime, payload.method)
            mapCallToSignableOperation(call, chain.addressPrefix)
        }
    }

    suspend fun reportSignDeclined(
        request: SignPayloadSubstrateRequest
    ) {
        beaconClient().respond(ErrorBeaconResponse.from(request, BeaconError.Aborted))
    }

    suspend fun reportPermissionsDeclined(
        request: PermissionSubstrateRequest
    ) {
        beaconClient().respond(ErrorBeaconResponse.from(request, BeaconError.Aborted))
    }

    suspend fun signPayload(
        request: SignPayloadSubstrateRequest
    ) {
        val payload = request.payload
        val signature = when (payload) {
            is SubstrateSignerPayload.Json -> signJsonPayload(payload)
            is SubstrateSignerPayload.Raw -> signRawPayload(payload)
        }

        val response =
            SignPayloadSubstrateResponse.from(
                request,
                transactionHash = null,
                signature = signature,
                payload = (payload as? SubstrateSignerPayload.Raw)?.data
            )
        beaconClient().respond(response)
    }

    private suspend fun signJsonPayload(payload: SubstrateSignerPayload.Json): String {
        val blockHash = payload.blockHash.fromHex()
        val era = payload.era
        val genesisHash = payload.genesisHash.requireHexPrefix().removePrefix("0x")

        val payloadBytes = useScaleWriter {
            directWrite(payload.method.fromHex())
            directWrite(era.fromHex())
            writeCompact(payload.nonce.fromHex().decodeToInt())
            writeCompact(payload.tip.fromHex().decodeToInt())
            writeUint32(payload.specVersion.fromHex().decodeToInt())
            writeUint32(payload.transactionVersion.fromHex().decodeToInt())
            directWrite(genesisHash.requireHexPrefix().fromHex())
            directWrite(blockHash)
        }

        val messageToSign = if (payloadBytes.size > 256) {
            payloadBytes.blake2b256()
        } else {
            payloadBytes
        }

        val currentMetaAccount = accountRepository.getSelectedMetaAccount()
        val secrets = accountRepository.getMetaAccountSecrets(currentMetaAccount.id) ?: error("There are no secrets for metaId: ${currentMetaAccount.id}")
        val keypairSchema = secrets[MetaAccountSecrets.SubstrateKeypair]
        val publicKey = keypairSchema[KeyPairSchema.PublicKey]
        val privateKey = keypairSchema[KeyPairSchema.PrivateKey]
        val nonce1 = keypairSchema[KeyPairSchema.Nonce]
        val keypair = Keypair(publicKey, privateKey, nonce1)
        val encryption = mapCryptoTypeToEncryption(currentMetaAccount.substrateCryptoType)

        return extrinsicService.createSignature(
            encryption,
            keypair,
            messageToSign.toHexString()
        )
    }

    private suspend fun signRawPayload(payload: SubstrateSignerPayload.Raw): String {
        val currentMetaAccount = accountRepository.getSelectedMetaAccount()
        val secrets = accountRepository.getMetaAccountSecrets(currentMetaAccount.id) ?: error("There are no secrets for metaId: ${currentMetaAccount.id}")
        val keypairSchema = secrets[MetaAccountSecrets.SubstrateKeypair]
        val publicKey = keypairSchema[KeyPairSchema.PublicKey]
        val privateKey = keypairSchema[KeyPairSchema.PrivateKey]
        val nonce = keypairSchema[KeyPairSchema.Nonce]
        val keypair = Keypair(publicKey, privateKey, nonce)
        val encryption = mapCryptoTypeToEncryption(currentMetaAccount.substrateCryptoType)

        return extrinsicService.createSignature(
            encryption,
            keypair,
            payload.data
        )
    }

    suspend fun allowPermissions(
        forRequest: PermissionSubstrateRequest
    ) {
        val account = accountRepository.getSelectedMetaAccount()
        val polkadotChain = chainRegistry.getChain(polkadotChainId)
        val polkadotAddress = account.address(polkadotChain)
        val accounts = forRequest.networks.map {
            val chainId = it.genesisHash.requireHexPrefix().removePrefix("0x")
            val chain = getChainSafe(chainId)
            val pubKey = (if (chain?.isEthereumBased == true) account.ethereumPublicKey else account.substratePublicKey)
            val address = chain?.let { chainValue -> account.address(chainValue) }
            return@map SubstrateAccount(network = it, publicKey = pubKey?.toHexString().orEmpty(), address = address.orEmpty())
        }.filterNotNull()

        val response = PermissionSubstrateResponse.from(forRequest, accounts)
        beaconClient().respond(response)
    }

    private suspend fun getChainSafe(chainId: String): Chain? {
        return try {
            chainRegistry.getChain(chainId)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun disconnect() {
        beaconClient().removeAllPeers()
        preferences.putBoolean(BEACON_CONNECTED_KEY, false)
    }

    private fun mapCallToSignableOperation(call: GenericCall.Instance, chainAddressPrefix: Int): SignableOperation {
        val moduleName = call.module.name
        val functionName = call.function.name
        val args = call.arguments

        val rawData = TransactionRawData(moduleName, functionName, args)
        val rawDataSerialized = gson.toJson(rawData, TransactionRawData::class.java)
        val destinationPublicKey = (args["dest"].cast<DictEnum.Entry<ByteArray>>()).value
        val destinationAccountId = destinationPublicKey.substrateAccountId()
        val address = destinationAccountId.toAddress(chainAddressPrefix.toShort())
        return when {
            call.isTransfer() -> {
                SignableOperation.Transfer(
                    module = moduleName,
                    call = functionName,
                    rawData = rawDataSerialized,
                    amount = bindNumber(args["value"]),
                    destination = address,
                    args = args
                )
            }

            else -> SignableOperation.Other(moduleName, functionName, args, rawDataSerialized)
        }
    }

    suspend fun estimateFee(operation: SignableOperation): BigInteger {
        val chainId = getBeaconRegisteredNetwork() ?: return BigInteger.ZERO
        val chain = chainRegistry.getChain(chainId)
        return extrinsicService.estimateFee(chain, false) {
            call(operation.module, operation.call, operation.args)
        }
    }

    suspend fun registerNetwork(chainId: String) {
        preferences.putString(REGISTERED_CHAINS_KEY, chainId)
        val chain = chainRegistry.getChain(chainId)
        beaconSharedState.update(chainId, chain.assets.first().id)
    }

    private fun getBeaconRegisteredNetwork(): String? = preferences.getString(REGISTERED_CHAINS_KEY)

    suspend fun getBeaconRegisteredChain(): Chain? {
        val chainId = getBeaconRegisteredNetwork() ?: return null
        return chainRegistry.getChain(chainId)
    }
}

class BeaconConnectionHasNoPeerException : Exception("There are no peers matching the app name")

data class PermissionFearlessResponse internal constructor(
    override val id: String,
    override val version: String,
    override val requestOrigin: Origin,
    override val blockchainIdentifier: String,
    val appMetadata: SubstrateAppMetadata,
    val scopes: List<SubstratePermission.Scope>,
    val accounts: List<SubstrateAccount>,
    val substrateAddress: String,
    val ethereumAddress: String
) : PermissionBeaconResponse() {
    companion object {

        fun from(
            request: PermissionSubstrateRequest,
            accounts: List<SubstrateAccount>,
            scopes: List<SubstratePermission.Scope> = request.scopes,
            substrateAddress: String,
            ethereumAddress: String
        ): PermissionFearlessResponse =
            PermissionFearlessResponse(
                request.id,
                request.version,
                request.origin,
                request.blockchainIdentifier,
                request.appMetadata,
                scopes,
                accounts,
                substrateAddress,
                ethereumAddress
            )
    }
}
