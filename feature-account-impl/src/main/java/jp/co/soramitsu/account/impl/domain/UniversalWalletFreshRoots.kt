package jp.co.soramitsu.account.impl.domain

import jp.co.soramitsu.common.data.storage.Preferences

/**
 * Remembers wallets created from words this app just generated. Nothing can have been sent to
 * their no-password addresses, so their universal accounts take the password without a probe.
 * Keyed by the Substrate public key, which exists before the wallet row does, so the account
 * upgrade can never see a new wallet without its mark.
 */
class UniversalWalletFreshRoots(private val preferences: Preferences) {

    fun markCreated(substratePublicKey: ByteArray) = preferences.putString(key(substratePublicKey), CREATED)

    fun isCreated(substratePublicKey: ByteArray?): Boolean =
        substratePublicKey != null && preferences.getString(key(substratePublicKey)) == CREATED

    /** Once every slot exists; a later import of the same words is probed like any other. */
    fun forget(substratePublicKey: ByteArray) = preferences.putString(key(substratePublicKey), null)

    private fun key(substratePublicKey: ByteArray): String =
        KEY_PREFIX + substratePublicKey.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    private companion object {
        const val KEY_PREFIX = "universal_wallet_fresh_root.v1."
        const val CREATED = "created"
    }
}
