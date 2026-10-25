package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.account.impl.domain.recovery.WalletKeyIdentity
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope

/** Public identity lookup used again immediately before a recovery commit. */
fun interface WalletKeyIdentityReader {
    suspend fun read(metaId: Long, scope: WalletSecretScope): WalletKeyIdentity
}
