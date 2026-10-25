package jp.co.soramitsu.account.impl.presentation.recovery

import android.os.Parcelable
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope
import jp.co.soramitsu.common.model.WalletEcosystem
import kotlinx.parcelize.Parcelize

/** Navigation carries only a durable wallet id and public key scope, never recovery input. */
@Parcelize
data class RecoveryTarget(val metaId: Long, val scopeId: String) : Parcelable {
    fun scope(): WalletSecretScope {
        require(metaId > 0)
        return when (scopeId) {
            "substrate" -> WalletSecretScope.Substrate
            "ethereum" -> WalletSecretScope.Ethereum
            "ton" -> WalletSecretScope.Ton
            else -> {
                require(scopeId.matches(Regex("chain:(?:[0-9a-f]{40}|[0-9a-f]{64})")))
                WalletSecretScope.ChainAccount(scopeId.removePrefix("chain:"))
            }
        }
    }

    fun ecosystem(): WalletEcosystem = when (val scope = scope()) {
        WalletSecretScope.Substrate -> WalletEcosystem.Substrate
        WalletSecretScope.Ethereum -> WalletEcosystem.Ethereum
        WalletSecretScope.Ton -> WalletEcosystem.Ton
        is WalletSecretScope.ChainAccount -> if (scope.accountId.size == 20) WalletEcosystem.Ethereum else WalletEcosystem.Substrate
    }

    companion object {
        fun from(metaId: Long, scope: WalletSecretScope): RecoveryTarget = RecoveryTarget(metaId, when (scope) {
            WalletSecretScope.Substrate -> "substrate"
            WalletSecretScope.Ethereum -> "ethereum"
            WalletSecretScope.Ton -> "ton"
            is WalletSecretScope.ChainAccount -> "chain:${scope.accountIdHex}"
        }).also { it.scope() }
    }
}
