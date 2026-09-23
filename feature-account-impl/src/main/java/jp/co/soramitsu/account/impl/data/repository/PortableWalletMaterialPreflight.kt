package jp.co.soramitsu.account.impl.data.repository

import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.model.RelationJoinedMetaAccountInfo
import jp.co.soramitsu.common.data.storage.encrypt.WalletCrossStoreMutationMutex
import jp.co.soramitsu.fearless_utils.scale.toByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Base64

private typealias WalletIdentity = PortableWalletMaterialDraft.WalletIdentity
private typealias ChainIdentity = PortableWalletMaterialDraft.ChainIdentity
private typealias FavoriteIdentity = PortableWalletMaterialDraft.FavoriteIdentity

/** Wallet-owned local material inventory; the draft capture is internal and has no upload path. */
class PortableWalletMaterialPreflight @Inject constructor(
    private val metaAccountDao: MetaAccountDao,
    private val accountRepository: AccountRepository
) {
    data class Coverage(
        val walletCount: Int,
        val substrateRootCount: Int,
        val ethereumRootCount: Int,
        val tonRootCount: Int,
        val chainAccountCount: Int
    )

    /**
     * Fail closed if a persisted wallet cannot be read through the validated V3/V2 paths.
     * Historical V1-only and watch-only material remain unsupported by portable backup until
     * a reviewed cross-platform format and restore path can preserve them explicitly.
     */
    suspend fun verifyCoverage(): Coverage = withContext(Dispatchers.IO) {
        val before = snapshot(metaAccountDao.getJoinedMetaAccountsInfo())
        check(before.isNotEmpty()) { "No wallets are available for portable backup" }

        var substrateRoots = 0
        var ethereumRoots = 0
        var tonRoots = 0
        var chainAccounts = 0
        before.forEach { wallet ->
            check(!accountRepository.isWalletRecoveryRequired(wallet.id)) {
                "A wallet requires recovery before portable backup"
            }
            check(wallet.hasMaterialIdentity()) {
                "A wallet has no recoverable identity"
            }

            if (wallet.substratePublicKey != null) {
                check(accountRepository.getSubstrateSecrets(wallet.id) != null) {
                    "Substrate wallet material is unavailable for portable backup"
                }
                substrateRoots++
            }
            if (wallet.ethereumPublicKey != null) {
                check(accountRepository.getEthereumSecrets(wallet.id) != null) {
                    "Ethereum wallet material is unavailable for portable backup"
                }
                ethereumRoots++
            }
            if (wallet.tonPublicKey != null) {
                check(accountRepository.getTonSecrets(wallet.id) != null) {
                    "TON wallet material is unavailable for portable backup"
                }
                tonRoots++
            }
            wallet.chainAccounts.forEach { chain ->
                check(accountRepository.getChainAccountSecrets(wallet.id, chain.chainId) != null) {
                    "Chain-account material is unavailable for portable backup"
                }
                chainAccounts++
            }
        }

        check(before == snapshot(metaAccountDao.getJoinedMetaAccountsInfo())) {
            "Wallet identities changed during portable-backup material validation"
        }
        before.forEach { wallet ->
            check(!accountRepository.isWalletRecoveryRequired(wallet.id)) {
                "A wallet requires recovery before portable backup"
            }
        }

        Coverage(before.size, substrateRoots, ethereumRoots, tonRoots, chainAccounts)
    }

    /**
     * Captures a complete Android-local plaintext draft under the existing cross-store mutation
     * lock. Callers must encrypt immediately and erase the returned bytes. This draft is not wired
     * to Drive or backup completion and cannot be installed on a replacement device.
     */
    internal suspend fun captureDraftPlaintext(): ByteArray = withContext(Dispatchers.IO) {
        WalletCrossStoreMutationMutex.instance.withLock {
            val before = snapshot(metaAccountDao.getJoinedMetaAccountsInfo())
            check(before.isNotEmpty()) { "No wallets are available for portable backup" }
            val captured = ArrayList<PortableWalletMaterialDraft.Wallet>(before.size)
            try {
                before.forEach { identity ->
                    check(!accountRepository.isWalletRecoveryRequired(identity.id)) {
                        "A wallet requires recovery before portable backup"
                    }
                    var substrate: ByteArray? = null
                    var ethereum: ByteArray? = null
                    var ton: ByteArray? = null
                    val chains = ArrayList<ByteArray>(identity.chainAccounts.size)
                    var retained = false
                    try {
                        substrate = identity.substratePublicKey?.let {
                            checkNotNull(accountRepository.getSubstrateSecrets(identity.id)) {
                                "Substrate wallet material is unavailable for portable backup"
                            }.toByteArray()
                        }
                        ethereum = identity.ethereumPublicKey?.let {
                            checkNotNull(accountRepository.getEthereumSecrets(identity.id)) {
                                "Ethereum wallet material is unavailable for portable backup"
                            }.toByteArray()
                        }
                        ton = identity.tonPublicKey?.let {
                            checkNotNull(accountRepository.getTonSecrets(identity.id)) {
                                "TON wallet material is unavailable for portable backup"
                            }.toByteArray()
                        }
                        identity.chainAccounts.forEach { chain ->
                            chains += checkNotNull(accountRepository.getChainAccountSecrets(identity.id, chain.chainId)) {
                                "Chain-account material is unavailable for portable backup"
                            }.toByteArray()
                        }
                        captured += PortableWalletMaterialDraft.Wallet(identity, substrate, ethereum, ton, chains)
                        retained = true
                    } finally {
                        if (!retained) {
                            substrate?.fill(0)
                            ethereum?.fill(0)
                            ton?.fill(0)
                            chains.forEach { it.fill(0) }
                        }
                    }
                }
                check(before == snapshot(metaAccountDao.getJoinedMetaAccountsInfo())) {
                    "Wallet identities changed during portable-backup material capture"
                }
                before.forEach { identity ->
                    check(!accountRepository.isWalletRecoveryRequired(identity.id)) {
                        "A wallet requires recovery before portable backup"
                    }
                }
                PortableWalletMaterialDraft.encode(PortableWalletMaterialDraft.Snapshot(captured))
            } finally {
                captured.forEach(PortableWalletMaterialDraft.Wallet::clearSecrets)
            }
        }
    }

    private fun snapshot(rows: List<RelationJoinedMetaAccountInfo>): List<WalletIdentity> {
        val wallets = rows.map { row ->
            val meta = row.metaAccount
            val substrateKey = meta.substratePublicKey.id()
            val substrateAccount = meta.substrateAccountId.id()
            val ethereumKey = meta.ethereumPublicKey.id()
            val ethereumAddress = meta.ethereumAddress.id()
            check(listOf(substrateKey, substrateAccount, meta.substrateCryptoType).count { it != null } in setOf(0, 3)) {
                "A wallet has an incomplete Substrate identity"
            }
            check((ethereumKey == null) == (ethereumAddress == null)) {
                "A wallet has an incomplete Ethereum identity"
            }
            val chains = row.chainAccounts.map { chain ->
                check(chain.metaId == meta.id && chain.chainId.isNotBlank()) {
                    "A chain account has an invalid wallet binding"
                }
                check(chain.publicKey.isNotEmpty() && chain.accountId.isNotEmpty()) {
                    "A chain account has an incomplete public identity"
                }
                ChainIdentity(
                    chain.chainId, chain.publicKey.id()!!, chain.accountId.id()!!,
                    chain.cryptoType.name, chain.name, chain.initialized
                )
            }.sortedBy(ChainIdentity::chainId)
            check(chains.map(ChainIdentity::chainId).distinct().size == chains.size) {
                "A wallet has duplicate chain identities"
            }
            val favorites = row.favoriteChains.map { favorite ->
                check(favorite.metaId == meta.id && favorite.chainId.isNotBlank()) {
                    "A favorite chain has an invalid wallet binding"
                }
                FavoriteIdentity(favorite.chainId, favorite.isFavorite)
            }.sortedBy(FavoriteIdentity::chainId)
            check(favorites.map(FavoriteIdentity::chainId).distinct().size == favorites.size) {
                "A wallet has duplicate favorite chains"
            }
            WalletIdentity(
                meta.id, meta.name, meta.isSelected, meta.position, meta.initialized, substrateKey,
                meta.substrateCryptoType?.name, substrateAccount, ethereumKey,
                ethereumAddress, meta.tonPublicKey.id(), chains, favorites
            )
        }.sortedBy(WalletIdentity::id)
        check(wallets.map(WalletIdentity::id).distinct().size == wallets.size) {
            "Portable-backup wallet inventory contains duplicate identities"
        }
        return wallets
    }

    private fun ByteArray?.id(): String? = this?.let {
        check(it.isNotEmpty()) { "A wallet has an empty public identity" }
        Base64.getEncoder().encodeToString(it)
    }

}
