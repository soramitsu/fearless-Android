package jp.co.soramitsu.common.data.network.subquery

import java.math.BigDecimal

class StakingCollatorsApyResponse(val collatorRounds: SubQueryNodes<CollatorApyElement>) {
    class CollatorApyElement(
        val collatorId: String?,
        val apr: BigDecimal?
    )
}
