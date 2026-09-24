package jp.co.soramitsu.account.impl.data.repository

import java.util.Collections

/**
 * A read-only, lossless intent for receiving FPWMSM01 material. This object has no storage or
 * installer dependency. Every plan is blocked until a cohort-wide transactional installer and
 * original-source verification exist.
 */
internal object PortableWalletReceiveInstallPlan {
    private val codec = PortableWalletSemanticMaterial
    private val role = PortableWalletSemanticMaterial.Role

    internal enum class Destination {
        // Logical receiving families, not bytes authorized for a secret-store write.
        V3_SUBSTRATE,
        V3_EVM,
        V3_TON,
        V1_LEGACY_SOURCE,
        V2_CHAIN,
        FAVORITE_CHAIN,
        ORIGINAL_SOURCE,
        WATCH_IDENTITY,
    }

    internal enum class BlockerReason {
        TRANSACTIONAL_INSTALLER_UNAVAILABLE,
        ROOT_RECOVERY_UNPROVEN,
        V1_SOURCE_UNPROVEN,
        V2_CHAIN_UNPROVEN,
        FAVORITE_CHAIN_UNMAPPED,
        ORIGINAL_SOURCE_UNPROVEN,
        WATCH_IDENTITY_UNPROVEN,
        METADATA_UNMAPPED,
    }

    internal data class Blocker(
        val reason: BlockerReason,
        val walletIndex: Int? = null,
        val slotRole: Int? = null,
        val slotKey: String? = null,
        val metadataId: Int? = null,
    ) {
        override fun toString(): String = "PortableWalletReceiveInstallPlan.Blocker(redacted)"
    }

    internal class Plan internal constructor(
        val selectedIndex: Int,
        wallets: List<WalletIntent>,
        blockers: List<Blocker>,
    ) {
        private var cleared = false
        val wallets: List<WalletIntent> = Collections.unmodifiableList(wallets.toList())
        val blockers: List<Blocker> = Collections.unmodifiableList(blockers.toList())

        /** The caller owns and must erase the returned secret-bearing snapshot. */
        fun snapshotCopy(): PortableWalletSemanticMaterial.Snapshot {
            check(!cleared) { "Receiving plan has been cleared" }
            return PortableWalletSemanticMaterial.Snapshot(selectedIndex, wallets.map(WalletIntent::snapshotCopy))
        }

        fun clearSecrets() {
            if (!cleared) {
                wallets.forEach(WalletIntent::clearSecrets)
                cleared = true
            }
        }

        override fun toString(): String = "PortableWalletReceiveInstallPlan.Plan(redacted)"
    }

    internal class WalletIntent internal constructor(source: PortableWalletSemanticMaterial.Wallet) {
        private val id = source.portableId.copyOf()
        val sourcePosition: Long = source.sourcePosition
        val initialized: Boolean = source.initialized
        val name: String = source.name
        val metadata: List<MetadataIntent> = Collections.unmodifiableList(source.metadata.map(::MetadataIntent))
        val materials: List<MaterialIntent> = Collections.unmodifiableList(source.slots.map(::MaterialIntent))

        fun portableIdCopy(): ByteArray = id.copyOf()

        internal fun snapshotCopy() = PortableWalletSemanticMaterial.Wallet(
            id.copyOf(),
            sourcePosition,
            initialized,
            name,
            metadata.map(MetadataIntent::snapshotCopy),
            materials.map(MaterialIntent::snapshotCopy),
        )

        internal fun clearSecrets() {
            id.fill(0)
            metadata.forEach(MetadataIntent::clearSecrets)
            materials.forEach(MaterialIntent::clearSecrets)
        }

        override fun toString(): String = "PortableWalletReceiveInstallPlan.WalletIntent(redacted)"
    }

    internal class MetadataIntent internal constructor(source: PortableWalletSemanticMaterial.Metadata) {
        val id: Int = source.id
        private val bytes = source.value.copyOf()

        fun valueCopy(): ByteArray = bytes.copyOf()

        internal fun snapshotCopy() = PortableWalletSemanticMaterial.Metadata(id, valueCopy())

        internal fun clearSecrets() = bytes.fill(0)

        override fun toString(): String = "PortableWalletReceiveInstallPlan.MetadataIntent(redacted)"
    }

    internal class MaterialIntent internal constructor(source: PortableWalletSemanticMaterial.Slot) {
        val destination: Destination = destinationFor(source.role)
        val role: Int = source.role
        val key: String = source.key
        val fields: List<FieldIntent> = Collections.unmodifiableList(source.fields.map(::FieldIntent))

        fun fieldValueCopy(id: Int): ByteArray? = fields.singleOrNull { it.id == id }?.valueCopy()

        internal fun snapshotCopy() = PortableWalletSemanticMaterial.Slot(
            role,
            key,
            fields.map(FieldIntent::snapshotCopy),
        )

        internal fun clearSecrets() = fields.forEach(FieldIntent::clearSecrets)

        override fun toString(): String = "PortableWalletReceiveInstallPlan.MaterialIntent(redacted)"
    }

    internal class FieldIntent internal constructor(source: PortableWalletSemanticMaterial.Field) {
        val id: Int = source.id
        private val bytes = source.value.copyOf()

        fun valueCopy(): ByteArray = bytes.copyOf()

        internal fun snapshotCopy() = PortableWalletSemanticMaterial.Field(id, valueCopy())

        internal fun clearSecrets() = bytes.fill(0)

        override fun toString(): String = "PortableWalletReceiveInstallPlan.FieldIntent(redacted)"
    }

    fun decode(encoded: ByteArray): Plan {
        val proof = PortableWalletRootSigningProof.verify(encoded)
        val source = codec.decode(encoded)
        try {
            val wallets = source.wallets.map(::WalletIntent)
            val blockers = buildBlockers(source, proof)
            return Plan(source.selectedIndex, wallets, blockers)
        } finally {
            source.clearSecrets()
        }
    }

    private fun buildBlockers(
        source: PortableWalletSemanticMaterial.Snapshot,
        proof: PortableWalletRootSigningProof.Counts,
    ): List<Blocker> = buildList {
        add(Blocker(BlockerReason.TRANSACTIONAL_INSTALLER_UNAVAILABLE))
        if (proof.unprovenRecoveryFields > 0) add(Blocker(BlockerReason.ROOT_RECOVERY_UNPROVEN))
        source.wallets.forEachIndexed { walletIndex, wallet ->
            wallet.metadata.forEach { metadata ->
                add(Blocker(BlockerReason.METADATA_UNMAPPED, walletIndex, metadataId = metadata.id))
            }
            wallet.slots.forEach { slot ->
                val reason = when (slot.role) {
                    role.LEGACY_SUBSTRATE -> BlockerReason.V1_SOURCE_UNPROVEN
                    role.CHAIN_ACCOUNT -> BlockerReason.V2_CHAIN_UNPROVEN
                    role.FAVORITE_CHAIN -> BlockerReason.FAVORITE_CHAIN_UNMAPPED
                    role.AUXILIARY_SOURCE -> BlockerReason.ORIGINAL_SOURCE_UNPROVEN
                    role.WATCH_IDENTITY -> BlockerReason.WATCH_IDENTITY_UNPROVEN
                    else -> null
                }
                if (reason != null) add(Blocker(reason, walletIndex, slot.role, slot.key))
            }
        }
    }

    private fun destinationFor(slotRole: Int): Destination = when (slotRole) {
        role.SUBSTRATE_ROOT -> Destination.V3_SUBSTRATE
        role.EVM_ROOT -> Destination.V3_EVM
        role.TON_ROOT -> Destination.V3_TON
        role.LEGACY_SUBSTRATE -> Destination.V1_LEGACY_SOURCE
        role.CHAIN_ACCOUNT -> Destination.V2_CHAIN
        role.FAVORITE_CHAIN -> Destination.FAVORITE_CHAIN
        role.AUXILIARY_SOURCE -> Destination.ORIGINAL_SOURCE
        role.WATCH_IDENTITY -> Destination.WATCH_IDENTITY
        else -> throw IllegalArgumentException("Unsupported receiving material role")
    }
}
