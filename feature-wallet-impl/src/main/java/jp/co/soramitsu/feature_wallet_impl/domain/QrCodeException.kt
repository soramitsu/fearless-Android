package jp.co.soramitsu.feature_wallet_impl.domain

sealed class QrCodeException : RuntimeException() {

    object UserNotFoundException : QrCodeException()
    object MyOwnQrCodeException : QrCodeException()
    object DecodeException : QrCodeException()
}