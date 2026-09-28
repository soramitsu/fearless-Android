package jp.co.soramitsu.coredb.model

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

/**
 * Canonical, versioned binding to signing-relevant public identity only. Names, order, favorites,
 * selection and initialized flags are presentation state and may change without changing custody.
 */
object WalletCustodyBinding {
    private val domain = "fearless-android-wallet-custody-v1\u0000".toByteArray(Charsets.US_ASCII)
    private const val MAX_CHAINS = 128
    private const val MAX_CHAIN_ID_BYTES = 2_048
    private const val MAX_PUBLIC_BYTES = 128
    private const val COMPLETE_SUBSTRATE_IDENTITY_PARTS = 3

    fun sha256(meta: MetaAccountLocal, chains: List<ChainAccountLocal>): ByteArray {
        require(meta.id > 0) { "Custody binding requires a durable wallet ID" }
        require(chains.size <= MAX_CHAINS) { "Custody binding has too many chain accounts" }
        require(
            listOf(meta.substratePublicKey, meta.substrateCryptoType, meta.substrateAccountId)
                .count { it != null } in setOf(0, COMPLETE_SUBSTRATE_IDENTITY_PARTS)
        ) { "Incomplete Substrate custody identity" }
        require(meta.ethereumPublicKey == null || meta.ethereumAddress != null) {
            "Incomplete Ethereum custody identity"
        }
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(domain)
        digest.long(meta.id)
        digest.bytes(meta.substratePublicKey)
        digest.text(meta.substrateCryptoType?.name)
        digest.bytes(meta.substrateAccountId)
        digest.bytes(meta.ethereumPublicKey)
        digest.bytes(meta.ethereumAddress)
        digest.bytes(meta.tonPublicKey)
        val ordered = chains.sortedBy(ChainAccountLocal::chainId)
        require(ordered.zipWithNext().all { (left, right) -> left.chainId != right.chainId }) {
            "Duplicate custody chain identity"
        }
        digest.int(ordered.size)
        ordered.forEach { chain ->
            require(chain.metaId == meta.id) { "Wrong custody chain owner" }
            digest.text(chain.chainId)
            digest.bytes(chain.publicKey)
            digest.bytes(chain.accountId)
            digest.text(chain.cryptoType.name)
        }
        return digest.digest()
    }

    private fun MessageDigest.long(value: Long) {
        update(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(value).array())
    }

    private fun MessageDigest.int(value: Int) {
        update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value).array())
    }

    private fun MessageDigest.bytes(value: ByteArray?) {
        if (value == null) {
            int(-1)
            return
        }
        require(value.size in 1..MAX_PUBLIC_BYTES) { "Invalid custody public identity size" }
        int(value.size)
        update(value)
    }

    private fun MessageDigest.text(value: String?) {
        if (value == null) {
            int(-1)
            return
        }
        val bytes = Charsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(java.nio.CharBuffer.wrap(value))
        require(bytes.remaining() in 1..MAX_CHAIN_ID_BYTES) { "Invalid custody identity text" }
        int(bytes.remaining())
        update(bytes)
    }
}
