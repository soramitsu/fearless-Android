package jp.co.soramitsu.feature_wallet_impl.presentation.beacon

import android.net.Uri
import com.google.gson.Gson
import it.airgap.beaconsdk.client.BeaconClient
import it.airgap.beaconsdk.data.beacon.P2pPeer
import it.airgap.beaconsdk.message.BeaconRequest
import it.airgap.beaconsdk.message.PermissionBeaconRequest
import it.airgap.beaconsdk.message.PermissionBeaconResponse
import it.airgap.beaconsdk.message.SignPayloadBeaconRequest
import it.airgap.beaconsdk.message.SignPayloadBeaconResponse
import jp.co.soramitsu.common.utils.Base58Ext.fromBase58Check
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.common.utils.useValue
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericCall
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.signWithCurrentAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class BeaconApi(
    private val gson: Gson,
    private val accountRepository: AccountRepository,
    private val runtimeProperty: SuspendableProperty<RuntimeSnapshot>
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

    suspend fun decodePayload(request: SignPayloadBeaconRequest): Result<GenericCall.Instance> = runCatching {
        runtimeProperty.useValue { runtime ->
            GenericCall.fromHex(runtime, request.payload)
        }
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
}

