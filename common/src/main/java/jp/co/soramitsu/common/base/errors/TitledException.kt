package jp.co.soramitsu.common.base.errors

open class TitledException(val title: String, message: String) : Exception(message)

class RewardsNotSupportedWarning : TitledException("Warning", "Pending rewards for this network is not supported yet")
