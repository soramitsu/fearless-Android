package jp.co.soramitsu.feature_staking_impl.presentation.staking.alerts.model

sealed class AlertStatus {
    class Alerts(val alerts: List<AlertModel>) : AlertStatus()

    object NoAlerts : AlertStatus()
}
