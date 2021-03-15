package jp.co.soramitsu.feature_staking_impl.domain.model

class NominatorSummary(
    val status: Status
) {
    enum class Status {
        ACTIVE, INACTIVE, WAITING, ELECTION
    }
}
