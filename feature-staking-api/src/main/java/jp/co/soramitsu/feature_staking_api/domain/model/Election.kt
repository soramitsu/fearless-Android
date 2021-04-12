package jp.co.soramitsu.feature_staking_api.domain.model

sealed class ElectionPhase {

    object Off : ElectionPhase()

    object Signed : ElectionPhase()

    class Unsigned(isOpen: Boolean, block: BlockNumber) : ElectionPhase()
}

sealed class ElectionStatus {

    object Close : ElectionStatus()

    class Open(block: BlockNumber) : ElectionStatus()
}

enum class Election {
    OPEN, CLOSED
}
