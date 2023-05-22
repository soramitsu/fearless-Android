package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.cryptoType
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v2.getChainAccountKeypair
import jp.co.soramitsu.common.data.secrets.v2.getMetaAccountKeypair
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.shared_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.shared_utils.extensions.toHexString

class KeyPairRepository(
    private val secretStoreV2: SecretStoreV2,
    private val accountRepository: AccountRepository
) : KeypairProvider {

    override suspend fun getCryptoTypeFor(chain: IChain, accountId: ByteArray): CryptoType {
        val metaAccount = accountRepository.findMetaAccount(accountId)
            ?: error("No meta account found accessing ${accountId.toHexString()}")
        return metaAccount.cryptoType(chain)
    }

    override suspend fun getKeypairFor(chain: IChain, accountId: ByteArray): Keypair {
        val metaAccount = accountRepository.findMetaAccount(accountId)
            ?: error("No meta account found accessing ${accountId.toHexString()}")
        return secretStoreV2.getKeypairFor(metaAccount, chain, accountId)
    }
}

private suspend fun SecretStoreV2.getKeypairFor(
    metaAccount: MetaAccount,
    chain: IChain,
    accountId: ByteArray
): Keypair {
    return if (hasChainSecrets(metaAccount.id, accountId)) {
        getChainAccountKeypair(metaAccount.id, accountId)
    } else {
        getMetaAccountKeypair(metaAccount.id, chain.isEthereumBased)
    }
}
