package jp.co.soramitsu.common.data.storage.encrypt

/** Explicit retry outcomes. Temporary provider failures never grant fresh-slot consent. */
sealed interface WalletKeyLossRetry {
    data object Recovered : WalletKeyLossRetry
    data object RestartDevice : WalletKeyLossRetry
    data object RetryLater : WalletKeyLossRetry
    data class CanStart(val consent: WalletKeyLossConsent) : WalletKeyLossRetry
}
