package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.account.api.domain.model.cryptoType
import jp.co.soramitsu.common.data.secrets.v1.Keypair
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecretCorruptionException
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecretValidator
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v2.getChainAccountKeypair
import jp.co.soramitsu.common.data.secrets.v2.mapKeypairStructToKeypair
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecretStore
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.TonSecretStore
import jp.co.soramitsu.common.data.secrets.v3.TonSecrets
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretCorruptionException
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretValidator
import jp.co.soramitsu.common.data.storage.encrypt.WalletRecoveryRequiredException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretAccessGuard
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.fearless_utils.extensions.toHexString

class KeyPairRepository(
    private val secretStoreV2: SecretStoreV2,
    private val ethereumSecretStore: EthereumSecretStore,
    private val substrateSecretStore: SubstrateSecretStore,
    private val tonSecretStore: TonSecretStore,
    private val accountRepository: AccountRepository,
    private val walletSecretAccessGuard: WalletSecretAccessGuard
) : KeypairProvider {

    override suspend fun getCryptoTypeFor(chain: IChain, accountId: ByteArray): CryptoType {
        val metaAccount = accountRepository.findMetaAccount(accountId)
            ?: error("No meta account found accessing ${accountId.toHexString()}")
        return metaAccount.cryptoType(chain) ?: error("The wallet doesn't support substrate or ethereum ecosystem")
    }

    override suspend fun getKeypairFor(chain: IChain, accountId: ByteArray): Keypair {
        val allMetaAccounts = accountRepository.allMetaAccounts()
        val metaAccount = allMetaAccounts.find { it.accountId(chain).contentEquals(accountId) }
            ?: error("No meta account found accessing ${accountId.toHexString()}")

        walletSecretAccessGuard.requireAccess(
            metaId = metaAccount.id,
            substratePublicKey = metaAccount.substratePublicKey,
            chainAccountId = accountId
        )

        val chainAccount = metaAccount.chainAccounts[chain.id]
            ?.takeIf { it.accountId.contentEquals(accountId) }
        val expectedPublicKey = chainAccount?.publicKey ?: when {
            chain.ecosystem == Ecosystem.Substrate -> metaAccount.substratePublicKey
            chain.ecosystem == Ecosystem.EthereumBased ||
                chain.ecosystem == Ecosystem.Ethereum -> metaAccount.ethereumPublicKey
            chain.ecosystem == Ecosystem.Ton -> metaAccount.tonPublicKey
            else -> null
        }

        val keypair = when {
            secretStoreV2.hasChainSecrets(metaAccount.id, accountId) -> {
                val requiredChainAccount = chainAccount
                    ?: throw WalletRecoveryRequiredException()
                secretStoreV2.getChainAccountKeypair(
                    metaId = metaAccount.id,
                    accountId = accountId,
                    expectedPublicKey = requiredChainAccount.publicKey,
                    expectedCryptoType = requiredChainAccount.cryptoType
                )
            }

            chain.ecosystem == Ecosystem.Substrate -> {
                substrateSecretStore.get(
                    metaId = metaAccount.id,
                    expectedPublicKey = metaAccount.substratePublicKey,
                    expectedCryptoType = metaAccount.substrateCryptoType,
                    expectedAccountId = metaAccount.substrateAccountId
                )
                    ?.get(SubstrateSecrets.SubstrateKeypair)
                    ?.let(::mapKeypairStructToKeypair)
            }

            chain.ecosystem == Ecosystem.EthereumBased ||
                chain.ecosystem == Ecosystem.Ethereum -> {
                ethereumSecretStore.get(
                    metaId = metaAccount.id,
                    expectedPublicKey = metaAccount.ethereumPublicKey,
                    expectedAddress = metaAccount.ethereumAddress
                )
                    ?.get(EthereumSecrets.EthereumKeypair)
                    ?.let(::mapKeypairStructToKeypair)
            }

            chain.ecosystem == Ecosystem.Ton -> {
                tonSecretStore.get(metaAccount.id, metaAccount.tonPublicKey)
                    ?.let { Keypair(it[TonSecrets.PublicKey], it[TonSecrets.PrivateKey]) }
            }

            else -> error("No keypair found for meta account: ${metaAccount.id}, chain: ${chain.id} (${chain.ecosystem.name})")
        }

        val validatedKeypair = keypair
            ?: error("No keypair found for meta account: ${metaAccount.id}, chain: ${chain.id} (${chain.ecosystem.name})")
        if (
            expectedPublicKey == null ||
            !validatedKeypair.publicKey.contentEquals(expectedPublicKey)
        ) {
            throw WalletRecoveryRequiredException()
        }
        chainAccount?.let {
            try {
                ChainAccountSecretValidator.validateKeypair(
                    keypair = validatedKeypair,
                    expectedAccountId = it.accountId,
                    expectedPublicKey = it.publicKey,
                    expectedCryptoType = it.cryptoType
                )
            } catch (failure: ChainAccountSecretCorruptionException) {
                throw WalletRecoveryRequiredException()
            }
        } ?: try {
            when (chain.ecosystem) {
                Ecosystem.Substrate -> {
                    WalletRootSecretValidator.validateSubstrateKeypair(
                        keypair = validatedKeypair,
                        expectedPublicKey = metaAccount.substratePublicKey
                            ?: throw WalletRootSecretCorruptionException(
                                "A Substrate signing key has no durable public identity"
                            ),
                        expectedCryptoType = metaAccount.substrateCryptoType
                            ?: throw WalletRootSecretCorruptionException(
                                "A Substrate signing key has no durable crypto type"
                            ),
                        expectedAccountId = metaAccount.substrateAccountId
                            ?: throw WalletRootSecretCorruptionException(
                                "A Substrate signing key has no durable account id"
                            )
                    )
                }

                Ecosystem.EthereumBased,
                Ecosystem.Ethereum -> {
                    WalletRootSecretValidator.validateEthereumKeypair(
                        keypair = validatedKeypair,
                        expectedPublicKey = metaAccount.ethereumPublicKey
                            ?: throw WalletRootSecretCorruptionException(
                                "An Ethereum signing key has no durable public identity"
                            ),
                        expectedAddress = metaAccount.ethereumAddress
                            ?: throw WalletRootSecretCorruptionException(
                                "An Ethereum signing key has no durable address"
                            )
                    )
                }

                Ecosystem.Ton -> {
                    WalletRootSecretValidator.validateTonKeypair(
                        keypair = validatedKeypair,
                        expectedPublicKey = metaAccount.tonPublicKey
                            ?: throw WalletRootSecretCorruptionException(
                                "A TON signing key has no durable public identity"
                            )
                    )
                }
            }
        } catch (failure: WalletRootSecretCorruptionException) {
            throw WalletRecoveryRequiredException()
        }

        return validatedKeypair
    }
}
