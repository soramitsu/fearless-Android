package jp.co.soramitsu.common.data.network.subquery

class StakingLastRoundId(val rounds: SubQueryNodes<RoundIdElement>) {
    class RoundIdElement(
        val id: String?
    )
}
