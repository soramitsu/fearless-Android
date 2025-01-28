package co.jp.soramitsu.tonconnect.model

sealed class ManifestException(
    message: String,
    cause: Throwable? = null
) : Throwable(message, cause) {

    data class NotFound(
        val httpCode: Int
    ) : ManifestException("http response code: $httpCode")

    data class FailedParse(
        val throwable: Throwable
    ) : ManifestException("Failed read manifest content", throwable)
}
