package jp.co.soramitsu.feature_staking_api.domain.model

sealed class ElectionStatus {

    object Close : ElectionStatus()

    class Open(block: BlockNumber) : ElectionStatus()
}
