package jp.co.soramitsu.feature_staking_impl.domain

sealed class Alert {
    sealed class CallToAction : Alert() {
        object ChangeValidators: CallToAction()

    }

    sealed class Warning : Alert(){
        object Election: Alert()
    }
}
