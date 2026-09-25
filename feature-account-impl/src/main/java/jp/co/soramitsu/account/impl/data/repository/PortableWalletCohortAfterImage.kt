package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.account.impl.data.repository.PortableWalletReceiveInstallPlan.MaterialIntent
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import java.nio.ByteBuffer
import java.util.Collections

/**
 * Canonical, secret-bearing after-image for a future cohort mutation journal. It pins newly
 * allocated local wallet IDs to the ordered FPWMSM01 wallets and retains the entire semantic
 * payload, including original source bytes. This codec does not stage secrets or write Room rows.
 * Every result remains blocked by [PortableWalletReceiveInstallPlan].
 *
 * Wire: ASCII FPWCAI01, u8 version, v2 ASCII SHA-256 receiving-policy digest, u16 wallet count,
 * ordered u64 local IDs, u32 semantic length,
 * then the exact canonical FPWMSM01 bytes. All integers are unsigned big endian except that IDs
 * must fit a positive signed Long. No trailing bytes or duplicate destination keys are accepted.
 */
internal object PortableWalletCohortAfterImage {
    private val magic = "FPWCAI01".toByteArray(Charsets.US_ASCII)
    private val codec = PortableWalletSemanticMaterial
    private val field = PortableWalletSemanticMaterial.FieldId
    private const val legacyVersion = 1
    private const val version = 2
    private const val policyDigestBytes = 64
    private const val maxWallets = 128
    private const val maxSemanticBytes = 256 * 1024
    private const val headerBytes = 8 + 1 + 2 + 4
    internal const val MAX_ENCODED_BYTES = headerBytes + policyDigestBytes + maxWallets * Long.SIZE_BYTES + maxSemanticBytes
    private const val v1Prefix = "security_source_"
    private const val accessSuffix = "ACCESS_SECRETS"
    private const val unsignedShortMask = 0xffff
    private const val substrateAccountIdBytes = 32
    private const val ethereumAddressBytes = 20
    private const val byteMask = 0xff
    private const val hexNibbleShift = 4
    private const val hexNibbleMask = 0x0f
    private val hexDigits = "0123456789abcdef".toCharArray()

    internal enum class Kind {
        WALLET_ROW,
        V3_SUBSTRATE,
        V3_EVM,
        V3_TON,
        V1_LEGACY_SOURCE,
        V2_CHAIN,
        FAVORITE_CHAIN,
        ORIGINAL_SOURCE,
        WATCH_IDENTITY,
        METADATA,
        SELECTION,
    }

    internal data class Destination(
        val kind: Kind,
        val walletIndex: Int,
        val localMetaId: Long,
        val slotKey: String? = null,
        val metadataId: Int? = null,
        val candidateSecretKey: String? = null,
    ) {
        override fun toString(): String = "PortableWalletCohortAfterImage.Destination(redacted)"
    }

    // Kotlin's enclosing object cannot call a nested private constructor. Encoding independently
    // revalidates internal constructions so they cannot become a journal after-image.
    internal class Record internal constructor(
        localIds: LongArray,
        semantic: ByteArray,
        val selectedIndex: Int,
        destinations: List<Destination>,
        blockers: List<PortableWalletReceiveInstallPlan.Blocker>,
        val wireVersion: Int = version,
        val policySha256: String? = PortableWalletReceivingChainPolicy.SHA256,
    ) {
        private val ids = localIds.copyOf()
        private val semanticBytes = semantic.copyOf()
        private var cleared = false

        val destinations: List<Destination> = Collections.unmodifiableList(destinations.toList())
        val blockers: List<PortableWalletReceiveInstallPlan.Blocker> =
            Collections.unmodifiableList(blockers.toList())

        fun localMetaIdsCopy(): LongArray = ids.copyOf()

        fun semanticCopy(): ByteArray {
            check(!cleared) { "Cohort after-image has been cleared" }
            return semanticBytes.copyOf()
        }

        fun clearSecrets() {
            if (!cleared) {
                semanticBytes.fill(0)
                ids.fill(0)
                cleared = true
            }
        }

        override fun toString(): String = "PortableWalletCohortAfterImage.Record(redacted)"
    }

    fun create(semantic: ByteArray, localMetaIds: List<Long>): Record =
        createVersioned(semantic, localMetaIds, version, PortableWalletReceivingChainPolicy.SHA256)

    private fun createVersioned(
        semantic: ByteArray,
        localMetaIds: List<Long>,
        wireVersion: Int,
        policySha256: String?,
    ): Record {
        require(semantic.size in 1..maxSemanticBytes) { "Cohort semantic material size is invalid" }
        val approvedGenesis = when (wireVersion) {
            legacyVersion -> {
                require(policySha256 == null) { "Legacy cohort cannot assert a receiving policy" }
                emptyList()
            }
            version -> {
                require(policySha256 == PortableWalletReceivingChainPolicy.SHA256) {
                    "Cohort receiving policy is not supported by this build"
                }
                PortableWalletReceivingChainPolicy.approvedGenesis
            }
            else -> throw IllegalArgumentException("Cohort after-image version is unsupported")
        }
        val plan = PortableWalletReceiveInstallPlan.decode(semantic, approvedGenesis)
        try {
            require(localMetaIds.size == plan.wallets.size && localMetaIds.size in 1..maxWallets) {
                "Cohort wallet ID count is invalid"
            }
            require(localMetaIds.all { it > 0 } && localMetaIds.toSet().size == localMetaIds.size) {
                "Cohort wallet IDs are invalid or duplicated"
            }
            val snapshot = plan.snapshotCopy()
            val canonical = try {
                codec.encode(snapshot)
            } finally {
                snapshot.clearSecrets()
            }
            try {
                require(canonical.contentEquals(semantic)) { "Cohort semantic material is not canonical" }
                val destinations = destinationsFor(plan, localMetaIds)
                return Record(
                    localMetaIds.toLongArray(),
                    canonical,
                    plan.selectedIndex,
                    destinations,
                    plan.blockers,
                    wireVersion,
                    policySha256,
                )
            } finally {
                canonical.fill(0)
            }
        } finally {
            plan.clearSecrets()
        }
    }

    fun encode(record: Record): ByteArray {
        val semantic = record.semanticCopy()
        val ids = record.localMetaIdsCopy()
        try {
            val verified = revalidate(record)
            verified.clearSecrets()
            val policySize = if (record.wireVersion == version) policyDigestBytes else 0
            val size = headerBytes + policySize + ids.size * Long.SIZE_BYTES + semantic.size
            require(size <= MAX_ENCODED_BYTES) { "Cohort after-image is oversized" }
            val writer = ByteBuffer.allocate(size)
            writer.put(magic)
            writer.put(record.wireVersion.toByte())
            record.policySha256?.let { writer.put(it.toByteArray(Charsets.US_ASCII)) }
            writer.putShort(ids.size.toShort())
            ids.forEach(writer::putLong)
            writer.putInt(semantic.size)
            writer.put(semantic)
            return writer.array()
        } finally {
            semantic.fill(0)
            ids.fill(0)
        }
    }

    /** Preserves historical wire bytes and policy when validating an in-memory record. */
    fun revalidate(record: Record): Record {
        val semantic = record.semanticCopy()
        val ids = record.localMetaIdsCopy()
        var verified: Record? = null
        try {
            verified = createVersioned(semantic, ids.asList(), record.wireVersion, record.policySha256)
            require(
                verified.selectedIndex == record.selectedIndex &&
                    verified.destinations == record.destinations && verified.blockers == record.blockers
            ) { "Cohort after-image was not constructed from its canonical material" }
            return verified
        } catch (failure: Exception) {
            verified?.clearSecrets()
            throw failure
        } finally {
            semantic.fill(0)
            ids.fill(0)
        }
    }

    fun decode(encoded: ByteArray): Record {
        require(encoded.size in headerBytes + Long.SIZE_BYTES + 1..MAX_ENCODED_BYTES) {
            "Cohort after-image size is invalid"
        }
        val reader = ByteBuffer.wrap(encoded)
        val foundMagic = ByteArray(magic.size)
        reader.get(foundMagic)
        val wireVersion = reader.get().toInt()
        require(foundMagic.contentEquals(magic) && wireVersion in legacyVersion..version) {
            "Cohort after-image version is unsupported"
        }
        val policySha256 = if (wireVersion == version) {
            require(reader.remaining() >= policyDigestBytes + 2 + Long.SIZE_BYTES + Int.SIZE_BYTES + 1) {
                "Cohort receiving policy is truncated"
            }
            ByteArray(policyDigestBytes).also(reader::get).toString(Charsets.US_ASCII)
        } else {
            null
        }
        val count = reader.short.toInt() and unsignedShortMask
        require(count in 1..maxWallets && reader.remaining() >= count * Long.SIZE_BYTES + Int.SIZE_BYTES) {
            "Cohort after-image wallet count is invalid"
        }
        val ids = LongArray(count) { reader.long }
        val semanticSize = reader.int
        require(semanticSize in 1..maxSemanticBytes && semanticSize == reader.remaining()) {
            "Cohort after-image semantic length is invalid"
        }
        val semantic = ByteArray(semanticSize)
        reader.get(semantic)
        try {
            return createVersioned(semantic, ids.asList(), wireVersion, policySha256)
        } finally {
            semantic.fill(0)
            ids.fill(0)
        }
    }

    private fun destinationsFor(
        plan: PortableWalletReceiveInstallPlan.Plan,
        localMetaIds: List<Long>,
    ): List<Destination> {
        val result = ArrayList<Destination>()
        val candidateKeys = HashSet<String>()
        plan.wallets.forEachIndexed { index, wallet ->
            val metaId = localMetaIds[index]
            result += Destination(Kind.WALLET_ROW, index, metaId)
            wallet.metadata.forEach { metadata ->
                result += Destination(Kind.METADATA, index, metaId, metadataId = metadata.id)
            }
            wallet.materials.forEach { material ->
                val kind = kindFor(material.destination)
                val key = candidateKey(metaId, material)
                require(key == null || candidateKeys.add(key)) {
                    "Cohort secret destination is duplicated"
                }
                result += Destination(kind, index, metaId, material.key, candidateSecretKey = key)
            }
        }
        result += Destination(Kind.SELECTION, plan.selectedIndex, localMetaIds[plan.selectedIndex])
        return result
    }

    private fun candidateKey(metaId: Long, material: MaterialIntent): String? {
        return when (material.destination) {
            PortableWalletReceiveInstallPlan.Destination.V3_SUBSTRATE -> "$metaId:SUBSTRATE_SECRETS"
            PortableWalletReceiveInstallPlan.Destination.V3_EVM -> "$metaId:ETHEREUM_SECRETS"
            PortableWalletReceiveInstallPlan.Destination.V3_TON -> "$metaId:TON_SECRETS"
            PortableWalletReceiveInstallPlan.Destination.V1_LEGACY_SOURCE -> v1CandidateKey(material)
            PortableWalletReceiveInstallPlan.Destination.V2_CHAIN -> {
                val accountId = requireNotNull(material.fieldValueCopy(field.ACCOUNT_ID_OR_ADDRESS))
                try {
                    require(accountId.size == ethereumAddressBytes || accountId.size == substrateAccountIdBytes) {
                        "Cohort chain account ID cannot use the active V2 namespace"
                    }
                    "$metaId:${accountId.lowerHex()}:$accessSuffix"
                } finally {
                    accountId.fill(0)
                }
            }
            else -> null
        }
    }

    private fun v1CandidateKey(material: MaterialIntent): String {
        val addressBytes = requireNotNull(material.fieldValueCopy(field.ACCOUNT_ID_OR_ADDRESS))
        val publicKey = requireNotNull(material.fieldValueCopy(field.PUBLIC_KEY))
        try {
            val address = addressBytes.decodeToString(throwOnInvalidSequence = true)
            val accountId = address.toAccountId()
            val derivedAccountId = publicKey.substrateAccountId()
            try {
                require(
                    accountId.size == substrateAccountIdBytes &&
                        accountId.contentEquals(derivedAccountId)
                ) {
                    "Cohort V1 address is not bound to its public source"
                }
            } finally {
                accountId.fill(0)
                derivedAccountId.fill(0)
            }
            return "$v1Prefix$address"
        } finally {
            addressBytes.fill(0)
            publicKey.fill(0)
        }
    }

    private fun kindFor(destination: PortableWalletReceiveInstallPlan.Destination): Kind {
        return when (destination) {
            PortableWalletReceiveInstallPlan.Destination.V3_SUBSTRATE -> Kind.V3_SUBSTRATE
            PortableWalletReceiveInstallPlan.Destination.V3_EVM -> Kind.V3_EVM
            PortableWalletReceiveInstallPlan.Destination.V3_TON -> Kind.V3_TON
            PortableWalletReceiveInstallPlan.Destination.V1_LEGACY_SOURCE -> Kind.V1_LEGACY_SOURCE
            PortableWalletReceiveInstallPlan.Destination.V2_CHAIN -> Kind.V2_CHAIN
            PortableWalletReceiveInstallPlan.Destination.FAVORITE_CHAIN -> Kind.FAVORITE_CHAIN
            PortableWalletReceiveInstallPlan.Destination.ORIGINAL_SOURCE -> Kind.ORIGINAL_SOURCE
            PortableWalletReceiveInstallPlan.Destination.WATCH_IDENTITY -> Kind.WATCH_IDENTITY
        }
    }

    private fun ByteArray.lowerHex(): String = buildString(size * 2) {
        this@lowerHex.forEach { byte ->
            val unsigned = byte.toInt() and byteMask
            append(hexDigits[unsigned ushr hexNibbleShift])
            append(hexDigits[unsigned and hexNibbleMask])
        }
    }
}
