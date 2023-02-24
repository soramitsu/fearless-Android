package jp.co.soramitsu.common.data.network.subquery

class SubsquidLastRoundId(val rounds: List<RoundIdElement>) {
    class RoundIdElement(
        val id: String?
    )
}
