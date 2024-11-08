package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.account.api.domain.model.cryptoType
import jp.co.soramitsu.common.data.secrets.v1.Keypair
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v2.getChainAccountKeypair
import jp.co.soramitsu.common.data.secrets.v2.mapKeypairStructToKeypair
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecretStore
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.TonSecretStore
import jp.co.soramitsu.common.data.secrets.v3.TonSecrets
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.shared_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.shared_utils.extensions.toHexString

class KeyPairRepository(
    private val secretStoreV2: SecretStoreV2,
    private val ethereumSecretStore: EthereumSecretStore,
    private val substrateSecretStore: SubstrateSecretStore,
    private val tonSecretStore: TonSecretStore,
    private val accountRepository: AccountRepository
) : KeypairProvider {

    override suspend fun getCryptoTypeFor(chain: IChain, accountId: ByteArray): CryptoType {
        val metaAccount = accountRepository.findMetaAccount(accountId)
            ?: error("No meta account found accessing ${accountId.toHexString()}")
        return metaAccount.cryptoType(chain)
    }

    override suspend fun getKeypairFor(chain: IChain, accountId: ByteArray): Keypair {
        val allMetaAccounts = accountRepository.allMetaAccounts()
        val metaAccount = allMetaAccounts.find { it.accountId(chain).contentEquals(accountId) }
            ?: error("No meta account found accessing ${accountId.toHexString()}")

        val keypair = when {
            secretStoreV2.hasChainSecrets(metaAccount.id, accountId) -> {
                secretStoreV2.getChainAccountKeypair(metaAccount.id, accountId)
            }
            chain.ecosystem == Ecosystem.EthereumBased ||
            chain.ecosystem == Ecosystem.Substrate -> substrateSecretStore.get(metaAccount.id)?.get(SubstrateSecrets.SubstrateKeypair)?.let { mapKeypairStructToKeypair(it) }
            chain.ecosystem == Ecosystem.Ethereum -> ethereumSecretStore.get(metaAccount.id)?.get(EthereumSecrets.EthereumKeypair)?.let { mapKeypairStructToKeypair(it) }
            chain.ecosystem == Ecosystem.Ton -> tonSecretStore.get(metaAccount.id)?.let { Keypair(it[TonSecrets.PublicKey], it[TonSecrets.PrivateKey]) }
            else -> error("No keypair found for meta account: ${metaAccount.id}, chain: ${chain.id} (${chain.ecosystem.name})")
        }

        return keypair ?: error("No keypair found for meta account: ${metaAccount.id}, chain: ${chain.id} (${chain.ecosystem.name})")
    }
}