package jp.co.soramitsu.feature_wallet_impl.domain.beacon

import android.net.Uri
import com.google.gson.Gson
import it.airgap.beaconsdk.blockchain.substrate.data.SubstrateAccount
import it.airgap.beaconsdk.blockchain.substrate.data.SubstrateNetwork
import it.airgap.beaconsdk.blockchain.substrate.message.request.PermissionSubstrateRequest
import it.airgap.beaconsdk.blockchain.substrate.message.request.SignSubstrateRequest
import it.airgap.beaconsdk.blockchain.substrate.message.response.PermissionSubstrateResponse
import it.airgap.beaconsdk.blockchain.substrate.message.response.SignSubstrateResponse
import it.airgap.beaconsdk.blockchain.substrate.substrate
import it.airgap.beaconsdk.client.wallet.BeaconWalletClient
import it.airgap.beaconsdk.core.data.BeaconError
import it.airgap.beaconsdk.core.data.P2P
import it.airgap.beaconsdk.core.data.P2pPeer
import it.airgap.beaconsdk.core.message.BeaconRequest
import it.airgap.beaconsdk.core.message.ErrorBeaconResponse
import it.airgap.beaconsdk.transport.p2p.matrix.p2pMatrix
import jp.co.soramitsu.common.data.network.runtime.binding.bindNumber
import jp.co.soramitsu.common.utils.Base58Ext.fromBase58Check
import jp.co.soramitsu.common.utils.isTransfer
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericCall
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.signWithCurrentAccount
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import jp.co.soramitsu.runtime.multiNetwork.getRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.math.BigInteger

private class TransactionRawData(
    val module: String,
    val call: String,
    val args: Map<String, Any?>
)

class BeaconInteractor(
    private val gson: Gson,
    private val accountRepository: AccountRepository,
    private val chainRegistry: ChainRegistry,
//    private val runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
//    private val feeEstimator: FeeEstimator
) {

    private val beaconClient by lazy {
        GlobalScope.async {
            BeaconWalletClient("Fearless Wallet", listOf(substrate())) {
                addConnections(
                    P2P(p2pMatrix()),
                )
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
                .map { it.getOrNull() }
            hashCode()
            peer to requestsFlow
        }
    }

    suspend fun decodeOperation(operation: String): Result<SignableOperation> = runCatching {
        val runtime = chainRegistry.getRuntime(polkadotChainId) //todo stub
        val call = GenericCall.fromHex(runtime, operation)

        mapCallToSignableOperation(call)
    }

    suspend fun reportSignDeclined(
        request: SignSubstrateRequest
    ) {
        beaconClient().respond(ErrorBeaconResponse.from(request, BeaconError.Aborted))
    }

    suspend fun reportPermissionsDeclined(
        request: PermissionSubstrateRequest
    ) {
        beaconClient().respond(ErrorBeaconResponse.from(request, BeaconError.Aborted))
    }

    suspend fun signPayload(
        request: SignSubstrateRequest
    ) {
        val signature = accountRepository.signWithCurrentAccount(request.payload.fromHex())
        val signatureHex = signature.toHexString(withPrefix = true)
        val response = SignSubstrateResponse.from(request, signatureHex, payload = null)
        beaconClient().respond(response)
    }

    //todo add multi-assets support
    suspend fun allowPermissions(
        forRequest: PermissionSubstrateRequest
    ) {
        val address = accountRepository.getSelectedAccount().address
        val publicKey = address.toAccountId().toHexString()
        //todo stub
        val testAccount = SubstrateAccount(
            network = SubstrateNetwork(//todo check
                genesisHash = "91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3",
                name = "Polkadot",
                rpcUrl = null
            ),
            addressPrefix = 0,
            publicKey = publicKey
        )
        val response = PermissionSubstrateResponse.from(forRequest, listOf(testAccount))
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
}
