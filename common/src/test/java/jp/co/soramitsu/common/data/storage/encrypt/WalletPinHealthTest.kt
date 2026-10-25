package jp.co.soramitsu.common.data.storage.encrypt

import org.junit.Assert.assertEquals
import org.junit.Test

class WalletPinHealthTest {
    @Test
    fun `only six ASCII digits are a healthy stored PIN`() {
        listOf("000000", "123456", "999999").forEach {
            assertEquals(PinHealth.HEALTHY, PinHealth.assess(true, it))
        }
        listOf(null, "", "12345", "1234567", "12a456", "１２３４５６", "12345 ").forEach {
            assertEquals(PinHealth.UNREADABLE, PinHealth.assess(true, it))
        }
        assertEquals(PinHealth.ABSENT, PinHealth.assess(false, null))
    }

    @Test
    fun `unverified master key clears the previously published PIN health`() {
        WalletPinHealth.replace(PinHealth.HEALTHY)
        WalletPinHealth.replace(null)
        assertEquals(null, WalletPinHealth.health.value)
    }
}
