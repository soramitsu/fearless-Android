package jp.co.soramitsu.feature_wallet_impl.domain.beacon

import android.net.Uri
import com.google.gson.Gson
import it.airgap.beaconsdk.client.BeaconClient
import it.airgap.beaconsdk.data.beacon.BeaconError
import it.airgap.beaconsdk.data.beacon.P2pPeer
import it.airgap.beaconsdk.message.BeaconRequest
import it.airgap.beaconsdk.message.ErrorBeaconResponse
import it.airgap.beaconsdk.message.PermissionBeaconRequest
import it.airgap.beaconsdk.message.PermissionBeaconResponse
import it.airgap.beaconsdk.message.SignPayloadBeaconRequest
import it.airgap.beaconsdk.message.SignPayloadBeaconResponse
import jp.co.soramitsu.common.data.network.runtime.binding.bindNumber
import jp.co.soramitsu.common.utils.Base58Ext.fromBase58Check
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.common.utils.isTransfer
import jp.co.soramitsu.common.utils.useValue
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericCall
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.signWithCurrentAccount
import jp.co.soramitsu.runtime.extrinsic.FeeEstimator
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
    private val runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
    private val feeEstimator: FeeEstimator
) {

    private val beaconClient by lazy {
        GlobalScope.async { BeaconClient("Fearless Wallet") }
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
        runtimeProperty.useValue { runtime ->
            val call = GenericCall.fromHex(runtime, operation)

            mapCallToSignableOperation(call)
        }
    }

    suspend fun reportSignDeclined(
        request: SignPayloadBeaconRequest
    ) {
        beaconClient().respond(ErrorBeaconResponse.from(request, BeaconError.Aborted))
    }

    suspend fun reportPermissionsDeclined(
        request: PermissionBeaconRequest
    )  {
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
        val accountAddress = accountRepository.getSelectedAccount().address

        return withContext(Dispatchers.IO) {
            feeEstimator.estimateFee(accountAddress) {
                call(operation.module, operation.call, operation.args)
            }
        }
    }
}
