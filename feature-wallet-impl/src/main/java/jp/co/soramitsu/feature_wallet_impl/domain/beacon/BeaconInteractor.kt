package jp.co.soramitsu.feature_wallet_impl.domain.beacon

import android.net.Uri
import com.google.gson.Gson
import it.airgap.beaconsdk.blockchain.substrate.data.SubstrateAccount
import it.airgap.beaconsdk.blockchain.substrate.data.SubstrateSignerPayload
import it.airgap.beaconsdk.blockchain.substrate.message.request.PermissionSubstrateRequest
import it.airgap.beaconsdk.blockchain.substrate.message.request.SignPayloadSubstrateRequest
import it.airgap.beaconsdk.blockchain.substrate.message.response.PermissionSubstrateResponse
import it.airgap.beaconsdk.blockchain.substrate.message.response.SignPayloadSubstrateResponse
import it.airgap.beaconsdk.blockchain.substrate.substrate
import it.airgap.beaconsdk.client.wallet.BeaconWalletClient
import it.airgap.beaconsdk.core.data.BeaconError
import it.airgap.beaconsdk.core.data.P2pPeer
import it.airgap.beaconsdk.core.message.BeaconRequest
import it.airgap.beaconsdk.core.message.ErrorBeaconResponse
import it.airgap.beaconsdk.transport.p2p.matrix.p2pMatrix
import java.math.BigInteger
import jp.co.soramitsu.common.data.network.runtime.binding.bindNumber
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.utils.Base58Ext.fromBase58Check
import jp.co.soramitsu.common.utils.isTransfer
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericCall
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.signWithCurrentMetaAccount
import jp.co.soramitsu.feature_account_api.domain.model.address
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.getRuntime
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
    private val preferences: Preferences
//    private val runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
//    private val feeEstimator: FeeEstimator
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
                    if(it.isSuccess) {
                        preferences.putBoolean(BEACON_CONNECTED_KEY, true)
                    }
                    it.getOrNull()
                }

            peer to requestsFlow
        }
    }

    suspend fun decodeOperation(operation: String): Result<SignableOperation> = runCatching {
        val currentRegisteredNetwork = getBeaconRegisteredNetwork()
        requireNotNull(currentRegisteredNetwork)
        val runtime = chainRegistry.getRuntime(currentRegisteredNetwork)
        val call = GenericCall.fromHex(runtime, operation)
        hashCode()
        mapCallToSignableOperation(call)
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
        val payload = (request.payload as? SubstrateSignerPayload.Raw)?.data ?: return

        val signature = accountRepository.signWithCurrentMetaAccount(payload.fromHex())
        val signatureHex = signature.toHexString(withPrefix = true)

        val response = SignPayloadSubstrateResponse.from(request, transactionHash = null, signature = signatureHex, payload = payload)
        SignPayloadSubstrateResponse
        beaconClient().respond(response)
    }

    suspend fun allowPermissions(
        forRequest: PermissionSubstrateRequest
    ) {
        val account = accountRepository.getSelectedMetaAccount()
        val accounts = forRequest.networks.map {
            val chain = chainRegistry.getChain(it.genesisHash)
            val pubKey = (if (chain.isEthereumBased) account.ethereumPublicKey else account.substratePublicKey) ?: return@map null
            val address = account.address(chain) ?: return@map null
            return@map SubstrateAccount(network = it, publicKey = pubKey.toHexString(), address = address)
        }.filterNotNull()

        val response = PermissionSubstrateResponse.from(forRequest, accounts)
        beaconClient().respond(response)
    }

    suspend fun disconnect() {
        beaconClient().removeAllPeers()
    }

    private fun mapCallToSignableOperation(call: GenericCall.Instance): SignableOperation {
        val moduleName = call.module.name
        val functionName = call.function.name
        val args = call.arguments

        val rawData = TransactionRawData(moduleName, functionName, args)
        val rawDataSerialized = gson.toJson(rawData, TransactionRawData::class.java)

        return when {
            call.isTransfer() -> {
                SignableOperation.Transfer(
                    module = moduleName,
                    call = functionName,
                    rawData = rawDataSerialized,
                    amount = bindNumber(args["value"]),
                    args = args
                )
            }

            else -> SignableOperation.Other(moduleName, functionName, args, rawDataSerialized)
        }
    }

    suspend fun estimateFee(operation: SignableOperation): BigInteger {
        //todo estimate fee for beacon
        return BigInteger.ZERO
//        val accountAddress = accountRepository.getSelectedAccount().address

//        return withContext(Dispatchers.IO) {
//            feeEstimator.estimateFee(accountAddress) {
//                call(operation.module, operation.call, operation.args)
//            }
//        }
    }

    fun registerNetwork(chainId: String) {
        preferences.putString(REGISTERED_CHAINS_KEY, chainId)
    }

    private fun getBeaconRegisteredNetwork(): String? = preferences.getString(REGISTERED_CHAINS_KEY)

    suspend fun getBeaconRegisteredChain(): Chain? {
        val chainId = getBeaconRegisteredNetwork() ?: return null
        return chainRegistry.getChain(chainId)
    }
}
