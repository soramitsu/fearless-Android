package jp.co.soramitsu.common.data.storage

import javax.inject.Inject

/**
 * Remembers whether people who updated the app still have to see the security notice:
 * their mobile wallets are safe, and only funds in wallets used with the old Chrome
 * extension need moving.
 */
class UpdateSecurityNoticeTracker @Inject constructor(
    private val preferences: Preferences
) {
    /** True until the notice has been shown and closed. */
    val isPending: Boolean
        get() = preferences.getString(KEY) == PENDING

    /**
     * Called at every startup once it is known whether a wallet is stored, before onboarding
     * can add one. Only the first call decides: an install that already had wallets was updated
     * and gets the notice; one without (a fresh install) never does.
     */
    fun recordStartup(hadStoredWallets: Boolean) {
        if (preferences.getString(KEY) in DECIDED) return

        preferences.putString(KEY, if (hadStoredWallets) PENDING else DONE)
    }

    /** The user closed the notice, so it is not shown again. */
    fun acknowledge() {
        preferences.putString(KEY, DONE)
    }

    private companion object {
        const val KEY = "update_security_notice"
        const val PENDING = "pending"
        const val DONE = "done"
        val DECIDED = setOf(PENDING, DONE)
    }
}
