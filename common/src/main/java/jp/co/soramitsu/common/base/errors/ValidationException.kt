package jp.co.soramitsu.common.base.errors

abstract class ValidationException(
    override val message: String,
    val explanation: String
) : Exception() {

    operator fun component1() = message
    operator fun component2() = explanation
}
