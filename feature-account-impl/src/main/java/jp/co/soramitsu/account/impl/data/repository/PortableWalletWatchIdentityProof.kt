package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.isValidEthereumCompressedPublicKey
import jp.co.soramitsu.common.utils.substrateAccountId

/**
 * Independent public-identity proof for watch wallets. This checks the address represented by
 * every present public key, but never claims key ownership or that a restored row has been
 * installed. A canonical iOS TON JSON address is accepted only with a V4R2 public-key match;
 * address-only TON and named universal chains remain unqualified.
 */
internal object PortableWalletWatchIdentityProof {
    private val role = PortableWalletSemanticMaterial.Role
    private val field = PortableWalletSemanticMaterial.FieldId
    private const val SUBSTRATE_ECOSYSTEM = 1
    private const val EVM_ECOSYSTEM = 2
    private const val TON_ECOSYSTEM = 3
    private const val CHAIN_ECOSYSTEM = 4
    private const val ECDSA_CRYPTO = 3
    private const val SUBSTRATE_KEY_BYTES = 32
    private const val COMPRESSED_KEY_BYTES = 33
    private const val EVM_ADDRESS_BYTES = 20
    private const val MAX_APPROVED_GENESIS = 128
    private const val BYTE_MASK = 0xff
    private val canonicalRawGenesis = Regex("[0-9a-f]{64}")

    fun verifyWallet(wallet: PortableWalletSemanticMaterial.Wallet): Int {
        require(wallet.slots.all { it.role == role.WATCH_IDENTITY || it.role == role.FAVORITE_CHAIN }) {
            "A watch wallet contains signing or original-source material"
        }
        return verifyPublicIdentities(wallet).also {
            require(it > 0) { "A watch wallet has no public identity" }
        }
    }

    /** A reviewed caller must derive this policy from the compiled Substrate inventory. */
    fun verifyReceivingWallet(
        wallet: PortableWalletSemanticMaterial.Wallet,
        approvedGenesis: List<PortableWalletChainSigningProof.ApprovedGenesis>,
    ): Int {
        require(
            approvedGenesis.size <= MAX_APPROVED_GENESIS &&
                approvedGenesis.map { it.id }.toSet().size == approvedGenesis.size
        ) { "Watch genesis policy is duplicated or oversized" }
        val approvedSubstrate = approvedGenesis.filter {
            it.identityKind == PortableWalletChainSigningProof.IdentityKind.SUBSTRATE
        }.map { it.id }.toSet()
        val count = verifyPublicIdentities(wallet)
        wallet.slots.filter { it.role == role.WATCH_IDENTITY && it.number(field.WATCH_ECOSYSTEM) == CHAIN_ECOSYSTEM }
            .forEach { slot ->
                val chainId = slot.text(field.WATCH_CHAIN_ID)
                // FPWMSM01/Android and the bundled chain registry use raw lowercase genesis IDs.
                // ApprovedGenesis uses a single 0x prefix; no incoming alias is normalized.
                require(canonicalRawGenesis.matches(chainId) && "0x$chainId" in approvedSubstrate) {
                    "Watch chain is not an approved canonical Substrate genesis"
                }
            }
        return count
    }

    private fun verifyPublicIdentities(wallet: PortableWalletSemanticMaterial.Wallet): Int {
        val watches = wallet.slots.filter { it.role == role.WATCH_IDENTITY }
        val seen = HashSet<String>()
        watches.forEach { slot ->
            val ecosystem = slot.number(field.WATCH_ECOSYSTEM)
            val identity = if (ecosystem == CHAIN_ECOSYSTEM) {
                "chain:${slot.text(field.WATCH_CHAIN_ID)}"
            } else {
                "root:$ecosystem"
            }
            require(seen.add(identity)) { "A watch wallet has duplicate public identities" }
            when (ecosystem) {
                SUBSTRATE_ECOSYSTEM, CHAIN_ECOSYSTEM -> verifySubstrate(slot)
                EVM_ECOSYSTEM -> verifyEvm(slot)
                TON_ECOSYSTEM -> verifyTon(slot)
                else -> error("Semantic codec accepted an unknown watch ecosystem")
            }
        }
        return watches.size
    }

    private fun verifySubstrate(slot: PortableWalletSemanticMaterial.Slot) {
        if (slot.number(field.WATCH_ECOSYSTEM) == CHAIN_ECOSYSTEM) {
            WalletCustodyProvenance.requireSupportedWatchChainId(slot.text(field.WATCH_CHAIN_ID))
        }
        val publicKey = slot.value(field.PUBLIC_KEY)
        val accountId = slot.value(field.ACCOUNT_ID_OR_ADDRESS)
        val ecdsa = slot.number(field.CRYPTO_TYPE) == ECDSA_CRYPTO
        require(publicKey.size == if (ecdsa) COMPRESSED_KEY_BYTES else SUBSTRATE_KEY_BYTES) {
            "Watch Substrate public key has an invalid length"
        }
        require(
            accountId.size == SUBSTRATE_KEY_BYTES &&
                publicKey.substrateAccountId().contentEquals(accountId)
        ) {
            "Watch Substrate key and account ID disagree"
        }
        if (ecdsa) {
            require(publicKey.isValidEthereumCompressedPublicKey()) {
                "Watch ECDSA public key is invalid"
            }
        }
    }

    private fun verifyEvm(slot: PortableWalletSemanticMaterial.Slot) {
        val address = slot.value(field.ACCOUNT_ID_OR_ADDRESS)
        require(address.size == EVM_ADDRESS_BYTES) { "Watch EVM address has an invalid length" }
        slot.optional(field.PUBLIC_KEY)?.let { publicKey ->
            require(
                publicKey.isValidEthereumCompressedPublicKey() &&
                    publicKey.ethereumAddressFromPublicKey().contentEquals(address)
            ) {
                "Watch EVM key and address disagree"
            }
        }
    }

    private fun verifyTon(slot: PortableWalletSemanticMaterial.Slot) {
        val publicKey = requireNotNull(slot.optional(field.PUBLIC_KEY)) {
            "Watch TON identity has no public key"
        }
        PortableWalletTonAddressProof.verifyV4R2(
            publicKey,
            slot.value(field.ACCOUNT_ID_OR_ADDRESS),
            slot.number(field.TON_ADDRESS_ENCODING),
            slot.number(field.TON_CONTRACT_VERSION),
        )
    }

    private fun PortableWalletSemanticMaterial.Slot.value(id: Int): ByteArray = fields.single { it.id == id }.value

    private fun PortableWalletSemanticMaterial.Slot.optional(id: Int): ByteArray? =
        fields.singleOrNull { it.id == id }?.value

    private fun PortableWalletSemanticMaterial.Slot.number(id: Int): Int = value(id)[0].toInt() and BYTE_MASK

    private fun PortableWalletSemanticMaterial.Slot.text(id: Int): String = value(id).toString(Charsets.UTF_8)
}
