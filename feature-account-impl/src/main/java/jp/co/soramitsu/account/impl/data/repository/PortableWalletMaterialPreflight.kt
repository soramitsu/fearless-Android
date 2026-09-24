package jp.co.soramitsu.account.impl.data.repository

import java.util.Base64
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletCrossStoreMutationMutex
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore
import jp.co.soramitsu.core.model.SecuritySource
import jp.co.soramitsu.core.model.WithDerivationPath
import jp.co.soramitsu.core.model.WithMnemonic
import jp.co.soramitsu.core.model.WithSeed
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.dao.WalletCustodyDao
import jp.co.soramitsu.coredb.model.RelationJoinedMetaAccountInfo
import jp.co.soramitsu.coredb.model.WalletCustodyLocal
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.scale.toByteArray
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private typealias WalletIdentity = PortableWalletMaterialDraft.WalletIdentity
private typealias ChainIdentity = PortableWalletMaterialDraft.ChainIdentity
private typealias FavoriteIdentity = PortableWalletMaterialDraft.FavoriteIdentity
private typealias LegacySource = PortableWalletMaterialDraft.LegacySubstrateSource

/** Wallet-owned local material inventory; the draft capture is internal and has no upload path. */
class PortableWalletMaterialPreflight @Inject constructor(
    private val metaAccountDao: MetaAccountDao,
    private val accountRepository: AccountRepository,
    private val encryptedPreferences: EncryptedPreferences,
    private val custodyDao: WalletCustodyDao,
    private val journalStore: WalletSecretMutationJournalStore
) {
    data class Coverage(
        val walletCount: Int,
        val substrateRootCount: Int,
        val ethereumRootCount: Int,
        val tonRootCount: Int,
        val chainAccountCount: Int,
        val legacyV1SourceCount: Int = 0
    )

    /**
     * Fail closed unless every persisted signing identity has a validated V3, V2 or V1 source.
     * This establishes local material coverage only; no cross-platform restore is wired.
     */
    suspend fun verifyCoverage(): Coverage = withContext(Dispatchers.IO) {
        WalletCrossStoreMutationMutex.instance.withLock {
            val before = snapshot(metaAccountDao.getJoinedMetaAccountsInfo())
            check(before.isNotEmpty()) { "No wallets are available for portable backup" }
            val legacyAddresses = discoverLegacyAddresses(before)

            var substrateRoots = 0
            var ethereumRoots = 0
            var tonRoots = 0
            var chainAccounts = 0
            var legacySources = 0
            before.forEach { wallet ->
                check(!accountRepository.isWalletRecoveryRequired(wallet.id)) {
                    "A wallet requires recovery before portable backup"
                }
                check(wallet.hasMaterialIdentity()) {
                    "A wallet has no recoverable identity"
                }

                if (wallet.substratePublicKey != null) {
                    val v3 = accountRepository.getSubstrateSecrets(wallet.id)
                    val legacyAddress = legacyAddresses[wallet.id]
                    check(v3 != null || legacyAddress != null) {
                        "Substrate wallet material is unavailable for portable backup"
                    }
                    if (v3 != null) substrateRoots++
                    if (legacyAddress != null) {
                        accountRepository.getSecuritySource(legacyAddress)
                        legacySources++
                    }
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
            check(legacyAddresses == discoverLegacyAddresses(before)) {
                "Legacy V1 source inventory changed during portable-backup validation"
            }
            before.forEach { wallet ->
                check(!accountRepository.isWalletRecoveryRequired(wallet.id)) {
                    "A wallet requires recovery before portable backup"
                }
            }

            Coverage(before.size, substrateRoots, ethereumRoots, tonRoots, chainAccounts, legacySources)
        }
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
            val legacyAddresses = discoverLegacyAddresses(before)
            val captured = ArrayList<PortableWalletMaterialDraft.Wallet>(before.size)
            try {
                before.forEach { identity ->
                    check(!accountRepository.isWalletRecoveryRequired(identity.id)) {
                        "A wallet requires recovery before portable backup"
                    }
                    captured += captureSignedWallet(identity, legacyAddresses[identity.id])
                }
                check(before == snapshot(metaAccountDao.getJoinedMetaAccountsInfo())) {
                    "Wallet identities changed during portable-backup material capture"
                }
                check(legacyAddresses == discoverLegacyAddresses(before)) {
                    "Legacy V1 source inventory changed during portable-backup capture"
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

    /**
     * Separate, unwired semantic capture. Existing rows without provenance remain UNKNOWN and must
     * prove signing material; only an exact WATCH marker permits public role-8 slots.
     */
    internal suspend fun captureSemanticPlaintext(): ByteArray = withContext(Dispatchers.IO) {
        WalletCrossStoreMutationMutex.instance.withLock {
            val rows = metaAccountDao.getJoinedMetaAccountsInfo()
            val before = snapshot(rows, allowAddressOnlyEvm = true)
            check(before.isNotEmpty()) { "No wallets are available for portable backup" }
            val rowById = rows.associateBy { it.metaAccount.id }
            val legacyAddresses = discoverLegacyAddresses(before)
            val markers = before.associate { identity ->
                identity.id to custodyDao.get(identity.id)
            }
            val projected = ArrayList<PortableWalletSemanticMaterial.Wallet>()
            val newlySigned = ArrayList<WalletCustodyLocal>()
            try {
                before.forEach { identity ->
                    check(!accountRepository.isWalletRecoveryRequired(identity.id)) {
                        "A wallet requires recovery before portable backup"
                    }
                    val row = checkNotNull(rowById[identity.id]) { "Wallet identity disappeared" }
                    when (WalletCustodyProvenance.classify(
                        row.metaAccount, row.chainAccounts, markers[identity.id]
                    )) {
                        WalletCustodyProvenance.Kind.WATCH -> {
                            WalletCustodyProvenance.requireWatchPublicIdentity(
                                row.metaAccount, row.chainAccounts
                            )
                            check(legacyAddresses[identity.id] == null &&
                                !journalStore.hasSecretNamespace(identity.id)) {
                                "A WATCH wallet has signing material or recovery evidence"
                            }
                            projected += PortableWalletDraftSemanticTranscoder.projectWatchWallet(identity)
                        }
                        WalletCustodyProvenance.Kind.SIGNED,
                        WalletCustodyProvenance.Kind.UNKNOWN -> {
                            val signed = captureSignedWallet(identity, legacyAddresses[identity.id])
                            try {
                                projected += PortableWalletDraftSemanticTranscoder.projectSignedWallet(signed)
                            } finally {
                                signed.clearSecrets()
                            }
                            if (markers[identity.id] == null) {
                                newlySigned += WalletCustodyProvenance.signedMarker(row.metaAccount)
                            }
                        }
                    }
                }
                check(before == snapshot(metaAccountDao.getJoinedMetaAccountsInfo(), allowAddressOnlyEvm = true) &&
                    legacyAddresses == discoverLegacyAddresses(before)) {
                    "Wallet identities changed during portable-backup material capture"
                }
                before.forEach { identity ->
                    check(!accountRepository.isWalletRecoveryRequired(identity.id)) {
                        "A wallet requires recovery before portable backup"
                    }
                    val row = checkNotNull(rowById[identity.id])
                    check(WalletCustodyProvenance.classify(
                        row.metaAccount, row.chainAccounts, custodyDao.get(identity.id)
                    ) == WalletCustodyProvenance.classify(
                        row.metaAccount, row.chainAccounts, markers[identity.id]
                    )) { "Wallet custody changed during portable-backup capture" }
                }
                val ordered = projected.sortedBy { it.sourcePosition }
                val selectedIndex = before.sortedWith(compareBy({ it.position }, { it.id }))
                    .indexOfFirst { it.isSelected }
                check(selectedIndex >= 0) { "Selected wallet disappeared during capture" }
                val semantic = PortableWalletSemanticMaterial.Snapshot(selectedIndex, ordered)
                val encoded = PortableWalletSemanticMaterial.encode(semantic)
                try {
                    newlySigned.forEach { custodyDao.insert(it) }
                    encoded
                } catch (failure: Exception) {
                    encoded.fill(0)
                    throw failure
                }
            } finally {
                projected.forEach(PortableWalletSemanticMaterial.Wallet::clearSecrets)
            }
        }
    }

    private suspend fun captureSignedWallet(
        identity: WalletIdentity,
        legacyAddress: String?
    ): PortableWalletMaterialDraft.Wallet {
        var substrate: ByteArray? = null
        var ethereum: ByteArray? = null
        var ton: ByteArray? = null
        var legacySource: LegacySource? = null
        val chains = ArrayList<ByteArray>(identity.chainAccounts.size)
        var retained = false
        try {
            substrate = identity.substratePublicKey?.let {
                accountRepository.getSubstrateSecrets(identity.id)?.toByteArray()
            }
            legacySource = legacyAddress?.let { address ->
                makeLegacySource(address, accountRepository.getSecuritySource(address))
            }
            if (identity.substratePublicKey != null) {
                check(substrate != null || legacySource != null) {
                    "Substrate wallet material is unavailable for portable backup"
                }
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
            return PortableWalletMaterialDraft.Wallet(
                identity, substrate, ethereum, ton, chains, legacySource
            ).also { retained = true }
        } finally {
            if (!retained) {
                substrate?.fill(0)
                ethereum?.fill(0)
                ton?.fill(0)
                legacySource?.clearSecrets()
                chains.forEach { it.fill(0) }
            }
        }
    }

    private fun snapshot(
        rows: List<RelationJoinedMetaAccountInfo>,
        allowAddressOnlyEvm: Boolean = false
    ): List<WalletIdentity> {
        val wallets = rows.map { row ->
            val meta = row.metaAccount
            val substrateKey = meta.substratePublicKey.id()
            val substrateAccount = meta.substrateAccountId.id()
            val ethereumKey = meta.ethereumPublicKey.id()
            val ethereumAddress = meta.ethereumAddress.id()
            check(listOf(substrateKey, substrateAccount, meta.substrateCryptoType).count { it != null } in setOf(0, 3)) {
                "A wallet has an incomplete Substrate identity"
            }
            check((ethereumKey == null || ethereumAddress != null) &&
                (allowAddressOnlyEvm || (ethereumKey == null) == (ethereumAddress == null))) {
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

    private fun discoverLegacyAddresses(wallets: List<WalletIdentity>): Map<Long, String> {
        val candidates = encryptedPreferences.keysWithPrefixes(
            prefixes = setOf(LEGACY_V1_PREFIX),
            maxResultCount = 4_096,
            maxKeyBytes = 256,
            maxTotalKeyBytes = 524_288,
            failOnOversizedMatch = true
        )
        val addresses = linkedMapOf<Long, String>()
        candidates.sorted().forEach { key ->
            check(key.startsWith(LEGACY_V1_PREFIX)) { "Legacy V1 source inventory is invalid" }
            val address = key.removePrefix(LEGACY_V1_PREFIX)
            check(address.length in MIN_SS58_ADDRESS_CHARS..MAX_SS58_ADDRESS_CHARS &&
                BASE58_ADDRESS.matches(address)) {
                "A legacy V1 source has an invalid SS58 address"
            }
            val accountId = try {
                address.toAccountId()
            } catch (_: Exception) {
                error("A legacy V1 source has ambiguous SS58 ownership")
            }
            check(accountId.size == SUBSTRATE_ACCOUNT_ID_BYTES) {
                "A legacy V1 source has ambiguous SS58 ownership"
            }
            val id = Base64.getEncoder().encodeToString(accountId)
            val matches = wallets.filter { it.substrateAccountId == id }
            check(matches.isNotEmpty()) { "A legacy V1 source has no durable wallet owner" }
            check(matches.size <= 1) { "Multiple wallets claim one legacy V1 source" }
            val wallet = matches.single()
            check(addresses.putIfAbsent(wallet.id, address) == null) {
                "A wallet has multiple legacy V1 source aliases"
            }
        }
        return addresses
    }

    private fun makeLegacySource(address: String, source: SecuritySource): LegacySource {
        val type = when (source) {
            is SecuritySource.Specified.Create -> PortableWalletMaterialDraft.SourceType.CREATE
            is SecuritySource.Specified.Seed -> PortableWalletMaterialDraft.SourceType.SEED
            is SecuritySource.Specified.Json -> PortableWalletMaterialDraft.SourceType.JSON
            is SecuritySource.Specified.Mnemonic -> PortableWalletMaterialDraft.SourceType.MNEMONIC
            is SecuritySource.Unspecified -> PortableWalletMaterialDraft.SourceType.UNSPECIFIED
            else -> error("An unsupported V1 source type cannot be captured")
        }
        val mnemonic = (source as? WithMnemonic)?.mnemonic
        val entropy = mnemonic?.let { MnemonicCreator.fromWords(it).entropy }
        return LegacySource(
            accountAddress = address,
            sourceType = type,
            publicKey = source.keypair.publicKey.copyOf(),
            privateKey = source.keypair.privateKey.copyOf(),
            nonce = (source.keypair as? Sr25519Keypair)?.nonce?.copyOf(),
            entropy = entropy,
            seed = (source as? WithSeed)?.seed?.copyOf(),
            mnemonic = mnemonic,
            derivationPath = (source as? WithDerivationPath)?.derivationPath
        )
    }

    private companion object {
        const val LEGACY_V1_PREFIX = "security_source_"
        const val MIN_SS58_ADDRESS_CHARS = 47
        const val MAX_SS58_ADDRESS_CHARS = 64
        const val SUBSTRATE_ACCOUNT_ID_BYTES = 32
        val BASE58_ADDRESS = Regex("^[1-9A-HJ-NP-Za-km-z]+$")
    }
}
