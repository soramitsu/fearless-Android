package jp.co.soramitsu.feature_wallet_impl.presentation.beacon

import android.net.Uri
import com.google.gson.Gson
import it.airgap.beaconsdk.client.BeaconClient
import it.airgap.beaconsdk.data.beacon.P2pPeer
import it.airgap.beaconsdk.message.BeaconRequest
import it.airgap.beaconsdk.message.PermissionBeaconRequest
import it.airgap.beaconsdk.message.PermissionBeaconResponse
import jp.co.soramitsu.common.utils.Base58Ext.fromBase58Check
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class BeaconApi(
    private val gson: Gson,
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

    suspend fun allowPermissions(
        forRequest: PermissionBeaconRequest,
        withAccountAddress: String
    ) {
        val response = PermissionBeaconResponse.from(forRequest, withAccountAddress.toAccountId().toHexString())

        beaconClient().respond(response)
    }

    suspend fun disconnect() {
        beaconClient().removeAllPeers()
    }
}
