package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.core.extrinsic.MutationExecutionGuard
import jp.co.soramitsu.core.extrinsic.keypair_provider.GuardedKeypairProvider
import jp.co.soramitsu.core.extrinsic.keypair_provider.WalletBoundKeypairProvider
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
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope
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
) : WalletBoundKeypairProvider {

    override fun forWallet(walletId: Long): GuardedKeypairProvider = object : GuardedKeypairProvider {
        override suspend fun getCryptoTypeFor(chain: IChain, accountId: ByteArray): CryptoType =
            reviewedWallet(walletId, chain, accountId).cryptoType(chain)
                ?: error("The reviewed wallet has no signing crypto type")

        override suspend fun getKeypairFor(chain: IChain, accountId: ByteArray): Keypair =
            readKeypair(chain, accountId, null, null, walletId)

        override suspend fun getAuthorizedKeypairFor(
            chain: IChain, accountId: ByteArray, guard: MutationExecutionGuard, intentSha256: String
        ): Keypair {
            require(chain.ecosystem != Ecosystem.Ton) { "Authorized Substrate mutations do not support TON keys" }
            guard.check(intentSha256)
            return readKeypair(chain, accountId, guard, intentSha256, walletId)
        }

        override suspend fun hasLocalSubstrateKey(chain: IChain, accountId: ByteArray): Boolean {
            val wallet = reviewedWallet(walletId, chain, accountId)
            val usesChainKey = requireSigningAccess(wallet, chain, accountId)
            return usesChainKey ||
                (chain.ecosystem == Ecosystem.Substrate && substrateSecretStore.hasSecret(wallet.id))
        }
    }

    private suspend fun reviewedWallet(walletId: Long, chain: IChain, accountId: ByteArray): MetaAccount {
        val wallet = accountRepository.getMetaAccount(walletId)
        check(wallet.id == walletId && accountId.isNotEmpty() && wallet.accountId(chain).contentEquals(accountId)) {
            "The reviewed wallet no longer owns this signing account"
        }
        check(wallet.chainAccounts[chain.id]?.metaId?.let { it == walletId } != false) {
            "The signing account belongs to a different wallet"
        }
        return wallet
    }

    override suspend fun getCryptoTypeFor(chain: IChain, accountId: ByteArray): CryptoType {
        val metaAccount = accountRepository.findMetaAccount(accountId)
            ?: error("No meta account found accessing ${accountId.toHexString()}")
        return metaAccount.cryptoType(chain) ?: error("The wallet doesn't support substrate or ethereum ecosystem")
    }

    override suspend fun getKeypairFor(chain: IChain, accountId: ByteArray): Keypair = readKeypair(chain, accountId, null, null)

    override suspend fun getAuthorizedKeypairFor(
        chain: IChain, accountId: ByteArray, guard: MutationExecutionGuard, intentSha256: String
    ): Keypair {
        require(chain.ecosystem != Ecosystem.Ton) { "Authorized Substrate mutations do not support TON keys" }
        guard.check(intentSha256)
        return readKeypair(chain, accountId, guard, intentSha256)
    }

    override suspend fun hasLocalSubstrateKey(chain: IChain, accountId: ByteArray): Boolean {
        val wallet = accountRepository.allMetaAccounts().find { it.accountId(chain).contentEquals(accountId) } ?: return false
        val usesChainKey = requireSigningAccess(wallet, chain, accountId)
        return usesChainKey ||
            (chain.ecosystem == Ecosystem.Substrate && substrateSecretStore.hasSecret(wallet.id))
    }

    /**
     * Fails only when a key that signs for [accountId] on [chain] needs
     * recovery (addendum U0.2): the per-network key named by [accountId], and
     * the root key of the chain's ecosystem when no per-network key signs. A
     * quarantined Ethereum key never stops Substrate signing, a broken
     * per-network key never stops another account, and a Bitcoin row, whose
     * 33-byte id has no key of its own, never stops anything.
     *
     * Returns whether the per-network key signs.
     */
    private suspend fun requireSigningAccess(
        metaAccount: MetaAccount,
        chain: IChain,
        accountId: ByteArray
    ): Boolean {
        if (accountId.isNotEmpty()) {
            walletSecretAccessGuard.requireAccess(
                metaId = metaAccount.id,
                scope = WalletSecretScope.chainAccount(accountId)
            )
        }
        val usesChainKey = secretStoreV2.hasChainSecrets(metaAccount.id, accountId)
        if (!usesChainKey) {
            walletSecretAccessGuard.requireAccess(
                metaId = metaAccount.id,
                scope = WalletSecretScope.forEcosystem(chain.ecosystem),
                substratePublicKey = metaAccount.substratePublicKey
            )
        }
        return usesChainKey
    }

    private suspend fun readKeypair(
        chain: IChain, accountId: ByteArray, guard: MutationExecutionGuard?, intent: String?, walletId: Long? = null
    ): Keypair {
        val metaAccount = if (walletId != null) reviewedWallet(walletId, chain, accountId)
        else accountRepository.allMetaAccounts().find { it.accountId(chain).contentEquals(accountId) }
            ?: error("No meta account found accessing ${accountId.toHexString()}")

        val usesChainKey = requireSigningAccess(metaAccount, chain, accountId)

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
            usesChainKey -> {
                val requiredChainAccount = chainAccount
                    ?: throw WalletRecoveryRequiredException()
                if (guard != null) secretStoreV2.getAuthorizedChainAccountKeypair(
                    metaAccount.id, accountId, requiredChainAccount.publicKey, requiredChainAccount.cryptoType,
                    guard, requireNotNull(intent)
                ) else secretStoreV2.getChainAccountKeypair(
                    metaId = metaAccount.id,
                    accountId = accountId,
                    expectedPublicKey = requiredChainAccount.publicKey,
                    expectedCryptoType = requiredChainAccount.cryptoType
                )
            }

            chain.ecosystem == Ecosystem.Substrate -> {
                guard?.check(requireNotNull(intent))
                val secrets = if (guard == null) substrateSecretStore.get(
                    metaAccount.id, metaAccount.substratePublicKey, metaAccount.substrateCryptoType, metaAccount.substrateAccountId
                ) else substrateSecretStore.getAuthorized(
                    metaAccount.id, metaAccount.substratePublicKey, metaAccount.substrateCryptoType, metaAccount.substrateAccountId,
                    guard, requireNotNull(intent)
                )
                secrets?.get(SubstrateSecrets.SubstrateKeypair)?.let(::mapKeypairStructToKeypair)
            }

            chain.ecosystem == Ecosystem.EthereumBased || chain.ecosystem == Ecosystem.Ethereum -> {
                guard?.check(requireNotNull(intent))
                val secrets = if (guard == null) ethereumSecretStore.get(
                    metaAccount.id, metaAccount.ethereumPublicKey, metaAccount.ethereumAddress
                ) else ethereumSecretStore.getAuthorized(
                    metaAccount.id, metaAccount.ethereumPublicKey, metaAccount.ethereumAddress, guard, requireNotNull(intent)
                )
                secrets?.get(EthereumSecrets.EthereumKeypair)?.let(::mapKeypairStructToKeypair)
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
