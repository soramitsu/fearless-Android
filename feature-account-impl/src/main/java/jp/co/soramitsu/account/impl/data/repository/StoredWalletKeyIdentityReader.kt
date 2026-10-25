package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.account.impl.domain.recovery.WalletKeyIdentity
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope
import jp.co.soramitsu.coredb.dao.MetaAccountDao

/** Reads only durable identities; never derives an expected identity from supplied recovery material. */
class StoredWalletKeyIdentityReader(private val accounts: MetaAccountDao) : WalletKeyIdentityReader {
    override suspend fun read(metaId: Long, scope: WalletSecretScope): WalletKeyIdentity {
        val wallet = requireNotNull(accounts.getMetaAccount(metaId)) { "The wallet no longer exists" }
        return when (scope) {
            WalletSecretScope.Substrate -> WalletKeyIdentity.Substrate(
                requireNotNull(wallet.substratePublicKey), requireNotNull(wallet.substrateAccountId),
                requireNotNull(wallet.substrateCryptoType)
            )
            WalletSecretScope.Ethereum -> WalletKeyIdentity.Ethereum(
                requireNotNull(wallet.ethereumPublicKey), requireNotNull(wallet.ethereumAddress)
            )
            WalletSecretScope.Ton -> WalletKeyIdentity.Ton(requireNotNull(wallet.tonPublicKey))
            is WalletSecretScope.ChainAccount -> {
                require(scope.hasSecretNamespace) { "This account derives its key from the root wallet" }
                val matches = accounts.getJoinedMetaAccountInfo(metaId).chainAccounts.filter {
                    it.accountId.contentEquals(scope.accountId)
                }
                val first = requireNotNull(matches.firstOrNull()) { "The network account no longer exists" }
                require(matches.all { it.publicKey.contentEquals(first.publicKey) && it.cryptoType == first.cryptoType }) {
                    "The network account has conflicting public identities"
                }
                WalletKeyIdentity.Chain(first.publicKey, first.accountId, first.cryptoType)
            }
        }
    }
}
