package jp.co.soramitsu.account.impl.domain

import jp.co.soramitsu.common.data.storage.Preferences

/**
 * Remembers wallets created from words this app just generated. Nothing can have been sent to
 * their no-password addresses, so their universal accounts take the password without a probe.
 *
 * The mark belongs to the one wallet the create flow made: [begin] marks the words before the
 * row exists, so the account upgrade never sees the new wallet unmarked, and [bind] moves the
 * mark to the id the create returned. Any import of the same words, a failed create and the
 * wallet's deletion clear it, so a later wallet with these words is probed like any other.
 */
class UniversalWalletFreshRoots(private val preferences: Preferences) {

    fun begin(substratePublicKey: ByteArray) = preferences.putString(pendingKey(substratePublicKey), PENDING)

    fun bind(substratePublicKey: ByteArray, metaId: Long) {
        preferences.putString(walletKey(metaId), substratePublicKey.toHex())
        preferences.putString(pendingKey(substratePublicKey), null)
    }

    /** For an import of these words or a create that failed: no wallet with them is fresh. */
    fun abandon(substratePublicKey: ByteArray) = preferences.putString(pendingKey(substratePublicKey), null)

    fun isCreated(metaId: Long, substratePublicKey: ByteArray?): Boolean = substratePublicKey != null && (
        preferences.getString(walletKey(metaId)) == substratePublicKey.toHex() ||
            preferences.getString(pendingKey(substratePublicKey)) == PENDING
        )

    /** Once every slot exists, or when the wallet is deleted. */
    fun forget(metaId: Long, substratePublicKey: ByteArray?) {
        preferences.putString(walletKey(metaId), null)
        substratePublicKey?.let { preferences.putString(pendingKey(it), null) }
    }

    private fun pendingKey(substratePublicKey: ByteArray): String = PENDING_KEY_PREFIX + substratePublicKey.toHex()

    private fun walletKey(metaId: Long): String = "$WALLET_KEY_PREFIX$metaId"

    private fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    private companion object {
        const val PENDING_KEY_PREFIX = "universal_wallet_fresh_root.v2.pending."
        const val WALLET_KEY_PREFIX = "universal_wallet_fresh_root.v2.wallet."
        const val PENDING = "pending"
    }
}
