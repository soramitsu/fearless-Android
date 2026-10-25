package jp.co.soramitsu.common.data.network.solana

/** Explicit account and rent failures that can be translated before requesting a signature. */
class SolanaAccountPreflightException(val code: Code) : IllegalArgumentException(code.name) {
    enum class Code {
        RECIPIENT_CANNOT_SIGN,
        RECIPIENT_IS_NOT_WALLET,
        INVALID_TOKEN_MINT,
        INVALID_TOKEN_ACCOUNT,
        TOKEN_ACCOUNT_FROZEN,
        INSUFFICIENT_BALANCE,
        RECIPIENT_BELOW_RENT,
        SENDER_BELOW_RENT
    }
}
