package co.jp.soramitsu.walletconnect.model

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class TonConnect(
    val clientId: String,
    val request: ConnectRequest,
    val returnUri: Uri?,
    val fromQR: Boolean,
    val jsInject: Boolean,
    val origin: Uri?
): Parcelable {

    companion object {
        fun parse(
            uri: Uri,
            refSource: Uri?,
            fromQR: Boolean,
            returnUri: Uri?
        ): TonConnect {
            val version = uri.getQueryParameter("v")?.toIntOrNull() ?: 0
            if (version != 2) {
                throw TonConnectException.UnsupportedVersion(version)
            }

            val clientId = uri.getQueryParameter("id")
            if (!isValidClientId(clientId)) {
                throw TonConnectException.WrongClientId(clientId)
            }

            val request = ConnectRequest.parse(uri.getQueryParameter("r"))

            return TonConnect(
                clientId = clientId!!,
                request = request,
                returnUri = returnUri,
                fromQR = fromQR,
                jsInject = false,
                origin = refSource
            )
        }

        private fun isValidClientId(clientId: String?): Boolean {
            return !clientId.isNullOrBlank() && clientId.length == 64
        }
    }
}