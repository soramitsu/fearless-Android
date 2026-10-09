package jp.co.soramitsu.common.data.secrets

/**
 * A deterministic structural violation in a wallet-secret SCALE payload.
 *
 * Callers should translate this exception to the record-local corruption type
 * used by the storage or migration boundary. No cryptography is performed by
 * the preflight parser.
 */
class WalletSecretScaleCorruptionException(
    message: String
) : IllegalArgumentException(message)
