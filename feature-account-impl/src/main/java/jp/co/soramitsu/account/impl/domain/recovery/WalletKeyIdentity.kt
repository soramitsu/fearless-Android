package jp.co.soramitsu.account.impl.domain.recovery

import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope
import jp.co.soramitsu.core.models.CryptoType

/** A durable public identity, never inferred from the replacement secret. */
sealed class WalletKeyIdentity(val scope: WalletSecretScope) {
    class Substrate(val publicKey: ByteArray, val accountId: ByteArray, val cryptoType: CryptoType) :
        WalletKeyIdentity(WalletSecretScope.Substrate)
    class Ethereum(val publicKey: ByteArray, val address: ByteArray) : WalletKeyIdentity(WalletSecretScope.Ethereum)
    class Ton(val publicKey: ByteArray) : WalletKeyIdentity(WalletSecretScope.Ton)
    class Chain(val publicKey: ByteArray, val accountId: ByteArray, val cryptoType: CryptoType) :
        WalletKeyIdentity(WalletSecretScope.chainAccount(accountId))
}
