package jp.co.soramitsu.common.data.network

import java.io.IOException

/** All configured providers are cooling down; callers may retry without changing the transaction. */
class WalletProviderUnavailableException : IOException("Wallet network providers are temporarily unavailable")
