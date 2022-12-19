package jp.co.soramitsu.common.base.errors

abstract class ValidationException(
    override val message: String,
    val explanation: String
) : Exception() {
    operator fun component1() = message
    operator fun component2() = explanation

    companion object
}

open class ValidationWarning(
    message: String,
    explanation: String,
    val positiveButtonText: String,
    val negativeButtonText: String
) : ValidationException(message, explanation) {
    operator fun component3() = positiveButtonText
    operator fun component4() = negativeButtonText
}
