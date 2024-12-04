package co.jp.soramitsu.tonconnect.model

import jp.co.soramitsu.coredb.model.TonConnectionLocal

data class TonDappConnection(
    val clientId: String,
    val name: String,
    val icon: String,
    val url: String
) {
    constructor(tonConnectionLocal: TonConnectionLocal) : this(
        clientId = tonConnectionLocal.clientId,
        name = tonConnectionLocal.name,
        icon = tonConnectionLocal.icon,
        url = tonConnectionLocal.url
    )
}