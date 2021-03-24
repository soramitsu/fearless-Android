package jp.co.soramitsu.feature_wallet_api.domain.interfaces

import java.math.BigInteger

interface WalletConstants {

    suspend fun existentialDeposit(): BigInteger
}
