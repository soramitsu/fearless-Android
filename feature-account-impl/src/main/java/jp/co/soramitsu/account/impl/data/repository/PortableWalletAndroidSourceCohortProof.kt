package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.account.impl.data.repository.PortableWalletChainSigningProof.ApprovedGenesis

/**
 * Read-only proof that every Android signing slot in a mixed FPWMSM01 capture has its original
 * V1, V2 or V3 export source and can sign for its recorded identity. Public watch and favorite
 * slots receive structural validation from the semantic codec, not an installation proof.
 * The caller must separately bind mapped display metadata to authoritative preference reads.
 * This does not prove an installed replacement wallet, complete UX metadata, or backup success.
 * The caller owns and must erase [encoded] after use.
 */
@Suppress("MagicNumber") // Fixed semantic source roles and encoded one-byte tags.
internal object PortableWalletAndroidSourceCohortProof {
    private val codec = PortableWalletSemanticMaterial
    private val role = PortableWalletSemanticMaterial.Role
    private val field = PortableWalletSemanticMaterial.FieldId
    private val metadata = PortableWalletSemanticMaterial.MetadataId

    internal class Counts(
        val wallets: Int,
        val signedWallets: Int,
        val watchWallets: Int,
        val v3Roots: Int,
        val v1Sources: Int,
        val v2Chains: Int,
        val publicFavorites: Int,
    ) {
        override fun toString(): String = "PortableWalletAndroidSourceCohortProof.Counts(redacted)"
    }

    fun verify(encoded: ByteArray, approvedGenesis: List<ApprovedGenesis>): Counts {
        val stable = encoded.copyOf()
        try {
            return verifyStable(stable, approvedGenesis)
        } finally {
            stable.fill(0)
        }
    }

    private fun verifyStable(encoded: ByteArray, approvedGenesis: List<ApprovedGenesis>): Counts {
        val decoded = codec.decode(encoded)
        val counts = try {
            inspect(decoded)
        } finally {
            decoded.clearSecrets()
        }
        val roots = PortableWalletRootSigningProof.verify(encoded)
        require(counts.v3Roots == roots.substrateRoots + roots.evmRoots + roots.nativeTonRoots) {
            "Android root proof inventory changed"
        }
        if (counts.v3Roots > 0) {
            require(PortableWalletV3OriginalSourceProof.verifyEmbedded(encoded).exactAndroidRoots == counts.v3Roots) {
                "Android V3 source proof is incomplete"
            }
        }
        if (counts.v1Sources > 0) {
            require(PortableWalletV1SourceProof.verify(encoded).provedLegacySources == counts.v1Sources) {
                "Android V1 source proof is incomplete"
            }
        }
        if (counts.v2Chains > 0) {
            require(PortableWalletChainSigningProof.verify(encoded, approvedGenesis).exactOriginalSources == counts.v2Chains) {
                "Android V2 source proof is incomplete"
            }
        } else {
            require(approvedGenesis.isEmpty()) { "Unused chain proof policy is not accepted" }
        }
        return counts
    }

    private fun inspect(snapshot: PortableWalletSemanticMaterial.Snapshot): Counts {
        var signedWallets = 0
        var watchWallets = 0
        var roots = 0
        var legacy = 0
        var chains = 0
        var favorites = 0
        snapshot.wallets.forEach { wallet ->
            require(
                wallet.metadata.all {
                    it.id == metadata.ANDROID_SELECTED_CHAIN_ID ||
                        it.id == metadata.ANDROID_CHAIN_SELECT_FILTER
                }
            ) { "Android wallet metadata has no source proof" }
            val rootSlots = wallet.slots.count { it.role in role.SUBSTRATE_ROOT..role.TON_ROOT }
            val legacySlots = wallet.slots.count { it.role == role.LEGACY_SUBSTRATE }
            val chainSlots = wallet.slots.count { it.role == role.CHAIN_ACCOUNT }
            val watchSlots = wallet.slots.count { it.role == role.WATCH_IDENTITY }
            val originals = wallet.slots.filter { it.role == role.AUXILIARY_SOURCE }
            val signedSlots = rootSlots + legacySlots + chainSlots
            val hasSigner = signedSlots > 0
            val hasWatch = watchSlots > 0
            require(hasSigner != hasWatch) {
                "Android wallet has mixed or missing custody material"
            }
            originals.forEach { source ->
                val platform = source.number(field.SOURCE_PLATFORM)
                val sourceRole = source.number(field.SOURCE_SLOT_ROLE)
                require(platform == 1 && sourceRole in 11..14) {
                    "Android original source kind is unproved"
                }
            }
            require(originals.size == rootSlots + chainSlots) {
                "Android original source inventory is incomplete or contains an orphan"
            }
            if (signedSlots > 0) signedWallets++ else watchWallets++
            roots += rootSlots
            legacy += legacySlots
            chains += chainSlots
            favorites += wallet.slots.count { it.role == role.FAVORITE_CHAIN }
        }
        return Counts(snapshot.wallets.size, signedWallets, watchWallets, roots, legacy, chains, favorites)
    }

    private fun PortableWalletSemanticMaterial.Slot.number(id: Int): Int =
        fields.single { it.id == id }.value[0].toInt() and 0xff
}
