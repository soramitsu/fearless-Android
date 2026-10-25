package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.account.impl.data.repository.PortableWalletCohortAfterImage.Kind
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import java.util.Collections

/**
 * A pure receiving projection of one exact FPWCAI01 after-image. It names every public row and
 * secret source without publishing Room rows or encrypted preferences. This is a logical intent,
 * not a Room-ready row: the fresh-install position policy does not merge existing local wallets,
 * backup flags have no semantic field, and custody needs a verified public-identity digest. A semantic
 * root is never re-derived from a phrase and an opaque original-source sidecar is never discarded.
 * The permanent installer and source blockers from the receiving plan remain attached.
 */
internal object PortableWalletCohortStorageProjection {
    private val codec = PortableWalletSemanticMaterial
    private val role = PortableWalletSemanticMaterial.Role
    private val field = PortableWalletSemanticMaterial.FieldId
    private const val BYTE_MASK = 0xff

    internal enum class Custody { SIGNED, WATCH }

    /** FPWMSM01 list order is authoritative. Source positions are historical uint32 evidence. */
    internal object FreshInstallPositionPolicy {
        private const val MAX_WALLETS = 128
        private const val MAX_SOURCE_POSITION = 0xffff_ffffL

        fun project(sourcePositions: List<Long>): List<Int> {
            require(sourcePositions.size in 1..MAX_WALLETS) { "Receiving wallet count is invalid" }
            require(sourcePositions.all { it in 0..MAX_SOURCE_POSITION }) {
                "Receiving source position is not uint32"
            }
            return sourcePositions.indices.toList()
        }
    }

    internal class RootFields(
        val substratePublicKey: ByteArray?,
        val substrateCryptoTypeCode: Int?,
        val substrateAccountId: ByteArray?,
        val ethereumPublicKey: ByteArray?,
        val ethereumAddress: ByteArray?,
        val tonPublicKey: ByteArray?,
    )

    internal class Projection internal constructor(
        wallets: List<WalletRowIntent>,
        secrets: List<SecretIntent>,
        originals: List<OriginalSourceIntent>,
        blockers: List<PortableWalletReceiveInstallPlan.Blocker>,
    ) {
        val wallets: List<WalletRowIntent> = Collections.unmodifiableList(wallets.toList())
        val secrets: List<SecretIntent> = Collections.unmodifiableList(secrets.toList())
        val originals: List<OriginalSourceIntent> = Collections.unmodifiableList(originals.toList())
        val blockers: List<PortableWalletReceiveInstallPlan.Blocker> =
            Collections.unmodifiableList(blockers.toList())

        fun clearSecrets() {
            wallets.forEach(WalletRowIntent::clearSecrets)
            secrets.forEach(SecretIntent::clearSecrets)
            originals.forEach(OriginalSourceIntent::clearSecrets)
        }

        override fun toString(): String = "PortableWalletCohortStorageProjection.Projection(redacted)"
    }

    internal class WalletRowIntent internal constructor(
        val localMetaId: Long,
        source: PortableWalletSemanticMaterial.Wallet,
        val selected: Boolean,
        val freshInstallRoomPosition: Int,
        val custody: Custody,
        val chains: List<ChainRowIntent>,
        val favorites: List<FavoriteRowIntent>,
        val metadata: List<MetadataIntent>,
        val watchIdentities: List<SourceFieldsIntent>,
        roots: RootFields,
    ) {
        private val portableId = source.portableId.copyOf()
        private val substratePublic = roots.substratePublicKey?.copyOf()
        private val substrateAccount = roots.substrateAccountId?.copyOf()
        private val ethereumPublic = roots.ethereumPublicKey?.copyOf()
        private val ethereumAccount = roots.ethereumAddress?.copyOf()
        private val tonPublic = roots.tonPublicKey?.copyOf()

        val sourcePosition: Long = source.sourcePosition
        val name: String = source.name
        val initialized: Boolean = source.initialized
        val substrateCryptoTypeCode: Int? = roots.substrateCryptoTypeCode

        fun portableIdCopy(): ByteArray = portableId.copyOf()
        fun substratePublicKeyCopy(): ByteArray? = substratePublic?.copyOf()
        fun substrateAccountIdCopy(): ByteArray? = substrateAccount?.copyOf()
        fun ethereumPublicKeyCopy(): ByteArray? = ethereumPublic?.copyOf()
        fun ethereumAddressCopy(): ByteArray? = ethereumAccount?.copyOf()
        fun tonPublicKeyCopy(): ByteArray? = tonPublic?.copyOf()

        internal fun clearSecrets() {
            portableId.fill(0)
            substratePublic?.fill(0)
            substrateAccount?.fill(0)
            ethereumPublic?.fill(0)
            ethereumAccount?.fill(0)
            tonPublic?.fill(0)
            chains.forEach(ChainRowIntent::clearSecrets)
            metadata.forEach(MetadataIntent::clearSecrets)
            watchIdentities.forEach(SourceFieldsIntent::clearSecrets)
        }

        override fun toString(): String = "PortableWalletCohortStorageProjection.WalletRowIntent(redacted)"
    }

    internal class ChainRowIntent internal constructor(
        val localMetaId: Long,
        source: PortableWalletSemanticMaterial.Slot,
    ) {
        val chainId: String = source.key
        val name: String = source.text(field.CHAIN_NAME)
        val initialized: Boolean = source.flag(field.INITIALIZED_OR_FAVORITE)
        val cryptoTypeCode: Int = source.number(field.CRYPTO_TYPE)
        private val publicKey = source.value(field.PUBLIC_KEY).copyOf()
        private val accountId = source.value(field.ACCOUNT_ID_OR_ADDRESS).copyOf()

        fun publicKeyCopy(): ByteArray = publicKey.copyOf()
        fun accountIdCopy(): ByteArray = accountId.copyOf()

        internal fun clearSecrets() {
            publicKey.fill(0)
            accountId.fill(0)
        }

        override fun toString(): String = "PortableWalletCohortStorageProjection.ChainRowIntent(redacted)"
    }

    internal data class FavoriteRowIntent(val localMetaId: Long, val chainId: String, val isFavorite: Boolean) {
        override fun toString(): String = "PortableWalletCohortStorageProjection.FavoriteRowIntent(redacted)"
    }

    internal class MetadataIntent internal constructor(val localMetaId: Long, source: PortableWalletSemanticMaterial.Metadata) {
        val id: Int = source.id
        private val bytes = source.value.copyOf()
        fun valueCopy(): ByteArray = bytes.copyOf()
        internal fun clearSecrets() = bytes.fill(0)
        override fun toString(): String = "PortableWalletCohortStorageProjection.MetadataIntent(redacted)"
    }

    /** One exact semantic slot. A future installer must prove its stored encoding and identity. */
    internal open class SourceFieldsIntent internal constructor(source: PortableWalletSemanticMaterial.Slot) {
        val roleCode: Int = source.role
        val slotKey: String = source.key
        private val fields = source.fields.map { it.id to it.value.copyOf() }

        fun fieldCopy(id: Int): ByteArray? = fields.singleOrNull { it.first == id }?.second?.copyOf()
        fun fieldIds(): List<Int> = fields.map(Pair<Int, ByteArray>::first)
        internal fun clearSecrets() = fields.forEach { it.second.fill(0) }
        override fun toString(): String = "PortableWalletCohortStorageProjection.SourceFieldsIntent(redacted)"
    }

    internal class SecretIntent internal constructor(
        val localMetaId: Long,
        val destinationKey: String,
        val destination: Kind,
        source: PortableWalletSemanticMaterial.Slot,
    ) : SourceFieldsIntent(source) {
        override fun toString(): String = "PortableWalletCohortStorageProjection.SecretIntent(redacted)"
    }

    /** Retains iOS Keychain and Android V2/V3 exact source bytes with their binding fields. */
    internal class OriginalSourceIntent internal constructor(
        val localMetaId: Long,
        source: PortableWalletSemanticMaterial.Slot,
    ) : SourceFieldsIntent(source) {
        fun sourceBytesCopy(): ByteArray = requireNotNull(fieldCopy(field.SOURCE_BYTES))
        override fun toString(): String = "PortableWalletCohortStorageProjection.OriginalSourceIntent(redacted)"
    }

    fun project(afterImage: PortableWalletCohortAfterImage.Record): Projection {
        val semantic = afterImage.semanticCopy()
        val ids = afterImage.localMetaIdsCopy()
        var checked: PortableWalletCohortAfterImage.Record? = null
        try {
            checked = PortableWalletCohortAfterImage.revalidate(afterImage)
            val source = codec.decode(semantic)
            try {
                return projectValidated(source, ids, checked)
            } finally {
                source.clearSecrets()
            }
        } finally {
            checked?.clearSecrets()
            semantic.fill(0)
            ids.fill(0)
        }
    }

    private fun projectValidated(
        source: PortableWalletSemanticMaterial.Snapshot,
        ids: LongArray,
        checked: PortableWalletCohortAfterImage.Record,
    ): Projection {
        val wallets = ArrayList<WalletRowIntent>()
        val secrets = ArrayList<SecretIntent>()
        val originals = ArrayList<OriginalSourceIntent>()
        try {
            val roomPositions = FreshInstallPositionPolicy.project(source.wallets.map { it.sourcePosition })
            source.wallets.forEachIndexed { index, wallet ->
                val localId = ids[index]
                val watchSlots = wallet.slots.filter { it.role == role.WATCH_IDENTITY }
                val signed = wallet.slots.any { it.role in role.SUBSTRATE_ROOT..role.CHAIN_ACCOUNT }
                require(signed || watchSlots.isNotEmpty()) { "Receiving wallet has no custody identity" }
                val substrate = wallet.slots.filter { it.role == role.SUBSTRATE_ROOT || it.role == role.LEGACY_SUBSTRATE }
                requireMatchingIdentity(substrate, field.PUBLIC_KEY)
                requireMatchingIdentity(substrate, field.CRYPTO_TYPE)
                val substratePublic = substrate.firstOrNull()?.value(field.PUBLIC_KEY)
                val substrateAccount = substrate.firstOrNull()?.accountId()
                val evm = wallet.slots.singleOrNull { it.role == role.EVM_ROOT }
                val ton = wallet.slots.singleOrNull { it.role == role.TON_ROOT }
                val chainRows = wallet.slots.filter { it.role == role.CHAIN_ACCOUNT }
                    .map { ChainRowIntent(localId, it) }
                val favorites = wallet.slots.filter { it.role == role.FAVORITE_CHAIN }
                    .map { FavoriteRowIntent(localId, it.key, it.flag(field.INITIALIZED_OR_FAVORITE)) }
                val metadata = wallet.metadata.map { MetadataIntent(localId, it) }
                val watch = watchSlots.map(::SourceFieldsIntent)
                val roots = RootFields(
                    substratePublic,
                    substrate.firstOrNull()?.number(field.CRYPTO_TYPE),
                    substrateAccount,
                    evm?.value(field.PUBLIC_KEY),
                    evm?.value(field.ACCOUNT_ID_OR_ADDRESS),
                    ton?.value(field.PUBLIC_KEY),
                )
                wallets += WalletRowIntent(
                    localId, wallet, index == source.selectedIndex, roomPositions[index],
                    if (signed) Custody.SIGNED else Custody.WATCH,
                    Collections.unmodifiableList(chainRows), Collections.unmodifiableList(favorites),
                    Collections.unmodifiableList(metadata), Collections.unmodifiableList(watch),
                    roots,
                )
                wallet.slots.forEach { slot ->
                    when (slot.role) {
                        in role.SUBSTRATE_ROOT..role.CHAIN_ACCOUNT -> {
                            val destination = checked.destinations.single {
                                it.walletIndex == index && it.slotKey == slot.key && it.kind == kindFor(slot.role)
                            }
                            secrets += SecretIntent(
                                localId,
                                requireNotNull(destination.candidateSecretKey),
                                destination.kind,
                                slot,
                            )
                        }
                        role.AUXILIARY_SOURCE -> originals += OriginalSourceIntent(localId, slot)
                    }
                }
            }
            requireNoCohortIdentityCollisions(wallets)
            require(secrets.map(SecretIntent::destinationKey).distinct().size == secrets.size) {
                "Receiving secret destination is duplicated"
            }
            return Projection(wallets, secrets, originals, checked.blockers)
        } catch (failure: Exception) {
            wallets.forEach(WalletRowIntent::clearSecrets)
            secrets.forEach(SecretIntent::clearSecrets)
            originals.forEach(OriginalSourceIntent::clearSecrets)
            throw failure
        }
    }

    private fun requireMatchingIdentity(slots: List<PortableWalletSemanticMaterial.Slot>, id: Int) {
        if (slots.size < 2) return
        require(slots.all { it.value(id).contentEquals(slots.first().value(id)) }) {
            "Receiving V1 and V3 Substrate field identities differ"
        }
        if (id != field.PUBLIC_KEY) return
        val firstAccount = slots.first().accountId()
        try {
            slots.drop(1).forEach { requireSameAccount(firstAccount, it) }
        } finally {
            firstAccount.fill(0)
        }
    }

    private fun requireSameAccount(firstAccount: ByteArray, slot: PortableWalletSemanticMaterial.Slot) {
        val otherAccount = slot.accountId()
        try {
            require(otherAccount.contentEquals(firstAccount)) {
                "Receiving V1 and V3 Substrate account identities differ"
            }
        } finally {
            otherAccount.fill(0)
        }
    }

    private fun requireNoCohortIdentityCollisions(wallets: List<WalletRowIntent>) {
        wallets.forEachIndexed { index, wallet ->
            wallets.take(index).forEach { prior ->
                require(
                    !samePresent(wallet.substrateAccountIdCopy(), prior.substrateAccountIdCopy()) &&
                    !samePresent(wallet.ethereumAddressCopy(), prior.ethereumAddressCopy()) &&
                    !samePresent(wallet.tonPublicKeyCopy(), prior.tonPublicKeyCopy())
                ) {
                    "Receiving public wallet identity is duplicated"
                }
                wallet.chains.forEach { chain ->
                    prior.chains.forEach { priorChain ->
                        require(
                            chain.chainId != priorChain.chainId ||
                            !samePresent(chain.accountIdCopy(), priorChain.accountIdCopy())
                        ) {
                            "Receiving chain account identity is duplicated"
                        }
                    }
                }
            }
        }
    }

    private fun samePresent(left: ByteArray?, right: ByteArray?): Boolean =
        left != null && right != null && left.contentEquals(right)

    private fun PortableWalletSemanticMaterial.Slot.accountId(): ByteArray =
        if (role == PortableWalletSemanticMaterial.Role.LEGACY_SUBSTRATE) {
            text(field.ACCOUNT_ID_OR_ADDRESS).toAccountId()
        } else {
            value(field.ACCOUNT_ID_OR_ADDRESS).copyOf()
        }

    private fun PortableWalletSemanticMaterial.Slot.value(id: Int): ByteArray = fields.single { it.id == id }.value
    private fun PortableWalletSemanticMaterial.Slot.number(id: Int): Int = value(id)[0].toInt() and BYTE_MASK
    private fun PortableWalletSemanticMaterial.Slot.flag(id: Int): Boolean = number(id) == 1
    private fun PortableWalletSemanticMaterial.Slot.text(id: Int): String =
        value(id).decodeToString(throwOnInvalidSequence = true)

    private fun kindFor(slotRole: Int): Kind = when (slotRole) {
        role.SUBSTRATE_ROOT -> Kind.V3_SUBSTRATE
        role.EVM_ROOT -> Kind.V3_EVM
        role.TON_ROOT -> Kind.V3_TON
        role.LEGACY_SUBSTRATE -> Kind.V1_LEGACY_SOURCE
        role.CHAIN_ACCOUNT -> Kind.V2_CHAIN
        else -> throw IllegalArgumentException("Receiving secret role is unsupported")
    }
}
