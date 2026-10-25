package jp.co.soramitsu.account.api.domain.model

/**
 * The Google Drive backup holds an Ethereum key but no Substrate key, and a new wallet cannot be
 * created from an Ethereum key alone, so nothing was imported.
 */
class EthereumOnlyGoogleBackupException : Exception("Backup holds only an Ethereum key")
