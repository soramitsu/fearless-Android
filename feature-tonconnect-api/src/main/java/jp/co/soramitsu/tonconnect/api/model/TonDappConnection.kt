package jp.co.soramitsu.tonconnect.api.model

import jp.co.soramitsu.coredb.model.TonConnectionLocal

data class TonDappConnection(
    val metaId: Long,
    val clientId: String,
    val name: String,
    val icon: String,
    val url: String
) {
    constructor(tonConnectionLocal: TonConnectionLocal) : this(
        metaId = tonConnectionLocal.metaId,
        clientId = tonConnectionLocal.clientId,
        name = tonConnectionLocal.name,
        icon = tonConnectionLocal.icon,
        url = tonConnectionLocal.url
    )
}
