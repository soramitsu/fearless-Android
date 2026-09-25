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
    internal data class DisplayPreferences(
        val selectedChainId: String?,
        val chainSelectFilter: String?
    ) {
        fun toSemanticMetadata(): List<PortableWalletSemanticMaterial.Metadata> {
            val metadata = ArrayList<PortableWalletSemanticMaterial.Metadata>(2)
            try {
                selectedChainId?.let { value ->
                    metadata += PortableWalletSemanticMaterial.Metadata(
                        PortableWalletSemanticMaterial.MetadataId.ANDROID_SELECTED_CHAIN_ID,
                        PortableWalletSemanticMaterial.encodeMetadataText(value)
                    )
                }
                chainSelectFilter?.let { value ->
                    metadata += PortableWalletSemanticMaterial.Metadata(
                        PortableWalletSemanticMaterial.MetadataId.ANDROID_CHAIN_SELECT_FILTER,
                        PortableWalletSemanticMaterial.encodeMetadataText(value)
                    )
                }
                return metadata
            } catch (failure: Exception) {
                metadata.forEach { it.value.fill(0) }
                throw failure
            }
        }
    }

    /** Capture only the two mapped wallet preferences; asset-row presentation remains blocked. */
    suspend fun captureDisplayPreferences(wallets: List<ExportWalletIdentity>): Map<Long, DisplayPreferences> =
        wallets.associate { wallet ->
            check(!assetDao.hasUnmappedWalletAssetPreferences(wallet.id)) {
                "A wallet has asset presentation metadata without a portable source mapping"
            }
            wallet.id to DisplayPreferences(
                exactOptionalString("wallet_selected_chain_id${wallet.id}"),
                exactOptionalString("chain_select_filter_applied_${wallet.id}")
            )
        }

    fun withDisplayMetadata(
        wallet: PortableWalletSemanticMaterial.Wallet,
        display: DisplayPreferences
    ): PortableWalletSemanticMaterial.Wallet = try {
        PortableWalletSemanticMaterial.Wallet(
            portableId = wallet.portableId,
            sourcePosition = wallet.sourcePosition,
            initialized = wallet.initialized,
            name = wallet.name,
            metadata = display.toSemanticMetadata(),
            slots = wallet.slots
        )
    } catch (failure: Exception) {
        wallet.clearSecrets()
        throw failure
    }

    private fun exactOptionalString(key: String): String? {
        if (!preferences.contains(key)) return null
        val value = checkNotNull(preferences.getString(key)) {
            "A present wallet display preference is not a string"
        }
        PortableWalletSemanticMaterial.encodeMetadataText(value).fill(0)
        return value
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
