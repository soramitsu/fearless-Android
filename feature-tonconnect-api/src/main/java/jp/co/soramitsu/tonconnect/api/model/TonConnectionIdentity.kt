package jp.co.soramitsu.tonconnect.api.model

import jp.co.soramitsu.coredb.model.ConnectionSource
import jp.co.soramitsu.coredb.model.TonConnectionLocal

/**
 * The complete immutable Room primary key for a TON Connect connection.
 */
data class TonConnectionIdentity(
    val metaId: Long,
    val url: String,
    val source: ConnectionSource
) {
    constructor(connection: TonConnectionLocal) : this(
        metaId = connection.metaId,
        url = connection.url,
        source = connection.source
    )
}
