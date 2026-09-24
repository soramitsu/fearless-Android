package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityRecovery
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.fearless_utils.extensions.toHexString

internal typealias ExportWalletIdentity = PortableWalletMaterialDraft.WalletIdentity

/** Application-storage inventory requirements before exposing a portable plaintext cohort. */
internal class PortableWalletExportInventoryGuard(
    private val encryptedPreferences: EncryptedPreferences,
    private val preferences: Preferences,
    private val assetDao: AssetDao
) {
    suspend fun requireNoUnmappedMetadata(wallets: List<ExportWalletIdentity>) {
        wallets.forEach { wallet ->
            check(
                !preferences.contains("wallet_selected_chain_id${wallet.id}") &&
                    !preferences.contains("chain_select_filter_applied_${wallet.id}") &&
                    !assetDao.hasUnmappedWalletAssetPreferences(wallet.id)
            ) { "A wallet has presentation metadata without a portable source mapping" }
        }
    }

    fun requireNoUnsupportedLegacyMaterial() {
        val prefixes = setOf(
            "private_", "seed_", "entropy_", "derivation_",
            "wallet_secret_quarantine:security_source_",
            "wallet_secret_quarantine:private_", "wallet_secret_quarantine:seed_",
            "wallet_secret_quarantine:entropy_", "wallet_secret_quarantine:derivation_",
            "wallet_secret_quarantine:legacy_v1_meta_",
            "wallet_secret_quarantine:legacy_v04_meta_",
            "wallet_secret_quarantine:legacy_v1_public_"
        )
        check(
            encryptedPreferences.keysWithPrefixes(
                prefixes = prefixes,
                maxResultCount = 4_096,
                maxKeyBytes = 256,
                maxTotalKeyBytes = 524_288,
                failOnOversizedMatch = true
            ).isEmpty()
        ) { "Unsupported or quarantined legacy wallet material is present" }
    }

    /** Unknown, quarantined and orphaned wallet-scoped keys must never be silently omitted. */
    fun walletSecretNamespaces(wallets: List<ExportWalletIdentity>): Map<Long, Set<String>> {
        // An exact scan of the current Room IDs misses active V2/V3 ciphertext
        // whose owner row was deleted. Reserve the numeric wallet-key grammar
        // globally, while ignoring ordinary numeric-prefixed preference names.
        val prefixes = buildSet {
            ('0'..'9').mapTo(this, Char::toString)
            add(WalletSecretQuarantine.KEY_PREFIX)
            add(WalletPublicIdentityRecovery.KEY_PREFIX)
        }
        val keys = encryptedPreferences.keysWithPrefixes(
            prefixes = prefixes,
            maxResultCount = 8_192,
            maxKeyBytes = 1_024,
            maxTotalKeyBytes = 1_048_576,
            failOnOversizedMatch = true
        )
        check(keys.all { key -> prefixes.any(key::startsWith) }) {
            "Wallet secret namespace enumeration returned an unrelated key"
        }
        val ownedPrefixes = wallets.map { "${it.id}:" }
        val hasOrphan = keys.any { key ->
            val recoveryNamespace = key.startsWith(WalletSecretQuarantine.KEY_PREFIX) ||
                key.startsWith(WalletPublicIdentityRecovery.KEY_PREFIX)
            val orphanActive = ownedPrefixes.none(key::startsWith) &&
                isOrphanWalletSecretNamespace(key)
            recoveryNamespace || orphanActive
        }
        check(!hasOrphan) { "An orphaned or quarantined wallet secret namespace is present" }
        return wallets.associate { wallet ->
            wallet.id to keys.filterTo(linkedSetOf()) { key ->
                key.startsWith("${wallet.id}:")
            }
        }
    }

    private fun isOrphanWalletSecretNamespace(key: String): Boolean {
        val separator = key.indexOf(':')
        if (separator <= 0 || key.first() !in '0'..'9') return false
        val suffix = key.substring(separator + 1)
        return suffix in ROOT_SECRET_SUFFIXES || suffix.endsWith(":ACCESS_SECRETS")
    }

    fun requireExactSourceInventory(
        identities: List<ExportWalletIdentity>,
        projected: List<PortableWalletSemanticMaterial.Wallet>,
        secretNamespace: Map<Long, Set<String>>,
        approvedGenesis: List<PortableWalletChainSigningProof.ApprovedGenesis>
    ) {
        val role = PortableWalletSemanticMaterial.Role
        val signedGenesis = projected.flatMap { wallet ->
            wallet.slots.filter { it.role == role.CHAIN_ACCOUNT }.map { it.key }
        }.toSet()
        check(
            approvedGenesis.map { it.id }.toSet() == signedGenesis &&
                approvedGenesis.size == signedGenesis.size
        ) { "Approved chain-source policy differs from the captured cohort" }
        check(identities.size == projected.size) { "Projected wallet inventory is incomplete" }
        identities.zip(projected).forEach { (identity, wallet) ->
            val expected = wallet.slots.mapNotNull { slot -> sourceKey(identity.id, slot) }.toSet()
            check(secretNamespace[identity.id] == expected) {
                "A wallet has missing, orphaned or unsupported original-secret namespaces"
            }
        }
    }

    private fun sourceKey(metaId: Long, slot: PortableWalletSemanticMaterial.Slot): String? {
        val role = PortableWalletSemanticMaterial.Role
        return when (slot.role) {
            role.SUBSTRATE_ROOT -> "$metaId:SUBSTRATE_SECRETS"
            role.EVM_ROOT -> "$metaId:ETHEREUM_SECRETS"
            role.TON_ROOT -> "$metaId:TON_SECRETS"
            role.CHAIN_ACCOUNT -> {
                val accountId = slot.fields.single {
                    it.id == PortableWalletSemanticMaterial.FieldId.ACCOUNT_ID_OR_ADDRESS
                }.value
                "$metaId:${accountId.toHexString()}:ACCESS_SECRETS"
            }
            else -> null
        }
    }

    private companion object {
        val ROOT_SECRET_SUFFIXES = setOf(
            "ACCESS_SECRETS",
            "SUBSTRATE_SECRETS",
            "ETHEREUM_SECRETS",
            "TON_SECRETS"
        )
    }
}
