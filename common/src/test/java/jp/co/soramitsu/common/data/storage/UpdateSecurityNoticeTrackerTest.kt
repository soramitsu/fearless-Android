package jp.co.soramitsu.common.data.storage

import jp.co.soramitsu.common.data.storage.pin.InMemoryPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Each tracker below stands for one app launch reading the same stored preferences. */
class UpdateSecurityNoticeTrackerTest {
    private val preferences = InMemoryPreferences()

    @Test
    fun `fresh install never shows the notice`() {
        val firstLaunch = UpdateSecurityNoticeTracker(preferences)
        firstLaunch.recordStartup(hadStoredWallets = false)
        assertFalse(firstLaunch.isPending)

        // The wallet created during onboarding does not turn the install into an update.
        val nextLaunch = UpdateSecurityNoticeTracker(preferences)
        nextLaunch.recordStartup(hadStoredWallets = true)
        assertFalse(nextLaunch.isPending)
    }

    @Test
    fun `update shows the notice once and not after it was closed`() {
        val firstLaunch = UpdateSecurityNoticeTracker(preferences)
        firstLaunch.recordStartup(hadStoredWallets = true)
        assertTrue(firstLaunch.isPending)

        firstLaunch.acknowledge()
        assertFalse(firstLaunch.isPending)

        val nextLaunch = UpdateSecurityNoticeTracker(preferences)
        nextLaunch.recordStartup(hadStoredWallets = true)
        assertFalse(nextLaunch.isPending)
    }

    @Test
    fun `notice that was not closed shows at the next launch`() {
        UpdateSecurityNoticeTracker(preferences).recordStartup(hadStoredWallets = true)

        val nextLaunch = UpdateSecurityNoticeTracker(preferences)
        nextLaunch.recordStartup(hadStoredWallets = true)
        assertTrue(nextLaunch.isPending)
    }
}
