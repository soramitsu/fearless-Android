package jp.co.soramitsu.feature_wallet_impl.domain.beacon

import android.net.Uri
import com.google.gson.Gson
import it.airgap.beaconsdk.blockchain.substrate.substrate
import it.airgap.beaconsdk.blockchain.tezos.message.request.SignPayloadTezosRequest
import it.airgap.beaconsdk.client.wallet.BeaconWalletClient
import it.airgap.beaconsdk.core.data.BeaconError
import it.airgap.beaconsdk.core.data.P2pPeer
import it.airgap.beaconsdk.core.message.BeaconRequest
import it.airgap.beaconsdk.core.message.ErrorBeaconResponse
import it.airgap.beaconsdk.core.message.PermissionBeaconRequest
import jp.co.soramitsu.common.data.network.runtime.binding.bindNumber
import jp.co.soramitsu.common.utils.Base58Ext.fromBase58Check
import jp.co.soramitsu.common.utils.isTransfer
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
        GlobalScope.async { BeaconWalletClient("Fearless Wallet", listOf(substrate())) }//BeaconClient("Fearless Wallet") }
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

            peer to requestsFlow
        }
    }

    suspend fun decodeOperation(operation: String): Result<SignableOperation> = runCatching {
        val runtime = chainRegistry.getRuntime(polkadotChainId) //todo stub
        val call = GenericCall.fromHex(runtime, operation)

        mapCallToSignableOperation(call)
    }

    suspend fun reportSignDeclined(
        request: SignPayloadBeaconRequest
    ) {
        beaconClient().respond(ErrorBeaconResponse.from(request, BeaconError.Aborted))
    }

    suspend fun reportPermissionsDeclined(
        request: PermissionBeaconRequest
    ) {
        beaconClient().respond(ErrorBeaconResponse.from(request, BeaconError.Aborted))
    }

    suspend fun signPayload(
        request: SignPayloadBeaconRequest
    ) {
        val signature = accountRepository.signWithCurrentAccount(request.payload.fromHex())
        val signatureHex = signature.toHexString(withPrefix = true)

        beaconClient().respond(SignPayloadBeaconResponse.from(request, request.signingType, signatureHex))
    }

    suspend fun allowPermissions(
        forRequest: PermissionBeaconRequest
    ) {
        val address = accountRepository.getSelectedAccount().address
        val publicKey = address.toAccountId().toHexString()
        val response = PermissionBeaconResponse.from(forRequest, publicKey)

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
