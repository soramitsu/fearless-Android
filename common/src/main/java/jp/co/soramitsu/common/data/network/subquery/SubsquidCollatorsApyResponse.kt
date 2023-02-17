package jp.co.soramitsu.common.data.network.subquery

import java.math.BigDecimal

class SubsquidCollatorsApyResponse(val stakers: List<CollatorApyElement>) {
    class CollatorApyElement(
        val stashId: String?,
        val apr24h: BigDecimal?
    )
}
