package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityRecovery
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
        val prefixes = wallets.flatMap { wallet ->
            listOf(
                "${wallet.id}:",
                "wallet_secret_quarantine:${wallet.id}:",
                "${WalletPublicIdentityRecovery.KEY_PREFIX}${wallet.id}:"
            )
        }.toSet()
        val keys = encryptedPreferences.keysWithPrefixes(
            prefixes = prefixes,
            maxResultCount = 4_096,
            maxKeyBytes = 1_024,
            maxTotalKeyBytes = 1_048_576,
            failOnOversizedMatch = true
        )
        check(keys.all { key -> prefixes.any(key::startsWith) }) {
            "Wallet secret namespace enumeration returned an unrelated key"
        }
        return wallets.associate { wallet ->
            wallet.id to keys.filterTo(linkedSetOf()) { key ->
                key.startsWith("${wallet.id}:") ||
                    key.startsWith("wallet_secret_quarantine:${wallet.id}:") ||
                    key.startsWith("${WalletPublicIdentityRecovery.KEY_PREFIX}${wallet.id}:")
            }
        }
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
}
