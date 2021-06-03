package jp.co.soramitsu.feature_staking_impl.domain

sealed class Alert {
    object ChangeValidators : Alert()

    object Election : Alert()
}
