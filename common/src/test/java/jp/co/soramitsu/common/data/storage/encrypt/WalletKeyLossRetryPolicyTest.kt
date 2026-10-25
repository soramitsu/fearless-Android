package jp.co.soramitsu.common.data.storage.encrypt

import org.junit.Assert.*
import org.junit.Test

class WalletKeyLossRetryPolicyTest {
    @Test
    fun `only two failures across known device boots qualify`() {
        assertTrue(WalletKeyLossRetryPolicy.mayOffer(4, 5, 2))
        listOf(Triple(-1, 5, 2), Triple(4, 4, 2), Triple(4, 3, 3), Triple(4, 5, 1)).forEach {
            assertFalse(WalletKeyLossRetryPolicy.mayOffer(it.first, it.second, it.third))
        }
    }

    @Test
    fun `consent is bound to exact value boot and five minute lifetime`() {
        val consent = WalletKeyLossConsent("fingerprint", 5, 1000)
        WalletKeyLossRetryPolicy.requireCurrent(consent, "fingerprint", 5, 1000)
        WalletKeyLossRetryPolicy.requireCurrent(consent, "fingerprint", 5, 301000)
        listOf(Triple("changed", 5, 1000L), Triple("fingerprint", 6, 1000L),
            Triple("fingerprint", 5, 999L), Triple("fingerprint", 5, 301001L)).forEach {
            assertThrows(IllegalArgumentException::class.java) {
                WalletKeyLossRetryPolicy.requireCurrent(consent, it.first, it.second, it.third)
            }
        }
        assertFalse(consent.toString().contains("fingerprint"))
    }
}
