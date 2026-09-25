package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.isValidEthereumCompressedPublicKey
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.common.utils.tonAccountId
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.model.ChainAccountLocal
import jp.co.soramitsu.coredb.model.MetaAccountLocal
import jp.co.soramitsu.coredb.model.WalletCustodyBinding
import jp.co.soramitsu.coredb.model.WalletCustodyLocal

/** An absent marker is UNKNOWN. Neither an absent secret nor historical metadata proves WATCH. */
internal object WalletCustodyProvenance {
    enum class Kind { UNKNOWN, WATCH, SIGNED }

    private const val SHA256_BYTES = 32
    private const val SUBSTRATE_KEY_BYTES = 32
    private const val ECDSA_PUBLIC_KEY_BYTES = 33
    private const val EVM_ADDRESS_BYTES = 20
    private const val TON_PUBLIC_KEY_BYTES = 32
    private const val MAX_CHAIN_ACCOUNTS = 128
    private const val COMPLETE_SUBSTRATE_IDENTITY_PARTS = 3

    private val unsupportedNamedChains = listOf(
        UniversalWalletRegistry.bitcoinMainnet,
        UniversalWalletRegistry.bitcoinTestnet
    ).flatMap { listOf(it.id, it.chainId) }.toSet() + setOf(
        UniversalWalletRegistry.solanaMainnet.id,
        UniversalWalletRegistry.solanaMainnet.chainId,
        UniversalWalletRegistry.solanaDevnet.id,
        UniversalWalletRegistry.solanaDevnet.chainId,
        UniversalWalletRegistry.taira.id,
        UniversalWalletRegistry.taira.chainId,
        UniversalWalletRegistry.nexus.id,
        UniversalWalletRegistry.nexus.chainId,
        UniversalWalletRegistry.tonMainnetRegistryEntry.id,
        UniversalWalletRegistry.tonMainnetRegistryEntry.chainId
    )

    fun classify(
        meta: MetaAccountLocal,
        chains: List<ChainAccountLocal>,
        marker: WalletCustodyLocal?
    ): Kind {
        if (marker == null) return Kind.UNKNOWN
        require(marker.metaId == meta.id && marker.publicIdentitySha256.size == SHA256_BYTES) {
            "Wallet custody marker has an invalid owner or digest"
        }
        val expected = WalletCustodyBinding.sha256(
            meta,
            if (marker.kind == WalletCustodyLocal.SIGNED) emptyList() else chains
        )
        require(marker.publicIdentitySha256.contentEquals(expected)) {
            "Wallet custody marker no longer matches public identity"
        }
        return when (marker.kind) {
            WalletCustodyLocal.WATCH -> Kind.WATCH
            WalletCustodyLocal.SIGNED -> Kind.SIGNED
            else -> throw IllegalArgumentException("Wallet custody marker kind is unsupported")
        }
    }

    fun watchMarker(meta: MetaAccountLocal, chains: List<ChainAccountLocal>): WalletCustodyLocal {
        requireWatchPublicIdentity(meta, chains)
        return WalletCustodyLocal(
            metaId = meta.id,
            kind = WalletCustodyLocal.WATCH,
            publicIdentitySha256 = WalletCustodyBinding.sha256(meta, chains)
        )
    }

    fun signedMarker(meta: MetaAccountLocal): WalletCustodyLocal = WalletCustodyLocal(
        metaId = meta.id,
        kind = WalletCustodyLocal.SIGNED,
        publicIdentitySha256 = WalletCustodyBinding.sha256(meta, emptyList())
    )

    fun requireWatchPublicIdentity(meta: MetaAccountLocal, chains: List<ChainAccountLocal>) {
        require(meta.id > 0) { "A watch wallet must have a durable ID" }
        val substrate = listOf(meta.substratePublicKey, meta.substrateCryptoType, meta.substrateAccountId)
        require(substrate.count { it != null } in setOf(0, COMPLETE_SUBSTRATE_IDENTITY_PARTS)) {
            "Incomplete watch Substrate identity"
        }
        meta.substratePublicKey?.let { publicKey ->
            requireSubstrateIdentity(publicKey, meta.substrateCryptoType!!, meta.substrateAccountId!!)
        }
        require(meta.ethereumPublicKey == null || meta.ethereumAddress != null) {
            "Incomplete watch Ethereum identity"
        }
        meta.ethereumAddress?.let { address ->
            require(address.size == EVM_ADDRESS_BYTES) { "Invalid watch Ethereum address" }
            meta.ethereumPublicKey?.let { publicKey ->
                require(
                    publicKey.isValidEthereumCompressedPublicKey() &&
                        publicKey.ethereumAddressFromPublicKey().contentEquals(address)
                ) {
                    "Watch Ethereum key and address disagree"
                }
            }
        }
        meta.tonPublicKey?.let { publicKey ->
            require(publicKey.size == TON_PUBLIC_KEY_BYTES) { "Invalid watch TON public key" }
            publicKey.tonAccountId(isTestnet = false) // V4R2 derivation must be available.
        }
        require(
            meta.substratePublicKey != null || meta.ethereumAddress != null ||
                meta.tonPublicKey != null || chains.isNotEmpty()
        ) {
            "Watch wallet has no public identity"
        }
        require(chains.size <= MAX_CHAIN_ACCOUNTS) { "Too many watch chain identities" }
        require(chains.map(ChainAccountLocal::chainId).distinct().size == chains.size) {
            "Duplicate watch chain identity"
        }
        chains.forEach { chain ->
            require(chain.metaId == meta.id && chain.chainId.isNotBlank()) {
                "Watch chain is bound to the wrong wallet"
            }
            requireSupportedWatchChainId(chain.chainId)
            requireSubstrateIdentity(chain.publicKey, chain.cryptoType, chain.accountId)
        }
    }

    /** Shared by local watch enrollment and portable receiving for identical chain semantics. */
    fun requireSupportedWatchChainId(chainId: String) {
        // Named universal chains need their own public-identity proof, not Substrate derivation.
        require(chainId !in unsupportedNamedChains) {
            "Named universal watch chain identity is unsupported"
        }
    }

    private fun requireSubstrateIdentity(
        publicKey: ByteArray,
        crypto: CryptoType,
        accountId: ByteArray
    ) {
        require(publicKey.size == if (crypto == CryptoType.ECDSA) ECDSA_PUBLIC_KEY_BYTES else SUBSTRATE_KEY_BYTES) {
            "Invalid watch Substrate public key"
        }
        require(
            accountId.size == SUBSTRATE_KEY_BYTES &&
                publicKey.substrateAccountId().contentEquals(accountId)
        ) {
            "Watch Substrate key and account ID disagree"
        }
        if (crypto == CryptoType.ECDSA) {
            require(publicKey.isValidEthereumCompressedPublicKey()) { "Invalid watch ECDSA public key" }
        }
    }
}
