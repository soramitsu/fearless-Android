package jp.co.soramitsu.tonconnect.api.domain

import jp.co.soramitsu.tonconnect.api.model.AppEntity
import jp.co.soramitsu.tonconnect.api.model.TonConnectLink
import jp.co.soramitsu.tonconnect.api.model.TonDappConnection
import org.json.JSONObject

/**
 * The connect flow for a dApp's connect link, the same from the scanners and from a link opened in the app:
 * reads the dApp's manifest, lets the person approve or decline ([approve] opens the connection details and
 * returns the reply it signed), then sends that reply to the link's client over the bridge.
 *
 * Returns the stored connection, or null when none was stored (declined, or the bridge refused the reply).
 */
suspend fun TonConnectInteractor.connectFromLink(
    link: TonConnectLink,
    approve: suspend (app: AppEntity, proofPayload: String?) -> JSONObject
): TonDappConnection? {
    val app = readManifest(link.request.manifestUrl)
    val reply = approve(app, link.request.proofPayload)
    return respondDappConnectRequest(link.clientId, link.request, reply, app)
}
